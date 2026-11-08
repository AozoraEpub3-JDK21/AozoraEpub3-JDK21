package com.github.hmdev.image;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.Test;

import com.github.hmdev.info.ImageInfo;

/**
 * 縮小・回転しても、色の表と透過を保つ（internal #77）。
 * 前は色のある 4 ビット以下の表が暗い灰色に、回転では真っ黒な 1 ビットに、透過のある画像の透明な所が黒になった。
 */
public class ImageUtilsCanvasTest {

	private static final int RED = 0xDC1E1E, GREEN = 0x1EB43C, BLUE = 0x1E3CDC;

	/** 縦に 3 本の帯（赤・緑・青）を、色の表の画像で作る */
	private static BufferedImage stripes(int bits, int[] rgb, int w, int h) {
		byte[] r = new byte[rgb.length], g = new byte[rgb.length], b = new byte[rgb.length];
		for (int i = 0; i < rgb.length; i++) { r[i] = (byte)(rgb[i] >> 16); g[i] = (byte)(rgb[i] >> 8); b[i] = (byte)rgb[i]; }
		IndexColorModel cm = new IndexColorModel(bits, rgb.length, r, g, b);
		BufferedImage image = new BufferedImage(w, h, bits == 8 ? BufferedImage.TYPE_BYTE_INDEXED : BufferedImage.TYPE_BYTE_BINARY, cm);
		for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.getRaster().setSample(x, y, 0, x * 3 / w);
		return image;
	}

	/** 縮める（maxH）か回す（rotate）だけの設定で PNG を書き、読み戻す */
	private static BufferedImage write(BufferedImage src, int maxH, int rotate) throws Exception {
		ImageInfo info = new ImageInfo("png", src.getWidth(), src.getHeight(), 0);
		info.rotateAngle = rotate;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageUtils.writeImage(null, src, out, info, 0.8f, null, 0, 0, maxH, 600, 800, 0, 0, 0, 0, 0, 0);
		BufferedImage written = ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
		assertTrue("書けていない", written != null);
		return written;
	}

	private static void assertStripes(BufferedImage image, boolean rotated) {
		int[] expected = {RED, GREEN, BLUE};
		for (int i = 0; i < 3; i++) {
			double f = (i * 2 + 1) / 6.0;
			int x = rotated ? image.getWidth() / 2 : (int)(image.getWidth() * f);
			int y = rotated ? (int)(image.getHeight() * f) : image.getHeight() / 2;
			assertEquals("帯 " + i + " の色", Integer.toHexString(expected[i]), Integer.toHexString(image.getRGB(x, y) & 0xFFFFFF));
		}
	}

	@Test
	public void fourBitColourPaletteKeepsItsColoursWhenResized() throws Exception {
		BufferedImage written = write(stripes(4, new int[]{RED, GREEN, BLUE, 0xFFFFFF}, 1200, 1600), 800, 0);
		assertEquals(800, written.getHeight());
		assertStripes(written, false);
	}

	@Test
	public void twoBitColourPaletteKeepsItsColoursWhenResized() throws Exception {
		assertStripes(write(stripes(2, new int[]{RED, GREEN, BLUE, 0xFFFFFF}, 1200, 1600), 800, 0), false);
	}

	/** 回転だけ（縮めない）でも、元の色の表のまま。前は既定の表（1 ビットの白黒）で真っ黒だった */
	@Test
	public void colourPaletteKeepsItsColoursWhenRotated() throws Exception {
		for (int bits : new int[]{4, 8}) {
			BufferedImage written = write(stripes(bits, new int[]{RED, GREEN, BLUE, 0xFFFFFF}, 600, 300), 0, 90);
			assertEquals(300, written.getWidth());
			assertEquals(600, written.getHeight());
			assertStripes(written, true);
		}
	}

	/** 灰色の 4 ビットの表を回しても、灰色の段が残る（1 ビットの白黒に落ちない） */
	@Test
	public void greyPaletteKeepsItsGreysWhenRotated() throws Exception {
		BufferedImage written = write(stripes(4, new int[]{0xC8C8C8, 0x282828, 0x787878, 0xFFFFFF}, 600, 300), 0, 90);
		int[] expected = {0xC8, 0x28, 0x78};
		for (int i = 0; i < 3; i++) {
			int y = (int)(written.getHeight() * (i * 2 + 1) / 6.0);
			assertEquals("段 " + i, Integer.toHexString(expected[i] * 0x010101), Integer.toHexString(written.getRGB(written.getWidth() / 2, y) & 0xFFFFFF));
		}
	}

