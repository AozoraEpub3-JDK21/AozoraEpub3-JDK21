package com.github.hmdev.config;

import static org.junit.Assert.*;

import java.io.StringWriter;
import java.nio.file.*;
import java.util.Properties;
import java.util.Vector;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Test;

/**
 * Velocityテンプレート vertical_text.vm / horizontal_text.vm に渡した値が
 * 期待どおりCSSへ反映されることの検証。
 */
public class CssTemplateRenderTest {

    @Test
    public void verticalTextCssReflectsValues() throws Exception {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        if (!Files.exists(projectRoot.resolve("template"))) {
            Path testClasses = Paths.get(CssTemplateRenderTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path buildDir = testClasses.getParent().getParent();
            projectRoot = buildDir.getParent();
        }

        Properties vp = new Properties();
        // テンプレート直下をルートに設定
        vp.setProperty("resource.loaders", "file");
        vp.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
        vp.setProperty("resource.loader.file.path", projectRoot.resolve("template").resolve("OPS").resolve("css").toString());
        VelocityEngine ve = new VelocityEngine(vp);

        VelocityContext ctx = new VelocityContext();
        ctx.put("pageMargin", new String[]{"1cm","1cm","2cm","3cm"});
        ctx.put("bodyMargin", new String[]{"4px","5px","6px","7px"});
        ctx.put("lineHeight", 1.7f);
        ctx.put("fontSize", 115);
        ctx.put("boldUseGothic", Boolean.TRUE);
        ctx.put("gothicUseBold", Boolean.TRUE);
        ctx.put("vecGaijiInfo", new Vector<>());

        StringWriter out = new StringWriter();
        Template t = ve.getTemplate("vertical_text.vm", "UTF-8");
        t.merge(ctx, out);
        String css = out.toString();

        assertTrue(css.contains("font-size: 115%"));
        assertTrue(css.contains("line-height: 1.7"));
        assertTrue(css.contains("@page"));
        assertTrue(css.contains("1cm 1cm 2cm 3cm"));
        assertTrue(css.contains("4px 5px 6px 7px"));
        assertTrue(css.contains("writing-mode: vertical-rl"));
    }
}
