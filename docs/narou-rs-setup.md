---
layout: default
lang: ja
title: narou.rs 導入ガイド（Windows 11・画像付き）
description: narou.rs と AozoraEpub3-JDK21 を連携して Web 小説を EPUB に変換する手順を、Windows 11 の初心者向けに画像付きで解説します。Java 不要の GPL 版（AozoraEpub3_Lite 組込み）と通常版の選び方、narou.rs のダウンロードから narou_rs init による AozoraEpub3 の登録、Web UI の環境設定で device を EPUB にする必須設定、小説の登録から EPUB の取り出し、VCRUNTIME140.dll エラー・SmartScreen 警告・Java 未導入時の対処まで。
---

<div style="text-align: right; margin-bottom: 1em;">
  <a href="en/narou-rs-setup.html">🌐 English</a>
</div>

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
   <strong>📚 ドキュメント:</strong>
   <a href="./">ホーム</a> | 
   <a href="usage.html">使い方</a> | 
   <a href="gaiji-settings.html">外字の設定</a> | 
   <a href="narou-setup.html">narou.rb</a> |
   <strong>narou.rs</strong> |
   <a href="development.html">開発者向け</a> | 
   <a href="epub33-ja.html">EPUB 3.3準拠</a> |
   <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
</nav>

## narou.rs 導入ガイド（Windows 11・画像付き）

