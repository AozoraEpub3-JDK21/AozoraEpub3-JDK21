import com.github.hmdev.web.WebAozoraConverter;
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.io.PrintStream;

public class AozoraRealTest {
    public static void main(String[] args) {
        try {
            // 本文ページを直接指定
            String url = "https://www.aozora.gr.jp/cards/000035/files/1567_14913.html";
            File currentDir = new File(".").getAbsoluteFile();
            if (currentDir.getName().equals(".")) {
                currentDir = currentDir.getParentFile();
            }
            File configPath = new File(currentDir, "web");
            File cachePath = new File(currentDir, "build/test_cache");
            cachePath.mkdirs();

            System.out.println("Config Path: " + configPath.getAbsolutePath());
            System.out.println("Converting: " + url);
            
            WebAozoraConverter converter = WebAozoraConverter.createWebAozoraConverter(url, configPath);
            if (converter == null) {
                System.err.println("Failed to create converter");
                return;
            }

            File result = converter.convertToAozoraText(url, cachePath, 1000, 0, false, false, false, 0);
            if (result != null && result.exists()) {
                System.out.println("Success! Result saved to: " + result.getAbsolutePath());
                System.out.println("--- First 1000 bytes of converted.txt (UTF-8 as Hex) ---");
                try (FileInputStream fis = new FileInputStream(result)) {
                    byte[] buf = new byte[1024];
                    int len = fis.read(buf);
                    for (int i = 0; i < Math.min(len, 1000); i++) {
                        System.out.print(String.format("%02x ", buf[i]));
                        if ((i + 1) % 16 == 0) System.out.println();
                    }
                    System.out.println();
                }
            } else {
                System.err.println("Conversion failed or result not found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