	/** 近い色が並ぶ表（写真のような絵）でも、回すだけなら画素は 1 つも変わらない。描き直して表へ寄せ直すと 97% 変わった（レビューで実測） */
	@Test
	public void rotationKeepsEveryPixelOfAPaletteImage() throws Exception {
		for (int bits : new int[]{4, 8}) {
			int size = 1 << bits;
			byte[] r = new byte[size], g = new byte[size], b = new byte[size];
			for (int i = 0; i < size; i++) { r[i] = (byte)(100 + i); g[i] = (byte)(100 + i / 2); b[i] = (byte)(140 - i / 3); }
			IndexColorModel cm = new IndexColorModel(bits, size, r, g, b);
			BufferedImage src = new BufferedImage(64, 32, bits == 8 ? BufferedImage.TYPE_BYTE_INDEXED : BufferedImage.TYPE_BYTE_BINARY, cm);
			java.util.Random random = new java.util.Random(77);
			for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) src.getRaster().setSample(x, y, 0, random.nextInt(size));
			for (int angle : new int[]{90, -90}) {
				BufferedImage written = write(src, 0, angle);
				assertEquals(32, written.getWidth());
				for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) {
					int rx = angle == 90 ? 31 - y : y, ry = angle == 90 ? x : 63 - x;
					assertEquals(bits + " ビット " + angle + " 度 (" + x + "," + y + ")", Integer.toHexString(src.getRGB(x, y)), Integer.toHexString(written.getRGB(rx, ry)));
				}
			}
		}
	}

	/** 透過のある色の表（tRNS）を回しても、表のまま・透明のまま */
	@Test
	public void rotationKeepsATransparentPalette() throws Exception {
		byte[] r = {(byte)220, 30, 0}, g = {30, (byte)180, 0}, b = {30, 60, 0}, a = {(byte)255, (byte)255, 0};
		IndexColorModel cm = new IndexColorModel(8, 3, r, g, b, a);
		BufferedImage src = new BufferedImage(60, 30, BufferedImage.TYPE_BYTE_INDEXED, cm);
		for (int y = 0; y < 30; y++) for (int x = 0; x < 60; x++) src.getRaster().setSample(x, y, 0, x * 3 / 60);
		BufferedImage written = write(src, 0, 90);
		assertTrue("色の表でなくなった: " + written.getColorModel(), written.getColorModel() instanceof IndexColorModel);
		assertEquals("透明な所", 0, written.getRGB(15, 55) >>> 24);
		assertEquals(Integer.toHexString(0xFFDC1E1E), Integer.toHexString(written.getRGB(15, 5)));
	}

	/** コントラスト調整をしながら縮めても、色の表の色と透過は保つ（調整の後で表へ戻す道） */
	@Test
	public void contrastKeepsColoursAndTransparency() throws Exception {
		byte[] identity = new byte[256];
		for (int i = 0; i < 256; i++) identity[i] = (byte)i;
		java.awt.image.LookupOp gamma = new java.awt.image.LookupOp(new java.awt.image.ByteLookupTable(0, identity), null);
		BufferedImage pal = stripes(4, new int[]{RED, GREEN, BLUE, 0xFFFFFF}, 1200, 1600);
		ImageInfo info = new ImageInfo("png", 1200, 1600, 0);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageUtils.writeImage(null, pal, out, info, 0.8f, gamma, 0, 0, 800, 600, 800, 0, 0, 0, 0, 0, 0);
		assertStripes(ImageIO.read(new ByteArrayInputStream(out.toByteArray())), false);
		BufferedImage argb = new BufferedImage(1200, 1600, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 1600; y++) for (int x = 400; x < 800; x++) argb.setRGB(x, y, 0xFF000000 | BLUE);
		out.reset();
		ImageUtils.writeImage(null, argb, out, new ImageInfo("png", 1200, 1600, 0), 0.8f, gamma, 0, 0, 800, 600, 800, 0, 0, 0, 0, 0, 0);
		BufferedImage written = ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
		assertEquals("透明な所の不透明度", 0, written.getRGB(90, 400) >>> 24);
		assertEquals(Integer.toHexString(BLUE), Integer.toHexString(written.getRGB(300, 400) & 0xFFFFFF));
	}

	/** 透過のある画像を縮めても、透明な所は透明のまま。前は黒になった */
	@Test
	public void transparentAreasStayTransparentWhenResized() throws Exception {
		BufferedImage argb = new BufferedImage(1200, 1600, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 1600; y++) for (int x = 400; x < 800; x++) argb.setRGB(x, y, 0xFF000000 | BLUE);
		BufferedImage written = write(argb, 800, 0);
		assertEquals("透明な所の不透明度", 0, written.getRGB(90, 400) >>> 24);
		assertEquals(Integer.toHexString(BLUE), Integer.toHexString(written.getRGB(300, 400) & 0xFFFFFF));
	}
}
