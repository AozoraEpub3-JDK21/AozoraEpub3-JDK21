package com.github.hmdev.preview;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1 回のプレビューの寿命を束ねる。
 *
 * <p>本 (EPUB) は登録だけ先に行い、<b>実際に表示されたときに初めて展開する</b>
 * (遅延展開)。Phase 2 の本棚で数百冊を登録しても展開コストが発生しないようにするため。</p>
 */
public class PreviewSession implements AutoCloseable
{
	private static final Logger logger = LoggerFactory.getLogger(PreviewSession.class);

	/** 一時ディレクトリの親。前回の残骸を起動時に掃除する目印にもなる */
	static final String TEMP_PREFIX = "aozoraepub3-preview";

	/**
	 * セッションが生存中であることを示すロックファイル。
	 * 複数のプレビュー (GUI と CLI の同時起動など) が並行しうるため、
	 * 掃除は「ロックを取得できたディレクトリ = 持ち主が居ない」ものだけに限定する。
	 */
	static final String LOCK_FILE = ".lock";

	/**
	 * この JVM が保持しているセッションの展開先。
	 * 掃除の際にファイルロックを触らずに除外するために使う (POSIX のロック解放問題を避けるため)。
	 */
	private static final Set<Path> LIVE_ROOTS = ConcurrentHashMap.newKeySet();

	/**
	 * ディレクトリ作成から {@link #LOCK_FILE} 生成までの競合窓を吸収する猶予時間。
	 * この時間内に作られた「.lock を持たないディレクトリ」は残骸とみなさない。
	 */
	static final long ORPHAN_GRACE_MILLIS = 60_000L;

	/** 登録済みの本 1 冊 */
	public static class Book
	{
		final String id;
		final Path epubFile;
		/** 展開先。再展開のたびに新しいディレクトリへ切り替える。展開前は null */
		private Path dir;
		/** 展開先ディレクトリの通し番号 */
		private int version;
		private OpfPackage opf;
		private List<TocEntry> toc;
		/** 展開したときの EPUB のサイズと更新時刻。再変換の検知に使う */
		private long extractedSize = -1;
		private FileTime extractedModified;

		Book(String id, Path epubFile)
		{
			this.id = id;
			this.epubFile = epubFile;
		}

		public String getId() { return this.id; }
		public Path getEpubFile() { return this.epubFile; }
		public Path getDir() { return this.dir; }
		public OpfPackage getOpf() { return this.opf; }
		public List<TocEntry> getToc() { return this.toc; }

		/** 表示名。展開前はファイル名、展開後は書名 */
		public String getDisplayName()
		{
			if (this.opf != null && this.opf.getTitle() != null && !this.opf.getTitle().isEmpty()) {
				return this.opf.getTitle();
			}
			return this.epubFile.getFileName().toString();
		}
	}

	private final Path root;
	private final String token;
	private final Map<String, Book> books = new LinkedHashMap<>();
	private final FontCatalog fontCatalog;
	private final FileChannel lockChannel;
	private final FileLock lock;
	private int nextBookNumber = 1;
	private String defaultBookId;

