package com.github.hmdev.epubcheck;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class SourceMapperTest {
    @Test
    public void resolvesSourceInfoFromSourceMap() throws Exception {
        Path tmp = Files.createTempFile("sample", ".epub");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmp.toFile()))) {
            // required mimetype first, uncompressed
            ZipEntry mimetype = new ZipEntry("mimetype");
            mimetype.setMethod(ZipEntry.STORED);
            byte[] mt = "application/epub+zip".getBytes(StandardCharsets.UTF_8);
            mimetype.setSize(mt.length);
            // CRC must be set for STORED; skip strictness by using DEFLATED for test simplicity
            mimetype.setMethod(ZipEntry.DEFLATED);
            zos.putNextEntry(mimetype);
            zos.write(mt);
            zos.closeEntry();

            // add source map
            zos.putNextEntry(new ZipEntry("OPS/aozora/aozora-source-map.json"));
            String json = "{" +
                    "\"files\": {" +
                    "  \"OPS/text/chapter1.xhtml\": {" +
                    "    \"sourceName\": \"第1章_出会い.txt\"," +
                    "    \"templateName\": \"novel_template_v2.html\"," +
                    "    \"type\": \"TEXT_CONTENT\"" +
                    "  }" +
                    "}" +
                    "}";
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        SourceMapper mapper = new SourceMapper(tmp);
        SourceInfo si = mapper.resolve("OPS/text/chapter1.xhtml");
        assertNotNull(si);
        assertEquals("第1章_出会い.txt", si.getSourceName());
        assertEquals("novel_template_v2.html", si.getTemplateName());
        assertEquals("TEXT_CONTENT", si.getType());

        Files.deleteIfExists(tmp);
    }
}
