---
layout: default
lang: ja
title: narou.rb 導入ガイド (2025年12月時点)
description: narou.rb のインストールと、2025-12 時点で必要な手動修正、AozoraEpub3 連携手順のまとめ
---

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
  <a href="../">ホーム</a> |
  <a href="../usage.html">使い方</a> |
  <a href="./narou-setup.html">narou.rb ガイド</a>
</nav>

# narou.rb 導入 & トラブルシューティングガイド

> ⚠️ 本記事は「narou.rb」公式マニュアルではありません。2025-12-21 時点の暫定対応です。ツール本体が更新された場合は手動修正が不要になる可能性があります。
>
> 対象バージョン例: Ruby 3.4.x, narou 3.9.1, AozoraEpub3 v1.2.5-jdk21

Web小説ダウンローダー **narou.rb** を導入し、AozoraEpub3 と連携させるための手順です。現行の narou.rb では依存ライブラリやサイト仕様変更に起因する不具合があり、下記の手動修正が必要です。

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

## 3. 起動エラーの修正（tilt/erubis）

`narou` 実行時に `cannot load such file -- tilt/erubis` が出る場合、tilt が新しすぎるのが原因です。narou 3.9.1 は tilt 2.4.0 で動作確認されています。

1. 現在の tilt を削除:
   ```powershell
   gem uninstall tilt
   ```
2. 旧版をインストール（固定）:
   ```powershell
   gem install tilt -v 2.4.0
   ```

> 理由: tilt 2.5系以降で erubis 呼び出し時に例外が発生するため、2.4.0 に固定します。

> GEM の実体パスは環境で異なります。必要に応じて `gem env home` で GEM_HOME を確認してください。

---

## 4. 「小説家になろう」目次取得エラーの修正

なろう側の仕様変更により、同梱の YAML を差し替える必要があります。以下はコミュニティで共有されている暫定修正です（PR #446 ベース）。

1. 修正ファイルをダウンロード（各リンクで **Download raw file** を選択）:
   - `webnovel/ncode.syosetu.com.yaml`
   - `webnovel/novel18.syosetu.com.yaml`

   出典: [Fix: なろうの仕様変更対応 (Pull Request #446)](https://github.com/whiteleaf7/narou/pull/446)

2. narou.rb のインストール先に上書きコピー。
   - 例 (Windows): `C:\Ruby34-x64\lib\ruby\gems\3.4.0\gems\narou-3.9.1\webnovel`
   - バージョンは環境に合わせて読み替えてください。
   - 上書き前に既存ファイルのバックアップを推奨します。

> PR がマージされ公式版に取り込まれた場合、この手動上書きは不要になります。

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

<div style="text-align: right;">
<small>情報更新日: 2025-12-21</small>
</div>
