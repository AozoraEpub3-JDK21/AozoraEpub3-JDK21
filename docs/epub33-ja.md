---
layout: default
lang: ja
title: EPUB 3.3 日本語解説
description: AozoraEpub3-JDK21 の EPUB 3.3 日本語解説ページ。EPUB 3.0 からの差分と対応状況を解説。
---

<nav style="background: #f5f5f5; padding: 12px; border-radius: 4px; margin-bottom: 24px;">
  <a href="index.html">🏠 ホーム</a> |
  <a href="usage.html">📖 使い方</a> |
  <a href="development.html">👨‍💻 開発者向け</a> |
  <strong>📚 EPUB 3.3</strong> |
  <a href="https://github.com/Harusame64/AozoraEpub3-JDK21">💻 GitHub</a>
  <div style="float: right;">🌐 <a href="en/epub33.html">English</a></div>
</nav>

# EPUB 3.3 日本語解説

AozoraEpub3-JDK21 による EPUB 3.3 対応の解説です。

---

## EPUB 3.3 とは

EPUB（Electronic Publication）3.3 は、国際的な電子書籍フォーマット標準です。

- **公式仕様**: [IDPF EPUB 3.3 Standard](https://www.w3.org/publishing/epub33/)
- **日本の参考**: [電書協 EPUB 3 制作ガイド](https://www.ebookjapan.jp/)

---

### セマンティクスの強化

EPUB 3.3 では、より詳細なメタデータと構造化情報が推奨されます。

- `epub:type` 属性の拡張（章・脚注・注記等の詳細化）
- HTML5 セマンティック要素の推奨（`<section>`, `<article>`, `<nav>` など）

### メディアクエリの改善

- CSS メディアクエリの対応強化
- 横書き・縦書き切り替えのサポート強化
- ダークモード対応の推奨

### アクセシビリティ向上

- ARIA ラベルの推奨拡大
- 画像の alt テキスト必須化
- 目次・標識の構造化強化

---

## AozoraEpub3-JDK21 の対応状況

### ✅ 実装済み

- 基本的な EPUB 3.3 フォーマット生成
- 電書協ガイドラインに準拠した CSS
- Ruby（ルビ）対応
- 縦書き・横書き自動判定

### 🚧 準備中

- メタデータの EPUB 3.3 対応詳細化
- アクセシビリティ属性の拡張
- より詳細な媒体別最適化

---

## 利用技術

- **言語**: Java 21 (LTS)
- **ビルド**: Gradle 8
- **検証**: epubcheck 5.x
- **テンプレートエンジン**: Apache Velocity

---

## 参考リソース

- [EPUB 3.3 仕様 (W3C)](https://www.w3.org/publishing/epub33/)
- [電書協 EPUB 3 制作ガイド](https://www.ebookjapan.jp/)
- [epubcheck プロジェクト](https://github.com/w3c/epubcheck)

---

## トップへ戻る

[インストールガイドに戻る](./){:.btn}
