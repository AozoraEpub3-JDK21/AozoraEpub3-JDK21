package com.github.hmdev.util;

import java.nio.file.*;
import java.util.Properties;

import org.apache.velocity.app.VelocityEngine;

/**
 * Test utilities for Velocity.
 */
public final class VelocityTestUtils {
    private VelocityTestUtils() {}

    /**
     * Build a VelocityEngine whose file loader points to template/<subPath> under project root.
     * Example: subPath="OPS/css" or "OPS".
     */
    public static VelocityEngine engineForTemplateSubpath(String subPath) throws Exception {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        if (!Files.exists(projectRoot.resolve("template"))) {
            Path testClasses = Paths.get(VelocityTestUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path buildDir = testClasses.getParent().getParent();
            projectRoot = buildDir.getParent();
        }
        Properties vp = new Properties();
        vp.setProperty("resource.loaders", "file");
        vp.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
        vp.setProperty("resource.loader.file.path", projectRoot.resolve("template").resolve(subPath).toString());
        return new VelocityEngine(vp);
    }
}
