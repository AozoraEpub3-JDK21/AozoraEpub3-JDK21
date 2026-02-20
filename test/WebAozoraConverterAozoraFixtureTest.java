import org.junit.Test;
import static org.junit.Assert.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.File;

public class WebAozoraConverterAozoraFixtureTest {

    @Test
    public void testIndexLinks() throws Exception {
        Path p = Paths.get("test_data", "aozora", "index.html");
        String s = Files.readString(p, StandardCharsets.UTF_8);
        Document doc = Jsoup.parse(s);
        Elements links = doc.select("a[href$=.html]");
        assertEquals("目次の章リンク数", 2, links.size());
    }

    @Test
    public void testChapterContainsRuby() throws Exception {
        Path p = Paths.get("test_data", "aozora", "ch01.html");
        String s = Files.readString(p, StandardCharsets.UTF_8);
        Document doc = Jsoup.parse(s);
        Elements honbun = doc.select(".main_text");
        assertTrue("本文要素が存在すること", honbun.size() > 0);
        assertTrue("本文に段落テキストが含まれること", honbun.text().contains("本文の段落1"));
        assertTrue("ルビ要素が含まれること", doc.select("ruby").size() > 0);
    }
}
