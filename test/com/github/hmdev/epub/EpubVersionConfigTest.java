package com.github.hmdev.epub;

import static org.junit.Assert.*;

import java.io.StringWriter;
import java.nio.file.*;
import java.util.*;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Test;

import com.github.hmdev.writer.Epub3Writer;

/**
 * EpubVersion の INI 上書き機構の動作確認。
 *
 * <p>モダン化計画書 ステージ S-1: package.opf の version 属性をプロパティで上書き可能にし、
 * デフォルトは後方互換のため "3.0" を維持する。許容値は 3.0 / 3.1 / 3.2 / 3.3。
 */
public class EpubVersionConfigTest {

    static class StubBookInfo {
        public boolean getImageOnly() { return false; }
        public boolean getVertical() { return true; }
        public boolean getInsertCoverPage() { return false; }
        public boolean getInsertTocPage() { return false; }
    }

    @Test
    public void defaultVersionIs30() {
        Epub3Writer writer = new Epub3Writer("./template/");
        assertEquals("3.0", writer.getEpubVersion());
    }

    @Test
    public void setEpubVersionAcceptsAllowedValues() {
        Epub3Writer writer = new Epub3Writer("./template/");
        writer.setEpubVersion("3.3");
        assertEquals("3.3", writer.getEpubVersion());
        writer.setEpubVersion("3.1");
        assertEquals("3.1", writer.getEpubVersion());
        writer.setEpubVersion("3.2");
        assertEquals("3.2", writer.getEpubVersion());
    }

    @Test
    public void setEpubVersionRejectsUnknownValueAndKeepsCurrent() {
        Epub3Writer writer = new Epub3Writer("./template/");
        writer.setEpubVersion("3.3");
        writer.setEpubVersion("9.9"); // 未対応値
        assertEquals("拒否されてデフォルト維持されること", "3.3", writer.getEpubVersion());
    }

    @Test
    public void setEpubVersionIgnoresNullAndBlank() {
        Epub3Writer writer = new Epub3Writer("./template/");
        writer.setEpubVersion(null);
        assertEquals("3.0", writer.getEpubVersion());
        writer.setEpubVersion("");
        assertEquals("3.0", writer.getEpubVersion());
        writer.setEpubVersion("   ");
        assertEquals("3.0", writer.getEpubVersion());
    }

    @Test
    public void setEpubVersionTrimsWhitespace() {
        Epub3Writer writer = new Epub3Writer("./template/");
        writer.setEpubVersion(" 3.3 ");
        assertEquals("3.3", writer.getEpubVersion());
    }

    @Test
    public void allowedVersionsContainsExpected() {
        assertTrue(Epub3Writer.ALLOWED_EPUB_VERSIONS.contains("3.0"));
        assertTrue(Epub3Writer.ALLOWED_EPUB_VERSIONS.contains("3.3"));
        // 3.4 は Working Draft なので現時点では未対応（モダン化計画書 ステージ S+ 参照）
        assertFalse("3.4 は Recommendation 化されるまで未対応",
            Epub3Writer.ALLOWED_EPUB_VERSIONS.contains("3.4"));
    }

    @Test
    public void packageVmRendersConfiguredVersion() throws Exception {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        if (!Files.exists(projectRoot.resolve("template"))) {
            Path testClasses = Paths.get(EpubVersionConfigTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path buildDir = testClasses.getParent().getParent();
            projectRoot = buildDir.getParent();
        }

        Properties vp = new Properties();
        vp.setProperty("resource.loaders", "file");
        vp.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
        vp.setProperty("resource.loader.file.path", projectRoot.resolve("template").resolve("OPS").toString());
        VelocityEngine ve = new VelocityEngine(vp);

        for (String version : new String[] {"3.0", "3.1", "3.2", "3.3"}) {
            VelocityContext ctx = new VelocityContext();
            ctx.put("title", "t");
            ctx.put("creator", "c");
            ctx.put("identifier", UUID.randomUUID().toString());
            ctx.put("modified", "2020-01-01T00:00:00Z");
            ctx.put("epubVersion", version);
            ctx.put("bookInfo", new StubBookInfo());
            ctx.put("kindle", Boolean.FALSE);
            ctx.put("svgImage", Boolean.FALSE);
            ctx.put("title_page", Boolean.FALSE);
            ctx.put("sections", new ArrayList<>());
            ctx.put("images", new ArrayList<>());

            Template t = ve.getTemplate("package.vm", "UTF-8");
            StringWriter out = new StringWriter();
            t.merge(ctx, out);
            String opf = out.toString();

            assertTrue("version=\""+version+"\" must appear in package.opf",
                opf.contains("version=\""+version+"\""));
        }
    }
}
