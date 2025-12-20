---
layout: default
lang: en
title: AozoraEpub3-JDK21 Download
description: Aozora Bunko to EPUB3 Converter - Setup & Quick Start Guide
---

# AozoraEpub3-JDK21 Download

<div style="text-align: center; margin: 2em 0;">
  <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/download/v1.2.5-jdk21/AozoraEpub3-1.2.5-jdk21.zip" class="btn" style="font-size: 1.2em; padding: 15px 30px; background: #0366d6; color: white; margin: 10px;">
    📦 Download for Windows (ZIP)
  </a>
  <br>
  <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/download/v1.2.5-jdk21/AozoraEpub3-1.2.5-jdk21.tar.gz" class="btn" style="margin: 10px;">
    🐧 Download for Linux (TAR.GZ)
  </a>
  <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/download/v1.2.5-jdk21/AozoraEpub3-1.2.5-jdk21.tar.gz" class="btn" style="margin: 10px;">
    🍎 Download for macOS (TAR.GZ)
  </a>
  <br>
  <small><a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases">View all releases</a></small>
</div>

---

## About This Project

This software is a derivative of **AozoraEpub3** by hmdev, updated for Java 21 (LTS) compatibility and support for modern operating systems.

It aims to comply with EPUB 3.3 and the [Japanese Book Publishing Association (電書協) EPUB 3 Production Guide](https://www.ebookjapan.jp/), validated with epubcheck 5.x.

---

## System Requirements

- Java 21 or later (JRE / JDK)
- Windows / macOS / Linux

If you don't have Java installed, download [Eclipse Temurin](https://adoptium.net/temurin/releases/) Java 21 LTS.

---

## Install Java 21 (Eclipse Temurin)

### Windows

1. Visit [Adoptium Releases](https://adoptium.net/temurin/releases/)
2. Select JDK 21 → Windows x64 → `.MSI`
3. Double-click the MSI file and follow the installer
4. Verify in Command Prompt: `java -version`

### macOS

1. Visit [Adoptium Releases](https://adoptium.net/temurin/releases/)
2. Select JDK 21 → macOS → `.PKG` (Intel or Apple Silicon M1/M2)
3. Double-click the PKG file and follow the installer
4. Verify in Terminal: `java -version`

### Linux (Ubuntu/Debian)

1. Visit [Adoptium Releases](https://adoptium.net/temurin/releases/)
2. Select JDK 21 → Linux x64 → `.TAR.GZ`
3. Extract: `tar -xzf OpenJDK21U-jdk_x64_linux_hotspot_21_x.tar.gz`
4. Verify: `./jdk-21.x.x+yy/bin/java -version` or add to PATH

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

- **Java is not installed** — Download Java 21 JRE from [Temurin](https://adoptium.net/temurin/releases/) and install.
- **JAR file won't open on Windows** — Use the BAT file, or launch from Command Prompt with `java -jar AozoraEpub3.jar`.
- **Permission denied on Linux/macOS** — Run `chmod +x AozoraEpub3.sh` and try again.
- **Other issues** — Report on [GitHub Issues](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/issues).

---

## Related Resources

- [GitHub README](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21#readme) — Features & detailed settings
- [EPUB 3.3 Guide](epub33.html) — Changes from 3.0 and support status
- [日本語](../index.html) — このページを日本語で表示
