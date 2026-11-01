package com.github.hmdev.info;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 画像だけの本のページの大きさ。本全体で 1 つにして、画像はその中に画素で置く。
 * ページごとに大きさを変えると、Apple Books は先頭と違う形のページを切る。
 */
public class FixedPageSize
{
	private final int width;
	private final int height;

	public FixedPageSize(int width, int height)
	{
		this.width = width;
		this.height = height;
	}

	/** いちばん多い大きさを返す。同数なら先に出てきたもの。画像が無ければ null */
	public static FixedPageSize mostCommon(Iterable<ImageInfo> images)
	{
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, ImageInfo> firstOfSize = new LinkedHashMap<>();
		for (ImageInfo image : images) {
			String size = image.getPageWidth()+"x"+image.getPageHeight();
			counts.merge(size, 1, Integer::sum);
			firstOfSize.putIfAbsent(size, image);
		}
		String common = null;
		for (Map.Entry<String, Integer> count : counts.entrySet()) {
			if (common == null || count.getValue() > counts.get(common)) common = count.getKey();
		}
		if (common == null) return null;
		ImageInfo image = firstOfSize.get(common);
		return new FixedPageSize(image.getPageWidth(), image.getPageHeight());
	}

	public int getWidth()
	{
		return width;
	}
	public int getHeight()
	{
		return height;
	}

	/** 画像を縦横比を保ってページの中央に置く */
	public Placement place(ImageInfo image)
	{
		int imageWidth = image.getPageWidth();
		int imageHeight = image.getPageHeight();
		if (imageWidth <= 0 || imageHeight <= 0) return new Placement(0, 0, width, height);
		double scale = Math.min((double)width/imageWidth, (double)height/imageHeight);
		int placedWidth = Math.max(1, (int)Math.round(imageWidth*scale));
		int placedHeight = Math.max(1, (int)Math.round(imageHeight*scale));
		return new Placement((width-placedWidth)/2, (height-placedHeight)/2, placedWidth, placedHeight);
	}

	/** ページの中の画像の位置と大きさ（画素） */
	public static class Placement
	{
		private final int x;
		private final int y;
		private final int width;
		private final int height;

		Placement(int x, int y, int width, int height)
		{
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
		public int getX()
		{
			return x;
		}
		public int getY()
		{
			return y;
		}
		public int getWidth()
		{
			return width;
		}
		public int getHeight()
		{
			return height;
		}
	}
}
