package com.github.hmdev.preview;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 * 指定フォルダ配下の EPUB を集めて本棚の一覧を作る。
 *
 * <p><b>EPUB は展開しない。</b>ZIP のエントリを直接読んで
 * {@code META-INF/container.xml} → OPF の順に辿り、書誌と表紙の位置だけを取る。
 * 数百冊を並べるのに全冊を展開するとディスクと時間を浪費するため、
 * 展開は一覧から選択したときに初めて行う (遅延展開)。</p>
 *
 * <p>解釈が展開経路とずれないよう、OPF の読み取りは
 * {@link OpfParser#parse(Document, String)} を共用する。</p>
 */
public class LibraryScanner
{
	private static final Logger logger = LoggerFactory.getLogger(LibraryScanner.class);

	/**
	 * 再帰の深さ上限。
	 * 出力フォルダを指定したつもりがホーム直下だった、という誤操作で
	 * ディスク全体を舐めないための歯止め。
	 */
	public static final int DEFAULT_MAX_DEPTH = 8;

	/**
	 * 一覧に載せる上限冊数。
	 * 超えた分は捨てて警告を出す (黙って切り詰めると「全部見えている」と誤解される)。
	 */
	public static final int MAX_BOOKS = 2000;

	/**
	 * 走査中に覚えておく候補パスの上限を決める倍率。
	 *
	 * <p>上限を「候補」ではなく「読めた本」で数えるため、壊れた本が混ざっていても
	 * {@link #MAX_BOOKS} 冊に届くだけの候補を集めておく必要がある。
	 * 一方で候補を無制限に溜めると、フォルダを取り違えたときに巨大なツリーを
	 * 最後まで舐めることになるので倍率で頭を押さえる。</p>
	 */
	static final int CANDIDATE_MULTIPLIER = 10;

	private LibraryScanner() {}

	/** 拡張子から EPUB とみなせるか。Kobo の {@code .kepub.epub} も対象 */
	public static boolean isEpub(Path file)
	{
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".epub") && name.length() > ".epub".length();
	}

	/**
	 * フォルダ配下を再帰スキャンして本棚の一覧を作る。
	 *
	 * @param root 走査の起点
	 * @param maxDepth 再帰の深さ上限
	 * @param cache 前回のインデックス。サイズと更新時刻が一致する本は再パースを省く。null 可
	 * @return パスの昇順に並べた一覧 (読めなかった EPUB は除外)
	 */
	public static List<LibraryEntry> scan(Path root, int maxDepth, LibraryIndexCache cache) throws IOException
	{
		return scan(root, maxDepth, cache, MAX_BOOKS);
	}

	/** 上限冊数を指定できる版 (テスト用) */
	static List<LibraryEntry> scan(Path root, int maxDepth, LibraryIndexCache cache, int maxBooks)
		throws IOException
	{
		if (root == null || !Files.isDirectory(root)) {
			throw new IOException("フォルダが見つかりません: " + root);
		}
		List<Path> files = collectEpubFiles(root, maxDepth, maxBooks * CANDIDATE_MULTIPLIER);
		files.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));

		List<LibraryEntry> entries = new ArrayList<>(Math.min(files.size(), maxBooks));
		for (Path file : files) {
			// 上限は「候補」ではなく「読めた本」で数える。候補で数えると、
			// 壊れた .epub が先に並んでいるだけで、後ろの正常な本が丸ごと落ちる
			if (entries.size() >= maxBooks) {
				logger.warn("本棚の上限 {} 冊に達したため、以降の本は一覧に含めません: {}", maxBooks, root);
				break;
			}
			LibraryEntry entry = readOrReuse(file, cache);
			if (entry != null) entries.add(entry);
		}
		return entries;
	}

	/** キャッシュが使えればそれを、駄目なら ZIP から読み直す。読めない EPUB は null */
	private static LibraryEntry readOrReuse(Path file, LibraryIndexCache cache)
	{
		long size;
		long modified;
		try {
			size = Files.size(file);
			modified = Files.getLastModifiedTime(file).toMillis();
		} catch (IOException e) {
			/* 意図的: スキャン中に消された等。その 1 冊を諦めて続行する */
			logger.debug("状態を取得できないためスキップします: {}", file, e);
			return null;
		}
		if (cache != null) {
			LibraryEntry cached = cache.get(file);
			if (cached != null && cached.matches(size, modified)) return cached;
		}
		try {
			return read(file, size, modified);
		} catch (IOException | RuntimeException e) {
			// 壊れた EPUB や EPUB でない .epub が 1 つあっても本棚全体を落とさない
			logger.warn("EPUB の書誌を読めませんでした: {} ({})", file, e.toString());
			return null;
		}
	}

	/** ZIP を展開せずに書誌と表紙の位置を読む */
	static LibraryEntry read(Path file, long size, long modifiedMillis) throws IOException
	{
		// ZIP のエントリ名は UTF-8 とは限らないが、EPUB (OCF) は UTF-8 必須
		try (ZipFile zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
			Document containerDoc = XmlUtils.parse(
				readEntry(zip, "META-INF/container.xml"), "META-INF/container.xml");
			String opfPath = OpfParser.findOpfPath(containerDoc);
			OpfPackage opf = OpfParser.parse(XmlUtils.parse(readEntry(zip, opfPath), opfPath), opfPath);

			String coverEntry = opf.getCoverImagePath();
			// manifest にあっても ZIP に無いことがある。無い表紙を持たせると
			// サムネイル要求のたびに 404 になるので、ここで確かめて落とす
			if (coverEntry != null && zip.getEntry(coverEntry) == null) {
				logger.debug("表紙が ZIP に見つかりません: {} ({})", coverEntry, file);
				coverEntry = null;
			}
			return new LibraryEntry(file.toAbsolutePath().normalize(), size, modifiedMillis,
				opf.getTitle(), opf.getCreator(), coverEntry);
		}
	}

	/**
	 * ZIP エントリを読み出す。
	 * 高圧縮の悪意ある EPUB で巨大な展開を起こさないよう、宣言サイズと実読み込み量の
	 * 両方で {@link XmlUtils#MAX_METADATA_BYTES} を超えたら打ち切る。
	 */
	static byte[] readEntry(ZipFile zip, String name) throws IOException
	{
		ZipEntry entry = zip.getEntry(name);
		if (entry == null) throw new IOException("EPUB 内に " + name + " がありません");
		if (entry.getSize() > XmlUtils.MAX_METADATA_BYTES) {
			throw new IOException("メタデータが大きすぎます (" + entry.getSize() + " bytes): " + name);
		}
		try (InputStream is = zip.getInputStream(entry)) {
			// 宣言サイズは信用できないので、実際の読み込み量でも上限を掛ける。
			// 上限 + 1 バイト読んで、超えていたら不正とみなす。
			// toIntExact にしておくと、将来 MAX_METADATA_BYTES を上げたときに
			// 黙って負値に化けず即座に落ちる
			byte[] bytes = is.readNBytes(Math.toIntExact(XmlUtils.MAX_METADATA_BYTES + 1));
			if (bytes.length > XmlUtils.MAX_METADATA_BYTES) {
				throw new IOException("メタデータが大きすぎます: " + name);
			}
			return bytes;
		}
	}

	/** 配下の .epub を集める。読めないディレクトリがあっても走査を止めない */
	private static List<Path> collectEpubFiles(Path root, int maxDepth, int maxCandidates) throws IOException
	{
		List<Path> files = new ArrayList<>();
		// ディレクトリのシンボリックリンクは辿らない。ループと本棚の外への脱出を避けるため
		Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Math.max(1, maxDepth),
			new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
				{
					if (!isEpub(file) || !isReadableFile(file, attrs)) return FileVisitResult.CONTINUE;
					files.add(file.toAbsolutePath().normalize());
					if (files.size() >= maxCandidates) {
						logger.warn("候補が {} 件に達したため走査を打ち切ります: {}", maxCandidates, root);
						return FileVisitResult.TERMINATE;
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException e)
				{
					/* 意図的: 権限不足などで読めない場所は飛ばして走査を続ける */
					logger.debug("走査できませんでした: {}", file, e);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException e)
				{
					// SimpleFileVisitor の既定実装は、ディレクトリの列挙が I/O エラーで
					// 中断した場合その例外を再スローする。ネットワークドライブや
					// 走査中に消えたフォルダで本棚全体が落ちてしまうため握り潰す
					if (e != null) logger.debug("走査を完了できませんでした: {}", dir, e);
					return FileVisitResult.CONTINUE;
				}
			});
		return files;
	}

	/**
	 * 読み出せる通常ファイルか。
	 *
	 * <p>リンクを辿らずに走査しているため、<b>ファイルへのシンボリックリンクは
	 * 属性上「通常ファイル」にならない</b>。Linux / macOS で本棚に本を集める運用では
	 * リンクを張るのが普通なので、リンク先が通常ファイルなら受け入れる。
	 * ディレクトリのリンクを辿らない方針 (ループ回避) はそのまま。</p>
	 */
	private static boolean isReadableFile(Path file, BasicFileAttributes attrs)
	{
		if (attrs.isRegularFile()) return true;
		return attrs.isSymbolicLink() && Files.isRegularFile(file);
	}
}
