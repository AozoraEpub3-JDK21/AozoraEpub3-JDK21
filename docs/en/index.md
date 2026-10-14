---
layout: default
lang: en
title:  AozoraEpub3-JDK21 Download
description: AozoraEpub3-JDK21 converts Aozora Bunko text to EPUB 3.3. Java 21–26 compatible on Windows, macOS, and Linux. Supports narou.rb, EpubCheck 5.x, and GUI/CLI.
---

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
  <strong>📚 Documentation:</strong>
  <strong>Home</strong> | 
  <a href="usage.html">Usage</a> | 
  <a href="gaiji-settings.html">Gaiji Settings</a> | 
  <a href="narou-setup.html">narou.rb Setup</a> |
  <a href="narou-rs-setup.html">narou.rs</a> |
  <a href="development.html">Development</a> | 
  <a href="epub33.html">EPUB 3.3</a> |
  <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
  <div style="float: right;">🌐 <a href="../">日本語</a></div>
</nav>

## AozoraEpub3-JDK21 Download

<div style="text-align: center; margin: 2em 0;">
  <p><strong>Latest: </strong> v1.7.2-jdk21 (October 14, 2026) |
  <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/tag/v1.7.2-jdk21">Release Notes</a></p>

  <div style="display: inline-block; text-align: center;">
    <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/download/v1.7.2-jdk21/AozoraEpub3-1.7.2-jdk21.zip" class="btn" style="display: inline-block; margin: 10px; padding: 12px 24px;">
      📦 Windows (ZIP)
    </a>
    <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/download/v1.7.2-jdk21/AozoraEpub3-1.7.2-jdk21.tar.gz" class="btn" style="display: inline-block; margin: 10px; padding: 12px 24px;">
      🐧 Linux (TAR.GZ)
    </a>
    <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/download/v1.7.2-jdk21/AozoraEpub3-1.7.2-jdk21.tar.gz" class="btn" style="display: inline-block; margin: 10px; padding: 12px 24px;">
      🍎 macOS (TAR.GZ)
    </a>
  </div>
  
  <p><small><a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases">📋 View all releases</a></small></p>
</div>

---

## What's New in v1.7.2-jdk21

- **Shelf (hidden feature)**: a download queue (reservations). While one work downloads, others can be reserved, and they download one after another. Fixed typed text being lost when the shelf redraws during a rename

See the [release notes](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/tag/v1.7.2-jdk21) for the full list.

## What's New in v1.7.0-jdk21

- **Aozora Bunko work URLs are converted from the text zip on the book card.** Reading the HTML back used to drop emphasis dots, indents, headings and more; now the result is the same as converting the zip directly. The GUI also converts zip URLs instead of only downloading them
- **Web conversion fixes**: comma-separated numbers in the body (`1,000円`) no longer vanish, and `［＃` in a title or episode title is no longer read as an annotation. Numbers in episode and chapter titles now follow narou.rb
- **Fixes that change the output**: short centered pages in vertical books (chapter title pages and the like) are now really centered, Web conversion puts a page break after a chapter title page, and the running head (柱) on chapter pages is output as narou.rb does
- **Fixes for stopped conversions and lost text**: an unclosed block annotation before the title no longer empties the body, an unclosed ruby or gaiji annotation no longer stops the whole conversion, and one failed book no longer stops a CLI batch

See the [release notes](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/tag/v1.7.0-jdk21) for the full list.

See the [release list](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases) for past changes.

---

## Screenshots

<div style="display: flex; flex-wrap: wrap; gap: 1em; justify-content: center; margin: 2em 0;">
  <figure style="flex: 1 1 320px; max-width: 480px; margin: 0;">
    <img src="../assets/screenshot-app.png" alt="Settings for title, cover and page output, with a drop area for input files" style="width: 100%; height: auto;">
    <figcaption style="text-align: center; font-size: 0.9em;">Conversion window (drag and drop a file or URL)</figcaption>
  </figure>
  <figure style="flex: 1 1 320px; max-width: 480px; margin: 0;">
    <img src="../assets/screenshot-preview.png" alt="Viewer with a chapter table of contents on the left and vertical multi-column text on the right" style="width: 100%; height: auto;">
    <figcaption style="text-align: center; font-size: 0.9em;">Browser preview (table of contents, vertical writing, ruby, multi-column)</figcaption>
  </figure>
</div>

You can preview a converted EPUB straight in your browser before moving it to a reader app.
The preview shows *Night on the Galactic Railroad* by Kenji Miyazawa from Aozora Bunko (public domain).

---

## About This Project

This software is a derivative of **AozoraEpub3** by hmdev, updated for Java 21–26 compatibility and support for modern operating systems.

It aims to comply with EPUB 3.3 and the [Japanese Book Publishing Association (電書協) EPUB 3 Production Guide](https://www.ebpaj.jp/counsel/guide), validated with epubcheck 5.x.

---

## System Requirements

- **Java 25 LTS recommended**
  - Compatible with Java 21 LTS as well (JDK 26 runtime also verified)
  - **Minimum requirement: Java 21 or later**
- Windows / macOS / Linux

If you don't have Java installed, download [Eclipse Temurin](https://adoptium.net/temurin/releases/) Java 25 LTS (Java 21 LTS also works).

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
3. Double-click `AozoraEpub3.exe` to launch
4. The GUI will open when ready

> **Note**: If double-clicking the JAR file doesn't work, use the EXE file instead.

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

## Related Guides

- **[narou.rs Setup Guide](narou-rs-setup.html)** (recommended) — Setting up the Rust-based compatible tool narou.rs with AozoraEpub3 (Windows 11, with screenshots). Since v0.4.0 you can also choose the Java-free GPL edition with a built-in conversion engine. Actively updated with feature and security fixes — the better choice if you are starting fresh
- **[narou.rb Setup Guide](narou-setup.html)** — Installing the Ruby-based narou.rb and connecting it to AozoraEpub3

---

## Troubleshooting

- **Java is not installed** — Download Java 25 LTS from [Temurin](https://adoptium.net/temurin/releases/) and install (Java 21 or later also works).
- **JAR file won't open on Windows** — Use the EXE file, or launch from Command Prompt with `java -jar AozoraEpub3.jar`.
- **"Windows protected your PC" when starting the EXE** — This is a SmartScreen warning, not a malware detection. Right-click the ZIP → Properties → tick **Unblock** **before** extracting, and it will not appear. [Full steps](usage.html#windows-protected-your-pc-when-launching-aozoraepub3exe)
- **Permission denied on Linux/macOS** — Run `chmod +x AozoraEpub3.sh` and try again.
- **narou.rb says Java is not installed, but Java is installed** — EPUB output has most likely failed. From v1.3.7-jdk21 the exit code on conversion failure changed from `0` to `1` (an intentional change). See the [narou.rb Setup Guide](narou-setup.html).
- **Detecting conversion success from a script** — The CLI returns `0` on success and `1` on failure. See [Exit Codes](usage.html#exit-codes) in the usage guide.
- **Other issues** — Report on [GitHub Issues](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/issues).

---

## Related Resources

- [GitHub README](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21#readme) — Features & detailed settings
- [EPUB 3.3 Guide](epub33.html) — Changes from 3.0 and support status
- [日本語](../index.html) — このページを日本語で表示
