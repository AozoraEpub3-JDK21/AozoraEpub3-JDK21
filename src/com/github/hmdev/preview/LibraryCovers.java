package com.github.hmdev.preview;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本棚に並べる表紙サムネイルを作る。
 *
 * <p>EPUB を展開せず ZIP から表紙 1 枚だけを読み、縮小して JPEG にする。
 * 生成コストが高いのでセッション内でキャッシュする。</p>
 *
 * <p>元画像は EPUB 由来で信用できないため、
 * <b>読み込むバイト数と展開後の画素数の両方に上限を掛ける</b>。
 * 宣言サイズだけを見ると、高圧縮の画像 1 枚でヒープを飛ばせる。</p>
 */
public class LibraryCovers
{
	private static final Logger logger = LoggerFactory.getLogger(LibraryCovers.class);

	/** サムネイルの最大寸法。本棚のグリッドに並ぶ大きさ */
	static final int MAX_WIDTH = 240;
	static final int MAX_HEIGHT = 360;

	/** 読み込む表紙画像の上限バイト数 */
	static final long MAX_SOURCE_BYTES = 16L * 1024 * 1024;

	/**
	 * デコードを許す画素数の上限。
	 * サイズが小さくても展開後が巨大な画像 (圧縮爆弾) を弾く。
	 * 4000x4000 相当まで許容する。
	 */
	static final long MAX_PIXELS = 16_000_000L;

	/** キャッシュに保持するサムネイル枚数 */
	static final int MAX_CACHED = 300;

