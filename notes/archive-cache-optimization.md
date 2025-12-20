# アーカイブキャッシュ最適化

## 問題
以前は、1つのアーカイブファイル（zip/rar）を変換する際に、以下の処理で合計4回アーカイブを開いていました：

1. **テキストファイル数のカウント** (`countZipText`/`countRarText`)
2. **書誌情報の取得** (`getBookInfo` → `getTextInputStream`)
3. **画像リストの読み込み** (`loadZipImageInfos`/`loadRarImageInfos`)
4. **実際の変換処理** (`convertFile` → `getTextInputStream`)

大きなアーカイブ（特に数百MBの画像付きzipなど）では、このスキャン処理が非常に時間がかかっていました。

## 解決策
**ArchiveCache**を導入し、アーカイブを1回だけスキャンして以下の情報をメモリにキャッシュ：

- テキストファイルの数
- テキストファイルの内容（byte配列として保持）
- 画像ファイルのエントリ名リスト

### メモリ使用量
2GBのアーカイブファイルでも、キャッシュに必要なメモリは：

- **テキスト内容**: 通常1～10MB（小説1ファイル分）
- **画像エントリリスト**: 数千ファイルでも数MB以内
- **合計**: 実用的には10～20MB程度

## 実装詳細

### 新規クラス
- **ArchiveCache**: アーカイブのスキャン結果をキャッシュ
- **ArchiveScanner**: zip/rarをスキャンしてテキスト・画像情報を抽出

### 変更されたクラス
- **ArchiveTextExtractor**: キャッシュを使用してカウント・ストリーム取得
- **ImageInfoReader**: キャッシュから画像リストを取得
- **AozoraEpub3**: 変換完了後にキャッシュをクリア（メモリ解放）

## 期待される効果
- **高速化**: アーカイブスキャンが4回→1回になり、大幅に高速化
- **特に効果的なケース**:
  - 大容量のzipファイル（100MB以上）
  - 多数の画像を含むアーカイブ
  - rarファイル（圧縮展開が遅いため）

## 使用例
```java
// 内部で自動的にキャッシュが使用されます
int count = ArchiveTextExtractor.countZipText(zipFile);
InputStream is = ArchiveTextExtractor.getTextInputStream(zipFile, "zip", reader, null, 0);
imageInfoReader.loadZipImageInfos(zipFile, true);

// 変換完了後、メモリを解放
ArchiveTextExtractor.clearCache(zipFile);
```

## 注意事項
- txtファイルはキャッシュされないため、メモリ使用量への影響は最小限
- 各ファイル変換後に`clearCache()`を呼び出してメモリを解放
- 複数ファイルの連続変換でも、1ファイルずつキャッシュをクリア
