---
layout: default
lang: ja
title: narou.rs 導入ガイド 補足（詳細解説）
description: narou.rs 導入ガイドの補足資料。GPL 版と通常版の違い（優先順位・外字・版の切り替え・ライセンスが分かれている理由・出力の実測比較・EPUB リーダーでの見た目の比較）、AozoraEpub3 を専用フォルダにする理由、narou.rs のフォルダ構成、init コマンドの出力とオプション、Web UI のポート番号、PATH への登録方法を解説します。
---

<div style="text-align: right; margin-bottom: 1em;">
  <a href="en/narou-rs-setup-details.html">🌐 English</a>
</div>

<nav style="background: #f6f8fa; padding: 1em; margin-bottom: 2em; border-radius: 6px;">
   <strong>📚 ドキュメント:</strong>
   <a href="./">ホーム</a> | 
   <a href="usage.html">使い方</a> | 
   <a href="gaiji-settings.html">外字の設定</a> | 
   <a href="narou-setup.html">narou.rb</a> |
   <a href="narou-rs-setup.html">narou.rs</a> |
   <a href="development.html">開発者向け</a> | 
   <a href="epub33-ja.html">EPUB 3.3準拠</a> |
   <a href="https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21">GitHub</a>
</nav>

## narou.rs 導入ガイド 補足（詳細解説）

このページは **[narou.rs 導入ガイド](narou-rs-setup.html)** の補足です。手順どおりに進めるだけならこのページを読む必要はありません。「なぜそうするのか」「ほかの選択肢はあるか」を知りたいときに参照してください。

