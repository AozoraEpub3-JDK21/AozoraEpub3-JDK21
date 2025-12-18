# EPUB 3.3 日本語解説（EPUB 3.0との差分）

このページは、EPUB 3.3 の要点を日本語で簡潔にまとめ、EPUB 3.0 との主な違いを実務目線で紹介します。本プロジェクト（AozoraEpub3-JDK21）での対応方針や、主要端末の挙動、検証手順も併記します。

---

## 概要と位置づけ
- EPUB 3.3 は W3C 勧告であり、EPUB 3.0 系からの更新版です。
- 実務では「仕様準拠」と「端末互換」を両立する運用が重要です。

## 3.0 → 3.3 の主な差分（要点）
- `epub:type` の値や使い方の明確化（例: `titlepage` の正規値）
- `nav` ドキュメント（目次）の構造要件の整理（landmarks/toc）
- 仕様の曖昧箇所の明確化と、EPUBCheck の検査項目更新

## 互換性（端末事情）
- Kindle 系は階層 TOC の表示が不安定なことがあり、フラット TOC が無難
- Kobo / Apple Books などは仕様に忠実な出力で概ね問題なし

## 本プロジェクトの方針（要旨）
- EPUB 3.3 準拠（EPUB 3.2 後方互換）を満たすテンプレートに整理
- `landmarks` は最小構成（cover/toc/bodymatter）に寄せて非表示（UI重複回避）
- `epub:type` の値を正規化（例: `titlepage`）
- 目次（`nav`）は Kindle 互換のためフラット化（他端末は問題なし）
- パッケージの `version` は 3.0 を維持し、端末互換を優先（仕様準拠はテンプレートで担保）

## 実務の検証手順
1. EPUB を生成
2. EPUBCheckで検査（CI で自動化推奨）
3. 主要端末で表示確認（Kindle/Kobo/Apple Books）

## 本リポとの具体例（対応表）
- `template/OPS/xhtml/xhtml_nav.vm`:
  - `landmarks` 最小構成＋非表示
  - `epub:type="toc"` は Kindle 時のみ除外
  - `titlepage` の値を採用
  - フラットな `<ol>` の TOC を採用

## 最小サンプル（差分の例）
- 3.0 相当: `nav` の自由度が高く、端末ごとの挙動差が残る
- 3.3 準拠: `landmarks` と `toc` の使い分けを明確化、`epub:type` 正規化

> 注: ここでは要点のみを記載しています。仕様原典やEPUBCheckの更新履歴も適宜参照してください。

---

## 参考リンク
- W3C EPUB 3.3（仕様原典）
- EPUBCheck（検証ツール）
- 本プロジェクトの README / DEVELOPMENT
