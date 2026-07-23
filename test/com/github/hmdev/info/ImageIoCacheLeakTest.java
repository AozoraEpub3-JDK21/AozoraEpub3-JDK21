package com.github.hmdev.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.github.hmdev.image.ImageUtils;

/**
 * ImageIO のディスクキャッシュ（temp ファイル）が残らないことのテスト（監査 #4）。
 *
 * ImageIO はデフォルトで FileCacheImageInputStream / FileCacheImageOutputStream を
 * 作るため、close しないと一時ファイルと FD が GC 任せで滞留する。
 *
 * ImageIO.setUseCache / setCacheDirectory は static グローバル状態なので、
 * @After で必ず復元する。
 */
public class ImageIoCacheLeakTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	private boolean originalUseCache;
	private File originalCacheDir;
	private File cacheDir;

	@Before
	public void setUp() throws IOException {
		originalUseCache = ImageIO.getUseCache();
		originalCacheDir = ImageIO.getCacheDirectory();
		cacheDir = tempFolder.newFolder("imageio_cache");
		ImageIO.setUseCache(true);
		ImageIO.setCacheDirectory(cacheDir);
	}

	@After
	public void tearDown() {
		ImageIO.setUseCache(originalUseCache);
		ImageIO.setCacheDirectory(originalCacheDir);
	}

	/** ImageInfo.getImageInfo が ImageInputStream の temp ファイルを残さないこと */
	@Test
	public void getImageInfoLeavesNoCacheFile() throws Exception {
		byte[] png = pngBytes(20, 10);
		CloseTrackingInputStream is = new CloseTrackingInputStream(png);

		ImageInfo info = ImageInfo.getImageInfo(is, -1);

		assertNotNull(info);
		assertEquals(20, info.getWidth());
		assertEquals(10, info.getHeight());
		assertEquals(0, cacheDir.list().length);
	}

	/** ImageInputStream の close は呼び出し側のストリームを閉じない
	 * （ImageInfoReader は zip のエントリ列挙中に同じストリームを使い続けるため必須） */
	@Test
	public void getImageInfoDoesNotCloseCallerStream() throws Exception {
		CloseTrackingInputStream is = new CloseTrackingInputStream(pngBytes(4, 4));

		ImageInfo.getImageInfo(is, -1);

		assertFalse("呼び出し側のストリームが閉じられている", is.closed);
	}

	/** ImageUtils.writeImage が ImageOutputStream の temp ファイルを残さないこと。
	 * 画像 1 枚ごとに発生するため getImageInfo より頻度が高い。
	 *
	 * あわせて、close() 追加で出力が壊れていないことも検証する。
	 * zip 全体のサイズだけを見るとヘッダのみで成立してしまい、
	 * 画像バイトが 0 の空エントリでも通ってしまうため、必ず読み戻してデコードする。 */
	@Test
	public void writeImageLeavesNoCacheFileAndKeepsOutputIntact() throws Exception {
		for (String ext : new String[]{"png", "jpeg"}) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos)) {
				zos.putArchiveEntry(new ZipArchiveEntry("image."+ext));
				BufferedImage src = new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB);
				ImageInfo info = ImageInfo.getImageInfo(ext, src, -1);
				ImageUtils.writeImage(null, src, zos, info,
					0.8f, null, 0, 0, 0, 600, 800, 0, 0, 100, 0f, 0, 0f);
				zos.closeArchiveEntry();
			}
			assertEquals(ext+" で ImageIO の temp ファイルが残っている",
				0, cacheDir.list().length);

			//書き出した画像を読み戻してデコードできること
			BufferedImage written = readSingleEntryAsImage(baos.toByteArray());
			assertNotNull(ext+" のエントリをデコードできない", written);
			assertEquals(ext+" で幅が変わった", 30, written.getWidth());
			assertEquals(ext+" で高さが変わった", 20, written.getHeight());
		}
	}

	/** zip の単一エントリを画像としてデコードする */
	private static BufferedImage readSingleEntryAsImage(byte[] zipBytes) throws IOException {
		try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(zipBytes))) {
			assertNotNull("zip エントリが無い", zis.getNextEntry());
			byte[] entryBytes = zis.readAllBytes();
			assertTrue("エントリが空", entryBytes.length > 0);
			return ImageIO.read(new ByteArrayInputStream(entryBytes));
		}
	}

	private static byte[] pngBytes(int w, int h) throws IOException {
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		//生成時のキャッシュを検証対象に混ぜないよう、この呼び出しだけキャッシュ無効で行う
		boolean useCache = ImageIO.getUseCache();
		ImageIO.setUseCache(false);
		try {
			ImageIO.write(image, "png", baos);
		} finally {
			ImageIO.setUseCache(useCache);
		}
		return baos.toByteArray();
	}

	/** close されたことを記録する InputStream */
	static class CloseTrackingInputStream extends ByteArrayInputStream {
		boolean closed = false;
		CloseTrackingInputStream(byte[] buf) { super(buf); }
		@Override
		public void close() throws IOException {
			closed = true;
			super.close();
		}
	}
}
