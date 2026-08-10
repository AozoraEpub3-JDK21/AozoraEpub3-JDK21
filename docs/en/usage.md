---
layout: default
lang: en
title: Usage Guide - AozoraEpub3
description: AozoraEpub3-JDK21 usage: GUI and CLI conversion, device presets for Kobo and Kindle, vertical text, ruby, image settings, Velocity template customization.
---

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
  <strong>📚 Documentation:</strong>
  <a href="index.html">Home</a> | 
  <strong>Usage</strong> | 
  <a href="narou-setup.html">narou.rb Setup</a> |
  <a href="narou-rs-setup.html">narou.rs</a> |
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

### Web Novel Direct Conversion

Drag & drop a web novel URL or `.url` shortcut file to fetch and convert directly from supported sites.

**Supported sites**: Shōsetsuka ni Narō / Narō R18 / Kakuyomu / Hameln / Akatsuki / novelist.jp / FC2 Novel, etc.

**narou.rb-Compatible Format Settings**: Open "Web Novel Settings" from the GUI menu to configure:

| Setting | INI Key | Description | Default |
|---------|---------|-------------|---------|
| Show update date per chapter | `show_post_date` | Display last update date at end of each chapter | OFF |
| Show initial publish date per chapter | `show_publish_date` | Display original publish date for revised chapters | OFF |
| Auto-detect author comments | `enable_author_comments` | Detect foreword/afterword by `*44`/`*48` patterns | ON |
| Auto indent | `enable_auto_indent` | Automatically insert line-leading indentation | ON |
| Heading after page break | `enable_enchant_midashi` | Convert first line after page break to heading | ON |
| Blank line compression | `enable_pack_blank_line` | Compress consecutive blank lines | ON |
| Number to kanji | `enable_convert_num_to_kanji` | Convert Arabic numerals to kanji | ON |
| Alphabet to zenkaku | `enable_alphabet_to_zenkaku` | Convert short English words to full-width | ON |
| End of book marker | `enable_display_end_of_book` | Show completion mark at end | ON |
| Auto join in brackets | `enable_auto_join_in_brackets` | Auto-join lines within brackets | ON |
| Auto join at comma | `enable_auto_join_line` | Join lines ending with commas | ON |

Settings are saved in `setting_narourb.ini` and are compatible with narou.rb's `setting.ini` keys.

**Notes:**
- **Rate limiting**: 1.5-second delay between chapter fetches to avoid server overload
- **HTML structure changes**: May break if the target site redesigns (especially Narō)
- **Long novels**: 100 chapters takes ~3 minutes
- **Recommendation**: This is an experimental feature; manual download is more reliable

---

## Command Line Interface

### Basic Syntax

```bash
java -jar AozoraEpub3.jar [OPTIONS] input_file
```

### Options

| Option | What it does | Example |
|--------|--------------|---------|
| `-h, --help` | Show usage | |
| `-i <file>` | Read settings from an ini file | `-i settings.ini` |
| `-enc <encoding>` | Input file encoding (default `MS932`) | `-enc UTF-8` |
| `-t <type>` | Title layout in the body text (`0`: title → author (default) / `1`: author → title / `2`: title → author, subtitle first / `3`: title only / `4`: none) | `-t 1` |
| `-tf` | Use the input file name as the title | |
| `-c <image>` | Cover image (`0`: first illustration / `1`: image with the same name as the input file / a file name or URL) | `-c cover.jpg` |
| `-d <directory>` | Output directory | `-d ./output/` |
| `-ext <extension>` | Output file extension | `-ext .kepub.epub` |
| `-of` | Name the output after the input file (default is `[author] title.epub`) | |
| `-hor` | Horizontal writing mode (default: vertical) | |
| `-device <type>` | Apply device-specific handling | `-device kindle` |
| `-url <URL>` | Convert directly from a web novel URL, or a `.zip` / `.txtz` / `.rar` archive URL (repeatable) | `-url https://ncode.syosetu.com/nXXXX/` |
| `-narou` | Apply narou.rb-compatible format settings | |
| `-interval <seconds>` | Page fetch interval (only with `-url`, default 1.0) | `-interval 1.5` |
| `-cache <path>` | Cache directory (only with `-url`, defaults to `.cache` next to the jar) | `-cache .cache` |
| `--preview` | Open the converted EPUB in your default browser | `--preview foo.epub` |
| `--library <folder>` | Open a folder as a library (repeatable, up to 8) | `--library ./output/` |

That is the complete list. Text size, line height, margins, image scaling, gaiji and dakuten
handling have **no command-line switches** — configure them in the GUI and reuse the saved ini,
or pass one of the device presets in `presets/` with `-i`.

```bash
java -jar AozoraEpub3.jar -i presets/kindle_pw.ini -of -d ./output/ input.txt
```

Presets in the `presets/` directory:

