package com.github.hmdev.epubcheck;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ErrorAnalyzer {
    private final SourceMapper mapper;

    public ErrorAnalyzer(SourceMapper mapper) {
        this.mapper = mapper;
    }

    public List<DiagnosticReport> analyze(EpubValidator.ValidationResult result) throws IOException {
        List<DiagnosticReport> out = new ArrayList<>();
        for (EpubValidator.Message m : result.getMessages()) {
            String code = m.codeOrId();
            String severity = m.severity;
            String message = m.message;
            if (m.locations == null || m.locations.isEmpty()) {
                out.add(new DiagnosticReport(code, severity, message, null, null, null));
                continue;
            }
            for (EpubValidator.Location loc : m.locations) {
                SourceInfo si = mapper.resolve(loc.path);
                out.add(new DiagnosticReport(code, severity, message, loc.path, loc.line, si));
            }
        }
        return out;
    }
}
