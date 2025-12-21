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

> ⚠️ **重要な注記**
> - 本記事は「narou.rb」公式マニュアルではありません。
> - 以下の記載は 2025-12-21 時点の暫定対応であり、**適用は自己責任** でお願いします。
> - **必ず [narou.rb 公式 Wiki/Issues](https://github.com/whiteleaf7/narou) を確認し、公式の最新情報を優先してください。**
> - ツール本体が更新された場合、本記事の手動修正は不要になる可能性があります。
> - 対象バージョン例: Ruby 3.4.x, narou 3.9.1, AozoraEpub3 v1.2.5-jdk21

Web小説ダウンローダー **narou.rb** を導入し、AozoraEpub3 と連携させるための手順です。

現行の narou.rb（v3.9.1 時点）では、以下の既知の不具合がコミュニティで報告されています：
- 依存ライブラリ（tilt）のバージョン不整合による起動エラー
- 「小説家になろう」サイト仕様変更への未対応

本ガイドは、これらに対するコミュニティから共有されている回避策をまとめたものです。

---

## 1. Ruby のインストール（Windows例）

1. [RubyInstaller for Windows](https://rubyinstaller.org/downloads/) にアクセス。
2. **Ruby+Devkit 3.4.x (x64)** をインストール（MSYS2 toolchain のチェックは入れたまま）。
3. インストール後に PowerShell で `ruby -v` が表示されればOK。

> macOS/Linux でも動作しますが、本記事では Windows を主に扱います。

---

## 2. narou.rb のインストール

```powershell
gem install narou
```

インストール直後は依存ライブラリの不整合で起動しない場合があります。次節の修正を続けてください。

---

## 2.5 AozoraEpub3 のダウンロードとインストール

narou.rb の準備ができたら、AozoraEpub3 をダウンロード・インストールします。

1. 公式サイトのダウンロードページを開く: https://aozoraepub3-jdk21.github.io/AozoraEpub3-JDK21/
2. OS に合ったパッケージをダウンロード（Windows: ZIP, macOS/Linux: TAR.GZ）。
3. 展開後、`AozoraEpub3起動.bat`（Windows）または `AozoraEpub3.sh`（macOS/Linux）で起動。
4. narou.rb からの連携設定例:
   - `narou s aozoraepub3.path="C:/path/to/AozoraEpub3.jar"`
   - 必要に応じて AozoraEpub3 の CLI オプション（例: `-of -d out`）を設定。

---

## 3. 起動エラーの修正（tilt/erubis）【既知の不具合】

**症状**: `narou` 実行時に `cannot load such file -- tilt/erubis` エラーが出る。

**原因**: narou 3.9.1 が新しい tilt (2.5系以降) で erubis 呼び出し時に例外を発生させるため、古い版 (2.4.0) へのダウングレードで回避します。

**回避手順**:
1. 現在の tilt を削除:
   ```powershell
   gem uninstall tilt
   ```
2. 動作確認済みの版をインストール（固定）:
   ```powershell
   gem install tilt -v 2.4.0
   ```

**参考**:
- [narou Issue #443](https://github.com/whiteleaf7/narou/issues/443) — 同様の報告
- GEM の実体パスは環境で異なります。必要に応じて `gem env home` で GEM_HOME を確認してください。

---

## 4. 「小説家になろう」目次取得エラーの修正【暫定回避策】

**症状**: ダウンロード時に目次や本文が取得できません。

**原因**: 「小説家になろう」側の仕様変更に narou が未対応のため、同梱の YAML を手動で差し替える必要があります。

**回避手順**:

コミュニティから共有されている暫定修正（[PR #446](https://github.com/whiteleaf7/narou/pull/446) ベース）を適用します。

1. 修正ファイルをダウンロード:
   - PR #446 を開き、ファイル一覧から以下を選択:
     - `webnovel/ncode.syosetu.com.yaml`
     - `webnovel/novel18.syosetu.com.yaml`
   - 各ファイルの枠右上「**…**」→ 「**View file**」をクリック
   - ファイル内容表示後、右上「**Download raw file**」（↓矢印）をクリック
   - 2つのファイル両方でこの操作を繰り返してダウンロード

2. narou.rb のインストール先へ上書きコピー:
   - **例 (Windows)**: `C:\Ruby34-x64\lib\ruby\gems\3.4.0\gems\narou-3.9.1\webnovel`
   - バージョン番号は環境に合わせて読み替えてください
   - **上書き前に既存ファイルをバックアップすることを強く推奨します**

**注意**:
- PR #446 がマージされ公式版に取り込まれた場合、この手動上書きは不要になります
- 公式版での対応予定をご確認のうえ、本手順の適用判断をお願いします

---

## 5. 初期化と動作確認

```powershell
mkdir MyNovels
cd MyNovels
narou init
```

`narou list` や `narou help` が正常に動けば環境準備完了です。

---

## 6. AozoraEpub3 との連携（最小構成）

- narou.rb から AozoraEpub3 を呼び出す場合、設定で実行パスを指定します。
  ```powershell
  narou s aozoraepub3.path="C:/path/to/AozoraEpub3.jar"
  ```
- 変換結果の EPUB を AozoraEpub3 側で後処理する場合は、出力先ディレクトリを共有し、`-of -d out` など CLI オプションを適宜設定してください。

---

## 7. トラブルシュートのヒント

- 依存ライブラリのバージョン確認: `gem list tilt` / `gem list narou`
- GEM パス確認: `gem env home`
- 公式ヘルプ: [narou.rb Wiki](https://github.com/whiteleaf7/narou/wiki)

---

## 参考リンク

- **[narou.rb 公式 Wiki](https://github.com/whiteleaf7/narou/wiki)** — 公式マニュアル・最新情報
- **[narou.rb Issues](https://github.com/whiteleaf7/narou/issues)** — バグ報告・既知問題
- **[AozoraEpub3 使い方](../usage.html)** — AozoraEpub3 の詳細設定

---

<div style="text-align: right;">
<small>情報更新日: 2025-12-21 | 本記事は公式ではなく、コミュニティ情報をまとめたものです。適用は自己責任でお願いします。</small>
</div>