> ⚠️ 本記事は [narou.rs](https://github.com/Rumia-Channel/narou.rs) の公式マニュアルではありません。不明な点は **[narou.rs の README](https://github.com/Rumia-Channel/narou.rs) や [Issues](https://github.com/Rumia-Channel/narou.rs/issues)** の最新情報を優先してください。

**narou.rs**（開発: [Rumia-Channel](https://github.com/Rumia-Channel) 氏）は、Web 小説のダウンロード・更新・EPUB 変換を行うツールです。[narou.rb](narou-setup.html)（whiteleaf7 氏作）の Rust 製互換ツールで、narou.rb と同じく変換エンジンに AozoraEpub3 を使います。

このページの手順を上から順に進めると、**小説の URL を貼り付けるだけで EPUB ができる**環境が完成します。所要時間は 15〜20 分程度です。

> 📖 各手順の理由・ライセンス・2 つの版の見た目の比較・PATH 登録などの詳しい解説は **[補足（詳細解説）](narou-rs-setup-details.html)** にまとめています。手順どおりに進めるだけなら読む必要はありません。

<a id="editions"></a>

### 最初に: 通常版と GPL 版のどちらを使うか

narou.rs は 2 種類の zip で配布されています。

| | 通常版 | GPL 版 |
|---|---|---|
| こんな人向け | AozoraEpub3 本体で変換したい（`AozoraEpub3.ini`・外字フォント・自動プレビューなど AozoraEpub3 側の設定がそのまま効く） | Java を入れずに、とにかく手軽に始めたい |
| Java・AozoraEpub3 | 必要 | **不要**（変換エンジンが組み込み済み） |
| 進める手順 | 1 → 7 のすべて | **手順 1・2 を飛ばす** |

ダウンロードして使うだけなら、どちらを選んでも問題ありません。本文はどちらも同じですが、扉・目次・見出しの見た目が少し異なります（[見た目の比較](narou-rs-setup-details.html#rendering)）。

### 全体の流れ

1. [Java のインストール](#1-java-のインストール)（GPL 版は不要）
2. [AozoraEpub3 の準備](#2-aozoraepub3-の準備narours-専用フォルダ)（GPL 版は不要）
3. [narou.rs のダウンロードと展開](#3-narours-のダウンロードと展開)
4. [初期化と AozoraEpub3 の登録](#4-初期化と-aozoraepub3-の登録)
5. [Web UI の起動](#5-web-ui-の起動)
6. [★ 出力を EPUB に設定（必須）](#6--出力を-epub-に設定必須)
7. [小説の登録と EPUB の取り出し](#7-小説の登録と-epub-の取り出し)

---

## 0. 準備するもの

- Windows 11 の PC とインターネット接続

本ガイドでは、次のフォルダ構成で説明します。別の場所に置く場合は、コマンド内のパスを読み替えてください。

| フォルダ | 用途 |
|---|---|
| `C:\Tools\AozoraEpub3-jdk21` | AozoraEpub3（変換エンジン）。**GPL 版では不要** |
| `C:\Tools\narou` | narou.rs 本体 |
| `C:\Tools\narou-novels` | 小説の保存・管理フォルダ |

> **Point**: 日本語やスペースを含む場所（`ダウンロード` フォルダや OneDrive 配下など）は避け、**半角英数字だけのパス**にしてください。

### コマンドの入力画面（PowerShell）の開き方

- **スタートボタンを右クリック** →「**ターミナル**」を選ぶ
- エクスプローラーでフォルダ内の何もない場所を右クリック →「**ターミナルで開く**」を選ぶと、**そのフォルダの場所で**開きます（手順 4・5 で使います）

コマンドは画面上で**右クリック**（または `Ctrl+V`）すると貼り付けられます。管理者として実行する必要はありません。

---

## 1. Java のインストール

> **GPL 版を使う場合、この手順は不要です** → [手順 3](#3-narours-のダウンロードと展開) へ進んでください。

PowerShell で次のコマンドを実行します。

```powershell
java -version
```

バージョン番号（`21` 以上）が表示されれば OK です。「認識されません」と表示された場合は、
👉 **[トップページの Java インストールガイド](./#java-25-のインストールeclipse-temurin)** に従って **Eclipse Temurin の Java 25 LTS** をインストールしてください。

> ✅ **ここまでの確認**: `java -version` でバージョンが表示される

---

## 2. AozoraEpub3 の準備（narou.rs 専用フォルダ）

> **GPL 版を使う場合、この手順は不要です** → [手順 3](#3-narours-のダウンロードと展開) へ進んでください。

1. **[AozoraEpub3-JDK21 のダウンロードページ](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/latest)** から `AozoraEpub3-x.x.x-jdk21.zip` をダウンロードします。
2. zip を右クリック →「すべて展開」を選び、展開先の欄に `C:\Tools\AozoraEpub3-jdk21` と入力して展開します。

> ⚠️ **注意**: AozoraEpub3 をほかの用途でも使っている場合も、**narou.rs 用に別のフォルダへ展開**してください（[理由](narou-rs-setup-details.html#dedicated-folder)）。

> ✅ **ここまでの確認**: `C:\Tools\AozoraEpub3-jdk21` を開くと、**その直下に** `AozoraEpub3.jar` がある。
> 一段深いフォルダの中に入ってしまっている場合は、その中身をすべて `C:\Tools\AozoraEpub3-jdk21` の直下に移動してください。

---

## 3. narou.rs のダウンロードと展開

1. **[narou.rs の Releases ページ](https://github.com/Rumia-Channel/narou.rs/releases/latest)** の「Assets」から Windows 用の zip をダウンロードします。
   - 通常版: `narou_rs_win_x64.zip`
   - GPL 版: `narou_rs_win_x64-GPL.zip`
   - ARM 版 Windows の PC では、名前が `win_arm64` のものを選びます。
2. zip を右クリック →「すべて展開」します。展開すると `narou` フォルダができるので、`C:\Tools\narou` となるように移動します。中のファイルは**移動・削除しないでください**。

> ✅ **ここまでの確認**: PowerShell で `C:\Tools\narou\narou_rs.exe version` と入力すると、バージョン番号（例: `0.4.4`）が表示される
> （「`VCRUNTIME140.dll` が見つかりません」と表示されたら → [困ったときは](#困ったときは)）

---

## 4. 初期化と AozoraEpub3 の登録

1. エクスプローラーのアドレス欄に `C:\Tools` と入力して開き、何もない場所を右クリック →「新規作成」→「フォルダー」で、`narou-novels` という名前のフォルダを作ります。
2. 作成した `narou-novels` フォルダを開き、何もない場所を右クリック →「**ターミナルで開く**」を選びます（行頭に `C:\Tools\narou-novels` と表示された画面が開きます）。
3. 版に応じて、次の 1 行を貼り付けて Enter を押します。

**通常版の場合**:

```powershell
C:\Tools\narou\narou_rs.exe init -p "C:\Tools\AozoraEpub3-jdk21"
```

**GPL 版の場合**（「AozoraEpub3のあるフォルダを入力して下さい」と聞かれたら、**何も入力せずに Enter**）:

```powershell
C:\Tools\narou\narou_rs.exe init
```

途中で「!!!WARNING!!!」と表示されますが、そのまま進めて構いません（[詳細](narou-rs-setup-details.html#init)）。

> ✅ **ここまでの確認**: 最後に「初期化が完了しました！」と表示される

---

## 5. Web UI の起動

手順 4 と同じように、`C:\Tools\narou-novels` を開いて「**ターミナルで開く**」を選び、次の 1 行を実行します。**次回以降もこの操作だけで起動できます。**

```powershell
C:\Tools\narou\narou_rs.exe web
```

ブラウザが自動的に開き、narou.rs の画面が表示されます。

![narou.rs Web UI のトップ画面。上部にメニュー、中央に黒いログ表示、下に小説リストが並ぶ](assets/narou-rs/02-web-top.png)

- **Web UI を使っている間は、PowerShell の画面を閉じないでください**（閉じると Web UI も終了します）。終了するときは PowerShell で `Ctrl+C` を押します。
- 初回起動時に **Windows ファイアウォールの許可ダイアログ**が表示されたら、「アクセスを許可する」を押してください。
- 初回は「新機能ツアー」が表示されることがあります。「✓ 確認した」で閉じて構いません。

> ✅ **ここまでの確認**: ブラウザに「Narou.rs WEB UI」の画面が表示される

---

## 6. ★ 出力を EPUB に設定（必須）

画面右上の「**⚙ オプション**」→「**環境設定...**」を開きます。

![オプションメニューを開いたところ。先頭の「環境設定...」を赤枠で強調](assets/narou-rs/03-options-menu.png)

「一般」タブの最上部にある「**device**（変換、送信対象の端末）」を「**EPUB**」に変更し、右上の「**設定を保存**」を押します。

![narou.rs の環境設定画面。device が EPUB に設定され、右上に設定を保存ボタンがある](assets/narou-rs/04-settings-device-epub.png)

<div style="border-left: 4px solid #cf222e; background: #fff8f8; padding: 0.8em 1em; margin: 1em 0;">
<strong>この設定を行わないと EPUB は出力されません。</strong>
Kindle など特定の端末で読む場合はその端末名を選んでも構いませんが、迷ったら EPUB を選んでください。
</div>

「← 小説リストに戻る」で元の画面に戻ります。

> ✅ **ここまでの確認**: 環境設定を開き直すと device が EPUB になっている

---

## 7. 小説の登録と EPUB の取り出し

画面左上の「**Download**」ボタンを押すと、URL の入力画面が開きます。読みたい小説のページ（小説家になろう・カクヨムなど）の **URL を貼り付けて「ダウンロード」**を押してください。

![ダウンロード入力ダイアログ。URL 入力欄とダウンロードボタンを赤枠で強調](assets/narou-rs/05-download-url.png)

ダウンロードから EPUB への変換までが自動で進みます。完了すると小説がリストに追加されるので、**「保存先」列のフォルダボタン**を押すと、変換された `.epub` ファイルの保存先フォルダが開きます。

![小説リストに登録された作品の行。保存先のフォルダボタンを赤枠で強調](assets/narou-rs/06-novel-list.png)

EPUB の保存先は `C:\Tools\narou-novels\小説データ\（サイト名）\（作品名）\` です。この `.epub` ファイルをお使いのリーダー（スマートフォンのアプリや Kindle など）に転送すれば読めます。

> **Point**: 連載の続きが公開されたら、「**Update**」ボタンを押してください。新しい話を取得して EPUB を作り直します。

> ✅ **ここまでの確認**: 保存先フォルダの中に `.epub` ファイルがある

---

## 困ったときは

| 症状 | 対処 |
|------|------|
| 「`VCRUNTIME140.dll` が見つかりません」と表示される | Microsoft 公式の **[Visual C++ 再頒布可能パッケージ](https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist)** をインストールしてください（通常は x64 版、ARM 版 Windows では ARM64 版） |
| `narou_rs` が「認識されません」と表示される | フルパス（`C:\Tools\narou\narou_rs.exe`）で実行してください。短く呼びたい場合は [PATH への登録（補足）](narou-rs-setup-details.html#path)を参照してください |
| 「Windows によって PC が保護されました」と表示される | `narou_rs.exe` をダブルクリックで起動した場合などに表示されることがあります。[SmartScreen 警告の回避方法](usage.html#aozoraepub3exe-の起動時にwindows-によって-pc-が保護されましたと出る)と同じ手順で回避できます |
| 変換時に Java 関連のエラーが出る | Java がインストールされているか確認してください → [手順 1](#1-java-のインストール)。GPL 版でも、AozoraEpub3 を登録していると Java が使われます → [補足](narou-rs-setup-details.html#editions) |
| 変換時に「`AozoraEpub3 not found`」と表示される | 通常版で AozoraEpub3 が登録されていません。手順 2 の後に `init -p ...` を実行してください（同じフォルダで何度でもやり直せます）→ [手順 4](#4-初期化と-aozoraepub3-の登録)。Java や AozoraEpub3 を使わずに変換したい場合は GPL 版を使います |
| 変換はされるが `.epub` ファイルが見つからない | device が EPUB になっているか確認してください → [手順 6](#6--出力を-epub-に設定必須) |
| 初期化したのに Web UI に反映されない・小説リストが空 | 別のフォルダでコマンドを実行した可能性があります。コマンド入力画面の行頭に `C:\Tools\narou-novels` と表示されているか確認してください → [手順 4](#4-初期化と-aozoraepub3-の登録) |
| ファイアウォールの許可画面が表示された | 「アクセスを許可する」を押してください（localhost で動作するだけで、外部には公開されません） |

---

## 参考リンク

- [narou.rs 導入ガイド 補足（詳細解説）](narou-rs-setup-details.html) — 2 種類の配布版の違い・ライセンス・出力と見た目の比較、init の詳細、PATH への登録など
- [narou.rs（GitHub）](https://github.com/Rumia-Channel/narou.rs) — README・最新リリース・不具合報告
- [AozoraEpub3_Lite（GitHub）](https://github.com/Rumia-Channel/AozoraEpub3_Lite) — GPL 版に組み込まれている変換エンジン
- [AozoraEpub3 の使い方](usage.html) — 変換の詳細設定
- [narou.rb 導入ガイド](narou-setup.html) — Ruby 版 narou.rb を使う場合
- [AozoraEpub3-JDK21 Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases) — AozoraEpub3 本体のダウンロード

---

<div style="text-align: right;"><small>情報更新日: 2026-09-27 | 本記事は narou.rs 公式ドキュメントではありません | 検証環境は<a href="narou-rs-setup-details.html">補足</a>を参照</small></div>
