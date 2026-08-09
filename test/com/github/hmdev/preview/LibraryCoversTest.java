package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 本棚のサムネイル生成。
 * 元画像は EPUB 由来で信用できないため、上限を越えるものを弾くことが要点。
 */
public class LibraryCoversTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private Path root() { return temp.getRoot().toPath(); }

	/** 指定サイズの PNG を作る */
	private static byte[] png(int width, int height) throws IOException
	{
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.BLUE);
		graphics.fillRect(0, 0, width, height);
		graphics.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private LibraryEntry scanFirst(Path folder) throws IOException
	{
		List<LibraryEntry> entries = LibraryScanner.scan(folder, 3, null);
		assertEquals(1, entries.size());
		return entries.get(0);
	}

	@Test
	public void makesAThumbnailThatFitsTheGrid() throws Exception
	{
		EpubFixture fixture = EpubFixture.withEpub3Cover();
		fixture.putBytes("OPS/images/cover.png", png(1200, 1800));
		fixture.writeTo(root().resolve("a.epub"));

		byte[] jpeg = new LibraryCovers().thumbnail("b1", scanFirst(root()));

		assertNotNull(jpeg);
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
		assertEquals(LibraryCovers.MAX_WIDTH, decoded.getWidth());
		assertEquals(LibraryCovers.MAX_HEIGHT, decoded.getHeight());
		assertTrue("縮小したのに元より大きい", jpeg.length < png(1200, 1800).length);
	}

	@Test
	public void aspectRatioIsKept() throws Exception
	{
		// 横長の表紙を枠いっぱいに引き伸ばすと本棚の見た目が壊れる
		EpubFixture fixture = EpubFixture.withEpub3Cover();
		fixture.putBytes("OPS/images/cover.png", png(1000, 250));
		fixture.writeTo(root().resolve("a.epub"));

		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(
			new LibraryCovers().thumbnail("b1", scanFirst(root()))));
		assertEquals(LibraryCovers.MAX_WIDTH, decoded.getWidth());
		assertEquals(LibraryCovers.MAX_WIDTH / 4, decoded.getHeight());
	}

	@Test
	public void smallCoversAreNotBlownUp() throws Exception
	{
		EpubFixture fixture = EpubFixture.withEpub3Cover();
		fixture.putBytes("OPS/images/cover.png", png(60, 90));
		fixture.writeTo(root().resolve("a.epub"));

		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(
			new LibraryCovers().thumbnail("b1", scanFirst(root()))));
		assertEquals(60, decoded.getWidth());
		assertEquals(90, decoded.getHeight());
	}

	@Test
	public void aBookWithoutACoverGivesNothing() throws Exception
	{
		EpubFixture.standard().writeTo(root().resolve("a.epub"));
		assertNull(new LibraryCovers().thumbnail("b1", scanFirst(root())));
		assertNull(new LibraryCovers().thumbnail("b1", null));
	}

	@Test
	public void aBrokenCoverIsNotFatal() throws Exception
	{
		// 拡張子は png でも中身が画像でない EPUB がある。
		// 1 冊ぶん絵が出ないだけで本棚は使えなければならない
		EpubFixture fixture = EpubFixture.withEpub3Cover();
		fixture.put("OPS/images/cover.png", "これは画像ではない");
		fixture.writeTo(root().resolve("a.epub"));

		assertNull(new LibraryCovers().thumbnail("b1", scanFirst(root())));
	}

	@Test
	public void anImageWithTooManyPixelsIsRejected() throws Exception
	{
		// 圧縮爆弾。宣言サイズが小さくても展開後が巨大な画像を弾く。
		// 実際に巨大な画像を作るとテストが重いので、ヘッダだけを組み立てて
		// デコーダに寸法を問い合わせさせる
		byte[] bomb = pngHeaderOnly(30000, 30000);
		assertNull("画素数の上限を超える画像をデコードしている",
			LibraryCovers.decodeBounded(bomb));
	}

	@Test
	public void unknownFormatsAreRejectedWithoutThrowing() throws Exception
	{
		assertNull(LibraryCovers.decodeBounded("これは画像ではない".getBytes(StandardCharsets.UTF_8)));
		assertNull(LibraryCovers.decodeBounded(new byte[0]));
	}

	@Test
	public void oversizedCoverBytesAreNotRead() throws Exception
	{
		// 宣言サイズ・実サイズとも上限を超える表紙は読まない
		byte[] huge = new byte[(int)LibraryCovers.MAX_SOURCE_BYTES + 1024];
		EpubFixture fixture = EpubFixture.withEpub3Cover();
		fixture.putBytes("OPS/images/cover.png", huge);
		fixture.writeTo(root().resolve("a.epub"));

		assertNull(new LibraryCovers().thumbnail("b1", scanFirst(root())));
	}

	@Test
	public void thumbnailsAreCachedPerBookAndInvalidatedByTheEtag() throws Exception
	{
		EpubFixture fixture = EpubFixture.withEpub3Cover();
		fixture.putBytes("OPS/images/cover.png", png(800, 1200));
		Path epub = fixture.writeTo(root().resolve("a.epub"));
		LibraryEntry entry = scanFirst(root());

		LibraryCovers covers = new LibraryCovers();
		byte[] first = covers.thumbnail("b1", entry);
		assertSame("同じ本を 2 度作り直している", first, covers.thumbnail("b1", entry));

		// 変換し直して EPUB が差し替わったら、ETag が変わって作り直される
		Files.setLastModifiedTime(epub,
			java.nio.file.attribute.FileTime.fromMillis(entry.modifiedMillis() + 5000));
		LibraryEntry updated = scanFirst(root());
		org.junit.Assert.assertNotEquals(
			LibraryCovers.etag("b1", entry), LibraryCovers.etag("b1", updated));
	}

	@Test
	public void subsamplingOnlyKicksInForLargeImages()
	{
		assertEquals(1, LibraryCovers.subsampling(240, 360));
		assertEquals(1, LibraryCovers.subsampling(800, 1200));
		// 目標の 4 倍を超えたら間引く
		assertTrue(LibraryCovers.subsampling(4000, 6000) > 1);
	}

	/** IHDR だけを持つ PNG。寸法の問い合わせには答えるが画素は持たない */
	private static byte[] pngHeaderOnly(int width, int height) throws IOException
	{
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		out.write(new byte[] {(byte)0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
		java.io.ByteArrayOutputStream ihdr = new java.io.ByteArrayOutputStream();
		java.io.DataOutputStream data = new java.io.DataOutputStream(ihdr);
		data.writeBytes("IHDR");
		data.writeInt(width);
		data.writeInt(height);
		data.writeByte(8);    // bit depth
		data.writeByte(2);    // color type: truecolor
		data.writeByte(0);
		data.writeByte(0);
		data.writeByte(0);
		byte[] chunk = ihdr.toByteArray();

		java.io.DataOutputStream result = new java.io.DataOutputStream(out);
		result.writeInt(chunk.length - 4);
		result.write(chunk);
		java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update(chunk);
		result.writeInt((int)crc.getValue());
		return out.toByteArray();
	}
}
