package com.github.hmdev.epubcheck;

import com.github.hmdev.util.LogAppender;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Convenience runner to execute epubcheck and summarize results */
public class EpubcheckRunner {
    /** Try to resolve epubcheck.jar under ./lib/epubcheck.jar */
    static Path resolveEpubcheckJar() {
        String userProvided = System.getProperty("epubcheck.jar");
        if (userProvided != null && userProvided.trim().length() > 0) {
            File f = new File(userProvided.trim());
            if (f.exists()) return f.toPath();
        }
        File lib = new File("lib/epubcheck.jar");
        if (lib.exists()) return lib.toPath();
        return null;
    }

    public static void runAndLog(File epubFile) throws Exception {
        Path jar = resolveEpubcheckJar();
        if (jar == null) {
            LogAppender.println("[INFO] epubcheck.jar が見つかりませんでした（lib/epubcheck.jar を同梱すると自動検証されます）");
            return;
        }
        EpubValidator validator = new EpubValidator();
        String json = validator.runEpubcheck(jar, epubFile.toPath(), Duration.ofSeconds(30));
        EpubValidator.ValidationResult vr = validator.parseJson(json);

        SourceMapper mapper = new SourceMapper(epubFile.toPath());
        ErrorAnalyzer analyzer = new ErrorAnalyzer(mapper);
        List<DiagnosticReport> reports = analyzer.analyze(vr);

        int errors = 0, warns = 0, infos = 0;
        for (DiagnosticReport r : reports) {
            String sev = r.getSeverity() != null ? r.getSeverity() : "INFO";
            if ("ERROR".equalsIgnoreCase(sev)) errors++;
            else if ("WARN".equalsIgnoreCase(sev) || "WARNING".equalsIgnoreCase(sev)) warns++;
            else infos++;
            String src = (r.getSourceInfo() != null ? r.getSourceInfo().getSourceName() : null);
            String loc = r.getPath() + (r.getLine() != null ? (":" + r.getLine()) : "");
            LogAppender.println(String.format("[%s] %s %s %s%s",
                    sev,
                    (r.getCode() != null ? r.getCode() : ""),
                    loc,
                    (src != null ? " ← " + src : ""),
                    (r.getMessage() != null ? "\n  └ " + r.getMessage() : "")));
        }
        LogAppender.println(String.format("epubcheck まとめ: ERROR=%d, WARN=%d, INFO=%d", errors, warns, infos));
    }
}
