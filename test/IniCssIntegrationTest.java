import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.Test;
import org.junit.Ignore;

/**
 * INIで指定したCSS設定がEPUB内CSSに反映されることの統合テスト。
 */
public class IniCssIntegrationTest {

    @Ignore("Runs CLI end-to-end; covered in Actions workflow")
    @Test
    public void iniCssReflectedInTextCss() throws Exception {
        Path tempDir = Files.createTempDirectory("ini_css_it");
        Path outDir = Files.createDirectories(tempDir.resolve("out"));
        Path ini = tempDir.resolve("test.ini");
        Path txt = tempDir.resolve("sample.txt");

        String iniContent = String.join("\n",
                "PageMargin=1,1,2,3",
                "PageMarginUnit=cm",
                "BodyMargin=4,5,6,7",
                "BodyMarginUnit=px",
                "LineHeight=1.7",
                "FontSize=115"
        ) + "\n";
        Files.write(ini, iniContent.getBytes(StandardCharsets.UTF_8));

        Files.write(txt, "表題\n本文です。".getBytes(StandardCharsets.UTF_8));

        // 生成
        AozoraEpub3.main(new String[] {"-i", ini.toString(), "-of", "-ext", ".epub", "-d", outDir.toString(), txt.toString()});

        // 出力EPUBを1つ取得
        Path[] epubs = Files.list(outDir).filter(p -> p.toString().endsWith(".epub")).toArray(Path[]::new);
        assertTrue("EPUBが生成されていること", epubs.length >= 1);

        // CSSの中身を検査
        try (ZipFile zf = new ZipFile(epubs[0].toFile())) {
            ZipEntry css = zf.getEntry("OPS/css/vertical_text.css");
            if (css == null) {
                css = zf.getEntry("OPS/css/horizontal_text.css");
            }
            assertNotNull("*_text.css が含まれること", css);
            try (InputStream is = zf.getInputStream(css)) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue("font-size が反映されること", content.contains("font-size: 115%"));
                assertTrue("line-height が反映されること", content.contains("line-height: 1.7"));
                assertTrue("@page margin が反映されること", content.contains("@page") && content.contains("1cm 1cm 2cm 3cm"));
                assertTrue("html margin が反映されること", content.contains("4px 5px 6px 7px"));
            }
        }
    }
}
