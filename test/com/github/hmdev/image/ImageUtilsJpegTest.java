package com.github.hmdev.image;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.Test;

import com.github.hmdev.info.ImageInfo;

/** JPEG に書く画像（Kindle 向けに JPEG へ直す表紙など） */
public class ImageUtilsJpegTest {

	/** 透過のある画像も JPEG に書ける。透けていたところは白になる。
	 * 前は JPEG の writer が投げ、空の 0000.jpg が本に入っていた（MED-004・PKG-021。internal #64） */
	@Test
	public void imageWithAlphaIsWrittenAsJpegOnWhite() throws Exception {
		BufferedImage argb = new BufferedImage(60, 80, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 80; y++) for (int x = 0; x < 60; x++) argb.setRGB(x, y, x < 30 ? 0x00000000 : 0xFFFF0000);
		ImageInfo info = new ImageInfo("jpg", 60, 80, 0);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageUtils.writeImage(null, argb, out, info, 0.9f, null, 0, 0, 0, 600, 800, 0, 0, 0, 0, 0, 0);
		BufferedImage jpeg = ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
		assertTrue("JPEG として読めない（" + out.size() + " バイト）", jpeg != null);
		assertEquals(60, jpeg.getWidth());
		java.awt.Color clear = new java.awt.Color(jpeg.getRGB(10, 40));
		assertTrue("透けていたところが白でない: " + clear, clear.getRed() > 240 && clear.getGreen() > 240 && clear.getBlue() > 240);
		java.awt.Color red = new java.awt.Color(jpeg.getRGB(50, 40));
		assertTrue("赤が残っていない: " + red, red.getRed() > 200 && red.getGreen() < 60);
	}
}
