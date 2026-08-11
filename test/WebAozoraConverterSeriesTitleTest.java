import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.StringWriter;
import java.lang.reflect.Method;

import com.github.hmdev.web.WebAozoraConverter;

/**
 * WebAozoraConverter - 中間テキスト先頭のシリーズ名・表題出力のテスト
 *
 * 青空文庫の単話 HTML のように extract.txt の SERIES と TITLE が同一要素に
 * マッチするサイトでは表題が二重になるため、series が title と同一の場合は
 * series を出力しない (docs/code-audit-followups.md 項目 28)。
 *
 * 実行方法:
 *   gradlew test --tests WebAozoraConverterSeriesTitleTest
 */
public class WebAozoraConverterSeriesTitleTest {

	private WebAozoraConverter converter;
	private Method printSeriesAndTitleMethod;

	@Before
	public void setUp() throws Exception {
		File webConfigPath = new File("web");
		String testUrl = "https://ncode.syosetu.com/n0000xx/"; // ダミーURL
		converter = WebAozoraConverter.createWebAozoraConverter(testUrl, webConfigPath);
		assertNotNull("Converter作成失敗", converter);

		printSeriesAndTitleMethod = WebAozoraConverter.class.getDeclaredMethod(
			"printSeriesAndTitle", BufferedWriter.class, String.class, String.class);
		printSeriesAndTitleMethod.setAccessible(true);
	}

	private String print(String series, String title) throws Exception {
		StringWriter sw = new StringWriter();
		try (BufferedWriter bw = new BufferedWriter(sw)) {
			printSeriesAndTitleMethod.invoke(converter, bw, series, title);
			bw.flush();
			return sw.toString();
		}
	}

	/** シリーズ名と表題が異なる場合は両方出力される (なろう等の既存挙動を維持) */
	@Test
	public void testSeriesAndTitleDifferent() throws Exception {
		assertEquals("シリーズ名\n作品タイトル\n", print("シリーズ名", "作品タイトル"));
	}

	/** SERIES と TITLE が同一マッチの場合は表題のみ出力される (項目 28) */
	@Test
	public void testSeriesSameAsTitle() throws Exception {
		assertEquals("走れメロス\n", print("走れメロス", "走れメロス"));
	}

	/** シリーズ名がない場合は表題のみ */
	@Test
	public void testSeriesNull() throws Exception {
		assertEquals("作品タイトル\n", print(null, "作品タイトル"));
	}

	/** 表題がない場合はシリーズ名のみ (従来挙動を維持) */
	@Test
	public void testTitleNull() throws Exception {
		assertEquals("シリーズ名\n", print("シリーズ名", null));
	}
}
