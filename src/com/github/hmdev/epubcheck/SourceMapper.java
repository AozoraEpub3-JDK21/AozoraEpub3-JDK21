package com.github.hmdev.epubcheck;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class SourceMapper {
    private final Path epubPath;
    private final Gson gson = new Gson();
    private Map<String, SourceInfo> mapCache;

    public SourceMapper(Path epubPath) {
        this.epubPath = epubPath;
    }

    public SourceInfo resolve(String epubInternalPath) throws IOException {
        if (mapCache == null) {
            load();
        }
        String key = normalizePath(epubInternalPath);
        return mapCache.get(key);
    }

    private void load() throws IOException {
        mapCache = new HashMap<>();
        try (ZipFile zip = new ZipFile(epubPath.toFile())) {
            ZipEntry entry = zip.getEntry("OPS/aozora/aozora-source-map.json");
            if (entry == null) return; // no source map
            try (InputStream in = zip.getInputStream(entry)) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                FilesDef def = gson.fromJson(json, FilesDef.class);
                if (def != null && def.files != null) {
                    for (Map.Entry<String, FileInfo> e : def.files.entrySet()) {
                        String k = normalizePath(e.getKey());
                        FileInfo fi = e.getValue();
                        mapCache.put(k, new SourceInfo(fi.sourceName, fi.templateName, fi.type, fi.originalLineOffset));
                    }
                }
            }
        }
    }

    private String normalizePath(String p) {
        if (p == null) return null;
        String s = p.replace('\\', '/');
        // Collapse any double slashes and enforce case as-is
        while (s.contains("//")) s = s.replace("//", "/");
        return s;
    }

    static class FilesDef {
        Map<String, FileInfo> files;
    }

    static class FileInfo {
        String sourceName;
        String templateName;
        String type;
        Integer originalLineOffset;
    }
}
