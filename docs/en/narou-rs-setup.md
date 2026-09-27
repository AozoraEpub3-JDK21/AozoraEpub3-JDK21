---
layout: default
lang: en
title: narou.rs Setup Guide (Windows 11, with Screenshots)
description: Step-by-step beginner's guide to converting web novels into EPUB with narou.rs and AozoraEpub3-JDK21 on Windows 11. Covers choosing between the Java-free GPL edition (with AozoraEpub3_Lite built in) and the standard edition, downloading narou.rs, registering AozoraEpub3 with narou_rs init, the required device=EPUB setting in the Web UI, downloading a novel and locating the generated EPUB, plus fixes for VCRUNTIME140.dll errors, SmartScreen warnings, and a missing Java installation.
---

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
   <strong>📚 Documentation:</strong>
   <a href="index.html">Home</a> | 
   <a href="usage.html">Usage</a> | 
   <a href="gaiji-settings.html">Gaiji Settings</a> | 
   <a href="narou-setup.html">narou.rb Setup</a> |
   <strong>narou.rs</strong> |
   <a href="development.html">Development</a> | 
   <a href="epub33.html">EPUB 3.3</a> |
   <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
   <div style="float: right;">🌐 <a href="../narou-rs-setup.html">日本語</a></div>
</nav>

## narou.rs Setup Guide (Windows 11, with Screenshots)

