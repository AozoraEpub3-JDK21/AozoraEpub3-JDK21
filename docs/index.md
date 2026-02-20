---
layout: default
lang: ja
title: ダウンロード
description: AozoraEpub3-JDK21は青空文庫テキストをEPUB 3.3に変換するツールです。Java 21/25対応でWindows・macOS・Linux上で動作。GUIとCLIの両モード搭載、縦書き・ルビ・外字・画像に対応。narou.rb連携やEpubCheck 5.x検証もサポート。最新版はこちら。
---

<div style="text-align: right; margin-bottom:  1em;">
  <a href="en/">🌐 English</a>
</div>

<nav style="background: #f6f8fa; padding:  1em; margin-bottom:  2em; border-radius:  6px;">
  <strong>📚 ドキュメント:</strong>
  <a href="./">ホーム</a> | 
  <a href="usage.html">使い方</a> | 
  <a href="narou-setup.html">narou. rb</a> |
  <a href="development.html">開発者向け</a> | 
  <a href="epub33-ja.html">EPUB 3.3準拠</a> |
  <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
</nav>

## ダウンロード

<div style="text-align: center; margin: 2em 0;">
  <p><strong>最新版:</strong> v1.2.6-jdk21 (2026年1月24日) | 
  <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/blob/master/RELEASE_NOTES.md">リリースノート</a></p>
  
  <div style="display: inline-block; text-align: center;">
    <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/download/v1.2.6-jdk21/AozoraEpub3-1.2.6-jdk21.zip" class="btn" style="display: inline-block; margin: 10px; padding: 12px 24px;">
      📦 Windows版 (ZIP)
    </a>
    <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/download/v1.2.6-jdk21/AozoraEpub3-1.2.6-jdk21.tar.gz" class="btn" style="display: inline-block; margin: 10px; padding: 12px 24px;">
      🐧 Linux版 (TAR.GZ)
    </a>
    <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/download/v1.2.6-jdk21/AozoraEpub3-1.2.6-jdk21.tar.gz" class="btn" style="display: inline-block; margin: 10px; padding: 12px 24px;">
      🍎 macOS版 (TAR.GZ)
    </a>
  </div>
  
  <p><small><a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases">📋 すべてのリリースを見る</a></small></p>
</div>

---

## このプロジェクトについて

本ソフトウェアは hmdev 氏の **AozoraEpub3** をベースに、narou.rbとの互換性維持、Java 25 対応と最新 OS 向けの調整を行った派生版です。

EPUB 3.3 および 電書協／電書連 EPUB 3 制作ガイドの準拠を目指し、epubcheck 5.x で検証しています。

---

## 動作環境

- Java 25 以降（JRE / JDK）を推奨
  - Java 21 (LTS) との互換性も維持しています
- Windows / macOS / Linux

Java をお持ちでない場合は、[Eclipse Temurin](https://adoptium.net/temurin/releases/) から Java 25 をダウンロードしてください。

---

## Java 25 のインストール（Eclipse Temurin）

### Windows

1. [Adoptium Releases](https://adoptium.net/temurin/releases/) を開きます
2. JDK 25 → Windows x64 → `.MSI` を選択しダウンロードします
3. MSI ファイルをダブルクリックしインストールします
4. コマンドプロンプトで `java -version` を実行し確認します

### macOS

1. [Adoptium Releases](https://adoptium.net/temurin/releases/) を開きます
2. JDK 25 → macOS → `.PKG`（Intel または Apple Silicon M1/M2）をダウンロードします
3. PKG ファイルをダブルクリックしインストールします
4. ターミナルで `java -version` を実行し確認します

### Linux（Ubuntu/Debian）

1. [Adoptium Releases](https://adoptium.net/temurin/releases/) を開きます
2. JDK 25 → Linux x64 → `.TAR.GZ` をダウンロードします
3. 展開します: `tar -xzf OpenJDK25U-jdk_x64_linux_hotspot_25_x.tar.gz`
4. PATH に追加するか、`./jdk-25.x.x+yy/bin/java -version` で確認します

---

## 推奨手順（Windows）

1. 上記の **Windows版ダウンロード** ボタンから最新の ZIP ファイルをダウンロードします
2. 任意のフォルダに展開します
3. `AozoraEpub3.bat` をダブルクリックして起動します
4. GUI が表示されたらセットアップ完了です

> **注意**: JAR ファイルのダブルクリックが機能しない場合は BAT ファイルを使用してください。

---

## インストール（macOS / Linux）

1. 上記の **macOS版** または **Linux版ダウンロード** ボタンから TAR.GZ ファイルをダウンロードします
2. 展開します: `tar -xzf AozoraEpub3-*.tar.gz`
3. フォルダに移動し実行します: `./AozoraEpub3.sh`
4. 実行権限エラーの場合は先に実行: `chmod +x AozoraEpub3.sh`

---

## コマンドラインでの実行

詳細な設定が必要な場合は、コマンドラインから実行できます。

```bash
java -jar AozoraEpub3.jar -of -d out input.txt
```

GUI を起動する場合は引数なしで実行します: `java -jar AozoraEpub3.jar`

詳細は [README](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21#readme) をご参照ください。

---

## 関連ガイド

- **[narou.rb 導入ガイド](narou-setup.html)** — Web小説ダウンローダーの導入と AozoraEpub3 連携

---

## トラブルシューティング

- **Java がインストールされていない場合** — [Temurin](https://adoptium.net/temurin/releases/) から Java 25 をダウンロードしインストールしてください（Java 21以降であれば動作します）。
- **Windows で JAR ファイルが開かない場合** — BAT ファイルを使用するか、コマンドプロンプトから `java -jar AozoraEpub3.jar` で起動してください。
- **Linux/macOS で permission denied エラー** — `chmod +x AozoraEpub3.sh` を実行し、再度起動してください。
- **その他の問題** — [GitHub Issues](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/issues) でご報告ください。

---

## 関連リソース

- [GitHub README](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21#readme) — 機能・設定詳細
- [EPUB 3.3 日本語解説](epub33-ja.html) — 3.0 との差分と対応状況
- [English](en/index.html) — View this page in English
