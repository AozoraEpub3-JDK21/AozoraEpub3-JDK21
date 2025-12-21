---
layout: default
lang: ja
title: narou.rb 導入ガイド (2025年12月時点)
description: narou.rb のインストールと、2025-12 時点で必要な手動修正、AozoraEpub3 連携手順のまとめ
---

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
   <strong>📚 ドキュメント:</strong>
   <a href="./">ホーム</a> | 
   <a href="usage.html">使い方</a> | 
   <strong>narou.rb</strong> |
   <a href="development.html">開発者向け</a> | 
   <a href="epub33-ja.html">EPUB 3.3準拠</a> |
   <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
</nav>

# narou.rb 導入 & トラブルシューティングガイド

> ⚠️ **注記**
> - 本記事は「narou.rb」公式マニュアルではありません。
> - 以下の記載は 2025-12-21 時点の暫定対応であり、**適用は自己責任** でお願いします。
> - **必ず [narou.rb 公式 Wiki/Issues](https://github.com/whiteleaf7/narou) を確認し、公式の最新情報を優先してください。**
> - ツール本体が更新された場合、本記事の手動修正は不要になる可能性があります。
> - 検証環境: Windows 11, Ruby 3.4.1, narou 3.9.1

Web小説ダウンローダー **narou.rb** を導入し、AozoraEpub3 と連携させるための手順です。

現行の narou.rb（v3.9.1 時点）では、以下の既知の不具合がコミュニティで報告されています：
1. 依存ライブラリ（tilt）のバージョン不整合による起動エラー
2. 「小説家になろう」サイト仕様変更への未対応（目次が取れない）

本ガイドは、これらに対するコミュニティから共有されている回避策をまとめたものです。

---

## 1. Ruby のインストール（Windowsの例）

1. **[RubyInstaller for Windows](https://rubyinstaller.org/downloads/)** にアクセスします。
2. **Ruby+Devkit 3.4.x (x64)** （WITH DEVKIT と書かれているもの）をダウンロードして実行します。
3. インストール画面の途中にある **「MSYS2 development toolchain」** のチェックボックスは**入れたまま**にしてください。
4. インストール完了後、PowerShell で `ruby -v` と入力し、バージョンが表示されればOKです。

> macOS/Linux でも動作しますが、本記事では主に Windows 環境について解説します。

---

## 2. narou.rb のインストール

PowerShell (またはコマンドプロンプト) で以下のコマンドを実行します。

```powershell
gem install narou
```

※ インストール直後は依存ライブラリの不整合で起動しない場合があります。エラーが出る場合は次節の修正を行ってください。

---

## 3. AozoraEpub3 の準備

本ソフトウェア（AozoraEpub3-JDK21）を準備します。

- **[ダウンロードページ (Releases)](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases)** から最新の zip ファイルをダウンロードします。
- 任意の場所（例: `C:\Tools\AozoraEpub3` など）に解凍します。

> **Point**: パス（フォルダの場所）に日本語やスペースが含まれていると、うまく動作しない場合があります。なるべく半角英数字だけの場所に置くことを推奨します。

---

## 4. 起動エラーの修正（tilt/erubis）【既知の不具合】

**症状**: `narou` コマンド実行時に `cannot load such file -- tilt/erubis` というエラーが出る。

**原因**: narou 3.9.1 が使用するライブラリのバージョンが新しすぎるため。古いバージョンに入れ替えることで回避します。

**回避手順**:
1. 現在の tilt を削除します:
   ```powershell
   gem uninstall tilt
   ```
   （"Successfully uninstalled..." と表示されればOK）
2. 対策バージョン (2.4.0) をインストールします:
   ```powershell
   gem install tilt -v 2.4.0
   ```

**参考**:
- [narou Issue #443](https://github.com/whiteleaf7/narou/issues/443) — 同様の報告
- GEM の実体パスは環境で異なります。必要に応じて `gem env home` で GEM_HOME を確認してください。

---

## 5. 「小説家になろう」目次取得エラーの修正【暫定回避策】

**症状**: ダウンロードを実行しても、目次や本文が取得できず終了してしまう。

**原因**: 「小説家になろう」側の仕様変更に narou の設定ファイルが未対応のため。有志によって修正された設定ファイル（YAML）に手動で差し替えます。

**回避手順**:

コミュニティから共有されている暫定修正（[PR #446](https://github.com/whiteleaf7/narou/pull/446) ベース）を適用します。

**1. 修正ファイルをダウンロード**
以下のリンク先（GitHub）を開き、手順に従って **2つのファイル** をダウンロードしてください。

* 👉 **[Pull Request #446 - Files changed](https://github.com/whiteleaf7/narou/pull/446/files)**

1. ファイル一覧から `webnovel/ncode.syosetu.com.yaml` を探します。
2. 右上の「**…**」（三点リーダー）をクリックし、「**View file**」を選択します。
3. ファイルの中身が表示されたら、右上の「**Download raw file**」（↓矢印アイコン）をクリックして保存します。
4. もう一つのファイル `webnovel/novel18.syosetu.com.yaml` も同様にダウンロードします。

**2. ファイルの上書き**
ダウンロードした2つのファイルを、narou.rb がインストールされているフォルダの中に**上書き保存（コピペ）**します。

* **フォルダの場所（例）**:
`C:\Ruby34-x64\lib\ruby\gems\3.4.0\gems\narou-3.9.1\webnovel`
*(※ Rubyのバージョン部分は環境に合わせて読み替えてください)*

> **推奨**: 上書きする前に、元々あったファイルを「～.yaml.bak」のように名前を変えてバックアップしておくと安心です。

---

## 6. 初期化と AozoraEpub3 の連携

小説保存用のフォルダを作成し、初期化コマンドを実行します。この中で AozoraEpub3 との連携設定も行います。

```powershell
mkdir MyNovels
cd MyNovels
narou init
```

コマンドを実行すると、いくつか設定項目を聞かれます。

1. **「AozoraEpub3のフォルダを指定して下さい」** と表示されます。
2. 手順3で用意した **`AozoraEpub3.jar` が入っているフォルダのパス** を入力して Enter を押します。
   - 例: `C:\Tools\AozoraEpub3`

これで `narou.rb` が AozoraEpub3 の場所を記憶し、自動連携の設定は完了です。

**補足: 設定ファイルについて**
初期化後、`AozoraEpub3.jar` と同じディレクトリに **`AozoraEpub3.ini`** というファイルが使用（または作成）されます。行間やフォントサイズなどの変換設定を変更したい場合は、このファイルを編集するか、AozoraEpub3 の GUI から設定を保存してください。

---

## 7. トラブルシュートのヒント

- **バージョン確認**:
   - `gem list tilt` (2.4.0 であること)
   - `gem list narou` (3.9.1 であること)
- **設定ファイルの場所がわからない**: `gem env home` でインストール先のベースフォルダを確認
- **公式ヘルプ**: [narou.rb Wiki](https://github.com/whiteleaf7/narou/wiki)

---

## 参考リンク

- **[narou.rb 公式 Wiki](https://github.com/whiteleaf7/narou/wiki)** — 公式マニュアル・最新情報
- **[narou.rb Issues](https://github.com/whiteleaf7/narou/issues)** — バグ報告・既知問題
- **[narou.rb 公式掲示板](https://jbbs.shitaraba.net/computer/44668/)** — ユーザーコミュニティ・情報交換
- **[AozoraEpub3 使い方](../usage.html)** — AozoraEpub3 の詳細設定

---

<div style="text-align: right;">
<small>情報更新日: 2025-12-21 | 本記事は公式ではなく、コミュニティ情報をまとめたものです。</small>
</div>
