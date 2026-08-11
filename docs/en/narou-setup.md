---
layout: default
lang: en
title: narou.rb Setup Guide (April 2026)
description: narou.rb installation, known issues (April 2026), and AozoraEpub3 integration steps.
---

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
   <strong>📚 Documentation:</strong>
   <a href="index.html">Home</a> | 
   <a href="usage.html">Usage</a> | 
   <strong>narou.rb Setup</strong> |
   <a href="narou-rs-setup.html">narou.rs</a> |
   <a href="development.html">Development</a> | 
   <a href="epub33.html">EPUB 3.3</a> |
   <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
   <div style="float: right;">🌐 <a href="../narou-setup.html">日本語</a></div>
</nav>

## narou.rb Setup & Troubleshooting Guide

> ⚠️ **Notice**
> - This article is **not** an official narou.rb manual.
> - The following information as of 2026-04-09 is a **temporary workaround** applied at your own risk.
> - **Always check the [narou.rb Official Wiki](https://github.com/whiteleaf7/narou/wiki) and [Issues](https://github.com/whiteleaf7/narou/issues) for the latest official information.**
> - If the narou.rb tool is updated, the workarounds in this guide may become unnecessary.
> - Tested environment: Windows 11, Ruby 3.4.1, narou 3.9.1

This guide provides steps to install **narou.rb** (a web novel downloader) and integrate it with AozoraEpub3.

<div style="border: 1px solid #0969da; background: #f0f7ff; border-radius: 6px; padding: 0.8em 1em; margin: 1em 0;">
💡 <strong>Recommendation</strong>: <strong>narou.rs</strong>, a narou.rb-compatible tool written in Rust, keeps receiving feature updates and security fixes, so it is the safer choice if you are starting fresh. It also works as an alternative when narou.rb does not work for you.
👉 <a href="narou-rs-setup.html"><strong>narou.rs Setup Guide (with screenshots)</strong></a>
</div>

As of narou.rb v3.9.1, the following known issues are reported by the community:
1. Dependency library (tilt) version mismatch causing startup errors
2. Incompatibility with current "syosetu.com" website specification changes (table of contents not fetched)
3. Kakuyomu site structure change (`tableOfContentsV2`) causing download failures

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

> **Note**: Dependency issues may occur immediately after installation. If you see errors, apply the fixes in section 4 and later.

---

## 3. Prepare AozoraEpub3

Running AozoraEpub3-JDK21 requires **Java 25** (recommended), though Java 21 or later also works.

1. **Check Java**  
   Run `java -version` in Command Prompt and confirm Java is installed.  
   * **If Java is not installed**: 👉 See the **[installation guide on the top page](index.html#install-java-25-eclipse-temurin)**.

2. **Download the software**
   * Download the latest zip from **[Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases)**.
   * Extract it to any location (e.g., `C:\Tools\AozoraEpub3`).

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

## 6. Fix Kakuyomu Table of Contents Error (Temporary Workaround)

**Symptom**: Downloading or updating Kakuyomu works fails with an error; table of contents and text cannot be retrieved.

**Cause**: Kakuyomu changed its internal data key (`tableOfContents` → `tableOfContentsV2`), and narou.rb's configuration file has not yet been updated. A community pull request ([PR #452](https://github.com/whiteleaf7/narou/pull/452)) has been submitted but was not merged as of 2026-04-09.

**Workaround**:

Apply the temporary fix shared by the community ([PR #452](https://github.com/whiteleaf7/narou/pull/452)).

**1. Download Fixed File**
Open the following GitHub link and download **1 file** as instructed:

* 👉 **[Pull Request #452 - Files changed](https://github.com/whiteleaf7/narou/pull/452/files)**

1. Find `webnovel/kakuyomu.jp.yaml` in the file list.
2. Click the "**…**" (three-dot menu) in the top-right corner and select "**View file**".
3. Once the file content appears, click the "**Download raw file**" (↓ arrow icon) to save it.

**2. Overwrite File**
Copy the downloaded `kakuyomu.jp.yaml` to the narou.rb installation folder:

* **Example path**:
`C:\Ruby34-x64\lib\ruby\gems\3.4.0\gems\narou-3.9.1\webnovel`
*(Adjust Ruby version number as needed for your environment)*

> **Recommendation**: Before overwriting, rename the original file (e.g., `kakuyomu.jp.yaml.bak`) as a backup.

**Reference**:
- [narou PR #452](https://github.com/whiteleaf7/narou/pull/452) — Community fix and discussion

---

## 7. Initialize and Configure AozoraEpub3 Integration

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

> **Note**: Closing the AozoraEpub3 GUI **rewrites this file in full** from the GUI's own settings, so hand-written comments are not preserved. If you mix GUI use with manual editing, edit the file after closing the GUI.

**Note: Opening a preview automatically after conversion (v1.5.1+)**
If you enable "Open the preview automatically after conversion" in the GUI (or set `AutoPreview=1` in `AozoraEpub3.ini`), conversions invoked via narou.rb / narou.rs also open the finished EPUB in your browser. The preview runs in a separate process, so narou.rb is never blocked; closing the browser tab shuts the preview process down automatically. Note that batch conversions such as `narou update` open **one tab per novel**, so consider turning this off during bulk operations.

---

## 8. Troubleshooting Tips

- **Check versions**:
   - `gem list tilt` (should be 2.4.0)
   - `gem list narou` (should be 3.9.1)
- **Unsure about config file location**: Run `gem env home` to find the base GEM installation folder
- **Official help**: [narou.rb Wiki](https://github.com/whiteleaf7/narou/wiki)

### "JavaがインストールされていないかAozoraEpub3実行時にエラーが発生しました"

If you see this message ("Java is not installed, or an error occurred while running AozoraEpub3") even though Java is installed correctly, the real cause is most likely that **EPUB output actually failed**.

narou.rb decides success or failure from AozoraEpub3's exit code, but it was written on the assumption that
"AozoraEpub3 always returns 0, even on error". As a result it interprets **any non-zero exit code as "Java could not run"**
and shows this message (narou.rb 3.9.1, `lib/novelconverter.rb`).

Starting with **v1.3.7-jdk21, AozoraEpub3 returns exit code `1` when conversion fails.**
This is an intentional change. In v1.3.6-jdk21 and earlier it returned `0` even on failure,
so **broken `.epub` files were imported into narou.rb as if they had succeeded**.

**How to find the real cause**: immediately before this message, narou.rb prints AozoraEpub3's **full stdout and stderr** as-is (`novelconverter.rb:197`).
If you see a line starting with `エラーが発生しました :` ("An error occurred:"), that is the actual reason.

> `narou convert -v <ID>` (verbose) does not add anything here.
> When the exit code is non-zero, narou.rb does `return :error` before reaching the verbose output block (`novelconverter.rb:195-200` vs `:218-223`).
> The full output is already shown, so you can diagnose it without `-v`.

Common causes: not enough free disk space at the destination, or no write permission on the output directory.

Note that image decode failures and cover download failures are still handled locally and do **not** cause this failure. They print an error line such as `画像読み込みエラー: ...` to the log, but the conversion continues and the exit code stays `0`.

> Note that a genuinely missing Java installation produces the same message. Check with `java -version`.

---

## Reference Links

- **[narou.rb Official Wiki](https://github.com/whiteleaf7/narou/wiki)** — Official manual and latest info
- **[narou.rb Issues](https://github.com/whiteleaf7/narou/issues)** — Bug reports and known issues
- **[narou.rb Community Forum](https://jbbs.shitaraba.net/computer/44668/)** — User community (Japanese)
- **[AozoraEpub3 Usage Guide](usage.html)** — Detailed AozoraEpub3 settings
- **[Send to Kindle (Web/Email)](https://www.amazon.co.jp/sendtokindle/)** — Convenient for reading on Kindle. *Note: A known narou.rb issue causes automatic email-sent titles to be converted to numbers.*

---

<footer style="margin-top: 40px; padding-top: 20px; border-top: 1px solid #ddd; font-size: 0.9em; color: #666;">
  <p>Last updated: 2026-04-09 | This guide is community-maintained, not official.</p>
  <p>
    <a href="index.html">Home</a> |
    <a href="usage.html">Usage</a> |
    <a href="development.html">Development</a> |
    <a href="epub33.html">EPUB 3.3</a> |
    <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
  </p>
</footer>
