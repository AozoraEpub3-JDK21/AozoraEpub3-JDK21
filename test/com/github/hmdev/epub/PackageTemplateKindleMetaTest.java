package com.github.hmdev.epub;

import static org.junit.Assert.*;

import java.io.StringWriter;
import java.nio.file.*;
import java.util.*;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Test;

/**
 * Kindle(iPhone)向けの primary-writing-mode 対応をテンプレートで維持していることを検証。
 * 対象: package.vm 内の `<meta name="primary-writing-mode" content="horizontal-rl"/>`。
 */
public class PackageTemplateKindleMetaTest {

    public static class StubBookInfo {
        public boolean getImageOnly() { return true; }
        public boolean isImageOnly() { return true; }
        public boolean getVertical() { return true; }
        public boolean isVertical() { return true; }
        public boolean getInsertCoverPage() { return false; }
        public boolean getInsertTocPage() { return false; }
    }

private VelocityEngine createVelocityEngine() throws Exception {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        if (!Files.exists(projectRoot.resolve("template"))) {
            Path testClasses = Paths.get(PackageTemplateKindleMetaTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path buildDir = testClasses.getParent().getParent();
            projectRoot = buildDir.getParent();
        }

        Properties vp = new Properties();
        vp.setProperty("resource.loaders", "file");
        vp.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
        vp.setProperty("resource.loader.file.path", projectRoot.resolve("template").resolve("OPS").toString());
        return new VelocityEngine(vp);
    }

    private String renderPackageVm(VelocityEngine ve, VelocityContext ctx) throws Exception {
        Template t = ve.getTemplate("package.vm", "UTF-8");
        StringWriter out = new StringWriter();
        t.merge(ctx, out);
        return out.toString();
    }

    @Test
    public void kindleImageOnlyEmitsHorizontalRlMeta() throws Exception {
        VelocityEngine ve = createVelocityEngine();

        VelocityContext ctx = new VelocityContext();
        ctx.put("title", "t");
        ctx.put("creator", "c");
        ctx.put("identifier", UUID.randomUUID().toString());
        ctx.put("modified", "2020-01-01T00:00:00Z");
        ctx.put("bookInfo", new StubBookInfo());
        ctx.put("kindle", Boolean.TRUE);
        ctx.put("svgImage", Boolean.FALSE);
        ctx.put("title_page", Boolean.FALSE);
        ctx.put("sections", new ArrayList<>());
        ctx.put("images", new ArrayList<>());

        String opf = renderPackageVm(ve, ctx);

        assertTrue("primary-writing-mode horizontal-rl should be present for kindle image-only",
                opf.contains("<meta name=\"primary-writing-mode\" content=\"horizontal-rl\"/>"));
    }

    /**
     * ImageOnly + Vertical (non-Kindle) で horizontal-rl が出力される。
     */
    @Test
    public void imageOnlyVerticalEmitsHorizontalRlMeta() throws Exception {
        VelocityEngine ve = createVelocityEngine();

        class VerticalBookInfo extends StubBookInfo {
            @Override public boolean getImageOnly() { return true; }
            @Override public boolean isImageOnly() { return true; }
            @Override public boolean getVertical() { return true; }
            @Override public boolean isVertical() { return true; }
        }

        VelocityContext ctx = new VelocityContext();
        ctx.put("title", "t");
        ctx.put("creator", "c");
        ctx.put("identifier", UUID.randomUUID().toString());
        ctx.put("modified", "2020-01-01T00:00:00Z");
        ctx.put("bookInfo", new VerticalBookInfo());
        ctx.put("kindle", Boolean.FALSE);
        ctx.put("svgImage", Boolean.FALSE);
        ctx.put("coverImage", new Object() {});
        ctx.put("title_page", Boolean.FALSE);
        ctx.put("sections", new ArrayList<>());
        ctx.put("images", new ArrayList<>());

        String opf = renderPackageVm(ve, ctx);

        assertTrue("primary-writing-mode horizontal-rl should be present for image-only vertical",
                opf.contains("<meta name=\"primary-writing-mode\" content=\"horizontal-rl\"/>"));
    }

    /**
     * ImageOnly + Horizontal で horizontal-lr が出力される。
     */
    @Test
    public void imageOnlyHorizontalEmitsHorizontalLrMeta() throws Exception {
        VelocityEngine ve = createVelocityEngine();

        class HorizontalBookInfo extends StubBookInfo {
            @Override public boolean getImageOnly() { return true; }
            @Override public boolean isImageOnly() { return true; }
            @Override public boolean getVertical() { return false; }
            @Override public boolean isVertical() { return false; }
        }

        VelocityContext ctx = new VelocityContext();
        ctx.put("title", "t");
        ctx.put("creator", "c");
        ctx.put("identifier", UUID.randomUUID().toString());
        ctx.put("modified", "2020-01-01T00:00:00Z");
        ctx.put("bookInfo", new HorizontalBookInfo());
        ctx.put("kindle", Boolean.FALSE);
        ctx.put("svgImage", Boolean.FALSE);
        ctx.put("coverImage", new Object() {});
        ctx.put("title_page", Boolean.FALSE);
        ctx.put("sections", new ArrayList<>());
        ctx.put("images", new ArrayList<>());

        String opf = renderPackageVm(ve, ctx);

        assertTrue("primary-writing-mode horizontal-lr should be present for image-only horizontal",
                opf.contains("<meta name=\"primary-writing-mode\" content=\"horizontal-lr\"/>"));
    }

    /**
     * ReflowableText + Vertical で writing-mode: vertical-rl と primary-writing-mode: horizontal-rl が出力される。
     */
    @Test
    public void reflowableVerticalEmitsVerticalWritingMode() throws Exception {
        VelocityEngine ve = createVelocityEngine();

        class VerticalBookInfo extends StubBookInfo {
            @Override public boolean getImageOnly() { return false; }
            @Override public boolean isImageOnly() { return false; }
            @Override public boolean getVertical() { return true; }
            @Override public boolean isVertical() { return true; }
        }

        VelocityContext ctx = new VelocityContext();
        ctx.put("title", "t");
        ctx.put("creator", "c");
        ctx.put("identifier", UUID.randomUUID().toString());
        ctx.put("modified", "2020-01-01T00:00:00Z");
        ctx.put("bookInfo", new VerticalBookInfo());
        ctx.put("kindle", Boolean.FALSE);
        ctx.put("svgImage", Boolean.FALSE);
        ctx.put("title_page", Boolean.FALSE);
        ctx.put("sections", new ArrayList<>());
        ctx.put("images", new ArrayList<>());

        String opf = renderPackageVm(ve, ctx);

        assertTrue("writing-mode vertical-rl should be present for reflowable vertical",
                opf.contains("<meta name=\"writing-mode\" content=\"vertical-rl\"/>"));
        assertTrue("primary-writing-mode horizontal-rl should be present for reflowable vertical",
                opf.contains("<meta name=\"primary-writing-mode\" content=\"horizontal-rl\"/>"));
    }

    /**
     * ReflowableText + Horizontal で primary-writing-mode: horizontal-lr が出力される（writing-mode は不在）。
     */
    @Test
    public void reflowableHorizontalEmitsHorizontalLrMeta() throws Exception {
        VelocityEngine ve = createVelocityEngine();

        class HorizontalBookInfo extends StubBookInfo {
            @Override public boolean getImageOnly() { return false; }
            @Override public boolean isImageOnly() { return false; }
            @Override public boolean getVertical() { return false; }
            @Override public boolean isVertical() { return false; }
        }

        VelocityContext ctx = new VelocityContext();
        ctx.put("title", "t");
        ctx.put("creator", "c");
        ctx.put("identifier", UUID.randomUUID().toString());
        ctx.put("modified", "2020-01-01T00:00:00Z");
        ctx.put("bookInfo", new HorizontalBookInfo());
        ctx.put("kindle", Boolean.FALSE);
        ctx.put("svgImage", Boolean.FALSE);
        ctx.put("title_page", Boolean.FALSE);
        ctx.put("sections", new ArrayList<>());
        ctx.put("images", new ArrayList<>());

        String opf = renderPackageVm(ve, ctx);

        assertTrue("primary-writing-mode horizontal-lr should be present for reflowable horizontal",
                opf.contains("<meta name=\"primary-writing-mode\" content=\"horizontal-lr\"/>"));
        assertFalse("writing-mode should not be present for reflowable horizontal",
                opf.contains("<meta name=\"writing-mode\""));
    }
}
