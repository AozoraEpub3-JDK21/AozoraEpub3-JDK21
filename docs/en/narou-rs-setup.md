---
layout: default
lang: en
title: narou.rs Setup Guide (Windows 11, with Screenshots)
description: Step-by-step beginner's guide to converting web novels into EPUB with narou.rs and AozoraEpub3-JDK21 on Windows 11. Covers downloading narou.rs and adding it to PATH, registering AozoraEpub3 with narou_rs init, the required device=EPUB setting in the Web UI, downloading a novel and locating the generated EPUB, plus fixes for VCRUNTIME140.dll errors, SmartScreen warnings, and a missing Java installation.
---

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
   <strong>📚 Documentation:</strong>
   <a href="index.html">Home</a> | 
   <a href="usage.html">Usage</a> | 
   <a href="narou-setup.html">narou.rb Setup</a> |
   <strong>narou.rs</strong> |
   <a href="development.html">Development</a> | 
   <a href="epub33.html">EPUB 3.3</a> |
   <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
   <div style="float: right;">🌐 <a href="../narou-rs-setup.html">日本語</a></div>
</nav>

## narou.rs Setup Guide (Windows 11, with Screenshots)

> ⚠️ **Notice**
> - This article is **not** an official [narou.rs](https://github.com/Rumia-Channel/narou.rs) manual. For anything unclear, prefer the latest information in the **[narou.rs README](https://github.com/Rumia-Channel/narou.rs) and [Issues](https://github.com/Rumia-Channel/narou.rs/issues)**.
> - Tested environment: Windows 11 (Japanese), narou.rs v0.3.4, AozoraEpub3 v1.4.0-jdk21

**narou.rs** (developed by [Rumia-Channel](https://github.com/Rumia-Channel)) is a compatible reimplementation (in Rust) of [narou.rb](narou-setup.html) (by whiteleaf7), the tool that downloads, updates, and converts web novels. Like narou.rb, it uses AozoraEpub3 as its conversion engine.

Follow the steps on this page from top to bottom and you will end up with a setup where **pasting a novel URL into your browser is all it takes to get an EPUB**. It takes about 20 to 30 minutes.

### Overview

1. [Install Java](#1-install-java)
2. [Install AozoraEpub3](#2-install-aozoraepub3-in-a-dedicated-folder-for-narours)
3. [Install narou.rs](#3-install-narours)
4. [Add it to PATH](#4-add-it-to-path-the-tricky-part)
5. [Initialize and register AozoraEpub3](#5-initialize-and-register-aozoraepub3)
6. [Open the Web UI](#6-open-the-web-ui)
7. [★ Set device to EPUB (Required)](#7--set-device-to-epub-required)
8. [Add a novel and get the EPUB](#8-add-a-novel-and-get-the-epub)

---

## 0. What You Need

- A Windows 11 PC and an internet connection
- A place to put the software (this guide uses `C:\Tools\` as the example location)

> **Point**: Use a path made of **ASCII characters only** (for example `C:\Tools\narou`) for the folders you install into.
> Avoid locations that contain non-ASCII characters or spaces (your `Downloads` folder in a localized Windows, anything under OneDrive, and so on).

### How to Open PowerShell

Throughout this guide you will paste commands into the blue (or black) command window, which is **PowerShell**. Either way of opening it works:

- **Right-click the Start button** → choose "**Terminal**"
- Press the Start button, type "**powershell**" → open "Windows PowerShell"

Once you have copied a command, paste it into the PowerShell window with a **right-click** (or `Ctrl+V`). You do not need to run it as an administrator.

---

## 1. Install Java

AozoraEpub3 needs Java to run. Open PowerShell (right-click the Start button → "Terminal") and check with:

```powershell
java -version
```

If a version number (`21` or later) is displayed, you are good to go. If you get "not recognized", follow the
👉 **[Java installation guide on the top page](index.html#install-java-25-eclipse-temurin)** and install **Java 25 LTS from Eclipse Temurin**.

> ✅ **Checkpoint**: `java -version` prints a version number

---

## 2. Install AozoraEpub3 (in a Dedicated Folder for narou.rs)

1. Download `AozoraEpub3-x.x.x-jdk21.zip` from the **[AozoraEpub3-JDK21 download page](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/latest)**.
2. Right-click the zip → "Properties" → if there is an "Unblock" checkbox at the bottom, tick it and click "OK" ([how to get past the SmartScreen warning](usage.html#windows-protected-your-pc-when-launching-aozoraepub3exe)).
3. Right-click the zip → "Extract All" and extract it into a **dedicated folder** such as `C:\Tools\AozoraEpub3-narou`.

> ⚠️ **Caution**: During initialization, narou.rs rewrites one of AozoraEpub3's configuration files (`chuki_tag.txt`).
> If you also use AozoraEpub3 for other purposes, **extract a separate copy into its own folder just for narou.rs** (narou.rs itself recommends this).

> ✅ **Checkpoint**: the extracted folder contains `AozoraEpub3.jar`

---

## 3. Install narou.rs

1. Download the Windows zip (its name contains `x86_64-pc-windows`) from the **[narou.rs Releases page](https://github.com/Rumia-Channel/narou.rs/releases/latest)**.
2. Right-click the zip → "Extract All". It contains a `narou/` folder — place it so that it ends up at `C:\Tools\narou`.

The extracted folder looks like this (keep `narou_rs.exe` and the `webnovel/` and `preset/` folders together in the same directory):

```text
C:\Tools\narou\
  narou_rs.exe
  narou_rs_updater.exe.new
  webnovel\
  preset\
  LICENSE / README.md and so on
```

> ⚠️ **Caution**: If you get "`VCRUNTIME140.dll` was not found" at startup,
> install the official Microsoft **[Visual C++ Redistributable (x64)](https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist)**.

> ✅ **Checkpoint**: `narou_rs.exe` is inside `C:\Tools\narou`

---

## 4. Add It to PATH (the Tricky Part)

So that the `narou_rs` command works from any folder, register the location of `narou_rs.exe` in your **PATH** (the list of folders where commands are looked up).

All you are about to do is **append one line to your own user settings**. Nothing system-wide is changed, and you can undo it at any time (the steps for that are right below).

<details markdown="1">
<summary>💡 To undo it later (how to remove the entry)</summary>

1. Press the Start button, type "**environment variables**" and open "**Edit environment variables for your account**" from the search results
2. In the upper box ("User variables for (your name)"), click **Path** to select it and press "**Edit...**"
3. In the list, click the `C:\Tools\narou` line to select it and press "**Delete**" on the right
4. Close with "OK" → "OK". If a PowerShell window is open, reopen it for the change to take effect

That restores the state before you registered it. Be careful not to touch the other lines.

</details>

Copy the following two lines **as-is** into [PowerShell](#how-to-open-powershell) and press Enter (if you put narou.rs somewhere other than `C:\Tools\narou`, change only that part).

```powershell
$narouPath = "C:\Tools\narou"
[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path", "User") + ";" + $narouPath, "User")
```

**Close PowerShell and open a new window** (the change does not take effect until you reopen it).

<details markdown="1">
<summary>Prefer the GUI instead of a command? Click here</summary>

1. Press the Start button, type "**environment variables**" and open "**Edit environment variables for your account**" from the search results
2. In the upper box ("User variables for (your name)"), click **Path** to select it and press "**Edit...**"
3. Press "**New**" and type `C:\Tools\narou`
4. Close with "OK" → "OK"
5. Close any open PowerShell window and open it again

</details>

> ✅ **Checkpoint**: in a **newly opened** PowerShell, `narou_rs version` prints a version number (for example `0.3.4`)

---

## 5. Initialize and Register AozoraEpub3

Create a folder to manage your novels and initialize inside it. Paste the following three lines into PowerShell
(the path after `-p` must match **the folder where you extracted AozoraEpub3 in step 2**).

```powershell
New-Item -ItemType Directory -Force -Path "C:\narou-novels" | Out-Null
Set-Location "C:\narou-novels"
narou_rs init -p "C:\Tools\AozoraEpub3-narou" -l 1.8
```

On success you will see output like this (narou.rs prints its messages in Japanese):

```text
.narou/ を作成しました
小説データ/ を作成しました
webnovel/ を作成しました (6 files)
AozoraEpub3の設定を行います
!!!WARNING!!!
AozoraEpub3の構成ファイルを書き換えます。narouコマンド用に別途新規インストールしておくことをオススメします
AozoraEpub3 の構成ファイルを書き換えました
グローバル設定を保存しました
初期化が完了しました！
```

> **Point**: `-p` sets the AozoraEpub3 location and `-l 1.8` sets the line height (1.8×) at the same time.
> If you run plain `narou_rs init` without `-p`, it asks for the AozoraEpub3 location interactively.
> Even if you skipped that prompt, you can still register it by **running `narou_rs init -p ...` again in the same folder**
> (it will say the folder is already initialized, but the AozoraEpub3 configuration is redone).

> ✅ **Checkpoint**: `初期化が完了しました！` ("initialization complete") is displayed

---

## 6. Open the Web UI

Run the following in your novel folder (`C:\narou-novels`).

```powershell
narou_rs web
```

Your browser opens automatically and shows the narou.rs screen (`http://localhost:16230/`).

> The screenshots below show the Japanese UI; the layout is identical in English. You can switch the Web UI language with the "**Language: 日本語 ↔ English**" item in the "⚙ Options" menu at the top right.

![The narou.rs Web UI top screen, with the menu at the top, a black log area in the middle, and the novel list below](../assets/narou-rs/02-web-top.png)

- **Do not close the black window (PowerShell)**. Closing it also shuts down the Web UI (press `Ctrl+C` in PowerShell when you do want to stop it).
- If the **Windows Firewall permission dialog** appears on first launch, click "Allow access".
- On the first run you may see a "new features tour". It is fine to dismiss it.

> ✅ **Checkpoint**: the "Narou.rs WEB UI" screen is displayed in your browser

---

## 7. ★ Set device to EPUB (Required)

Open "**⚙ Options**" at the top right of the screen → "**Settings...**".

![The options menu opened, with the first item "Settings..." highlighted in a red box](../assets/narou-rs/03-options-menu.png)

At the very top of the "General" tab, change "**device** (the target device for conversion and transfer)" to "**EPUB**", then click "**Save settings**" at the top right.

![The narou.rs settings screen with device set to EPUB and the Save settings button at the top right](../assets/narou-rs/04-settings-device-epub.png)

<div style="border-left: 4px solid #cf222e; background: #fff8f8; padding: 0.8em 1em; margin: 1em 0;">
<strong>Without this setting, no EPUB is produced.</strong>
If you use a specific device such as a Kindle you may select that device name instead, but if you just want an EPUB, choose EPUB.
</div>

Click "← Back to the novel list" to return to the previous screen.

> ✅ **Checkpoint**: reopening the settings shows device set to EPUB

---

## 8. Add a Novel and Get the EPUB

Click the "**Download**" button at the top left to open the input field. **Paste the URL** of the novel page you want to read (Syosetu, Kakuyomu, and so on) and press "**Download**".

![The download dialog, with the URL input field and the download button highlighted in red boxes](../assets/narou-rs/05-download-url.png)

Everything from downloading to EPUB conversion runs automatically. When it finishes, the novel is added to the list — click the **folder button in the "Save location" column** to open the folder containing the generated `.epub` file.

![A row for a registered novel in the novel list, with the save-location folder button highlighted in a red box](../assets/narou-rs/06-novel-list.png)

The EPUB is stored under `C:\narou-novels\小説データ\(site name)\(title)\`. From there, just send it to your reader of choice (a smartphone app, a Kindle, and so on).

> **Point**: To pull in newly published chapters of an ongoing series, just press the "**Update**" button. It fetches the new episodes and rebuilds the EPUB.

> ✅ **Checkpoint**: there is an `.epub` file in the folder

---

## Troubleshooting

| Symptom | What to do |
|------|------|
| `narou_rs` is "not recognized" | Make sure you reopened PowerShell after editing PATH → [step 4](#4-add-it-to-path-the-tricky-part) |
| `VCRUNTIME140.dll was not found` | Install the Visual C++ Redistributable → [step 3](#3-install-narours) |
| "Windows protected your PC" | [How to get past the SmartScreen warning](usage.html#windows-protected-your-pc-when-launching-aozoraepub3exe) |
| A Java-related error during conversion | Check that Java is installed → [step 1](#1-install-java) |
| Conversion runs but there is no `.epub` file | Check that device is set to EPUB → [step 7](#7--set-device-to-epub-required) |
| The firewall dialog appeared | Click "Allow access" (it only runs on localhost, so nothing is exposed externally) |

---

## Reference Links

- [narou.rs (GitHub)](https://github.com/Rumia-Channel/narou.rs) — README, latest releases, bug reports
- [AozoraEpub3 Usage Guide](usage.html) — detailed conversion settings
- [narou.rb Setup Guide](narou-setup.html) — if you want to use the Ruby version, narou.rb
- [AozoraEpub3-JDK21 Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases) — download AozoraEpub3 itself

---

<div style="text-align: right;"><small>Last updated: 2026-08-01 | This guide is not official narou.rs documentation.</small></div>