- `kobo__full.ini` — Kobo maximum size
- `kobo_glo.ini` — Kobo Glo
- `kobo_touch.ini` — Kobo Touch
- `kindle_pw.ini` — Kindle Paperwhite
- `reader.ini` — Sony Reader
- `reader_t3.ini` — Sony Reader T3

### EPUB Preview

Check the result in a browser before transferring it to a device.

```bash
# Show an existing EPUB as-is (no conversion)
java -jar AozoraEpub3.jar --preview foo.epub

# Convert, then show the result
java -jar AozoraEpub3.jar -of -d ./output/ --preview input.txt
```

In the GUI, the "Preview" button becomes available once a conversion finishes. Turning on
"Open the preview automatically after conversion" on the "Preview" tab opens it after every
conversion (off by default).

| Control | What it does |
|---------|--------------|
| Table of contents panel (`☰` / `t`) | Jump to a chapter or heading |
| `Aa` button | Font, text size, line height, margins |
| `◐` button | Theme (follow system / light / dark) |
| `ⓘ` button | Metadata, structure, manifest breakdown, effective style, CSS, embedded fonts |
| Click the left/right edge, wheel, ← →, Space | Turn the page |
| `[` `]` | Previous / next section |

The default body font is UD Digi Kyokasho, falling back to Yu Mincho and others when it is
not installed. Display settings are stored in `~/.aozoraepub3/preview-settings.json` and
restored on the next run.

The server listens on a random port on the loopback address (`127.0.0.1`, or `::1` where IPv6
takes precedence) behind a URL token, so it is not reachable from other machines. In CLI mode it shuts down automatically once you close the browser
(Ctrl-C also works).

#### Library

Open a folder that holds your EPUB files as a library and pick a book from a grid of cover
thumbnails. Subfolders are scanned as well. Up to 8 folders can be registered.

```bash
# Open the library only (no input file)
java -jar AozoraEpub3.jar --library ./output/

# Open several library folders
java -jar AozoraEpub3.jar --library ./output/ --library ./novels/

# Convert, preview the result, and open the library too
java -jar AozoraEpub3.jar -of -d ./output/ --library ./output/ input.txt
```

In the GUI, add folders under "Library folders" on the "Preview" tab and click "Open library".
The folders you add are stored in `AozoraEpub3.ini`.

> This is an approximation of screen size and fonts. Kindle, Kobo and Apple Books use their own
> rendering engines, so the result will not match a real device exactly.

### Examples

#### Convert UTF-8 text (vertical)
```bash
java -jar AozoraEpub3.jar -enc UTF-8 -of -d output novel.txt
```

#### Convert with Kobo preset
```bash
java -jar AozoraEpub3.jar -i presets/kobo_glo.ini -of -d output novel.txt
```

#### Horizontal writing
```bash
java -jar AozoraEpub3.jar -hor -of -d output essay.txt
```

Text size and line height have no command-line switches — set them in the GUI and pass the
saved ini with `-i`.

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
java -jar AozoraEpub3.jar -c cover.jpg -of -d output novel.txt
```

#### Convert web novel from URL
```bash
java -jar AozoraEpub3.jar -url https://ncode.syosetu.com/nXXXX/ -d output

# With narou.rb-compatible settings
java -jar AozoraEpub3.jar -url https://ncode.syosetu.com/nXXXX/ -narou -d output
```

#### Convert an archive URL directly (v1.4.0+)
```bash
java -jar AozoraEpub3.jar -url https://www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip -d output
```

> When `-url` points at a `.zip` / `.txtz` / `.rar` file, the archive is downloaded into the
> output directory (`-d`, or the current directory if omitted) instead of being scraped as HTML,
> and is then converted through exactly the same path as a local archive input.
> Aozora Bunko text ZIPs are Shift_JIS, so the default `-enc MS932` is correct.

### Exit Codes

When run from the CLI, AozoraEpub3 returns an exit code so that shell scripts and external tools can detect success or failure.

| Exit code | Meaning |
|---|---|
| `0` | All input files converted successfully (`-h` / `--help` also returns `0`) |
| `1` | One or more input files failed to convert; the INI file (`-i`), output directory (`-d`), or input file does not exist; the options were invalid; or neither an input file nor `-url` was given (help is printed and the run ends) |

```bash
java -jar AozoraEpub3.jar -of -d output novel.txt
if [ $? -ne 0 ]; then
  echo "Conversion failed"
