import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import com.github.hmdev.web.NarouFormatSettings;

public class NarouFormatSettingsTest {

    @Test
    public void testLoadIniSetsFlags() throws Exception {
        File tmp = File.createTempFile("setting_narourb", ".ini");
        tmp.deleteOnExit();
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8"))) {
            bw.write("author_comment_style=\"plain\"\n");
            bw.write("enable_convert_num_to_kanji=true\n");
            bw.write("enable_kanji_num_with_units=true\n");
            bw.write("kanji_num_with_units_lower_digit_zero=4\n");
            bw.write("enable_auto_join_in_brackets=true\n");
        }

        NarouFormatSettings s = new NarouFormatSettings();
        s.load(tmp);

        assertEquals("plain", s.getAuthorCommentStyle());
        assertTrue(s.isEnableConvertNumToKanji());
        assertTrue(s.isEnableKanjiNumWithUnits());
        assertEquals(4, s.getKanjiNumWithUnitsLowerDigitZero());
        assertTrue(s.isEnableAutoJoinInBrackets());
    }
}
