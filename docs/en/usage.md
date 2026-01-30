---
layout: default
title: Usage Guide - AozoraEpub3
---

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
  <strong>📚 Documentation:</strong>
  <a href="index.html">Home</a> | 
  <strong>Usage</strong> | 
  <a href="narou-setup.html">narou.rb Setup</a> |
  <a href="development.html">Development</a> | 
  <a href="epub33.html">EPUB 3.3</a> |
  <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
  <div style="float: right;">🌐 <a href="../usage.html">日本語</a></div>
</nav>

## AozoraEpub3 Usage Guide

Complete guide for using AozoraEpub3 to convert Aozora Bunko format text files to EPUB 3 format.

> **System Requirements**: Java 25 recommended (Java 21 or later also works). Check with `java -version`.

## Table of Contents

- [Quick Start](#quick-start)
- [GUI Mode](#gui-mode)
- [Command Line Interface](#command-line-interface)
- [Device Presets](#device-presets)
- [Template Customization](#template-customization)
- [Troubleshooting](#troubleshooting)
- [Advanced Features](#advanced-features)

---

## Quick Start

### GUI Mode (No Arguments)

Simply double-click the JAR file or run:

```bash
java -jar AozoraEpub3.jar
```

A graphical interface will open where you can:
1. Select input file (TXT, ZIP, RAR)
2. Choose device preset (Kobo, Kindle, etc.)
3. Click "Convert" to generate EPUB

### CLI Mode (Simple)

```bash
## Convert UTF-8 text to EPUB
java -jar AozoraEpub3.jar -of -d output input.txt

## Convert with encoding specification
java -jar AozoraEpub3.jar -enc UTF-8 -of -d output input.txt
```

---

## GUI Mode

### Main Window

![GUI Screenshot](assets/images/gui_main.png)

#### Input File Selection
- **Supported formats**: `.txt`, `.zip`, `.rar`
- **Encoding**: Auto-detect or manual selection (UTF-8, Shift_JIS, etc.)
- **Multiple files**: Select multiple text files for batch conversion

#### Output Settings
- **Output directory**: Where EPUB files will be saved
- **Output filename**: Auto-generated from title/author or custom name
- **Overwrite**: Option to overwrite existing files

#### Device Presets
Select optimized settings for your e-reader:
- **Kobo**: Kobo Touch, Glo, Full-size
- **Kindle**: Kindle Paperwhite
- **Sony Reader**: Reader, Reader T3

#### Image Settings
- **Resize**: Scale images to fit device screen
- **Remove margins**: Automatically crop white borders
- **Format**: Convert to JPEG or keep original
- **Quality**: JPEG quality (1-100)

#### Style Settings
- **Font size**: Base font size (80-150%)
- **Line height**: Line spacing (1.0-2.0)
- **Margins**: Page margins (em units)
- **Vertical/Horizontal**: Writing mode

### Conversion Process

1. Click **Browse** to select input file
2. Select **Device Preset** (optional)
3. Adjust **Style Settings** as needed
4. Click **Convert**
5. Progress bar shows conversion status
6. EPUB file is saved to output directory

---

## Command Line Interface

### Basic Syntax

```bash
java -jar AozoraEpub3.jar [OPTIONS] input_file
```

### Common Options

#### Input/Output
```bash
-enc <encoding>     Input file encoding (UTF-8, Shift_JIS, etc.)
-of                 Overwrite existing output file
-d <directory>      Output directory
-o <filename>       Output filename (without extension)
```

#### Device Presets
```bash
-p <preset.ini>     Use device preset file
```

Available presets (in `presets/` directory):
- `kobo__full.ini` - Kobo maximum size
- `kobo_glo.ini` - Kobo Glo
- `kobo_touch.ini` - Kobo Touch
- `kindle_pw.ini` - Kindle Paperwhite
- `reader.ini` - Sony Reader
- `reader_t3.ini` - Sony Reader T3

#### Style Options
```bash
-y                  Horizontal writing mode (default: vertical)
-fs <size>          Font size percentage (80-150, default: 100)
-lh <height>        Line height (1.0-2.0, default: 1.75)
-mar <top>,<bottom>,<left>,<right>  Margins in em (default: 0,0,0,0)
```

#### Image Options
```bash
-ih <height>        Image max height in pixels
-iw <width>         Image max width in pixels
-isize <kb>         Image max size in KB
-jpeg <quality>     JPEG quality (1-100, default: 85)
-rmargin            Remove image margins
```

#### Table of Contents
```bash
-tocnest <level>    TOC nesting level (1-3, default: 2)
-toctitle <title>   TOC title (default: "目次")
```

#### Cover Image
```bash
-cover <image>      Cover image file
```

#### Advanced
```bash
-gaiji <mode>       Gaiji (external characters) mode
                    0=Use UTF-8, 1=Use alternative characters
-dakuten <mode>     Dakuten font mode (0-2)
-autopage <lines>   Auto page break after N lines
```

### Examples

#### Convert UTF-8 text (vertical)
```bash
java -jar AozoraEpub3.jar -enc UTF-8 -of -d output novel.txt
```

#### Convert with Kobo preset
```bash
java -jar AozoraEpub3.jar -p presets/kobo_glo.ini -of -d output novel.txt
```

#### Horizontal writing with custom style
```bash
java -jar AozoraEpub3.jar -y -fs 110 -lh 1.8 -of -d output essay.txt
```

#### Batch conversion
```bash
java -jar AozoraEpub3.jar -of -d output chapter*.txt
```

#### Convert ZIP archive
```bash
java -jar AozoraEpub3.jar -of -d output novel_archive.zip
```

#### With cover image
```bash
java -jar AozoraEpub3.jar -cover cover.jpg -of -d output novel.txt
```

---

## Device Presets

Preset files (`.ini`) contain optimized settings for specific e-readers.

### Using Presets

**GUI**: Select from "Device Preset" dropdown

**CLI**: Use `-p` option
```bash
java -jar AozoraEpub3.jar -p presets/kobo_glo.ini input.txt
```

### Preset File Format

```ini
[画像設定]
画像の倍率=1.0
画像縮小JPEG品質=80
最大画像横幅=758
最大画像縦幅=1024
最大画像ファイルサイズ=64

[余白設定]
表紙上余白=0.0
表紙下余白=0.0
本文上余白=0.0
本文下余白=0.0

[スタイル設定]
フォントサイズ=100
行の高さ=1.7
```

### Creating Custom Presets

1. Copy an existing preset file
2. Edit values in a text editor
3. Save with `.ini` extension
4. Use with `-p` option

---

## Template Customization

AozoraEpub3 uses Apache Velocity templates for EPUB generation.

### Template Files Location

```
template/
├── mimetype
├── META-INF/
│   └── container.xml
└── OPS/
    ├── package.vm          # package.opf generation
    ├── toc.ncx.vm          # NCX table of contents
    └── css/
        ├── vertical_text.vm    # Vertical CSS
        └── horizontal_text.vm  # Horizontal CSS
```

### Customizing CSS

Edit `template/OPS/css/vertical_text.vm` or `horizontal_text.vm`:

```velocity
:root {
  --font-size: ${fontSize}%;
  --line-height: ${lineHeight};
  --margin-top: ${marginTop}em;
  --margin-bottom: ${marginBottom}em;
}

body {
  font-size: var(--font-size);
  line-height: var(--line-height);
}
```

Variables from INI files or CLI options are automatically injected.

### Customizing XHTML Structure

Edit `package.vm` to modify EPUB metadata or manifest structure.

**Note**: After modifying templates, rebuild the application:
```bash
./gradlew clean build
```

---

## Troubleshooting

### Encoding Issues

**Problem**: Garbled text in EPUB

**Solution**: Specify correct encoding
```bash
java -jar AozoraEpub3.jar -enc UTF-8 input.txt
```

Common encodings:
- `UTF-8` - Unicode
- `Shift_JIS` - Japanese Windows
- `EUC-JP` - Japanese Unix

### Image Size Issues

**Problem**: Images too large for device

**Solution**: Use image resize options
```bash
java -jar AozoraEpub3.jar -iw 758 -ih 1024 input.txt
```

### Memory Issues

**Problem**: `OutOfMemoryError` with large files

**Solution**: Increase Java heap size
```bash
java -Xmx2g -jar AozoraEpub3.jar input.txt
```

### EPUB Validation Errors

**Problem**: EPUB doesn't open on device

**Solution**: Validate with epubcheck
```bash
java -jar epubcheck.jar output.epub
```

Fix common issues:
- Ensure UTF-8 encoding
- Check image file sizes
- Verify metadata (title, author)

### ZIP/RAR Archive Issues

**Problem**: Cannot extract text from archive

**Solution**: 
- Ensure archive contains `.txt` files
- Check file encoding inside archive
- Use `-enc` to specify encoding

---

## Advanced Features

### Aozora Notation Support

AozoraEpub3 supports most Aozora Bunko notation:

#### Ruby (Furigana)
```
漢字《かんじ》
｜漢字《かんじ》
```

#### Emphasis
```
［＃傍点］強調テキスト［＃傍点終わり］
［＃「○○」に傍点］
```

#### Font Size
```
［＃大きな文字］Large Text［＃大きな文字終わり］
［＃小さな文字］Small Text［＃小さな文字終わり］
```

#### Alignment
```
［＃ここから２字下げ］
Indented paragraph
［＃ここで字下げ終わり］
```

#### Page Breaks
```
［＃改ページ］
［＃改丁］
```

### External Characters (Gaiji)

AozoraEpub3 handles external characters using:
- Unicode mapping (`chuki_utf.txt`)
- Alternative characters (`chuki_alt.txt`)
- IVS (Ideographic Variation Sequence) (`chuki_ivs.txt`)

**Example:**
```
※［＃「木＋世」、第3水準1-85-66］
```

### Auto Page Break

Split large files into multiple pages:

```bash
java -jar AozoraEpub3.jar -autopage 100 large_novel.txt
```

Creates page breaks every 100 lines.

### Table of Contents Depth

Control TOC nesting:

```bash
java -jar AozoraEpub3.jar -tocnest 3 novel.txt
```

- `1` - Chapter titles only
- `2` - Chapters and sections (default)
- `3` - Full hierarchy

---

## Links

- [🏠 Home](index.html)
- [👨‍💻 Development Guide](development.html)
- [📚 EPUB 3.3 Support](epub33.html)
- [💻 GitHub Repository](https://github.com/AozoraJDK21-bot/AozoraEpub3-JDK21)
- [📝 Aozora Bunko](https://www.aozora.gr.jp/)

---

<footer style="margin-top: 40px; padding-top: 20px; border-top: 1px solid #ddd; font-size: 0.9em; color: #666;">
  <p>© 2025 AozoraEpub3-JDK21 Project</p>
  <p>
    <a href="index.html">Home</a> |
    <a href="usage.html">Usage</a> |
    <a href="development.html">Development</a> |
    <a href="epub33.html">EPUB 3.3</a> |
    <a href="https://github.com/AozoraJDK21-bot/AozoraEpub3-JDK21">GitHub</a>
  </p>
</footer>
