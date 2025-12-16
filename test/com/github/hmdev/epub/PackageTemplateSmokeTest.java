package com.github.hmdev.epub;

import static org.junit.Assert.*;

import java.io.StringWriter;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Test;

/**
 * package.vm の最小スモーク。必要なメタと縦書きの基本項目が出力されることを確認。
 */
public class PackageTemplateSmokeTest {

    static class StubBookInfo {
        // Velocity の ${bookInfo.X} は getX()/isX() を解決する
        public boolean getImageOnly() { return false; }
        public boolean getVertical() { return true; }
        public boolean getInsertCoverPage() { return false; }
        public boolean getInsertTocPage() { return false; }
    }

    @Test
    public void renderMinimalVerticalPackage() throws Exception {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        if (!Files.exists(projectRoot.resolve("template"))) {
            Path testClasses = Paths.get(PackageTemplateSmokeTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path buildDir = testClasses.getParent().getParent();
            projectRoot = buildDir.getParent();
        }

        Properties vp = new Properties();
        vp.setProperty("resource.loaders", "file");
        vp.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
        vp.setProperty("resource.loader.file.path", projectRoot.resolve("template").resolve("OPS").toString());
        VelocityEngine ve = new VelocityEngine(vp);

        VelocityContext ctx = new VelocityContext();
        String title = "サンプルタイトル";
        String creator = "著者名";
        String identifier = UUID.randomUUID().toString();
        String modified = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date());
        ctx.put("title", title);
        ctx.put("creator", creator);
        ctx.put("identifier", identifier);
        ctx.put("modified", modified);
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

            assertTrue(opf.contains("<package "));
            assertTrue(opf.contains("<metadata"));
            assertTrue(opf.contains("urn:uuid:"));
            assertTrue(opf.contains("<manifest>"));
            assertTrue(opf.contains("nav.xhtml"));
            assertTrue(opf.contains("<spine "));
    }
}
