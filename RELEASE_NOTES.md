# リリースノート - JDK21 初期リリース

## バージョン: jdk21-initial

**リリース日**: 2025年12月16日

### 概要
AozoraEpub3 を Java 21 と現代的なツールチェーンに完全に近代化しました。EPUB 3.2 サポートの向上と包括的な依存関係の更新が含まれています。

### 主な変更点

#### 🔧 ビルド・ツール
- **Gradle**: 9.2.1 へアップグレード
- **Java**: Java 21（JDK 21 LTS）でビルド・テスト
- **CI/CD**: GitHub Actions によるオートビルド・テスト・EPUB検証
- **オプション**: CI パイプラインで Java 25 評価版対応

#### 📦 依存関係の更新
- **Apache Velocity**: 2.4.1（EPUB テンプレートレンダリング）
- **JSoup**: 1.18.1（Web スクレイパーの HTML パース）
- **Apache Commons**: CLI 1.7.0、Collections 4.5.0、Compress 1.27.1、Lang3 3.15.0
- **Batik**: 1.18（SVG サポート）
- **SLF4J**: 2.0.16（ロギング）
- **Junrar**: 7.5.5（アーカイブ展開）

#### 🎨 EPUB テンプレート・CSS 改善
- **外字（gaiji）フォント対応**: OPF マニフェストへの含有を修正、Velocity コンテキスト経由でフォント宣言を適切に注入
- **タイトル・カバーレイアウト**: Kindle・iOS レンダラー向けの CSS パディングと垂直配置を改善
- **縦書き対応**: Kindle 向け writing-mode ディレクティブの強化（iOS 制限はドキュメント化済み）
- **Package.vm**: ループベースの外字フォント項目生成をマニフェストに追加

#### 🔄 Web 機能
- **レート制限**: デフォルト 1500ms（最小 1000ms）へ引き上げ、サーバー負荷に配慮
- **対応プラットフォーム**: narou.rb 互換の Web 小説変換
- **注意**: ncode.syosetu.com の HTML 構造が変わった可能性があり、セレクタの更新が必要な場合があります

#### 📝 ドキュメント
- **README.md**: 
  - プロジェクト出典を hmdev として記載
  - narou.rb 用途を記載
  - 実行環境・実行例を列記
  - 既知の問題: iPhone Kindle 縦書きレンダリングの差異
  - Web スクレイピングのレート制限と潜在的なセレクタ破損についての警告
- **DEVELOPMENT.md**: ツール版のアップデート、epubcheck 検証ステップ追加、Velocity 設定の明確化
- **THIRD-PARTY-NOTICES.txt**: 現在の依存関係版を反映
- **LICENSE.txt**: 派生版および JDK 21/25 近代化に関する記載

#### 🔐 セキュリティ・保守
- **Git 設定**: 匿名コミット著者対応（no-reply メール設定）
- **.gitignore**: シークレット漏洩対策強化（キー、証明書、.env、認証情報ディレクトリ）
- **Copilot 指示**: AI アシスタント向けのプロジェクト貢献ガイダンス追加

### テスト
- **ユニットテスト**: JUnit 4.13.2 の全 5 テスト合格
- **ビルド状態**: Gradle 9.2.1・Java 21 で `BUILD SUCCESSFUL`
- **EPUB 検証**: CI/CD パイプラインで epubcheck 検証対応済み

### 互換性
- **基本版**: hmdev/AozoraEpub3 の全機能・デバイスプリセットを継承
- **デバイスプリセット**: Kobo（Touch、Glo、Full）、Kindle Paperwhite、Reader（T3、標準）
- **入力形式**: 青空文庫テキスト（.txt、ルビ・圏点・外字・画像対応）
- **出力形式**: EPUB 3.2 準拠、電書協/電書連ガイド（denso-booken）対応

### 破壊的変更
なし。このリリースは既存の青空文庫テキスト入力とプリセットと完全に後方互換です。

### 既知の問題
- **iPhone Kindle**: 縦書きモードのタイトルページレンダリングに差異がある可能性があります（期待される動作として記載済み）
- **ncode.syosetu.com**: HTML 構造が変わった場合、`web/ncode.syosetu.com/extract.txt` のセレクタの更新が必要になる場合があります

### マイグレーションガイド
新しいビルドで JAR を置き換えるだけです：
```bash
./gradlew distZip
# build/distributions/ の生成された zip を使用
```

または直接ビルド・実行：
```bash
java -jar build/libs/AozoraEpub3.jar -of -d output/ input.txt
```

### 謝辞
- **オリジナル作成者**: hmdev
- **JDK 21 近代化・依存関係**: AozoraEpub3-JDK21 貢献者
- **アップストリーム**: narou.rb プロジェクトとの連携を想定

### リンク
- **GitHub リポジトリ**: https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21
- **前安定版**: pre-jdk21 タグ（hmdev の最終リリース）
- **ビルド**: DEVELOPMENT.md を参照してセットアップ・貢献ガイドラインを確認

---

**概要**: このリリースは AozoraEpub3 を現代的な Java エコシステムに導き入れ、オリジナルの hmdev コードベースとの互換性を保持しています。すべてのテストが合格し、Java 21 での再現可能なビルドが実現されました。