> ⚠️ This article is **not** an official [narou.rs](https://github.com/Rumia-Channel/narou.rs) manual. For anything unclear, prefer the latest information in the **[narou.rs README](https://github.com/Rumia-Channel/narou.rs) and [Issues](https://github.com/Rumia-Channel/narou.rs/issues)**.

**narou.rs** (developed by [Rumia-Channel](https://github.com/Rumia-Channel)) downloads, updates, and converts web novels into EPUB. It is a compatible reimplementation, in Rust, of [narou.rb](narou-setup.html) (by whiteleaf7), and like narou.rb it uses AozoraEpub3 as its conversion engine.

Follow the steps on this page from top to bottom and you will end up with a setup where **pasting a novel URL is all it takes to get an EPUB**. It takes about 15 to 20 minutes.

> 📖 The reasons behind each step, licensing, a side-by-side look at the two editions' output, adding narou.rs to PATH, and other details are collected in **[Details](narou-rs-setup-details.html)**. You do not need them to follow the steps.

<a id="editions"></a>

### First: Standard or GPL Edition?

narou.rs is distributed as two different zips.

| | Standard edition | GPL edition |
|---|---|---|
| Best for | Converting with AozoraEpub3 itself (your AozoraEpub3 settings such as `AozoraEpub3.ini`, gaiji fonts, and automatic preview apply as-is) | Getting started quickly without installing Java |
| Java and AozoraEpub3 | Required | **Not required** (conversion engine built in) |
| Steps to follow | All of 1 → 7 | **Skip steps 1 and 2** |

If you just download and use it, either edition is fine. The body text is the same with both, but the title page, table of contents, and headings look slightly different ([rendering comparison](narou-rs-setup-details.html#rendering)).

### Overview

1. [Install Java](#1-install-java) (not needed for the GPL edition)
2. [Install AozoraEpub3](#2-install-aozoraepub3-in-a-dedicated-folder-for-narours) (not needed for the GPL edition)
3. [Install narou.rs](#3-install-narours)
4. [Initialize and register AozoraEpub3](#4-initialize-and-register-aozoraepub3)
5. [Open the Web UI](#5-open-the-web-ui)
6. [★ Set device to EPUB (Required)](#6--set-device-to-epub-required)
7. [Add a novel and get the EPUB](#7-add-a-novel-and-get-the-epub)

---

## 0. What You Need

- A Windows 11 PC and an internet connection

This guide uses the following folders. If you put things elsewhere, adjust the paths in the commands accordingly.

| Folder | Purpose |
|---|---|
| `C:\Tools\AozoraEpub3-jdk21` | AozoraEpub3 (the conversion engine). **Not needed for the GPL edition** |
| `C:\Tools\narou` | narou.rs itself |
| `C:\Tools\narou-novels` | Where your novels are stored and managed |

> **Point**: Avoid locations that contain non-ASCII characters or spaces (your `Downloads` folder in a localized Windows, anything under OneDrive, and so on) and use **paths made of ASCII characters only**.

### How to Open the Command Window (PowerShell)

- **Right-click the Start button** → choose "**Terminal**"
- In File Explorer, right-click an empty area inside a folder and choose "**Open in Terminal**" — this opens it **in that folder** (used in steps 4 and 5)

Paste a command into the window with a **right-click** (or `Ctrl+V`). You do not need to run it as an administrator.

---

## 1. Install Java

> **Not needed for the GPL edition** → skip to [step 3](#3-install-narours).

Run this command in PowerShell:

```powershell
java -version
```

If a version number (`21` or later) is displayed, you are good to go. If you get "not recognized", follow the
👉 **[Java installation guide on the top page](index.html#install-java-25-eclipse-temurin)** and install **Java 25 LTS from Eclipse Temurin**.

> ✅ **Checkpoint**: `java -version` prints a version number

---

## 2. Install AozoraEpub3 (in a Dedicated Folder for narou.rs)

> **Not needed for the GPL edition** → skip to [step 3](#3-install-narours).

1. Download `AozoraEpub3-x.x.x-jdk21.zip` from the **[AozoraEpub3-JDK21 download page](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/latest)**.
2. Right-click the zip → "Extract All", type `C:\Tools\AozoraEpub3-jdk21` into the destination box, and extract.

> ⚠️ **Caution**: Even if you already use AozoraEpub3 for other purposes, **extract a separate copy for narou.rs** ([why](narou-rs-setup-details.html#dedicated-folder)).

> ✅ **Checkpoint**: opening `C:\Tools\AozoraEpub3-jdk21` shows `AozoraEpub3.jar` **directly inside it**.
> If everything ended up one folder deeper, move the contents up so they sit directly under `C:\Tools\AozoraEpub3-jdk21`.

---

## 3. Install narou.rs

1. Download the Windows zip from "Assets" on the **[narou.rs Releases page](https://github.com/Rumia-Channel/narou.rs/releases/latest)**.
   - Standard edition: `narou_rs_win_x64.zip`
   - GPL edition: `narou_rs_win_x64-GPL.zip`
   - On an ARM-based Windows PC, pick the one named `win_arm64` instead.
2. Right-click the zip → "Extract All". It contains a `narou/` folder — place it so that it ends up at `C:\Tools\narou`. **Do not move or delete the files inside it** ([folder layout](narou-rs-setup-details.html#folder-layout)).

> ✅ **Checkpoint**: typing `C:\Tools\narou\narou_rs.exe version` in PowerShell prints a version number (for example `0.4.4`)
> (if you get "`VCRUNTIME140.dll` was not found" → [Troubleshooting](#troubleshooting))

---

## 4. Initialize and Register AozoraEpub3

1. In File Explorer, type `C:\Tools` into the address bar to open it, right-click an empty area → "New" → "Folder", and create a folder named `narou-novels`.
2. Open the new `narou-novels` folder, right-click an empty area inside it → choose "**Open in Terminal**" (a window opens whose prompt line shows `C:\Tools\narou-novels`).
3. Paste the line for your edition and press Enter.

**Standard edition**:

```powershell
C:\Tools\narou\narou_rs.exe init -p "C:\Tools\AozoraEpub3-jdk21"
```

**GPL edition** (when asked `AozoraEpub3のあるフォルダを入力して下さい` — "enter the folder containing AozoraEpub3" — **press Enter without typing anything**):

```powershell
C:\Tools\narou\narou_rs.exe init
```

Along the way it prints `!!!WARNING!!!`; you can just continue ([details](narou-rs-setup-details.html#init)).

> ✅ **Checkpoint**: it ends with `初期化が完了しました！` ("initialization complete")

---

## 5. Open the Web UI

Just like in step 4, open `C:\Tools\narou-novels`, choose "**Open in Terminal**", and run this single line. **From now on, this is all you need to start it.**

```powershell
C:\Tools\narou\narou_rs.exe web
```

Your browser opens automatically and shows the narou.rs screen ([about the address](narou-rs-setup-details.html#port)).

> The screenshots below show the Japanese UI; the layout is identical in English. You can switch the Web UI language with the "**Language: 日本語 ↔ English**" item in the "⚙ Options" menu at the top right.

![The narou.rs Web UI top screen, with the menu at the top, a black log area in the middle, and the novel list below](../assets/narou-rs/02-web-top.png)

- **Do not close the black window (PowerShell)**. Closing it also shuts down the Web UI (press `Ctrl+C` in PowerShell when you do want to stop it).
- If the **Windows Firewall permission dialog** appears on first launch, click "Allow access".
- On the first run you may see a "new features tour". It is fine to dismiss it.

> ✅ **Checkpoint**: the "Narou.rs WEB UI" screen is displayed in your browser

---

## 6. ★ Set device to EPUB (Required)

Open "**⚙ Options**" at the top right of the screen → "**Settings...**".

![The options menu opened, with the first item "Settings..." highlighted in a red box](../assets/narou-rs/03-options-menu.png)

At the very top of the "General" tab, change "**device** (the target device for conversion and transfer)" to "**EPUB**", then click "**Save settings**" at the top right.

![The narou.rs settings screen with device set to EPUB and the Save settings button at the top right](../assets/narou-rs/04-settings-device-epub.png)

<div style="border-left: 4px solid #cf222e; background: #fff8f8; padding: 0.8em 1em; margin: 1em 0;">
<strong>Without this setting, no EPUB is produced.</strong>
If you read on a specific device such as a Kindle you may select that device name instead, but if you are unsure, choose EPUB.
</div>

Click "← Back to the novel list" to return to the previous screen.

> ✅ **Checkpoint**: reopening the settings shows device set to EPUB

---

## 7. Add a Novel and Get the EPUB

Click the "**Download**" button at the top left to open the input field. **Paste the URL** of the novel page you want to read (Syosetu, Kakuyomu, and so on) and press "**Download**".

![The download dialog, with the URL input field and the download button highlighted in red boxes](../assets/narou-rs/05-download-url.png)

Everything from downloading to EPUB conversion runs automatically. When it finishes, the novel is added to the list — click the **folder button in the "Save location" column** to open the folder containing the generated `.epub` file.

![A row for a registered novel in the novel list, with the save-location folder button highlighted in a red box](../assets/narou-rs/06-novel-list.png)

The EPUB is stored under `C:\Tools\narou-novels\小説データ\(site name)\(title)\`. From there, just send it to your reader of choice (a smartphone app, a Kindle, and so on).

> **Point**: To pull in newly published chapters of an ongoing series, just press the "**Update**" button. It fetches the new episodes and rebuilds the EPUB.

> ✅ **Checkpoint**: there is an `.epub` file in the folder

---

## Troubleshooting

| Symptom | What to do |
|------|------|
| `VCRUNTIME140.dll was not found` | Install the official Microsoft **[Visual C++ Redistributable](https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist)** (usually the x64 version; the ARM64 version on ARM-based Windows) |
| `narou_rs` is "not recognized" | Run it with the full path (`C:\Tools\narou\narou_rs.exe`), or see [Add narou.rs to PATH (Details)](narou-rs-setup-details.html#path) |
| "Windows protected your PC" | Can appear if you double-click `narou_rs.exe`, for example. Get past it with the same steps as for the [SmartScreen warning](usage.html#windows-protected-your-pc-when-launching-aozoraepub3exe) |
| A Java-related error during conversion | Check that Java is installed → [step 1](#1-install-java). Even the GPL edition uses Java when AozoraEpub3 is registered → [Details](narou-rs-setup-details.html#editions) |
| `AozoraEpub3 not found` during conversion | The standard edition has no AozoraEpub3 registered. Run `init -p ...` after step 2 (you can rerun it in the same folder as often as needed) → [step 4](#4-initialize-and-register-aozoraepub3). To convert without Java or AozoraEpub3, use the GPL edition |
| Conversion runs but there is no `.epub` file | Check that device is set to EPUB → [step 6](#6--set-device-to-epub-required) |
| Initialization ran but the Web UI shows nothing / the novel list is empty | You may have run the command in a different folder. Check that the prompt line shows `C:\Tools\narou-novels` → [step 4](#4-initialize-and-register-aozoraepub3) |
| The firewall dialog appeared | Click "Allow access" (it only runs on localhost, so nothing is exposed externally) |

---

## Reference Links

- [narou.rs Setup Guide — Details](narou-rs-setup-details.html) — the two editions, licensing, output and rendering comparison, init details, adding to PATH, and more
- [narou.rs (GitHub)](https://github.com/Rumia-Channel/narou.rs) — README, latest releases, bug reports
- [AozoraEpub3_Lite (GitHub)](https://github.com/Rumia-Channel/AozoraEpub3_Lite) — the conversion engine built into the GPL edition
- [AozoraEpub3 Usage Guide](usage.html) — detailed conversion settings
- [narou.rb Setup Guide](narou-setup.html) — if you want to use the Ruby version, narou.rb
- [AozoraEpub3-JDK21 Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases) — download AozoraEpub3 itself

---

<div style="text-align: right;"><small>Last updated: 2026-09-27 | This guide is not official narou.rs documentation. | See <a href="narou-rs-setup-details.html">Details</a> for the tested environment</small></div>
