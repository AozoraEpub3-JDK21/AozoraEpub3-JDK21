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
        Properties vp = new Properties();
        vp.setProperty("resource.loaders", "file");
        vp.setProperty("resource.loader.file.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
        vp.setProperty("resource.loader.file.path", templateDir().resolve(subPath).toString());
        return new VelocityEngine(vp);
    }

    /** The project's template/ directory (falls back to the test classes' location when the working directory is elsewhere). */
    public static Path templateDir() throws Exception {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        if (!Files.exists(projectRoot.resolve("template"))) {
            Path testClasses = Paths.get(VelocityTestUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path buildDir = testClasses.getParent().getParent();
            projectRoot = buildDir.getParent();
        }
        return projectRoot.resolve("template");
    }
}