	public PreviewSession() throws IOException
	{
		cleanupOrphans();
		this.root = Files.createTempDirectory(TEMP_PREFIX + "-");

		// 展開先を作ったら、他プロセスの掃除から守る処理を「真っ先に」済ませる。
		// ここに時間のかかる処理を挟むと、その間このディレクトリは
		// 「.lock を持たない残骸」に見え、同時起動した別プロセスに消される
		this.lockChannel = FileChannel.open(this.root.resolve(LOCK_FILE),
			StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		FileLock acquired;
		try {
			acquired = this.lockChannel.tryLock();
		} catch (IOException e) {
			/* 意図的: ロックできない環境でもプレビュー自体は動かす */
			logger.debug("プレビュー一時ディレクトリのロックに失敗しました: {}", this.root, e);
			acquired = null;
		}
		this.lock = acquired;
		LIVE_ROOTS.add(this.root.toAbsolutePath().normalize());

		this.token = UUID.randomUUID().toString().replace("-", "");
		// FontCatalog.detect() は Windows の初回呼び出しで数秒かかることがあるため、
		// 必ず上の保護を済ませてから呼ぶ
		this.fontCatalog = FontCatalog.detect();
	}

	public Path getRoot() { return this.root; }
	public String getToken() { return this.token; }
	public FontCatalog getFontCatalog() { return this.fontCatalog; }
	public String getDefaultBookId() { return this.defaultBookId; }
	public List<Book> getBooks() { return new ArrayList<>(this.books.values()); }

	/**
	 * 本を登録する。この時点では展開しない。
	 *
	 * @return 採番された bookId。URL に載るのはこの不透明 ID だけで、
	 *         ユーザーが指定したパスはそのまま URL に現れない
	 */
	public synchronized String addBook(Path epubFile)
	{
		// normalize しないと out/./book.epub と out/book.epub が別扱いになり二重展開される
		Path absolute = epubFile.toAbsolutePath().normalize();
		// 同じ EPUB を繰り返しプレビューしても展開先が増えないようにする
		// (GUI のボタンを押すたびに dakuten フォント 222 本が複製されるのを避ける)
		for (Book existing : this.books.values()) {
			if (existing.epubFile.equals(absolute)) return existing.id;
		}
		String id = "b" + (this.nextBookNumber++);
		this.books.put(id, new Book(id, absolute));
		if (this.defaultBookId == null) this.defaultBookId = id;
		return id;
	}

	/** bookId から本を引く。未登録なら null */
	public synchronized Book getBook(String bookId)
	{
		return this.books.get(bookId);
	}

	/**
	 * 本を展開し、OPF と目次を解析する。既に展開済みなら何もしない。
	 *
	 * @return 展開済みの本
	 */
	public synchronized Book ensureExtracted(String bookId) throws IOException
	{
		Book book = this.books.get(bookId);
		if (book == null) throw new IOException("未登録の書籍です: " + bookId);

		// 展開済みなら、元 EPUB が消えていても展開結果を配り続ける。
		// serveBookFile はファイル要求のたびにここを通るため、
		// 元ファイルの有無を必須にすると kindlegen が .epub を消した瞬間に
		// 表示中のプレビューが丸ごと 500 になってしまう
		if (book.opf != null && !isSourceChanged(book)) return book;

		if (!Files.isRegularFile(book.epubFile)) {
			if (book.opf != null) return book;
			throw new IOException("EPUB が見つかりません: " + book.epubFile);
		}
		long size = Files.size(book.epubFile);
		FileTime modified = Files.getLastModifiedTime(book.epubFile);

		// 新しいディレクトリへ展開し、成功してから切り替える。
		// 先に消してしまうと、変換で書き込み途中の .epub を掴んだときに
		// 直前まで正常だった展開結果まで失われて表示できなくなる
		Path staging = this.root.resolve(book.id + "-v" + (book.version + 1));
		deleteRecursively(staging);
		OpfPackage opf;
		List<TocEntry> toc;
		try {
			EpubExtractor.extract(book.epubFile, staging);
			opf = OpfParser.parse(staging);
			toc = TocParser.parse(staging, opf);
		} catch (IOException | RuntimeException e) {
			deleteRecursively(staging);
			throw e;
		}

		Path previous = book.dir;
		book.version++;
		book.dir = staging;
		book.opf = opf;
		book.toc = toc;
		book.extractedSize = size;
		book.extractedModified = modified;
		if (previous != null) deleteRecursively(previous);
		return book;
	}

	/**
	 * 展開したときから元 EPUB が差し替わっているか。
	 * 状態を取得できない (消された・アクセスできない) 場合は「変わっていない」とみなし、
	 * 展開済みの内容をそのまま配れるようにする。
	 */
	private static boolean isSourceChanged(Book book)
	{
		try {
			if (!Files.isRegularFile(book.epubFile)) return false;
			return book.extractedSize != Files.size(book.epubFile)
				|| !Files.getLastModifiedTime(book.epubFile).equals(book.extractedModified);
		} catch (IOException e) {
			/* 意図的: 判定できないときは展開済みを使い続ける */
			logger.debug("EPUB の更新確認に失敗しました: {}", book.epubFile, e);
			return false;
		}
	}

	/** セッション情報を JSON で返す */
	public synchronized String sessionJson()
	{
		StringBuilder buf = new StringBuilder(1024);
		buf.append('{');
		Json.prop(buf, "token", this.token);
		Json.prop(buf, "defaultBookId", this.defaultBookId);
		Json.key(buf, "fonts");
		this.fontCatalog.toJson(buf);
		Json.key(buf, "books");
		buf.append('[');
		boolean first = true;
		for (Book book : this.books.values()) {
			if (!first) buf.append(',');
			first = false;
			buf.append('{');
			Json.prop(buf, "id", book.id);
			Json.prop(buf, "name", book.getDisplayName());
			// EPUB の絶対パスは公開しない (ビューアーは使わないため)
			Json.prop(buf, "fileName", book.epubFile.getFileName().toString());
			buf.append('}');
		}
		buf.append(']');
		buf.append('}');
		return buf.toString();
	}

	/** 本の spine と目次を JSON で返す (未展開なら展開する) */
	public String bookJson(String bookId) throws IOException
	{
		Book book = ensureExtracted(bookId);
		StringBuilder buf = new StringBuilder(4096);
		buf.append('{');
		Json.prop(buf, "id", book.id);
		Json.prop(buf, "title", book.getDisplayName());
		Json.prop(buf, "creator", book.opf.getCreator());
		Json.prop(buf, "pageProgressionDirection", book.opf.getPageProgressionDirection());
		Json.key(buf, "spine");
		buf.append('[');
		List<SpineItem> spine = book.opf.getSpine();
		for (int i = 0; i < spine.size(); i++) {
			if (i > 0) buf.append(',');
			buf.append('{');
			Json.prop(buf, "path", spine.get(i).path());
			Json.prop(buf, "mediaType", spine.get(i).mediaType());
			buf.append('}');
		}
		buf.append(']');
		Json.key(buf, "toc");
		TocParser.toJson(buf, book.toc);
		buf.append('}');
		return buf.toString();
	}

	/** インスペクタ情報を JSON で返す (未展開なら展開する) */
	public String inspectJson(String bookId) throws IOException
	{
		Book book = ensureExtracted(bookId);
		return new EpubInspection(book.dir, book.opf, book.epubFile).toJson();
	}

	@Override
	public void close()
	{
		// ロックを解放し切るまでは LIVE_ROOTS に残しておく。
		// 先に外すと、その隙間に同一 JVM の掃除が .lock を open/close して
		// POSIX の fcntl ロック全解放を踏む
		try {
			if (this.lock != null && this.lock.isValid()) this.lock.release();
			this.lockChannel.close();
		} catch (IOException e) {
			/* 意図的: 解放できなくても後続の削除と次回起動時の掃除で回収する */
			logger.debug("ロックの解放に失敗しました: {}", this.root, e);
		}
		LIVE_ROOTS.remove(this.root.toAbsolutePath().normalize());
		deleteRecursively(this.root);
	}

	/**
	 * 過去のプロセスが残した一時ディレクトリを掃除する。
	 * Windows ではブラウザがフォントを掴んだまま JVM が終了して削除に失敗することがあるため、
	 * 起動時にも回収する。
	 *
	 * <p>GUI と CLI を同時に使う等、プレビューは並行して動きうる。
	 * <b>ロックを取得できたディレクトリだけ</b>を削除することで、
	 * 実行中の他セッションの展開先を消さないようにする。</p>
	 */
	static void cleanupOrphans()
	{
		Path tmp = Path.of(System.getProperty("java.io.tmpdir", "."));
		try (Stream<Path> children = Files.list(tmp)) {
			children.filter(Files::isDirectory)
				.filter(path -> path.getFileName().toString().startsWith(TEMP_PREFIX + "-"))
				.sorted(Comparator.comparing(Path::toString))
				.forEach(PreviewSession::deleteIfNotInUse);
		} catch (IOException e) {
			/* 意図的: 掃除は best-effort。失敗してもプレビュー自体は継続する */
			logger.debug("プレビュー一時ディレクトリの掃除に失敗しました", e);
		}
	}

	/** 持ち主の居ないディレクトリだけを削除する */
	static void deleteIfNotInUse(Path dir)
	{
		// 同じ JVM が持っているセッションには触れない。
		// POSIX の fcntl ロックは「同一プロセスが対象ファイルの fd を 1 つでも閉じると
		// そのプロセスのロックが全て解放される」ため、判定のためにチャネルを開いて閉じるだけで
		// 生存中セッションのロックが外れてしまう (Windows では起きない)。
		if (LIVE_ROOTS.contains(dir.toAbsolutePath().normalize())) {
			logger.debug("同一 JVM の生存セッションなのでスキップします: {}", dir);
			return;
		}
		// ディレクトリ作成 → .lock 生成 → tryLock は原子的ではない。
		// その途中の状態 (.lock がまだ無い / .lock はあるがまだロックしていない) を
		// 残骸と誤認して消さないよう、作成直後は .lock の有無に関わらず触らない
		if (isYoungerThanGrace(dir)) {
			logger.debug("作成直後の可能性があるためスキップします: {}", dir);
			return;
		}
		Path lockFile = dir.resolve(LOCK_FILE);
		// notExists は「無いと確認できた」ときだけ true。exists の否定だと、権限不足で
		// 確認できない場合 (POSIX の共有 /tmp で他ユーザーの 700 ディレクトリ) も
		// 「.lock 無し = 残骸」と誤判定して生存セッションを消しうる (Windows の %TEMP% は
		// ユーザー毎なので起きない = OS 依存の穴になる)
		if (Files.notExists(lockFile)) {
			// 十分に古く .lock も無いなら、古い版が残した残骸。回収してよい
			deleteRecursively(dir);
			return;
		}
		try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
			FileLock acquired = channel.tryLock();
			if (acquired == null) {
				// 他プロセスが使用中なので触らない
				logger.debug("使用中のためスキップします: {}", dir);
				return;
			}
			acquired.release();
		} catch (IOException | OverlappingFileLockException e) {
			/* 意図的: 判定できないものは安全側に倒して残す */
			logger.debug("使用状況を判定できないためスキップします: {}", dir, e);
			return;
		}
		deleteRecursively(dir);
	}

	/** 作成直後かもしれないディレクトリか (.lock 生成までの競合窓を避けるための猶予判定) */
	static boolean isYoungerThanGrace(Path dir)
	{
		try {
			long age = System.currentTimeMillis() - Files.getLastModifiedTime(dir).toMillis();
			return age < ORPHAN_GRACE_MILLIS;
		} catch (IOException e) {
			/* 意図的: 判定できないものは安全側に倒して残す */
			logger.debug("更新時刻を取得できないためスキップします: {}", dir, e);
			return true;
		}
	}

	/** ディレクトリを再帰削除する。失敗しても致命エラーにしない */
	static void deleteRecursively(Path path)
	{
		if (path == null || !Files.exists(path)) return;
		try (Stream<Path> walk = Files.walk(path)) {
			walk.sorted(Comparator.reverseOrder()).forEach(entry -> {
				try {
					Files.deleteIfExists(entry);
				} catch (IOException e) {
					/* 意図的: 使用中ファイルは次回起動時の掃除で回収する */
					logger.debug("削除できませんでした: {}", entry);
				}
			});
		} catch (IOException e) {
			/* 意図的: 同上 */
			logger.debug("再帰削除に失敗しました: {}", path, e);
		}
	}
}