fi
```

> Running `java -jar AozoraEpub3.jar` with no arguments launches the GUI, so the table above does not apply to that case.

> **Changed in v1.3.7-jdk21**: v1.3.6-jdk21 and earlier **always returned `0`**, even when conversion failed.
> If writing the EPUB was interrupted partway through (disk full, no write permission on the output directory), the tool still reported success,
> leaving a **broken `.epub` behind that looked like a successful conversion**.
> From v1.3.7-jdk21 onward, failures return `1` and the **partially written `.epub` is deleted** (the same applies when you cancel a conversion).
>
> Recoverable problems — image decode failures, cover download failures, suspicious archive entries — are still handled locally and do not abort the run,
> so conversions that used to succeed will not start failing.
> If you use narou.rb, see the [narou.rb Setup Guide](narou-setup.html) as well.

---

## Device Presets

Preset files (`.ini`) contain optimized settings for specific e-readers.

### Using Presets

**GUI**: Select from "Device Preset" dropdown

**CLI**: Pass the preset ini with `-i`
```bash
java -jar AozoraEpub3.jar -i presets/kobo_glo.ini input.txt
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
4. Pass it with `-i`

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

**Solution**: Set the maximum image width / height in the GUI (or in the ini) and pass that ini
with `-i`. There are no command-line switches for image sizing.

```bash
java -jar AozoraEpub3.jar -i presets/kobo_glo.ini input.txt
```

### Memory Issues

**Problem**: `OutOfMemoryError` with large files

**Solution**: Increase Java heap size
```bash
java -Xmx2g -jar AozoraEpub3.jar input.txt
```

### "Windows protected your PC" when launching `AozoraEpub3.exe`

**Problem**: Microsoft Defender SmartScreen shows a warning. **This does not mean malware was detected.**

It appears when two conditions coincide:

1. `AozoraEpub3.exe` is not code-signed (this is an individual open-source project without a signing certificate)
2. The downloaded ZIP carries the **Mark of the Web**, and extracting it with Windows Explorer **propagates that mark to every file inside**

SmartScreen only reacts to files that carry the Mark of the Web. So if you clear it **before** extracting, the warning never appears. This is also why the behaviour differs between extraction tools — 7-Zip and similar tools do not propagate the Mark of the Web by default.

**Solution (once, before extracting)**

1. Right-click the downloaded `AozoraEpub3-*.zip` → **Properties**
2. At the bottom of the General tab, tick **Unblock** next to "This file came from another computer..." → OK
3. **Then** extract the ZIP

The same thing in PowerShell:

```powershell
Unblock-File .\AozoraEpub3-*.zip
```

If you already extracted it, run this against the extracted folder:

```powershell
Get-ChildItem -Recurse .\AozoraEpub3-* | Unblock-File
```

If the warning is already on screen you can still start the app via "More info" → "Run anyway" (that choice is remembered, so you will not be asked again for the same file). The advantage of unblocking beforehand is that **the warning never appears at all, and the bundled `.jar` and configuration files do not keep the Mark of the Web either**.

> These steps address the SmartScreen warning. On systems where Windows 11 **Smart App Control** is enabled, unsigned apps can be blocked regardless of the Mark of the Web, and this procedure will not help in that case.

You can verify that the download is genuine with the published SHA-256 checksums — see [VERIFY.md](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/blob/master/VERIFY.md).

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

Long files can be split into several XHTML pages so readers stay responsive. This is an ini
setting, not a command-line switch — set it in the GUI, or edit `AozoraEpub3.ini`:

```ini
PageBreak=1
# split once the page grows past this many KB
PageBreakSize=400
# split at PageBreakEmptyLine consecutive blank lines, once the page is past PageBreakEmptySize KB
PageBreakEmpty=1
PageBreakEmptyLine=3
PageBreakEmptySize=300
# split at chapter headings, once the page is past PageBreakChapterSize KB
PageBreakChapter=1
PageBreakChapterSize=200
```

Every `*Size` is the minimum page size (KB) at which that trigger starts to apply — blank lines
and chapter headings do not split a page that is still smaller than the threshold.

Then pass the ini with `-i`:

```bash
java -jar AozoraEpub3.jar -i AozoraEpub3.ini -of -d ./output/ large_novel.txt
```

### Table of Contents Depth

Whether chapter headings nest under their parent chapter is also an ini setting:

```ini
# nest entries in nav.xhtml
NavNest=1
# nest entries in toc.ncx
NcxNest=1
```

Set them to `0` for a flat table of contents. Which headings become entries at all is
controlled by the `Chapter*` keys (`ChapterH1` … `ChapterH3`, `ChapterName`, and so on).

---

## Links

- [🏠 Home](index.html)
- [👨‍💻 Development Guide](development.html)
- [📚 EPUB 3.3 Support](epub33.html)
- [💻 GitHub Repository](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21)
- [📝 Aozora Bunko](https://www.aozora.gr.jp/)

---

<footer style="margin-top: 40px; padding-top: 20px; border-top: 1px solid #ddd; font-size: 0.9em; color: #666;">
  <p>© 2025 AozoraEpub3-JDK21 Project</p>
  <p>
    <a href="index.html">Home</a> |
    <a href="usage.html">Usage</a> |
    <a href="development.html">Development</a> |
    <a href="epub33.html">EPUB 3.3</a> |
    <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
  </p>
</footer>
