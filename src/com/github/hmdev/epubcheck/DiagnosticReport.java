package com.github.hmdev.epubcheck;

public class DiagnosticReport {
    private final String code;
    private final String severity;
    private final String message;
    private final String path;
    private final Integer line;
    private final SourceInfo sourceInfo;

    public DiagnosticReport(String code, String severity, String message, String path, Integer line, SourceInfo sourceInfo) {
        this.code = code;
        this.severity = severity;
        this.message = message;
        this.path = path;
        this.line = line;
        this.sourceInfo = sourceInfo;
    }

    public String getCode() { return code; }
    public String getSeverity() { return severity; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public Integer getLine() { return line; }
    public SourceInfo getSourceInfo() { return sourceInfo; }
}
