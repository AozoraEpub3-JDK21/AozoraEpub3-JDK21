package com.github.hmdev.epubcheck;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class TestUtil {
    static void writeSimpleEpubWithSourceMap(Path out,
                                             String internalPath,
                                             String sourceName,
                                             String templateName,
                                             String type) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out.toFile()))) {
            // Non-strict mimetype (DEFLATED) is fine for tests
            ZipEntry mimetype = new ZipEntry("mimetype");
            mimetype.setMethod(ZipEntry.DEFLATED);
            zos.putNextEntry(mimetype);
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OPS/aozora/aozora-source-map.json"));
            String json = "{" +
                    "\"files\": {" +
                    "  \"" + internalPath + "\": {" +
                    "    \"sourceName\": \"" + sourceName + "\"," +
                    "    \"templateName\": \"" + templateName + "\"," +
                    "    \"type\": \"" + type + "\"" +
                    "  }" +
                    "}" +
                    "}";
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}
