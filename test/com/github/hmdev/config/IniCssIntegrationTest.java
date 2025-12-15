package com.github.hmdev.config;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.Test;
import org.junit.Ignore;
import java.util.Properties;
import org.apache.velocity.app.VelocityEngine;

import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.writer.Epub3Writer;

/**
 * INI相当の値がEPUB内CSSに反映されることを、直接Writer/Converterを使って統合検証する。
 */
public class IniCssIntegrationTest {

    @Test
    public void iniCssReflectedInTextCss() throws Exception {
        Path tempDir = Files.createTempDirectory("ini_css_it");
        Path outDir = Files.createDirectories(tempDir.resolve("out"));
        Path txt = tempDir.resolve("sample.txt");
        Files.write(txt, "表題\n本文です。".getBytes(StandardCharsets.UTF_8));

        // プロジェクトルート（chuki_*.txt や template/ がある場所）
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        if (!Files.exists(projectRoot.resolve("template"))) {
            // build/classes/java/test からの相対 → ルートに補正
            Path testClasses = Paths.get(IniCssIntegrationTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path buildDir = testClasses.getParent().getParent(); // build/classes
            projectRoot = buildDir.getParent();
        }

        // VelocityEngine を注入（テンプレートのベースパスは template/）
        Path templateRoot = projectRoot.resolve("template");
        Properties vp = new Properties();
        vp.setProperty("resource.loaders", "file");
        vp.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
        vp.setProperty("resource.loader.file.path", templateRoot.toString());
        VelocityEngine ve = new VelocityEngine(vp);

        // Writer/Converter 準備（templatePath は template/ の絶対パス＋末尾スラッシュ）
        String templatePath = templateRoot.toString();
        if (!templatePath.endsWith("/") && !templatePath.endsWith("\\")) {
            templatePath = templatePath + "/";
        }
        Epub3Writer writer = new Epub3Writer(templatePath, ve);
        // 画像等のパラメータ（既定）
        writer.setImageParam(600, 800, 600, 800, 0, 0, 480, 640, 600, 
                com.github.hmdev.info.SectionInfo.IMAGE_SIZE_TYPE_HEIGHT, true, false, 0, 
                1.0f, 0, 0, 0, 0.8f, 1.0f, 0, 0, 100, 0f, 0, 0.03f);
        // トク情報/スタイル（INIの値に相当）
        String[] pageMargin = {"1cm","1cm","2cm","3cm"};
        String[] bodyMargin = {"4px","5px","6px","7px"};
        writer.setTocParam(false, false);
        writer.setStyles(pageMargin, bodyMargin, 1.7f, 115, true, true);

        // コンバータはテーブル類のロードに projectRoot を利用
        AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, projectRoot.toString() + "/");

        // BookInfo（最小）
        BookInfo bookInfo = new BookInfo(txt.toFile());
        bookInfo.vertical = true;

        // 出力ファイル
        Path epub = outDir.resolve("sample.epub");
        try (BufferedReader br = Files.newBufferedReader(txt, StandardCharsets.UTF_8)) {
            writer.write(converter, br, txt.toFile(), "txt", epub.toFile(), bookInfo, new ImageInfoReader(true, txt.toFile()));
        }

        assertTrue("EPUBが生成されていること", Files.exists(epub));

        // CSSの中身を検査
        try (ZipFile zf = new ZipFile(epub.toFile())) {
            ZipEntry css = zf.getEntry("OPS/css/vertical_text.css");
            if (css == null) css = zf.getEntry("OPS/css/horizontal_text.css");
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
