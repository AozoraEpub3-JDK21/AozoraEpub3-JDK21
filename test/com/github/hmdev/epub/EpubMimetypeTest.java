package com.github.hmdev.epub;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipMethod;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Test;

import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.writer.Epub3Writer;

/**
 * EPUBのmimetypeエントリが先頭かつSTOREDで格納されていることを確認する。
 */
public class EpubMimetypeTest {

    @Test
    public void mimetypeIsFirstAndStored() throws Exception {
        Path tempDir = Files.createTempDirectory("epub_mimetype_test");
        Path txt = tempDir.resolve("sample.txt");
        Files.write(txt, "表題\n本文です。".getBytes(StandardCharsets.UTF_8));

        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        if (!Files.exists(projectRoot.resolve("template"))) {
            Path testClasses = Paths.get(EpubMimetypeTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path buildDir = testClasses.getParent().getParent();
            projectRoot = buildDir.getParent();
        }

        // VelocityEngine を template/ ベースで構築
        Path templateRoot = projectRoot.resolve("template");
        Properties vp = new Properties();
        vp.setProperty("resource.loaders", "file");
        vp.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
        vp.setProperty("resource.loader.file.path", templateRoot.toString());
        VelocityEngine ve = new VelocityEngine(vp);

        String templatePath = templateRoot.toString();
        if (!templatePath.endsWith("/") && !templatePath.endsWith("\\")) {
            templatePath = templatePath + "/";
        }
        Epub3Writer writer = new Epub3Writer(templatePath, ve);

        // 必要最小限の設定
        writer.setImageParam(600, 800, 600, 800, 0, 0, 480, 640, 600,
                com.github.hmdev.info.SectionInfo.IMAGE_SIZE_TYPE_HEIGHT, true, false, 0,
                1.0f, 0, 0, 0, 0.8f, 1.0f, 0, 0, 100, 0f, 0, 0.03f);
        writer.setTocParam(false, false);
        writer.setStyles(new String[]{"0","0","0","0"}, new String[]{"0","0","0","0"}, 1.6f, 100, true, true);

        AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, projectRoot.toString() + "/");
        BookInfo bookInfo = new BookInfo(txt.toFile());
        bookInfo.vertical = true;

        Path epub = tempDir.resolve("out.epub");
        try (BufferedReader br = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
            writer.write(converter, br, txt.toFile(), "txt", epub.toFile(), bookInfo, new ImageInfoReader(true, txt.toFile()));
        }

        assertTrue(Files.exists(epub));

        try (InputStream fis = new BufferedInputStream(Files.newInputStream(epub));
             ZipArchiveInputStream zis = new ZipArchiveInputStream(fis)) {
            ArchiveEntry entry = zis.getNextEntry();
            assertNotNull(entry);
            assertEquals("mimetype", entry.getName());
            assertTrue(entry instanceof ZipArchiveEntry);
            ZipArchiveEntry first = (ZipArchiveEntry) entry;
            // メソッドが STORED（0）
            assertEquals(ZipMethod.STORED.getCode(), first.getMethod());
            assertTrue(first.getSize() > 0);
            assertTrue(first.getCrc() != 0);
        }
    }
}