	/** キーは bookId + サイズ + 更新時刻。EPUB が差し替わったら別物として作り直す */
	private final Map<String, byte[]> cache = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest)
		{
			return size() > MAX_CACHED;
		}
	};

	/**
	 * サムネイルを返す。
	 *
	 * @param cacheKey {@link #currentCacheKey} が返すキー。
	 *        <b>スキャン時ではなく要求時のファイル状態から作ること。</b>
	 *        スキャン時の値で固定すると、再変換しても古いサムネイルを配り続ける
	 * @param entry 表紙の位置を持つ本棚の記録
	 * @return JPEG のバイト列。表紙が無い / 読めない場合は null
	 */
	public byte[] thumbnail(String cacheKey, LibraryEntry entry)
	{
		if (entry == null || entry.coverEntry() == null) return null;
		byte[] cached = lookup(cacheKey);
		// 作れなかったことも覚える。覚えないと、壊れた表紙の本がグリッドにある限り
		// 表示のたびに ZIP を開き直してデコードを試み続ける
		if (cached != null) return (cached.length == 0) ? null : cached;
		byte[] thumbnail;
		try {
			// ZIP 読み出しとデコードはロックの外で行う。サーバのスレッドプールは
			// 4 本しかないので、ここを排他にすると本棚を開いた瞬間に
			// 本文の読み込みまで表紙生成待ちで詰まる
			thumbnail = render(entry);
		} catch (IOException | RuntimeException e) {
			// 表紙が作れなくても本棚は使える。1 冊ぶん絵が出ないだけ
			logger.debug("表紙サムネイルを作れませんでした: {}", entry.file(), e);
			thumbnail = null;
		}
		store(cacheKey, (thumbnail == null) ? EMPTY : thumbnail);
		return thumbnail;
	}

	/** 「作れなかった」ことを表す印 */
	private static final byte[] EMPTY = new byte[0];

	private synchronized byte[] lookup(String cacheKey)
	{
		// LinkedHashMap の access-order は get でも構造を変えるため排他が要る
		return this.cache.get(cacheKey);
	}

	private synchronized void store(String cacheKey, byte[] thumbnail)
	{
		this.cache.put(cacheKey, thumbnail);
	}

	/**
	 * <b>いま</b>のファイル状態からキャッシュキーを作る。
	 *
	 * <p>本棚のスキャンは起動時に 1 回しか走らないため、{@link LibraryEntry} が持つ
	 * サイズと更新時刻はすぐ古くなる。それを ETag に使うと、
	 * 「変換し直したのに古い表紙が出続ける」ことになる
	 * (しかも {@code no-cache} + ETag による再検証が意味を失う)。</p>
	 *
	 * <p>ファイルの状態を取れない場合はスキャン時の値に倒す。元 EPUB が消えても
	 * 展開済みのものを配り続けるという既存方針に合わせる。</p>
	 */
	public static String currentCacheKey(String bookId, LibraryEntry entry)
	{
		try {
			return cacheKey(bookId, Files.size(entry.file()),
				Files.getLastModifiedTime(entry.file()).toMillis());
		} catch (IOException | RuntimeException e) {
			/* 意図的: 状態を取れなければスキャン時の値で識別する */
			logger.debug("EPUB の状態を取得できませんでした: {}", entry.file(), e);
			return cacheKey(bookId, entry.size(), entry.modifiedMillis());
		}
	}

	static String cacheKey(String bookId, long size, long modifiedMillis)
	{
		return bookId + "-" + size + "-" + modifiedMillis;
	}

	/** ETag に使う識別子 */
	public static String etag(String cacheKey)
	{
		return "\"" + cacheKey + "\"";
	}

	// ------------------------------------------------------------------

	private static byte[] render(LibraryEntry entry) throws IOException
	{
		byte[] source = readCoverBytes(entry);
		if (source == null) return null;
		BufferedImage image = decodeBounded(source);
		if (image == null) return null;
		return encodeJpeg(scaleToFit(image));
	}

	/** ZIP から表紙 1 枚を読み出す。大きすぎるものは読まない */
	private static byte[] readCoverBytes(LibraryEntry entry) throws IOException
	{
		try (ZipFile zip = new ZipFile(entry.file().toFile(), StandardCharsets.UTF_8)) {
			ZipEntry cover = zip.getEntry(entry.coverEntry());
			if (cover == null) {
				logger.debug("表紙が ZIP に見つかりません: {} ({})", entry.coverEntry(), entry.file());
				return null;
			}
			if (cover.getSize() > MAX_SOURCE_BYTES) {
				logger.debug("表紙が大きすぎます ({} bytes): {}", cover.getSize(), entry.file());
				return null;
			}
			try (InputStream is = zip.getInputStream(cover)) {
				// 宣言サイズは信用できないので実読み込み量でも縛る
				byte[] bytes = is.readNBytes(Math.toIntExact(MAX_SOURCE_BYTES + 1));
				if (bytes.length > MAX_SOURCE_BYTES) {
					logger.debug("表紙が大きすぎます: {}", entry.file());
					return null;
				}
				return bytes;
			}
		}
	}

	/**
	 * 画素数の上限を確かめてからデコードする。
	 *
	 * <p>{@code ImageIO.read} は寸法を見る前に全部展開してしまうため、
	 * リーダーを直接使って先に幅と高さを問い合わせる。
	 * 併せて、縮小して使うだけなので間引き読み込み (subsampling) で負荷を下げる。</p>
	 */
	static BufferedImage decodeBounded(byte[] source) throws IOException
	{
		// ImageIO.createImageInputStream は既定で FileCacheImageInputStream (一時ファイル) を
		// 作る。元データは既にメモリ上にあるので、ディスクを経由する意味が無い。
		// いずれにせよ閉じないと FD が滞留する (code-audit-followups #4 と同根)
		try (ImageInputStream input =
				new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				logger.debug("対応していない画像形式のため表紙を作れません");
				return null;
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input);
				long width = reader.getWidth(0);
				long height = reader.getHeight(0);
				if (width <= 0 || height <= 0) return null;
				if (width * height > MAX_PIXELS) {
					logger.debug("表紙の画素数が上限を超えています: {}x{}", width, height);
					return null;
				}
				ImageReadParam param = reader.getDefaultReadParam();
				int step = subsampling((int)width, (int)height);
				if (step > 1) param.setSourceSubsampling(step, step, 0, 0);
				return reader.read(0, param);
			} finally {
				reader.dispose();
			}
		}
	}

	/**
	 * 間引き読み込みの間隔。
	 * サムネイルの 2 倍以上の解像度は要らないので、そこまで落とす。
	 */
	static int subsampling(int width, int height)
	{
		int step = 1;
		while (width / (step * 2) >= MAX_WIDTH * 2 && height / (step * 2) >= MAX_HEIGHT * 2) step *= 2;
		return step;
	}

	/** 縦横比を保って収まる大きさに縮小する。元が小さければそのまま */
	static BufferedImage scaleToFit(BufferedImage image)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		double scale = Math.min(MAX_WIDTH / (double)width, MAX_HEIGHT / (double)height);
		if (scale >= 1.0) scale = 1.0;
		int targetWidth = Math.max(1, (int)Math.round(width * scale));
		int targetHeight = Math.max(1, (int)Math.round(height * scale));

		// JPEG は透過を持てない。透過 PNG の表紙が黒く潰れないよう白で敷く
		BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
				RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, targetWidth, targetHeight);
			graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
		} finally {
			graphics.dispose();
		}
		return target;
	}

	private static byte[] encodeJpeg(BufferedImage image) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream(32 * 1024);
		if (!ImageIO.write(image, "jpeg", out)) {
			throw new IOException("JPEG エンコーダが見つかりません");
		}
		return out.toByteArray();
	}
}
