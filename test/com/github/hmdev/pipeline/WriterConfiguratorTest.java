package com.github.hmdev.pipeline;

import org.junit.Test;
import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.Properties;

import com.github.hmdev.writer.Epub3ImageWriter;
import com.github.hmdev.writer.Epub3Writer;

/**
 * WriterConfigurator - ini 値の Writer への反映テスト
 *
 * AutoMarginNombreSize が autoMarginPadding へ再代入されて
 * ノンブルサイズが ini から読まれないバグの回帰テスト
 * (docs/code-audit-followups.md 項目 25)。
 *
 * 実行方法:
 *   gradlew test --tests com.github.hmdev.pipeline.WriterConfiguratorTest
 */
public class WriterConfiguratorTest {

	private float getFloatField(Epub3Writer writer, String name) throws Exception {
		Field f = Epub3Writer.class.getDeclaredField(name);
		f.setAccessible(true);
		return f.getFloat(writer);
	}

	/** AutoMargin=1 のとき Padding と NombreSize がそれぞれの変数に読まれること */
	@Test
	public void testAutoMarginPaddingAndNombreSize() throws Exception {
		Properties props = new Properties();
		props.setProperty("AutoMargin", "1");
		props.setProperty("AutoMarginPadding", "1.5");
		props.setProperty("AutoMarginNombreSize", "3.0");

		Epub3Writer writer = new Epub3Writer("");
		Epub3ImageWriter imageWriter = new Epub3ImageWriter("");
		WriterConfigurator.apply(props, writer, imageWriter);

		// AutoMarginPadding が NombreSize で上書きされないこと
		assertEquals(1.5f, getFloatField(writer, "autoMarginPadding"), 0.0001f);
		// GUI と同じく % 値を 0.01 倍した比率で渡ること (3.0 -> 0.03)
		assertEquals(0.03f, getFloatField(writer, "autoMarginNombreSize"), 0.0001f);
	}

	/** NombreSize が既定値 (3.0 = 0.03) 以外でも ini から読まれること */
	@Test
	public void testAutoMarginNombreSizeNonDefault() throws Exception {
		Properties props = new Properties();
		props.setProperty("AutoMargin", "1");
		props.setProperty("AutoMarginPadding", "0");
		props.setProperty("AutoMarginNombreSize", "5.0");

		Epub3Writer writer = new Epub3Writer("");
		Epub3ImageWriter imageWriter = new Epub3ImageWriter("");
		WriterConfigurator.apply(props, writer, imageWriter);

		assertEquals(0.0f, getFloatField(writer, "autoMarginPadding"), 0.0001f);
		assertEquals(0.05f, getFloatField(writer, "autoMarginNombreSize"), 0.0001f);
	}

	/** AutoMargin が無効なら両方とも既定値のまま */
	@Test
	public void testAutoMarginDisabled() throws Exception {
		Properties props = new Properties();
		props.setProperty("AutoMarginPadding", "1.5");
		props.setProperty("AutoMarginNombreSize", "5.0");

		Epub3Writer writer = new Epub3Writer("");
		Epub3ImageWriter imageWriter = new Epub3ImageWriter("");
		WriterConfigurator.apply(props, writer, imageWriter);

		assertEquals(0.0f, getFloatField(writer, "autoMarginPadding"), 0.0001f);
		assertEquals(0.03f, getFloatField(writer, "autoMarginNombreSize"), 0.0001f);
	}
}
