package com.github.hmdev.info;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web から取った作品 1 つの台帳。作品のフォルダ（キャッシュの中の、変換した txt と update.txt のあるフォルダ）に
 * {@value #FILE_NAME} として置く。
 *
 * <p>掲載先で題が変わっても（「【書籍化】」が付く、など）同じ本として扱えるように、
 * identifier と出力のファイル名は台帳に初めて書いたときの値を使い続ける（internal #11）。
 * identifier は掲載元の URL だけから決めるので、台帳を消しても同じ値に戻る。</p>
 */
public final class BookLedger
{
	static final Logger logger = LoggerFactory.getLogger(BookLedger.class);

	/** 作品のフォルダに置く台帳のファイル名 */
	public static final String FILE_NAME = "book.properties";

	static final String KEY_SOURCE_URL = "sourceUrl";
	static final String KEY_IDENTIFIER = "identifier";
	static final String KEY_OUTPUT_BASE_NAME = "outputBaseName";

	/** 掲載元の URL（最初に取ったときのもの） */
	public final String sourceUrl;
	/** dc:identifier に使う UUID */
	public final String identifier;
	/** 出力のファイル名（拡張子なし）。"[作者] 題" */
	public final String outputBaseName;

	BookLedger(String sourceUrl, String identifier, String outputBaseName)
	{
		this.sourceUrl = sourceUrl;
		this.identifier = identifier;
		this.outputBaseName = outputBaseName;
	}

	/** 新しい作品の台帳。identifier は URL から決める */
	public static BookLedger create(String sourceUrl, String outputBaseName)
	{
		return new BookLedger(sourceUrl, identifierFor(sourceUrl), outputBaseName);
	}

	/**
	 * URL から identifier を決める。http と https、ホスト名の大文字小文字、末尾の / と # 以降の違いは同じ作品とみなす。
	 * クエリは作品を分けるサイトがあるので残す
	 */
	public static String identifierFor(String sourceUrl)
	{
		return UUID.nameUUIDFromBytes(("AozoraEpub3:source:"+sourceKey(sourceUrl)).getBytes(StandardCharsets.UTF_8)).toString();
	}

	static String sourceKey(String sourceUrl)
	{
		String url = sourceUrl.trim();
		int hash = url.indexOf('#');
		if (hash >= 0) url = url.substring(0, hash);
		int scheme = url.indexOf("://");
		if (scheme >= 0) url = url.substring(scheme+3);
		//ホスト名はパスかクエリの手前まで（https://example.com?work=A のようにパスが無いこともある）
		int end = url.length();
		for (char c : new char[]{'/', '?'}) {
			int i = url.indexOf(c);
			if (i >= 0 && i < end) end = i;
		}
		String host = url.substring(0, end);
		String rest = url.substring(end);
		int query = rest.indexOf('?');
		String path = query < 0 ? rest : rest.substring(0, query);
		String q = query < 0 ? "" : rest.substring(query);
		while (path.endsWith("/")) path = path.substring(0, path.length()-1);
		return host.toLowerCase(Locale.ROOT)+path+q;
	}

	/** 作品のフォルダの台帳を読む。無い・読めない・欄が欠けているときは null */
	public static BookLedger load(File workDir)
	{
		if (workDir == null) return null;
		File file = new File(workDir, FILE_NAME);
		if (!file.isFile()) return null;
		Properties props = new Properties();
		try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			props.load(reader);
		} catch (IOException | IllegalArgumentException e) {
			logger.warn("台帳を読めませんでした: {}", file, e);
			return null;
		}
		String sourceUrl = blankToNull(props.getProperty(KEY_SOURCE_URL));
		String identifier = blankToNull(props.getProperty(KEY_IDENTIFIER));
		//手で書き換えられていても出力先の外へ出ないよう、ファイル名に使えない文字は落とす
		String outputBaseName = blankToNull(safeFileName(props.getProperty(KEY_OUTPUT_BASE_NAME)));
		if (sourceUrl == null) {
			logger.warn("台帳に {} がありません: {}", KEY_SOURCE_URL, file);
			return null;
		}
		//identifier が読めなければ URL から決め直す（同じ値になる）
		if (identifier == null || !isUuid(identifier)) identifier = identifierFor(sourceUrl);
		return new BookLedger(sourceUrl, identifier, outputBaseName);
	}

	/** 作品のフォルダに書く。途中で止まっても前の台帳が壊れないよう、一時ファイルから置き換える */
	public void save(File workDir) throws IOException
	{
		Properties props = new Properties();
		props.setProperty(KEY_SOURCE_URL, this.sourceUrl);
		props.setProperty(KEY_IDENTIFIER, this.identifier);
		if (this.outputBaseName != null) props.setProperty(KEY_OUTPUT_BASE_NAME, this.outputBaseName);
		Path dir = workDir.toPath();
		Files.createDirectories(dir);
		Path tmp = Files.createTempFile(dir, FILE_NAME, ".tmp");
		try {
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				props.store(writer, "AozoraEpub3 book ledger");
			}
			Path dst = dir.resolve(FILE_NAME);
			try {
				Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	/** 台帳の値を書誌情報に移す。txt の隣に台帳が無ければ何もしない */
	public static void applyTo(File srcFile, BookInfo bookInfo)
	{
		if (srcFile == null || bookInfo == null) return;
		BookLedger ledger = load(srcFile.getAbsoluteFile().getParentFile());
		if (ledger == null) return;
		bookInfo.sourceUrl = ledger.sourceUrl;
		bookInfo.identifier = ledger.identifier;
		bookInfo.outputBaseName = ledger.outputBaseName;
	}

	/** ファイル名に使えない文字を落とす（WebAozoraConverter が txt の名前を作るときと同じ組） */
	public static String safeFileName(String value)
	{
		if (value == null) return null;
		return value.replaceAll("[\\\\|\\/|\\:|\\*|\\!|\\?|\\<|\\>|\\||\\\"|\t]", "");
	}

	static boolean isUuid(String value)
	{
		try {
			return UUID.fromString(value).toString().equalsIgnoreCase(value);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	static String blankToNull(String value)
	{
		if (value == null) return null;
		value = value.trim();
		return value.isEmpty() ? null : value;
	}

	/** URI として扱えるかだけ確かめる（dc:source に書く前） */
	public static boolean isHttpUrl(String value)
	{
		if (value == null) return false;
		try {
			String scheme = URI.create(value).getScheme();
			return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
