---
layout: default
lang: en
title: narou.rb Setup Guide (December 2025)
description: narou.rb installation, known issues (December 2025), and AozoraEpub3 integration steps.
---

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
   <strong>📚 Documentation:</strong>
   <a href="index.html">Home</a> | 
   <a href="usage.html">Usage</a> | 
   <strong>narou.rb Setup</strong> |
   <a href="development.html">Development</a> | 
   <a href="epub33.html">EPUB 3.3</a> |
   <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
   <div style="float: right;">🌐 <a href="../narou-setup.html">日本語</a></div>
</nav>

# narou.rb Setup & Troubleshooting Guide

> ⚠️ **Notice**
> - This article is **not** an official narou.rb manual.
> - The following information as of 2025-12-21 is a **temporary workaround** applied at your own risk.
> - **Always check the [narou.rb Official Wiki](https://github.com/whiteleaf7/narou/wiki) and [Issues](https://github.com/whiteleaf7/narou/issues) for the latest official information.**
> - If the narou.rb tool is updated, the workarounds in this guide may become unnecessary.
> - Tested environment: Windows 11, Ruby 3.4.1, narou 3.9.1

This guide provides steps to install **narou.rb** (a web novel downloader) and integrate it with AozoraEpub3.

As of narou.rb v3.9.1, the following known issues are reported by the community:
1. Dependency library (tilt) version mismatch causing startup errors
2. Incompatibility with current "syosetu.com" website specification changes (table of contents not fetched)

This guide consolidates community-shared workarounds for these issues.

For official installation steps and prerequisites, please also refer to:
- **[narou.rb Official Wiki - Installation](https://github.com/whiteleaf7/narou/wiki/Home#%E3%82%A4%E3%83%B3%E3%82%B9%E3%83%88%E3%83%BC%E3%83%AB)**

Note: kindlegen is not covered in this guide as "Send to Kindle" / email registration is currently unavailable.

---

## 1. Install Ruby (Windows Example)

1. Visit **[RubyInstaller for Windows](https://rubyinstaller.org/downloads/)**.
2. Download and run **Ruby+Devkit 3.4.x (x64)** (marked "WITH DEVKIT").
3. During installation, **keep the "MSYS2 development toolchain" checkbox checked**.
4. After installation, open PowerShell and run `ruby -v` to verify the version is displayed.

> macOS/Linux are also supported, but this guide primarily covers Windows.

---

## 2. Install narou.rb

In PowerShell (or Command Prompt), run:

```powershell
gem install narou
```

> **Note**: Dependency issues may occur immediately after installation. If you see errors, proceed to the next section for fixes.

---

## 3. Prepare AozoraEpub3

Set up the AozoraEpub3-JDK21 software.

- Download the latest zip file from **[Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases)**.
- Extract it to any location (example: `C:\Tools\AozoraEpub3`).

> **Point**: Avoid paths with Japanese characters or spaces; use ASCII characters only for best compatibility.

---

## 4. Fix tilt/erubis Error (Known Issue)

**Symptom**: Running `narou` produces `cannot load such file -- tilt/erubis` error.

**Cause**: narou 3.9.1 uses a library version that is too new. Downgrade to an older version to work around this.

**Fix**:
1. Uninstall current tilt:
   ```powershell
   gem uninstall tilt
   ```
   (Should display "Successfully uninstalled...")
2. Install the workaround version:
   ```powershell
   gem install tilt -v 2.4.0
   ```

**Reference**:
- [narou Issue #443](https://github.com/whiteleaf7/narou/issues/443) — Similar reports
- GEM paths vary by environment. Run `gem env home` to check your GEM_HOME.

---

## 5. Fix "syosetu.com" Table of Contents Error (Temporary Workaround)

**Symptom**: Downloads fail; table of contents and text cannot be retrieved.

**Cause**: "syosetu.com" website specifications have changed, and narou's configuration files are outdated. Manually replace the YAML configuration files with community-provided fixes.

**Workaround**:

Apply the temporary fix shared by the community ([PR #446](https://github.com/whiteleaf7/narou/pull/446)).

**1. Download Fixed Files**
Open the following GitHub link and download **2 files** as instructed:

* 👉 **[Pull Request #446 - Files changed](https://github.com/whiteleaf7/narou/pull/446/files)**

1. Find `webnovel/ncode.syosetu.com.yaml` in the file list.
2. Click the "**…**" (three-dot menu) in the top-right corner and select "**View file**".
3. Once the file content appears, click the "**Download raw file**" (↓ arrow icon) to save it.
4. Repeat for `webnovel/novel18.syosetu.com.yaml`.

**2. Overwrite Files**
Copy the 2 downloaded files to the narou.rb installation folder:

* **Example path**:
`C:\Ruby34-x64\lib\ruby\gems\3.4.0\gems\narou-3.9.1\webnovel`
*(Adjust Ruby version number as needed for your environment)*

> **Recommendation**: Before overwriting, rename the original files (e.g., `filename.yaml.bak`) as a backup.

---

## 6. Initialize and Configure AozoraEpub3 Integration

Create a folder for managing novels and run the initialization command:

```powershell
mkdir MyNovels
cd MyNovels
narou init
```

When prompted with configuration options:

1. **"Please specify the AozoraEpub3 folder"** will be displayed.
2. Enter the path to the folder containing **`AozoraEpub3.jar`** from step 3.
   - Example: `C:\Tools\AozoraEpub3`
   - You can also drag and drop the folder from File Explorer.

Once complete, narou.rb will remember the AozoraEpub3 location and auto-integration is configured.

**Note: Configuration Files**
After initialization, an **`AozoraEpub3.ini`** file appears in the same directory as `AozoraEpub3.jar`. To adjust line height, font size, and other conversion settings, edit this file directly or save settings from the AozoraEpub3 GUI.

---

## 7. Troubleshooting Tips

- **Check versions**:
   - `gem list tilt` (should be 2.4.0)
   - `gem list narou` (should be 3.9.1)
- **Unsure about config file location**: Run `gem env home` to find the base GEM installation folder
- **Official help**: [narou.rb Wiki](https://github.com/whiteleaf7/narou/wiki)

---

## Reference Links

- **[narou.rb Official Wiki](https://github.com/whiteleaf7/narou/wiki)** — Official manual and latest info
- **[narou.rb Issues](https://github.com/whiteleaf7/narou/issues)** — Bug reports and known issues
- **[narou.rb Community Forum](https://jbbs.shitaraba.net/computer/44668/)** — User community (Japanese)
- **[AozoraEpub3 Usage Guide](usage.html)** — Detailed AozoraEpub3 settings
- **[Send to Kindle (Web/Email)](https://www.amazon.co.jp/sendtokindle/)** — Convenient for reading on Kindle. *Note: A known narou.rb issue causes automatic email-sent titles to be converted to numbers.*

---

<footer style="margin-top: 40px; padding-top: 20px; border-top: 1px solid #ddd; font-size: 0.9em; color: #666;">
  <p>Last updated: 2025-12-21 | This guide is community-maintained, not official.</p>
  <p>
    <a href="index.html">Home</a> |
    <a href="usage.html">Usage</a> |
    <a href="development.html">Development</a> |
    <a href="epub33.html">EPUB 3.3</a> |
    <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
  </p>
</footer>
