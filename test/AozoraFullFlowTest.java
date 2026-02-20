import com.github.hmdev.web.WebAozoraConverter;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.io.PrintStream;

public class AozoraFullFlowTest {
    public static void main(String[] args) {
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));

            String url = "https://www.aozora.gr.jp/cards/000035/files/1567_14913.html";
            File currentDir = new File(".").getAbsoluteFile();
            if (currentDir.getName().equals(".")) {
                currentDir = currentDir.getParentFile();
            }
            File configPath = new File(currentDir, "web");
            File cachePath = new File(currentDir, "build/test_cache");
            cachePath.mkdirs();

            System.out.println("Step 1: Downloading and converting to Aozora Text...");
            WebAozoraConverter converter = WebAozoraConverter.createWebAozoraConverter(url, configPath);
            if (converter == null) {
                System.err.println("Failed to create converter");
                return;
            }

            // アルゴリズムによる自動生成。ファイル名は [著者名] タイトル.txt になるはず
            File txtFile = converter.convertToAozoraText(url, cachePath, 1000, 0, false, false, false, 0, null);
            
            if (txtFile == null || !txtFile.exists()) {
                System.err.println("Text conversion failed.");
                return;
            }
            System.out.println("Text file created: " + txtFile.getAbsolutePath());

            System.out.println("Step 2: Converting Aozora Text to EPUB...");
            File epubOutDir = new File(currentDir, "build/epub_out");
            if (epubOutDir.exists()) {
                Files.walk(epubOutDir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
            epubOutDir.mkdirs();

            String[] epubArgs = {
                "-enc", "UTF-8",
                "-tf",
                "-d", epubOutDir.getAbsolutePath(),
                txtFile.getAbsolutePath()
            };
            
            Class<?> aozoraClass = Class.forName("AozoraEpub3");
            Method mainMethod = aozoraClass.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object)epubArgs);
            
            File[] files = epubOutDir.listFiles((dir, name) -> name.endsWith(".epub"));
            if (files != null && files.length > 0) {
                for (File epub : files) {
                    System.out.println("EPUB created: " + epub.getAbsolutePath());
                    StringBuilder sb = new StringBuilder();
                    for (char c : epub.getName().toCharArray()) {
                        if (c <= 127) sb.append(c);
                        else sb.append(String.format("\\u%04x", (int)c));
                    }
                    System.out.println("EPUB filename (escaped): " + sb.toString());
                }
            } else {
                System.err.println("EPUB generation failed.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
