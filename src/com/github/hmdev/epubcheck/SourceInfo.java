package com.github.hmdev.epubcheck;

public class SourceInfo {
    private final String sourceName;
    private final String templateName;
    private final String type;
    private final Integer originalLineOffset;

    public SourceInfo(String sourceName, String templateName, String type, Integer originalLineOffset) {
        this.sourceName = sourceName;
        this.templateName = templateName;
        this.type = type;
        this.originalLineOffset = originalLineOffset;
    }

    public String getSourceName() { return sourceName; }
    public String getTemplateName() { return templateName; }
    public String getType() { return type; }
    public Integer getOriginalLineOffset() { return originalLineOffset; }
}
