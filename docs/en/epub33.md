---
layout: default
lang: en
title: EPUB 3.3 Guide
description: AozoraEpub3-JDK21 EPUB 3.3 support guide. Changes from 3.0 and implementation status.
---

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
  <strong>📚 Documentation:</strong>
  <a href="index.html">Home</a> | 
  <a href="usage.html">Usage</a> | 
  <a href="narou-setup.html">narou.rb Setup</a> |
  <a href="development.html">Development</a> | 
  <strong>EPUB 3.3</strong> |
  <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
  <div style="float: right;">🌐 <a href="../epub33-ja.html">日本語</a></div>
</nav>

# EPUB 3.3 Guide

Overview of EPUB 3.3 support in AozoraEpub3-JDK21.

---

## What is EPUB 3.3?

EPUB (Electronic Publication) 3.3 is the international standard for e-book formats.

- **Official Specification**: [IDPF EPUB 3.3 Standard](https://www.w3.org/publishing/epub33/)
- **Japanese Reference**: [Japanese Book Publishing Association EPUB 3 Production Guide](https://www.ebookjapan.jp/)

---

### Enhanced Semantics

EPUB 3.3 emphasizes more detailed metadata and structured information.

- Expanded `epub:type` attributes (for chapters, footnotes, annotations, etc.)
- Recommended use of HTML5 semantic elements (`<section>`, `<article>`, `<nav>`, etc.)

### Improved Media Queries

- Enhanced CSS media query support
- Better horizontal/vertical writing mode switching
- Recommended dark mode support

### Enhanced Accessibility

- Expanded ARIA label recommendations
- Required alt text for images
- Improved table of contents and landmark structure

---

## AozoraEpub3-JDK21 Support Status

### ✅ Implemented

- Basic EPUB 3.3 format generation
- CSS compliant with Japanese e-book guidelines
- Ruby (furigana) support
- Vertical text mode support
- Image embedding
- Character variation handling (IVS, gaiji, dakuten)
- Device-specific CSS presets (Kindle, Kobo, etc.)

### 📋 Planned / Under Development

- Enhanced accessibility metadata
- Advanced media overlay support
- Fixed layout EPUB support

### ❌ Not Supported

- EPUB 2.0 / KF8 output (EPUB 3.3 only)
- Interactive JavaScript content
- DRM (Digital Rights Management)

---

## Key Features

### Ruby (Furigana) Support

```xml
<ruby>
  <rb>漢字</rb>
  <rt>かんじ</rt>
</ruby>
```

### Vertical Text Layout

Use CSS to enable vertical writing:

```css
writing-mode: vertical-rl;
```

### Device Presets

Pre-configured CSS for popular e-readers:
- Kindle Paperwhite
- Kobo Glo / Glo HD
- Sony Reader

---

## How to Use

1. Prepare your text file (UTF-8 format)
2. Place images in the same directory (optional)
3. Run AozoraEpub3 with your text file
4. Specify device preset if needed
5. The resulting EPUB file is ready for distribution

---

## Related Resources

- [Back to Main Page](index.html)
- [GitHub Repository](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21)
- [EPUB 3.3 Specification](https://www.w3.org/publishing/epub33/)
