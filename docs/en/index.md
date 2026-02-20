---
layout: default
lang: en
title:  AozoraEpub3-JDK21 Download
description: AozoraEpub3-JDK21 converts Aozora Bunko text to EPUB 3.3. Java 21/25 compatible on Windows, macOS, and Linux. Supports narou.rb, EpubCheck 5.x, and GUI/CLI.
---

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
  <strong>📚 Documentation:</strong>
  <a href="./">Home</a> | 
  <a href="usage.html">Usage</a> | 
  <a href="narou-setup.html">narou.rb Setup</a> |
  <a href="development.html">Development</a> | 
  <a href="epub33.html">EPUB 3.3</a> |
  <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
  <div style="float: right;">🌐 <a href="../">日本語</a></div>
</nav>

## AozoraEpub3-JDK21 Download

<div style="text-align: center; margin: 2em 0;">
  <p><strong>Latest: </strong> v1.2.6-jdk21 (January 24, 2026) | 
  <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/blob/master/RELEASE_NOTES.md">Release Notes</a></p>
   
  <div style="display: inline-block; text-align: center;">
    <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/download/v1.2.6-jdk21/AozoraEpub3-1.2.6-jdk21.zip" class="btn" style="display: inline-block; margin: 10px; padding: 12px 24px;">
      📦 Windows (ZIP)
    </a>
    <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/download/v1.2.6-jdk21/AozoraEpub3-1.2.6-jdk21.tar.gz" class="btn" style="display: inline-block; margin: 10px; padding: 12px 24px;">
      🐧 Linux (TAR.GZ)
    </a>
    <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/download/v1.2.6-jdk21/AozoraEpub3-1.2.6-jdk21.tar.gz" class="btn" style="display: inline-block; margin: 10px; padding: 12px 24px;">
      🍎 macOS (TAR.GZ)
    </a>
  </div>
  
  <p><small><a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases">📋 View all releases</a></small></p>
</div>

---

## About This Project

This software is a derivative of **AozoraEpub3** by hmdev, updated for Java 25 compatibility and support for modern operating systems.

It aims to comply with EPUB 3.3 and the [Japanese Book Publishing Association (電書協) EPUB 3 Production Guide](https://www.ebookjapan.jp/), validated with epubcheck 5.x.

---

## System Requirements

- Java 25 or later (JRE / JDK) recommended
  - Also compatible with Java 21 (LTS)
- Windows / macOS / Linux

If you don't have Java installed, download [Eclipse Temurin](https://adoptium.net/temurin/releases/) Java 25.

---

## Install Java 25 (Eclipse Temurin)

### Windows

1. Visit [Adoptium Releases](https://adoptium.net/temurin/releases/)
2. Select JDK 25 → Windows x64 → `.MSI`
3. Double-click the MSI file and follow the installer
4. Verify in Command Prompt: `java -version`

### macOS

1. Visit [Adoptium Releases](https://adoptium.net/temurin/releases/)
2. Select JDK 25 → macOS → `.PKG` (Intel or Apple Silicon M1/M2)
3. Double-click the PKG file and follow the installer
4. Verify in Terminal: `java -version`

### Linux (Ubuntu/Debian)

1. Visit [Adoptium Releases](https://adoptium.net/temurin/releases/)
2. Select JDK 25 → Linux x64 → `.TAR.GZ`
3. Extract: `tar -xzf OpenJDK25U-jdk_x64_linux_hotspot_25_x.tar.gz`
4. Verify: `./jdk-25.x.x+yy/bin/java -version` or add to PATH

---

## Quick Start (Windows)

1. Download the latest ZIP file from [Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases)
2. Extract to any folder
3. Double-click `AozoraEpub3.bat` to launch
4. The GUI will open when ready

> **Note**: If double-clicking the JAR file doesn't work, use the BAT file instead.

---

## Installation (macOS / Linux)

1. Download the TAR.GZ file from [Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases)
2. Extract: `tar -xzf AozoraEpub3-*.tar.gz`
3. Navigate to the folder and run: `./AozoraEpub3.sh`
4. If you get a permission error, first run: `chmod +x AozoraEpub3.sh`

---

## Command Line Usage

For advanced configuration, you can run from the command line:

```bash
java -jar AozoraEpub3.jar -of -d out input.txt
```

To launch the GUI, run without arguments: `java -jar AozoraEpub3.jar`

See the [README](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21#readme) for detailed options.

---

## Troubleshooting

- **Java is not installed** — Download Java 25 from [Temurin](https://adoptium.net/temurin/releases/) and install (Java 21 or later also works).
- **JAR file won't open on Windows** — Use the BAT file, or launch from Command Prompt with `java -jar AozoraEpub3.jar`.
- **Permission denied on Linux/macOS** — Run `chmod +x AozoraEpub3.sh` and try again.
- **Other issues** — Report on [GitHub Issues](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/issues).

---

## Related Resources

- [GitHub README](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21#readme) — Features & detailed settings
- [EPUB 3.3 Guide](epub33.html) — Changes from 3.0 and support status
- [日本語](../index.html) — このページを日本語で表示
