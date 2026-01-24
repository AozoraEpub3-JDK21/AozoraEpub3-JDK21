---
layout: default
title: Development Guide - AozoraEpub3
---

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
  <strong>📚 Documentation:</strong>
  <a href="index.html">Home</a> | 
  <a href="usage.html">Usage</a> | 
  <a href="narou-setup.html">narou.rb Setup</a> |
  <strong>Development</strong> | 
  <a href="epub33.html">EPUB 3.3</a> |
  <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
  <div style="float: right;">🌐 <a href="../development.html">日本語</a></div>
</nav>

## Development Guide

Developer documentation for contributing to AozoraEpub3 or understanding its internal implementation.

## Table of Contents

- [Development Environment Setup](#development-environment-setup)
- [Building and Testing](#building-and-testing)
- [Project Structure](#project-structure)
- [Code Architecture](#code-architecture)
- [Template System (Velocity)](#template-system-velocity)
- [EPUB 3.3 Compliance](#epub-33-compliance)
- [Aozora Notation Support](#aozora-notation-support)
- [External Character Handling](#external-character-handling)
- [GitHub Actions CI](#github-actions-ci)
- [Contributing Guidelines](#contributing-guidelines)
- [Common Issues](#common-issues)

---

## Development Environment Setup

### Requirements

- **Java**: JDK 21+ (build), JRE 21+ (runtime)
- **Gradle**: 8.0+ (Gradle Wrapper recommended)
- **Git**: Version control
- **IDE**: IntelliJ IDEA, Eclipse, VS Code, etc.

### Clone Repository

```bash
git clone https://github.com/AozoraJDK21-bot/AozoraEpub3-JDK21.git
cd AozoraEpub3-JDK21
```

### IDE Setup

#### VS Code
1. Install Java Extension Pack
2. Open folder
3. Gradle tasks auto-detected

#### IntelliJ IDEA
1. File → Open → Select `build.gradle`
2. "Import Gradle Project" auto-configures

#### Eclipse
```bash
./gradlew eclipse
```
Then: Import → Existing Projects

---

## Building and Testing

### Basic Build

```bash
## Clean build
./gradlew clean build

## Create FAT JAR (with all dependencies)
./gradlew jar

## Create distribution packages (ZIP + TAR.GZ)
./gradlew dist
```

**Important**: `distZip` task is disabled. Use `dist` task for distribution packages.

### Running Tests

```bash
## Run all tests
./gradlew test

## Generate test report
./gradlew test --rerun-tasks
## → build/reports/tests/test/index.html
```

### Build Artifacts

- **FAT JAR**: `build/libs/AozoraEpub3.jar`
- **Distribution packages**: 
  - `build/distributions/AozoraEpub3-<version>.zip`
  - `build/distributions/AozoraEpub3-<version>.tar.gz`
- **Test report**: `build/reports/tests/test/index.html`

### Running the Application

```bash
## Launch GUI (no arguments)
java -jar build/libs/AozoraEpub3.jar

## CLI usage (convert UTF-8 text to EPUB)
java -jar build/libs/AozoraEpub3.jar -of -d out input.txt

## Vertical writing sample
java -jar build/libs/AozoraEpub3.jar -enc UTF-8 test_data/test_title.txt

## Horizontal writing sample
java -jar build/libs/AozoraEpub3.jar -enc UTF-8 -y test_data/test_yoko.txt
```

---

## Project Structure

```
AozoraEpub3/
├── src/                       # Main source code
│   ├── AozoraEpub3.java       # CLI entry point
│   ├── AozoraEpub3Applet.java # GUI entry point
│   └── com/github/hmdev/      # Package root
│       ├── converter/         # Text→EPUB conversion
│       ├── epub/              # EPUB specification
│       ├── io/                # File/archive handling
│       ├── image/             # Image processing
│       ├── config/            # Configuration parsing
│       └── pipeline/          # Conversion pipeline
│
├── test/                      # Test code
│   ├── AozoraEpub3SmokeTest.java
│   ├── IniCssIntegrationTest.java
│   └── com/github/hmdev/      # Package tests
│
├── template/                  # Velocity templates
│   ├── mimetype               # EPUB mimetype file
│   ├── META-INF/
│   │   └── container.xml      # EPUB container definition
│   └── OPS/
│       ├── package.vm         # package.opf generation
│       ├── toc.ncx.vm         # NCX TOC generation
│       └── css/               # CSS templates
│           ├── vertical_text.vm
│           └── horizontal_text.vm
│
├── test_data/                 # Test fixtures
│   ├── test_title.txt         # Title/author tests
│   ├── test_ruby.txt          # Ruby conversion tests
│   ├── test_gaiji.txt         # Gaiji conversion tests
│   └── img/                   # Test images
│
├── presets/                   # Device presets
│   ├── kobo__full.ini         # Kobo maximum size
│   ├── kindle_pw.ini          # Kindle Paperwhite
│   └── reader.ini             # Sony Reader
│
├── chuki_*.txt                # Notation definition files
│   ├── chuki_tag.txt          # Notation → Tag conversion
│   ├── chuki_alt.txt          # Gaiji → Alternative chars
│   ├── chuki_utf.txt          # Gaiji → UTF-8
│   ├── chuki_ivs.txt          # Gaiji → IVS
│   └── chuki_latin.txt        # Latin character conversion
│
├── build.gradle               # Gradle build definition
├── gradlew, gradlew.bat       # Gradle Wrapper
├── README.md                  # User documentation
└── DEVELOPMENT.md             # Original dev documentation
```

### Key Classes

#### Entry Points
- `AozoraEpub3.java`: CLI processing, argument parsing, conversion execution
- `AozoraEpub3Applet.java`: Swing-based GUI

#### Conversion Pipeline
- `Epub3Writer`: EPUB 3 file generation (uses Velocity)
- `AozoraEpubConverter`: Aozora notation parsing and conversion
- `BookInfoReader`: Title/author extraction
- `CharacterConverter`: Gaiji and ruby conversion

#### I/O & Archives
- `ArchiveTextExtractor`: Text extraction from zip/rar (with caching)
- `ArchiveCache`: Archive scan result caching
- `OutputNamer`: Output filename generation

#### Image Processing
- `ImageInfoReader`: Image metadata reading
- `ImageConverter`: Image resizing and optimization

#### Configuration
- `IniFile`: INI file parsing
- `ConfigValues`: Configuration value storage

---

## Code Architecture

### Recent Improvements (v1.2.4+)

#### Modularization and Class Extraction

Separated responsibilities from the large `AozoraEpub3.java` (originally 645 lines) to improve maintainability:

**Extracted Classes:**

1. **OutputNamer** (`com.github.hmdev.io`): Filename generation logic (50 lines)
   - Creator/title-based auto-naming
   - Filename sanitization (invalid character removal)
   - Default extension handling

2. **WriterConfigurator** (`com.github.hmdev.pipeline`): Writer configuration aggregation (110 lines)
   - Image parameter configuration
   - TOC nesting configuration
   - Style settings (margins, line height, fonts)

3. **ArchiveTextExtractor** (`com.github.hmdev.io`): Unified archive handling (90 lines)
   - Text extraction from zip/rar/txt
   - Text file counting
   - Cache mechanism integration

**Refactoring Results:**
- `AozoraEpub3.java`: 645 lines → 450 lines (**200 lines reduced**)
- New classes: 5 classes (350 lines total)
- Unit tests added: OutputNamerTest (4 tests)

Details: [notes/refactor-plan.md](https://github.com/AozoraJDK21-bot/AozoraEpub3-JDK21/blob/master/notes/refactor-plan.md)

#### Performance Optimization 🚀

**Problem**: 4 archive scans per file:
1. Text file count
2. Book information retrieval
3. Image list loading
4. Actual conversion

**Solution**: Archive caching mechanism

- **ArchiveCache**: Scan archive once and hold info in memory
  - Text file content (byte array)
  - Image file entry name list
  - Text file count

- **ArchiveScanner**: Unified zip/rar scanner
  - Collect text and image entries in one pass
  - Optimized RAR temporary file extraction

**Optimization Results:**
- Archive scans: **4 → 1** (75% reduction)
- Significant speed improvement for large zip/rar (100MB+)
- Memory usage: ~10-20MB cache even for 2GB archives

**Memory Management:**
- Auto-release via `ArchiveTextExtractor.clearCache()` after conversion
- Only text content cached (images loaded on demand)

Details: [notes/archive-cache-optimization.md](https://github.com/AozoraJDK21-bot/AozoraEpub3-JDK21/blob/master/notes/archive-cache-optimization.md)

---

## Template System (Velocity)

### Design Principles

`Epub3Writer` supports **VelocityEngine injection** to avoid global initialization dependencies.

#### Constructors

```java
// Recommended: Inject VelocityEngine (testable, customizable)
new Epub3Writer(templatePath, velocityEngine)

// Or legacy style (backward compatible)
new Epub3Writer(templatePath)  // Uses global initialization
```

#### Test Usage Example

```java
Properties p = new Properties();
p.setProperty("resource.loaders", "file");
p.setProperty("resource.loader.file.class", 
    "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
p.setProperty("resource.loader.file.path", 
    projectRoot.resolve("template").resolve("OPS").toString());
VelocityEngine ve = new VelocityEngine(p);

VelocityContext ctx = new VelocityContext();
ctx.put("title", "Sample Title");
ctx.put("bookInfo", bookInfoObject);
ctx.put("sections", sectionList);
// ... other context setup

Template t = ve.getTemplate("package.vm", "UTF-8");
StringWriter out = new StringWriter();
t.merge(ctx, out);
String opf = out.toString();
```

### Template Files

| Template | Purpose | Output File |
|----------|---------|-------------|
| `package.vm` | EPUB metadata/manifest | `package.opf` |
| `toc.ncx.vm` | Table of contents (NCX) | `toc.ncx` |
| `css/vertical_text.vm` | Vertical text CSS | `vertical_text.css` |
| `css/horizontal_text.vm` | Horizontal text CSS | `horizontal_text.css` |
| `xhtml/*.vm` | Content XHTML | `*.xhtml` |

### INI Values to CSS

INI settings (`font_size`, `line_height`, etc.) are placed in Velocity context and used as CSS variables in templates.

**Example (vertical_text.vm):**
```velocity
:root {
  --font-size: ${fontSize}%;
  --line-height: ${lineHeight};
}
```

**Related Tests:**
- [CssTemplateRenderTest.java](https://github.com/AozoraJDK21-bot/AozoraEpub3-JDK21/blob/master/test/com/github/hmdev/config/CssTemplateRenderTest.java): Vertical CSS
- [HorizontalCssTemplateRenderTest.java](https://github.com/AozoraJDK21-bot/AozoraEpub3-JDK21/blob/master/test/com/github/hmdev/config/HorizontalCssTemplateRenderTest.java): Horizontal CSS

---

## EPUB 3.3 Compliance

### Validation Items (Automated in CI)

#### mimetype File
- Must be first entry in ZIP and stored uncompressed (STORED)

#### package.opf (OPF File)
- `unique-identifier` required
- `dc:identifier` in `urn:uuid:` format (RFC 4122)
- `dcterms:modified` in ISO8601 format
- `language` = `ja` (Japanese content)
- `nav` item exists in manifest
- `spine` `page-progression-direction` attribute:
  - Vertical → `rtl` (right-to-left)
  - Horizontal → `ltr` (left-to-right)
- CSS `writing-mode` consistency:
  - Vertical → `vertical-rl`
  - Horizontal → unspecified or `horizontal-tb`

#### container.xml
- Well-formed XML (validated with xmllint)

### Kindle (iPhone) Support

In `package.vm` L60 (for ImageOnly + Kindle):
```xml
<meta name="primary-writing-mode" content="horizontal-rl"/>
```

**Preservation Test**: [PackageTemplateKindleMetaTest.java](https://github.com/AozoraJDK21-bot/AozoraEpub3-JDK21/blob/master/test/com/github/hmdev/epub/PackageTemplateKindleMetaTest.java)

### epubcheck Validation

```bash
## Custom Gradle task
./gradlew epubcheck \
  -PepubDir=build/epub_local \
  -PepubcheckJar=build/tools/epubcheck-5.3.0/epubcheck.jar
```

**Get epubcheck:**
```bash
mkdir -p build/tools
cd build/tools
curl -L -o epubcheck-5.3.0.zip \
  https://github.com/w3c/epubcheck/releases/download/v5.3.0/epubcheck-5.3.0.zip
unzip epubcheck-5.3.0.zip
```

---

## Aozora Notation Support

### Basic Notation (Configuration Files)

- `chuki_tag.txt`: Notation → ePub tag conversion
- `chuki_tag_suf.txt`: Forward-reference → Start-end conversion
- `chuki_alt.txt`: Gaiji → Alternative character mapping
- `chuki_utf.txt`: Gaiji → UTF-8 mapping
- `chuki_ivs.txt`: Gaiji → IVS UTF-8 mapping
- `chuki_latin.txt`: Latin character → UTF-8 conversion

### Programmatically Processed Notation

```
- Page left/right centering
- Auto ruby notation conversion ［＃「○」に「△」のルビ］ → ｜○《△》
- Auto emphasis conversion ［＃「○」に×傍点］ → × ruby
- Complex indentation (fold/indent calculation)
- Warichu line break addition
- Page break by 底本：
```

### Unsupported Notation

```
- Corrections and "ママ" (ignored)
- Left ruby
- Inline bottom alignment
- Two-column layout
```

---

## External Character Handling

Extract and convert gaiji (external characters) from Aozora notation:

```
※［＃「字名」、U+6DB6］                          → Direct Unicode
※［＃「字名」、第3水準1-85-57］                  → JIS code → UTF-8
※［＃「さんずい＋垂」、UCS6DB6］                → UCS format
```

Gaiji without corresponding code are replaced with alternative characters via `chuki_alt.txt`.

---

## GitHub Actions CI

### Workflows

#### ci.yml (Build, Test, EPUB Generation, Validation)
Automated processes:
- Build (Gradle)
- Test execution (JUnit)
- Sample EPUB generation (vertical/horizontal, multiple presets)
- `epubcheck` validation
- OPF XPath validation (UUID format, writing direction consistency)
- Accessibility warning summary (missing img alt count)
- Upload generated EPUBs as artifacts
- Manual execution support (workflow_dispatch + inputs)

**Manual Execution:**  
GitHub → Actions → CI → Run workflow

Optional inputs:
- `ini_font_size`: INI font_size (default: 115)
- `ini_line_height`: INI line_height (default: 1.7)

#### test.yml (Unit Tests Only)
- Test execution (JUnit)
- Manual execution support

---

## Contributing Guidelines

### Bug Reports & Feature Requests

Provide the following information in GitHub Issues:

#### For Bugs
- Environment (OS, Java version)
- Input text (or minimal reproduction)
- Error messages/logs
- Expected vs actual behavior

#### For Feature Requests
- Proposal details
- Use cases
- References (if any)

### Pull Requests

1. Fork repository and create feature branch
2. Make changes and add tests
3. Verify all tests pass: `./gradlew test`
4. Create PR (with description and related issue links)

### Coding Conventions

- Follow existing style (Java standard)
- Always add tests (JUnit 4)
- Update corresponding tests when modifying templates
- Write clear commit messages (English or Japanese)

---

## Common Issues

### Velocity Resource Resolution Failure

**Symptom**: `template/*.vm` not found during test execution

**Cause**: Gradle Worker runs in different working directory

**Solution:**
- Specify absolute path with FileResourceLoader
- Get projectRoot via `Paths.get(".").toAbsolutePath().normalize()`
- Use `VelocityTestUtils` utility

### Windows Path Issues

**Symptom**: `\` causes escaping failures

**Solution:**
- Use `Paths` API (auto-converts to `/`)
- Or explicitly use forward slashes

### Test Instability

**Symptom**: Passes locally but fails in CI

**Causes**: 
- File I/O timing
- Resource resolution path dependencies

**Solutions:**
- Avoid relative paths, use absolute or classpath
- Make template paths configurable

---

## Performance Optimization Tips

### Large Text Conversion
- Increase memory: `java -Xmx2g ...`
- Enable forced page breaks (file splitting)

### Image Processing
- Resizing has high CPU load (Bicubic)
- Margin removal is slow for PNG

### EPUB Generation Speed
- Optimize Velocity templates (avoid large loops)
- Image streaming (process large images incrementally)

---

## Links

- [🏠 Home](index.html)
- [📖 Usage Guide](usage.html)
- [📚 EPUB 3.3 Support](epub33.html)
- [💻 GitHub Repository](https://github.com/AozoraJDK21-bot/AozoraEpub3-JDK21)
- [🔧 Original Project](https://github.com/hmdev/AozoraEpub3)
- [📝 Aozora Bunko](https://www.aozora.gr.jp/)
- [📖 EPUB 3.3 Specification](https://www.w3.org/TR/epub-33/)
- [✅ epubcheck](https://github.com/w3c/epubcheck)

---

## License

GPL v3 - See [README](https://github.com/Harusame64/AozoraEpub3-JDK21#license) for details

---

<footer style="margin-top: 40px; padding-top: 20px; border-top: 1px solid #ddd; font-size: 0.9em; color: #666;">
  <p>© 2025 AozoraEpub3-JDK21 Project</p>
  <p>
    <a href="index.html">Home</a> |
    <a href="usage.html">Usage</a> |
    <a href="development.html">Development</a> |
    <a href="epub33.html">EPUB 3.3</a> |
    <a href="https://github.com/Harusame64/AozoraEpub3-JDK21">GitHub</a>
  </p>
</footer>
