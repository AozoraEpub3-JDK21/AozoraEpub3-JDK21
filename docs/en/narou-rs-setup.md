---
layout: default
lang: en
title: narou.rs Setup Guide (Windows 11, with Screenshots)
description: Step-by-step beginner's guide to converting web novels into EPUB with narou.rs and AozoraEpub3-JDK21 on Windows 11. Covers downloading narou.rs, registering AozoraEpub3 with narou_rs init, the required device=EPUB setting in the Web UI, downloading a novel and locating the generated EPUB, plus fixes for VCRUNTIME140.dll errors, SmartScreen warnings, and a missing Java installation.
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

Follow the steps on this page from top to bottom and you will end up with a setup where **pasting a novel URL into your browser is all it takes to get an EPUB**. It takes about 15 to 20 minutes.

### Overview

1. [Install Java](#1-install-java)
2. [Install AozoraEpub3](#2-install-aozoraepub3-in-a-dedicated-folder-for-narours)
3. [Install narou.rs](#3-install-narours)
4. [Initialize and register AozoraEpub3](#4-initialize-and-register-aozoraepub3)
5. [Open the Web UI](#5-open-the-web-ui)
6. [★ Set device to EPUB (Required)](#6--set-device-to-epub-required)
7. [Add a novel and get the EPUB](#7-add-a-novel-and-get-the-epub)

---

## 0. What You Need

- A Windows 11 PC and an internet connection

This guide uses the following three folders. If you put things elsewhere, adjust the paths in the commands accordingly.

| Folder | Purpose |
|---|---|
| `C:\Tools\AozoraEpub3-jdk21` | AozoraEpub3 (the conversion engine) |
| `C:\Tools\narou` | narou.rs itself |
| `C:\Tools\narou-novels` | Where your novels are stored and managed |

> **Point**: Use paths made of **ASCII characters only** for the folders you install into.
> Avoid locations that contain non-ASCII characters or spaces (your `Downloads` folder in a localized Windows, anything under OneDrive, and so on).

### How to Open PowerShell

Throughout this guide you will paste commands into the blue (or black) command window, which is **PowerShell**. Either way of opening it works:

- **Right-click the Start button** → choose "**Terminal**"
- Press the Start button, type "**powershell**" → open "Windows PowerShell"

You can also right-click an empty area inside a folder open in File Explorer and choose "**Open in Terminal**" — this opens a command window (usually PowerShell) **in that folder** (used in steps 4 and 5).

Once you have copied a command, paste it into the PowerShell window with a **right-click** (or `Ctrl+V`). You do not need to run it as an administrator.

---

## 1. Install Java

AozoraEpub3 needs Java to run. Open PowerShell and check with:

```powershell
java -version
```

If a version number (`21` or later) is displayed, you are good to go. If you get "not recognized", follow the
👉 **[Java installation guide on the top page](index.html#install-java-25-eclipse-temurin)** and install **Java 25 LTS from Eclipse Temurin**.

> ✅ **Checkpoint**: `java -version` prints a version number

---

## 2. Install AozoraEpub3 (in a Dedicated Folder for narou.rs)

1. Download `AozoraEpub3-x.x.x-jdk21.zip` from the **[AozoraEpub3-JDK21 download page](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/latest)**.
2. Right-click the zip → "Extract All", type `C:\Tools\AozoraEpub3-jdk21` into the destination box, and extract.

> ⚠️ **Caution**: During initialization, narou.rs rewrites one of AozoraEpub3's configuration files (`chuki_tag.txt`).
> If you also use AozoraEpub3 for other purposes, **extract a separate copy into its own folder just for narou.rs** (narou.rs itself recommends this).

> ✅ **Checkpoint**: opening `C:\Tools\AozoraEpub3-jdk21` shows `AozoraEpub3.jar` **directly inside it**.
> If everything ended up one folder deeper, move the contents up so they sit directly under `C:\Tools\AozoraEpub3-jdk21`.

---

## 3. Install narou.rs

1. Download the Windows zip (its name contains `x86_64-pc-windows`) from the **[narou.rs Releases page](https://github.com/Rumia-Channel/narou.rs/releases/latest)**.
2. Right-click the zip → "Extract All". It contains a `narou/` folder — place it so that it ends up at `C:\Tools\narou`.

The extracted folder has the following structure.

```text
C:\Tools\narou\
  narou_rs.exe
  narou_rs_updater.exe.new
  webnovel\
  preset\
  commitversion
  LICENSE / README.md / Third-Party-License.md
```

> ⚠️ **Caution**: `narou_rs.exe` relies on `webnovel\`, `preset\`, and `commitversion` being in the same folder.
> **Do not move them elsewhere or delete them.**

> ⚠️ **Caution**: If you get "`VCRUNTIME140.dll` was not found" at startup,
> install the official Microsoft **[Visual C++ Redistributable (x64)](https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist)**.

> ✅ **Checkpoint**: typing `C:\Tools\narou\narou_rs.exe version` in PowerShell prints a version number (for example `0.3.4`)

---

## 4. Initialize and Register AozoraEpub3

Create the folder that will hold your novels, then run a single initialization command inside it.

1. In File Explorer, type `C:\Tools` into the address bar to open it, right-click an empty area → "New" → "Folder", type `narou-novels` as the folder name and press Enter.
2. Open the new `narou-novels` folder, right-click an empty area inside it → choose "**Open in Terminal**" (a window opens whose prompt line shows `C:\Tools\narou-novels`).
3. Paste the following single line and press Enter.

```powershell
C:\Tools\narou\narou_rs.exe init -p "C:\Tools\AozoraEpub3-jdk21"
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

> **Point**: `-p` must point to **the folder where you extracted AozoraEpub3 in step 2**.
> The line height defaults to 1.8× (only add `-l 2.0` or similar if you want to change it).
> If you use a PowerShell window opened from the Start menu instead, first run `cd C:\Tools\narou-novels` to move there (same for step 5).
> If the registration fails, just **run `init -p ...` again in the same folder**
> (it will say the folder is already initialized, but the AozoraEpub3 configuration is redone).

> ⚠️ **Caution**: Do **not** create the novel folder **inside** the narou.rs folder (`C:\Tools\narou`).
> narou.rs is designed to keep its own folder and your novel folder separate (as instructed by the official README).

> ✅ **Checkpoint**: `初期化が完了しました！` ("initialization complete") is displayed

---

## 5. Open the Web UI

Just like in step 4, open `C:\Tools\narou-novels` in File Explorer, right-click an empty area inside it → choose "**Open in Terminal**", and run this single line. **From now on, this is all you need to start it.**

```powershell
C:\Tools\narou\narou_rs.exe web
```

Your browser opens automatically and shows the narou.rs screen. The address has the form `http://localhost:(port number)/`, and **the port number is chosen automatically on the first launch and reused from then on**.

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

## Add narou.rs to PATH (Optional)

This lets you type just `narou_rs` instead of the full `C:\Tools\narou\narou_rs.exe` every time. **Everything in this guide works without it** (the official narou.rs README assumes a PATH-based setup, but running by full path as in this guide behaves the same).

<details markdown="1">
<summary>Show the steps</summary>

Paste the following two lines **as-is** into PowerShell and press Enter (run it **only once**), then **close PowerShell and open a new window**.

```powershell
$narouPath = "C:\Tools\narou"
[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path", "User") + ";" + $narouPath, "User")
```

From then on, a newly opened PowerShell accepts the short form, such as `narou_rs web`.

To undo it: press the Start button → type "environment variables" → open "Edit environment variables for your account" → select **Path** in the upper box and press "Edit..." → select the `C:\Tools\narou` line and press "Delete" → close with "OK".

</details>

---

## Troubleshooting

| Symptom | What to do |
|------|------|
| `VCRUNTIME140.dll was not found` | Install the Visual C++ Redistributable → [step 3](#3-install-narours) |
| `narou_rs` is "not recognized" | Run it with the full path (`C:\Tools\narou\narou_rs.exe`), or see [Add narou.rs to PATH (Optional)](#add-narours-to-path-optional) |
| "Windows protected your PC" | Can appear if you double-click `narou_rs.exe`, for example. Get past it with the same steps as for the [SmartScreen warning](usage.html#windows-protected-your-pc-when-launching-aozoraepub3exe) |
| A Java-related error during conversion | Check that Java is installed → [step 1](#1-install-java) |
| Conversion runs but there is no `.epub` file | Check that device is set to EPUB → [step 6](#6--set-device-to-epub-required) |
| Initialization ran but the Web UI shows nothing / the novel list is empty | You may have run the command in a different folder. Check that the prompt line shows `C:\Tools\narou-novels` → [step 4](#4-initialize-and-register-aozoraepub3) |
| The firewall dialog appeared | Click "Allow access" (it only runs on localhost, so nothing is exposed externally) |

---

## Reference Links

- [narou.rs (GitHub)](https://github.com/Rumia-Channel/narou.rs) — README, latest releases, bug reports
- [AozoraEpub3 Usage Guide](usage.html) — detailed conversion settings
- [narou.rb Setup Guide](narou-setup.html) — if you want to use the Ruby version, narou.rb
- [AozoraEpub3-JDK21 Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases) — download AozoraEpub3 itself

---

<div style="text-align: right;"><small>Last updated: 2026-08-01 | This guide is not official narou.rs documentation.</small></div>