> ⚠️ **注記**
> - 本記事は [narou.rs](https://github.com/Rumia-Channel/narou.rs) の公式マニュアルではありません。不明な点は **[narou.rs の README](https://github.com/Rumia-Channel/narou.rs) や [Issues](https://github.com/Rumia-Channel/narou.rs/issues)** の最新情報を優先してください。
> - 検証環境: Windows 11（日本語）、narou.rs v0.3.4（手順・画面）/ v0.4.4（2 種類の配布版の動作確認・出力比較）、AozoraEpub3 v1.4.0-jdk21（手順・画面）/ v1.6.1-jdk21（出力比較）、Thorium Reader 3.5.1（見た目の比較）

---

<a id="editions"></a>

## 2 種類の配布版（v0.4.0 以降）

narou.rs v0.4.0 以降は、同じバージョンが 2 種類の zip で配布されています。

| | 通常版 | GPL 版 |
|---|---|---|
| zip の名前（Windows 64bit） | `narou_rs_win_x64.zip` | `narou_rs_win_x64-GPL.zip` |
| EPUB 変換エンジン | 外部の AozoraEpub3（AozoraEpub3-JDK21 など） | 組み込みの [AozoraEpub3_Lite](https://github.com/Rumia-Channel/AozoraEpub3_Lite)（AozoraEpub3 の Rust 移植） |
| Java | 必要 | **不要** |
| AozoraEpub3 のダウンロード | 必要 | **不要** |
| ライセンス | BSD-2-Clause | GPL-3.0 |

### どちらのエンジンが使われるか

GPL 版でも、AozoraEpub3 を登録している場合（`init -p` で登録した・`AozoraEpub3` に PATH が通っている など）は**そちらが優先して使われます**。組み込みエンジンが使われるのは、AozoraEpub3 が見つからないときだけです。そのため、GPL 版でも AozoraEpub3 を登録していると Java が必要になります。

また、AozoraEpub3 を登録していない場合は、AozoraEpub3 の `gaiji` フォルダに置く外字フォントは使われません。

### あとから版を切り替える

Web UI を終了（`Ctrl+C`）してから、もう一方の zip をダウンロードして展開し、中の `narou` フォルダの**中身**を `C:\Tools\narou` にコピーして上書きします（「ファイルを置き換える」を選びます）。

セルフアップデートで取得する版は、Web UI の環境設定（Global タブ）の `self-update.variant` で選べます（`gpl` = GPL 版 / `standard` = 通常版）。

### ライセンスが分かれている理由

AozoraEpub3（hmdev 氏の原作と、そこから派生した改造版 AozoraEpub3（kyukyunyorituryo 氏）・AozoraEpub3-JDK21 など）は **GPL-3.0** で公開されています。GPL のプログラムを組み込んだソフトウェアを配布する場合、その配布物全体を GPL で配布しなければなりません（**ライセンスの伝搬**）。AozoraEpub3_Lite は AozoraEpub3 を移植したものなので GPL-3.0 です。そのため、これを組み込んだ narou.rs は配布物全体が GPL-3.0 になり、BSD-2-Clause の通常版とは別の zip で配布されています。通常版は AozoraEpub3 を別のプログラムとして呼び出すだけなので、BSD-2-Clause のままです。

ライセンスの違いが関係するのは、**プログラムを改変したり再配布したりする場合**です。ダウンロードして使うだけなら、どちらの版を選んでも問題ありません。

### 出力の比較（本サイトでの実測）

小説家になろうの 4 作品（1,404 話の長編 1 作を含む、本文ファイル計 1,678 個）を同じ本文データから変換し、GPL 版の組み込みエンジンと AozoraEpub3-JDK21 v1.6.1 の出力を比べました（narou.rs v0.4.4）。

| 項目 | 結果 |
|---|---|
| 変換 | 4 作品ともどちらのエンジンでも成功 |
| 本文の文字 | 全ファイルで一致 |
| ルビ（読みを含む） | 全 15 か所で一致 |
| 挿絵 | 対象作品に挿絵がなく、未確認 |
| epubcheck 5.2.0 | どちらもエラー・警告なし |
| EPUB の内部構成 | **異なる**。組み込みエンジンは[改造版 AozoraEpub3](https://github.com/kyukyunyorituryo/AozoraEpub3)（kyukyunyorituryo 氏、電書連 EPUB 3 制作ガイドに沿ったフォーク）と同じ `item/` 配下の構成、AozoraEpub3-JDK21 は `OPS/` 配下の構成 |
| 見出し・中扉のマークアップ | **異なる**。例: 話の見出しは組み込みエンジンでは `<h2>`、AozoraEpub3-JDK21 では `<div class="chap2">` |

読める内容は同じですが、マークアップや CSS が異なるため、見出しの大きさや中扉のレイアウトなどの見た目が異なる場合があります。

<a id="rendering"></a>

### 見た目の比較（Thorium Reader での表示）

同じ本文を 2 つのエンジンで変換し、同じ EPUB リーダーで同じページを表示して並べました。

- 本文: 青空文庫の[太宰治「走れメロス」](https://www.aozora.gr.jp/cards/000035/card1567.html)（パブリックドメイン）を、小説家になろうの作品と同じ形（2 章・3 話）に組み立てたもの。narou.rs の通常の変換経路を通しています
- 変換: narou.rs v0.4.4 GPL 版。左は組み込みエンジン、右は AozoraEpub3-JDK21 v1.6.1 を登録した状態（どちらも narou.rs の既定設定）
- 表示: [Thorium Reader](https://thorium.edrlab.org/) 3.5.1（Windows 11）、同じウィンドウサイズ・既定の表示設定。左右の青い矢印は Thorium のページ送りボタンです

見え方は EPUB リーダーによって変わります。お使いのリーダーでは異なる表示になる場合があります。

<table>
<thead>
<tr><th style="width: 16%;">ページ</th><th>組み込みエンジン（GPL 版）</th><th>AozoraEpub3-JDK21</th></tr>
</thead>
<tbody>
<tr>
<td><strong>扉</strong><br/>書名と著者名</td>
<td><img src="assets/narou-rs/engine-compare/title-lite.png" alt="組み込みエンジンの扉。書名が左寄せの横書きで、下に罫線と著者名が並ぶ" width="480"/></td>
<td><img src="assets/narou-rs/engine-compare/title-java.png" alt="AozoraEpub3-JDK21 の扉。書名と著者名が中央揃えの横書きで上下に離れて並ぶ" width="480"/></td>
</tr>
<tr>
<td><strong>目次</strong></td>
<td><img src="assets/narou-rs/engine-compare/toc-lite.png" alt="組み込みエンジンの目次。章の下に話が字下げされた階層表示" width="480"/></td>
<td><img src="assets/narou-rs/engine-compare/toc-java.png" alt="AozoraEpub3-JDK21 の目次。章と話が同じ階層の箇条書きで並ぶ" width="480"/></td>
</tr>
<tr>
<td><strong>章の中扉</strong></td>
<td><img src="assets/narou-rs/engine-compare/chapter-lite.png" alt="組み込みエンジンの章の中扉。ページ上部の中央寄りに、柱の書名と大きめの章題が表示される" width="480"/></td>
<td><img src="assets/narou-rs/engine-compare/chapter-java.png" alt="AozoraEpub3-JDK21 の章の中扉。右上に柱の書名と小さめの章題が表示される" width="480"/></td>
</tr>
<tr>
<td><strong>話の本文</strong><br/>見出しとルビ</td>
<td><img src="assets/narou-rs/engine-compare/episode-lite.png" alt="組み込みエンジンの本文ページ。太字の話の見出しに続いて、ルビ付きの縦書き本文" width="480"/></td>
<td><img src="assets/narou-rs/engine-compare/episode-java.png" alt="AozoraEpub3-JDK21 の本文ページ。通常の太さの話の見出しに続いて、ルビ付きの縦書き本文" width="480"/></td>
</tr>
</tbody>
</table>

この環境で見えた違いは次のとおりです。

| ページ | 組み込みエンジン | AozoraEpub3-JDK21 |
|---|---|---|
| 扉 | 書名を明朝体で左寄せ、罫線の下に著者名 | 書名と著者名をゴシック体で中央揃え |
| 目次 | 章の下に話を字下げした階層表示 | 章と話を同じ階層の箇条書きで表示 |
| 章の中扉 | 章題が大きく、ページ上部の中央寄りに表示 | 章題は小さめで、柱とともにページ右上に表示 |
| 話の本文 | 見出しが太字 | 見出しは通常の太さ。本文の組み方・ルビは同じ |

---

<a id="dedicated-folder"></a>

## AozoraEpub3 を narou.rs 専用のフォルダにする理由

narou.rs は初期化（`init -p`）の際に、AozoraEpub3 の構成ファイル（`chuki_tag.txt`）を書き換えます。ほかの用途で使っている AozoraEpub3 を登録すると、そちらの変換結果にも影響します。そのため、narou.rs 用には別のフォルダに展開した AozoraEpub3 を使ってください（narou.rs 自身も専用インストールを推奨しています）。

---

<a id="folder-layout"></a>

## narou.rs のフォルダ構成

zip を展開した後の構成は次のとおりです（通常版・GPL 版とも同じ）。

```text
C:\Tools\narou\
  narou_rs.exe
  narou_rs_updater.exe.new
  narou_rs_backup.exe
  narou_rs_login.exe
  webnovel\
  preset\
  commitversion
  LICENSE / README.md / Third-Party-License.md
```

`narou_rs.exe` は同じフォルダにある `webnovel\`・`preset\`・`commitversion` を参照して動作します。**これらを別の場所に移動したり削除したりしないでください。**

起動時に「`VCRUNTIME140.dll` が見つかりません」と表示された場合は、Microsoft 公式の **[Visual C++ 再頒布可能パッケージ](https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist)** をインストールしてください（通常は x64 版、ARM 版 Windows では ARM64 版）。

---

<a id="init"></a>

## 初期化（init）の詳細

### 通常版の出力

`init -p` が成功すると、次のように表示されます。

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

`!!!WARNING!!!` は、[上で説明した](#dedicated-folder) `chuki_tag.txt` の書き換えについての警告です。

### GPL 版の出力

`init`（`-p` なし）で「AozoraEpub3のあるフォルダを入力して下さい」に何も入力せず Enter を押した場合も、途中で「!!!WARNING!!! AozoraEpub3の構成ファイルを書き換えます…」と表示されます。AozoraEpub3 を登録しない場合は何も書き換えられないので、気にせず進めて構いません。最後に「AozoraEpub3 の設定をスキップしました」「初期化が完了しました！」と表示されます。

### オプションとやり直し

- `-p` には AozoraEpub3 を展開したフォルダ（`AozoraEpub3.jar` があるフォルダ）を指定します。
- 行間は標準で 1.8 倍に設定されます。変えたい場合のみ `-l 2.0` のように追加します。
- 登録に失敗した場合も、**同じフォルダでもう一度 `init -p ...` を実行**すればやり直せます（「既に初期化済みです」と表示されますが、AozoraEpub3 の設定はやり直せます）。

### 実行する場所

導入ガイドでは、`init` と `web` をどちらも小説管理フォルダ（`C:\Tools\narou-novels`）で開いた画面から実行しています。スタートメニューから開いた PowerShell で実行する場合は、先に `cd C:\Tools\narou-novels` と入力して現在地を移動してください。

小説管理フォルダは、narou.rs 本体のフォルダ（`C:\Tools\narou`）の**中には作らないでください**。narou.rs は本体フォルダと小説管理フォルダを分ける前提で設計されています（公式 README の指示）。

---

<a id="port"></a>

## Web UI のアドレス（ポート番号）

Web UI のアドレスは `http://localhost:（ポート番号）/` の形式です。**ポート番号は初回起動時に自動で決まり、次回以降も同じ番号が使われます**。Web UI は自分の PC（localhost）でだけ動作し、外部には公開されません。

---

<a id="path"></a>

## PATH への登録（任意）

毎回 `C:\Tools\narou\narou_rs.exe` と入力する代わりに、`narou_rs` だけで呼び出せるようにする設定です。**なくても導入ガイドの手順はすべて動作します**（narou.rs 公式 README は PATH 登録を前提に説明していますが、フルパス実行でも動作は同じです）。

PowerShell に次の 2 行を**そのまま**貼り付けて Enter を押し（実行するのは **1 回だけ**）、**PowerShell を開き直します**。

```powershell
$narouPath = "C:\Tools\narou"
[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path", "User") + ";" + $narouPath, "User")
```

以降は新しく開いた PowerShell で `narou_rs web` のように短く入力できます。

元に戻すには: スタートボタン → 「環境変数」と入力 → 「アカウントの環境変数を編集」→ 上段の **Path** を選んで「編集...」→ `C:\Tools\narou` の行を選んで「削除」→ 「OK」で閉じます。

---

<p><a href="narou-rs-setup.html">← narou.rs 導入ガイドに戻る</a></p>

<div style="text-align: right;"><small>情報更新日: 2026-09-27 | 本記事は narou.rs 公式ドキュメントではありません</small></div>
