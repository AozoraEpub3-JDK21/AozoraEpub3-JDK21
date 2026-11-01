package com.github.hmdev.info;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 画像だけの本のページの大きさ。本全体で 1 つにして、画像はその中に置く。
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

	/**
	 * 縦横比が真ん中の画像の大きさを返す。画像が無ければ null。
	 * スキャンした本は寸法が少しずつ違うので、同じ寸法を数えても本の形は分からない。
	 * 偶数枚なら縦長の側を取る（縦長のページの中なら横長の絵も収まる）
	 */
	public static FixedPageSize ofMedianAspect(List<ImageInfo> images)
	{
		if (images.isEmpty()) return null;
		List<ImageInfo> sorted = new ArrayList<>(images);
		sorted.sort(Comparator.comparingDouble(FixedPageSize::aspect));
		ImageInfo median = sorted.get((sorted.size()-1)/2);
		return new FixedPageSize(median.getPageWidth(), median.getPageHeight());
	}

	private static double aspect(ImageInfo image)
	{
		return image.getPageHeight() > 0 ? (double)image.getPageWidth()/image.getPageHeight() : 0;
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
		if (imageWidth <= 0 || imageHeight <= 0) return new Placement(0, 0, width, height, width, height);
		double scale = Math.min((double)width/imageWidth, (double)height/imageHeight);
		int placedWidth = Math.max(1, (int)Math.round(imageWidth*scale));
		int placedHeight = Math.max(1, (int)Math.round(imageHeight*scale));
		return new Placement((width-placedWidth)/2, (height-placedHeight)/2, placedWidth, placedHeight, width, height);
	}

	/**
	 * ページの中の画像の位置と大きさ。SVG は画素で、それ以外はページに対する割合で書く。
	 * 割合なら、固定レイアウトを解さないリーダーでも画面の中に収まる。
	 */
	public static class Placement
	{
		private final int x;
		private final int y;
		private final int width;
		private final int height;
		private final int pageWidth;
		private final int pageHeight;

		Placement(int x, int y, int width, int height, int pageWidth, int pageHeight)
		{
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.pageWidth = pageWidth;
			this.pageHeight = pageHeight;
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
		public String getLeftPercent()
		{
			return percent(x, pageWidth);
		}
		public String getTopPercent()
		{
			return percent(y, pageHeight);
		}
		public String getWidthPercent()
		{
			return percent(width, pageWidth);
		}
		public String getHeightPercent()
		{
			return percent(height, pageHeight);
		}
		private static String percent(int value, int whole)
		{
			return String.format(Locale.ROOT, "%.4f", 100.0*value/whole);
		}
	}
}
