# AozoraEpub3 JDK21対応 初期リリース

## バージョン: jdk21-initial

**リリース日**: 2025年12月16日

### 概要
AozoraEpub3 を Java 21 と最新のツールチェーンに対応させました。EPUB 3.2 サポートを強化し、依存ライブラリを包括的に更新しています。

### 主な変更点

#### 🔧 ビルド・ツール
- **Gradle**: 9.2.1 にアップグレード
- **Java**: Java 21（JDK 21 LTS）でビルド・テスト実施
- **CI/CD**: GitHub Actions による自動ビルド・テスト・EPUB 検証
- **試験版**: CI パイプラインで Java 25 対応も評価中

#### 📦 依存ライブラリ更新
- **Apache Velocity**: 2.4.1（EPUB テンプレートレンダリング）
- **JSoup**: 1.18.1（HTML パース）
- **Apache Commons**: CLI 1.7.0 / Collections 4.5.0 / Compress 1.27.1 / Lang3 3.15.0
- **Batik**: 1.18（SVG 対応）
- **SLF4J**: 2.0.16
- **Junrar**: 7.5.5

#### 🎨 EPUB テンプレート・CSS 改善
- **外字フォント対応**: OPF マニフェストに外字フォント宣言を正しく注入
- **タイトル・カバーレイアウト**: Kindle・iOS 向けの CSS パディングと垂直配置を最適化
- **縦書き対応**: Kindle の writing-mode ディレクティブを改善（iOS の制限は既知の問題）
- **Package.vm**: 外字フォント項目をマニフェストに動的に生成

#### 🔄 Web 機能
- **レート制限**: デフォルト 1500ms（最小 1000ms）に設定、サーバー負荷に配慮
- **互換性**: narou.rb との連携を想定した Web 小説変換に対応
- **注意**: ncode.syosetu.com の HTML 構造が変わった場合、セレクタの更新が必要な可能性あり

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
- **ユニットテスト**: JUnit 4.13.2 の 5 テスト全て合格
- **ビルド**: Gradle 9.2.1 / Java 21 で `BUILD SUCCESSFUL` を確認
- **EPUB 検証**: CI/CD パイプラインで epubcheck 検証に対応

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
- **JDK 21 近代化・ライブラリ更新**: AozoraEpub3-JDK21 プロジェクト
- **連携想定**: narou.rb プロジェクト

### リンク
- **GitHub リポジトリ**: https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21
- **以前の安定版**: pre-jdk21 タグ（hmdev の最終リリース）
- **セットアップ・貢献**: DEVELOPMENT.md を参照

---

**まとめ**: このリリースは AozoraEpub3 を最新の Java 環境に対応させながら、hmdev の元のコードベースとの互換性を完全に保持しています。全テスト合格し、Java 21 での再現可能なビルドが実現されました。
