# 公開ドキュメント（GitHub Pages）の残件

2026-08-26、`docs/gaiji-settings.md` 追加にあわせて日英の全ページをレビューした際の積み残し。
その場で直した分は PR に含めている。ここに残すのは**分量が大きく別 PR が適切なもの**。

レビューは Opus のサブエージェント 2 本（日本語ページ / 英語ページ）で実施した。

---

## 1. 英語版が日本語版に追いついていない（優先度：高）

`docs/en/usage.md` に、日本語版にあるセクションが存在しない。

| 欠落セクション | 日本語版の該当箇所 |
|---|---|
| 更新確認（Update check） | `docs/usage.md:360-379` |
| 既知の問題（Known Issues） | `docs/usage.md:527-533` |
| ライセンス（License） | `docs/usage.md:535-549` |

`docs/en/epub33.md` には日本語版の「利用技術」セクション（`docs/epub33-ja.md:74-79`）が無い。

> 2026-08-26 時点で CLI オプション表への `-cu / --check-update` 追加のみ対応済み。
> 本文セクションの翻訳は未着手。

## 2. `docs/en/epub33.md` の見出し階層が壊れている（優先度：中）

`docs/en/epub33.md:36-53` の `###` 見出し 3 つに親の `##` が無い。
また front matter の description が「changes from 3.0」を約束しているが、
対応する H2 セクションが存在しない。日本語版と構成を突き合わせて直す必要がある。

## 3. bash コードフェンス内のコメントが `##`（優先度：低）

`docs/usage.md:57,60`、`docs/development.md:81,84,87,96,99,100,115,118,121,124,378`、
および英語版の対応箇所で、シェルコメントが `#` ではなく `##` になっている。

シェルとしては `##` も有効なコメントなので**動作上の問題は無い**。見た目の問題のみ。
一括置換はコードフェンス外の Markdown 見出し `## ` を巻き込む危険があるため、
フェンス内に限定した置換スクリプトを書くか、手作業で直すこと。

## 4. `docs/narou-rs-setup.md` / `docs/en/narou-rs-setup.md` の検証環境（優先度：低）

「検証環境: … AozoraEpub3 v1.4.0-jdk21」（ja `:28` / en `:25`）と記載されている。
現在の最新は v1.6.0-jdk21。

**ただしこれは「その環境で検証した」という記録**なので、
バージョン番号だけを書き換えると虚偽の記録になる。
最新版で手順を再検証してから更新すること。再検証しないなら現状のままが正しい。

## 5. 英語の言い回し（優先度：低）

レビューで挙がった改善案。意味は通じるので急がない。

- `docs/en/usage.md:23` — "Complete guide for using AozoraEpub3 to convert Aozora Bunko format text files to EPUB 3 format."
  → "A complete guide to converting Aozora Bunko text files to EPUB 3 with AozoraEpub3."
- `docs/en/usage.md:43` — "Simply double-click the JAR file or run:" は
  `docs/en/index.md:125`（JAR のダブルクリックが効かない場合は EXE を使う）と矛盾する。
  → "Double-click `AozoraEpub3.exe`, or run:"
- `docs/en/usage.md:200-203` — 60 語の一文。"…working directory)." で分割する
- `docs/en/narou-setup.md:146` — "Downloading or updating Kakuyomu works fails with an error"
  → "Downloading or updating a Kakuyomu work fails with an error"
- `docs/en/narou-setup.md:37` — "the following known issues are reported by the community"
  → "…have been reported by the community"
- `docs/en/index.md:123` — "The GUI will open when ready" → "The GUI opens."
- `docs/en/index.md:24` — `<strong>Latest: </strong> v1.6.0-jdk21` に二重スペース

## 6. 相互リンクの追加（優先度：低）

- `docs/en/usage.md:568` の "External Characters (Gaiji)" セクションから
  `gaiji-settings.html` へのリンク
- `docs/index.md` / `docs/en/index.md` の関連ガイド一覧に「外字の設定」を追加

日本語版 `docs/usage.md` の外字セクションにも同様のリンクがあるとよい。

---

## 関連

- `docs/gaiji-fallback-plan.md` — 外字フォールバック機能の設計と検証
- `docs/web-site-support-status.md` — Web 小説サイトの対応状況（`docs/usage.md` から参照）
