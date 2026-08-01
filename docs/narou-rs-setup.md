---
layout: default
lang: ja
title: narou.rs 導入ガイド（Windows 11・画像付き）
description: narou.rs と AozoraEpub3-JDK21 を連携して Web 小説を EPUB に変換する手順を、Windows 11 の初心者向けに画像付きで解説します。narou.rs のダウンロードと PATH 登録、narou_rs init による AozoraEpub3 の登録、Web UI の環境設定で device を EPUB にする必須設定、小説の登録から EPUB の取り出し、VCRUNTIME140.dll エラー・SmartScreen 警告・Java 未導入時の対処まで。
---

<div style="text-align: right; margin-bottom: 1em;">
  <a href="en/narou-rs-setup.html">🌐 English</a>
</div>

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
   <strong>📚 ドキュメント:</strong>
   <a href="./">ホーム</a> | 
   <a href="usage.html">使い方</a> | 
   <a href="narou-setup.html">narou.rb</a> |
   <strong>narou.rs</strong> |
   <a href="development.html">開発者向け</a> | 
   <a href="epub33-ja.html">EPUB 3.3準拠</a> |
   <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
</nav>

## narou.rs 導入ガイド（Windows 11・画像付き）

> ⚠️ **注記**
> - 本記事は [narou.rs](https://github.com/Rumia-Channel/narou.rs) の公式マニュアルではありません。不明な点は **[narou.rs の README](https://github.com/Rumia-Channel/narou.rs) や [Issues](https://github.com/Rumia-Channel/narou.rs/issues)** の最新情報を優先してください。
> - 検証環境: Windows 11（日本語）、narou.rs v0.3.4、AozoraEpub3 v1.4.0-jdk21

**narou.rs**（開発: [Rumia-Channel](https://github.com/Rumia-Channel) 氏）は、Web 小説のダウンロード・更新・変換を行うツール [narou.rb](narou-setup.html)（whiteleaf7 氏作）の互換ツールです。Rust で実装されており、narou.rb と同じく変換エンジンに AozoraEpub3 を使用します。

本ガイドの手順を上から順に進めると、**小説の URL を貼り付けるだけで EPUB が生成される**環境が完成します。所要時間は 20〜30 分程度です。

### 全体の流れ

1. [Java のインストール](#1-java-のインストール)
2. [AozoraEpub3 の準備](#2-aozoraepub3-の準備narours-専用フォルダ)
3. [narou.rs のダウンロードと展開](#3-narours-のダウンロードと展開)
4. [PATH への登録](#4-path-への登録つまずきやすいポイント)
5. [初期化と AozoraEpub3 の登録](#5-初期化と-aozoraepub3-の登録)
6. [Web UI の起動](#6-web-ui-の起動)
7. [★ 出力を EPUB に設定（必須）](#7--出力を-epub-に設定必須)
8. [小説の登録と EPUB の取り出し](#8-小説の登録と-epub-の取り出し)

---

## 0. 準備するもの

- Windows 11 の PC とインターネット接続
- ソフトのインストール先（本ガイドでは `C:\Tools\` を例に説明します）

> **Point**: インストール先は**半角英数字だけのパス**（例: `C:\Tools\narou`）にするとトラブルを避けられます。
> 日本語やスペースを含む場所（`ダウンロード` フォルダや OneDrive 配下など）は避けてください。

### PowerShell の開き方

本ガイドでは、コマンド入力用の画面（**PowerShell**）を何度か使います。次のどちらかの方法で開けます。

- **スタートボタンを右クリック** → 「**ターミナル**」を選ぶ
- スタートボタンを押して「**powershell**」と入力 → 「Windows PowerShell」を開く

コピーしたコマンドは、PowerShell の画面上で**右クリック**（または `Ctrl+V`）すると貼り付けられます。管理者として実行する必要はありません。

---

## 1. Java のインストール

AozoraEpub3 の実行には Java が必要です。まず、すでにインストールされているかを PowerShell で確認します。

```powershell
java -version
```

バージョン番号（`21` 以上）が表示されれば OK です。「認識されません」と表示された場合は、
👉 **[トップページの Java インストールガイド](./#java-25-のインストールeclipse-temurin)** に従って **Eclipse Temurin の Java 25 LTS** をインストールしてください。

> ✅ **ここまでの確認**: `java -version` でバージョンが表示される

---

## 2. AozoraEpub3 の準備（narou.rs 専用フォルダ）

1. **[AozoraEpub3-JDK21 のダウンロードページ](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases/latest)** から `AozoraEpub3-x.x.x-jdk21.zip` をダウンロードします。
2. ダウンロードした zip を右クリック →「プロパティ」を開き、下部に「許可する」チェックがあればオンにして「OK」を押します（[SmartScreen 警告の回避](usage.html#aozoraepub3exe-の起動時にwindows-によって-pc-が保護されましたと出る)）。
3. zip を右クリック →「すべて展開」で、`C:\Tools\AozoraEpub3-narou` のような**専用フォルダ**に展開します。

> ⚠️ **注意**: narou.rs は初期化の際に AozoraEpub3 の構成ファイル（`chuki_tag.txt`）を書き換えます。
> AozoraEpub3 をほかの用途でも使っている場合は、**narou.rs 専用として別のフォルダに展開**してください（narou.rs 自身も専用インストールを推奨しています）。

> ✅ **ここまでの確認**: 展開したフォルダの中に `AozoraEpub3.jar` がある

---

## 3. narou.rs のダウンロードと展開

1. **[narou.rs の Releases ページ](https://github.com/Rumia-Channel/narou.rs/releases/latest)** から Windows 用の zip（名前に `x86_64-pc-windows` を含むもの）をダウンロードします。
2. zip を右クリック →「すべて展開」します。展開すると `narou` フォルダができるので、`C:\Tools\narou` となるように移動します。

展開後の構成は次のとおりです。

```text
C:\Tools\narou\
  narou_rs.exe
  narou_rs_updater.exe.new
  webnovel\
  preset\
  commitversion
  LICENSE / README.md / Third-Party-License.md
```

> ⚠️ **注意**: `narou_rs.exe` は同じフォルダにある `webnovel\`・`preset\`・`commitversion` を参照して動作します。
> **これらを別の場所に移動したり削除したりしないでください。**

> ⚠️ **注意**: 起動時に「`VCRUNTIME140.dll` が見つかりません」と表示された場合は、
> Microsoft 公式の **[Visual C++ 再頒布可能パッケージ (x64)](https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist)** をインストールしてください。

> ✅ **ここまでの確認**: `C:\Tools\narou` の中に `narou_rs.exe` がある

---

## 4. PATH への登録（つまずきやすいポイント）

どのフォルダからでも `narou_rs` コマンドを使えるように、`narou_rs.exe` のあるフォルダを **PATH**（Windows がコマンドを探す場所の一覧）に登録します。

ここで行うのは、**現在サインインしているユーザーの設定に、フォルダの場所を 1 行追加する**ことだけです。システム全体の設定は変更しませんし、いつでも元に戻せます（戻し方は次の折りたたみ内にあります）。

<details markdown="1">
<summary>💡 元に戻したいとき（登録を削除する手順）</summary>

1. スタートボタンを押して「**環境変数**」と入力し、検索結果の「**アカウントの環境変数を編集**」を開きます
2. 上の段「（ユーザー名）のユーザー環境変数」の一覧から **Path** をクリックして選択し、「**編集...**」を押します
3. 一覧から `C:\Tools\narou` の行をクリックして選択し、右側の「**削除**」を押します
4. 「OK」→「OK」で閉じます。PowerShell を開いている場合は、開き直すと反映されます

これで登録前の状態に戻ります。ほかの行には触らないよう注意してください。

</details>

[PowerShell](#powershell-の開き方) に次の 2 行を**そのまま**コピーして貼り付け、Enter を押します（`C:\Tools\narou` 以外の場所に展開した場合は、そこだけ書き換えてください）。

```powershell
$narouPath = "C:\Tools\narou"
[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path", "User") + ";" + $narouPath, "User")
```

> **Point**: このコマンドを実行するのは **1 回だけ**にしてください。繰り返し実行すると、同じ内容が重複して登録されます（動作に支障はありませんが、一覧が汚れます）。

実行したら、**PowerShell をいったん閉じて、新しく開き直してください**。開き直すまで設定は反映されません。

<details markdown="1">
<summary>コマンドを使わず、設定画面から登録する場合はこちら</summary>

1. スタートボタンを押して「**環境変数**」と入力し、検索結果の「**アカウントの環境変数を編集**」を開きます
2. 上の段「（ユーザー名）のユーザー環境変数」の一覧から **Path** をクリックして選択し、「**編集...**」を押します
3. 「**新規**」を押し、`C:\Tools\narou` と入力します
4. 「OK」→「OK」で閉じます
5. PowerShell を開いている場合は、閉じて開き直します

</details>

> ✅ **ここまでの確認**: **新しく開いた** PowerShell で `narou_rs version` と入力すると、バージョン番号（例: `0.3.4`）が表示される

---

## 5. 初期化と AozoraEpub3 の登録

小説を保存・管理するためのフォルダを作成し、そのフォルダの中で初期化コマンドを実行します。次の 3 行を PowerShell に貼り付けてください。`-p` には**手順 2 で AozoraEpub3 を展開したフォルダ**を指定します。

```powershell
New-Item -ItemType Directory -Force -Path "C:\narou-novels" | Out-Null
Set-Location "C:\narou-novels"
narou_rs init -p "C:\Tools\AozoraEpub3-narou" -l 1.8
```

成功すると、次のように表示されます。

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

> **Point**: `-p` は AozoraEpub3 の場所、`-l 1.8` は行間（1.8 倍）の指定です。
> `-p` を付けずに `narou_rs init` だけを実行した場合は、AozoraEpub3 の場所を対話形式で質問されます。
> その場で設定しなかった場合も、**同じフォルダでもう一度 `narou_rs init -p ...` を実行**すれば登録できます
> （「既に初期化済みです」と表示されますが、AozoraEpub3 の設定はやり直せます）。

> ✅ **ここまでの確認**: 「初期化が完了しました！」と表示される

---

## 6. Web UI の起動

手順 5 で作成した小説管理フォルダ（`C:\narou-novels`）で、次のコマンドを実行します。

```powershell
narou_rs web
```

ブラウザが自動的に開き、narou.rs の画面が表示されます。アドレスは `http://localhost:（ポート番号）/` の形式で、**ポート番号は初回起動時に自動で決まり、次回以降も同じ番号が使われます**。

![narou.rs Web UI のトップ画面。上部にメニュー、中央に黒いログ表示、下に小説リストが並ぶ](assets/narou-rs/02-web-top.png)

- **Web UI を使っている間は、PowerShell の画面を閉じないでください**（閉じると Web UI も終了します）。終了するときは PowerShell で `Ctrl+C` を押します。
- 初回起動時に **Windows ファイアウォールの許可ダイアログ**が表示されたら、「アクセスを許可する」を押してください。
- 初回は「新機能ツアー」が表示されることがあります。「✓ 確認した」で閉じて構いません。

> ✅ **ここまでの確認**: ブラウザに「Narou.rs WEB UI」の画面が表示される

---

## 7. ★ 出力を EPUB に設定（必須）

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

## 8. 小説の登録と EPUB の取り出し

画面左上の「**Download**」ボタンを押すと、URL の入力画面が開きます。読みたい小説のページ（小説家になろう・カクヨムなど）の **URL を貼り付けて「ダウンロード」**を押してください。

![ダウンロード入力ダイアログ。URL 入力欄とダウンロードボタンを赤枠で強調](assets/narou-rs/05-download-url.png)

ダウンロードから EPUB への変換までが自動で進みます。完了すると小説がリストに追加されるので、**「保存先」列のフォルダボタン**を押すと、変換された `.epub` ファイルの保存先フォルダが開きます。

![小説リストに登録された作品の行。保存先のフォルダボタンを赤枠で強調](assets/narou-rs/06-novel-list.png)

EPUB の保存先は `C:\narou-novels\小説データ\（サイト名）\（作品名）\` です。この `.epub` ファイルをお使いのリーダー（スマートフォンのアプリや Kindle など）に転送すれば読めます。

> **Point**: 連載の続きが公開されたら、「**Update**」ボタンを押してください。新しい話を取得して EPUB を作り直します。

> ✅ **ここまでの確認**: 保存先フォルダの中に `.epub` ファイルがある

---

## 困ったときは

| 症状 | 対処 |
|------|------|
| `narou_rs` が「認識されません」と表示される | PATH 登録後に PowerShell を開き直したか確認してください → [手順 4](#4-path-への登録つまずきやすいポイント) |
| 「`VCRUNTIME140.dll` が見つかりません」と表示される | Visual C++ 再頒布可能パッケージをインストールしてください → [手順 3](#3-narours-のダウンロードと展開) |
| 「Windows によって PC が保護されました」と表示される | [SmartScreen 警告の回避方法](usage.html#aozoraepub3exe-の起動時にwindows-によって-pc-が保護されましたと出る)を参照してください |
| 変換時に Java 関連のエラーが出る | Java がインストールされているか確認してください → [手順 1](#1-java-のインストール) |
| 変換はされるが `.epub` ファイルが見つからない | device が EPUB になっているか確認してください → [手順 7](#7--出力を-epub-に設定必須) |
| ファイアウォールの許可画面が表示された | 「アクセスを許可する」を押してください（localhost で動作するだけで、外部には公開されません） |

---

## 参考リンク

- [narou.rs（GitHub）](https://github.com/Rumia-Channel/narou.rs) — README・最新リリース・不具合報告
- [AozoraEpub3 の使い方](usage.html) — 変換の詳細設定
- [narou.rb 導入ガイド](narou-setup.html) — Ruby 版 narou.rb を使う場合
- [AozoraEpub3-JDK21 Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases) — AozoraEpub3 本体のダウンロード

---

<div style="text-align: right;"><small>情報更新日: 2026-08-01 | 本記事は narou.rs 公式ドキュメントではありません</small></div>
