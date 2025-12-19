package com.github.hmdev.epubcheck;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class EpubValidator {
    private final Gson gson = new Gson();

    public ValidationResult parseJson(String json) {
        if (json == null || json.isEmpty()) return new ValidationResult(new ArrayList<>());
        try {
            Messages m = gson.fromJson(json, Messages.class);
            return new ValidationResult(m != null && m.messages != null ? m.messages : new ArrayList<>());
        } catch (Exception e) {
            return new ValidationResult(new ArrayList<>());
        }
    }

    public String runEpubcheck(Path epubcheckJar, Path epubFile, Duration timeout) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        cmd.add("-jar");
        cmd.add(epubcheckJar.toAbsolutePath().toString());
        cmd.add(epubFile.toAbsolutePath().toString());
        cmd.add("--json");
        Process p = new ProcessBuilder(cmd).start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) out.append(line).append('\n');
        }
        boolean finished = p.waitFor(timeout != null ? timeout.toMillis() : 30000, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new InterruptedException("epubcheck timed out");
        }
        return out.toString();
    }

    public static class ValidationResult {
        private final List<Message> messages;
        public ValidationResult(List<Message> messages) { this.messages = messages; }
        public List<Message> getMessages() { return messages; }
    }

    public static class Messages {
        List<Message> messages;
    }

    public static class Message {
        @SerializedName("ID")
        public String id; // e.g., RSC-005
        public String code; // alternative field in some versions
        public String severity; // ERROR/WARN/INFO
        public String message; // human-readable
        public List<Location> locations;

        public String codeOrId() { return id != null ? id : code; }
    }

    public static class Location {
        public String path; // OPS/... internal path
        public Integer line;
    }
}
