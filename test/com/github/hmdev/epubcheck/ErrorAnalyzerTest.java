package com.github.hmdev.epubcheck;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class ErrorAnalyzerTest {
    @Test
    public void joinsValidatorMessagesWithSourceInfo() throws Exception {
        String json = "{" +
                "\"messages\": [" +
                "  {" +
                "    \"ID\": \"RSC-005\"," +
                "    \"severity\": \"ERROR\"," +
                "    \"message\": \"element div not allowed here\"," +
                "    \"locations\": [{\"path\": \"OPS/text/chapter1.xhtml\", \"line\": 45}]" +
                "  }" +
                "]" +
                "}";
        EpubValidator validator = new EpubValidator();
        EpubValidator.ValidationResult vr = validator.parseJson(json);

        // Prepare source map epub
        Path tmp = Files.createTempFile("sample", ".epub");
        TestUtil.writeSimpleEpubWithSourceMap(tmp,
                "OPS/text/chapter1.xhtml",
                "第1章_出会い.txt",
                "novel_template_v2.html",
                "TEXT_CONTENT");

        SourceMapper mapper = new SourceMapper(tmp);
        ErrorAnalyzer analyzer = new ErrorAnalyzer(mapper);
        List<DiagnosticReport> reports = analyzer.analyze(vr);

        assertEquals(1, reports.size());
        DiagnosticReport r = reports.get(0);
        assertEquals("RSC-005", r.getCode());
        assertEquals("ERROR", r.getSeverity());
        assertEquals("OPS/text/chapter1.xhtml", r.getPath());
        assertNotNull(r.getSourceInfo());
        assertEquals("第1章_出会い.txt", r.getSourceInfo().getSourceName());
    }
}
