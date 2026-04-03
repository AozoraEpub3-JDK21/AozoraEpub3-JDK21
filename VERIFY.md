# 配布物の検証方法

このドキュメントでは、AozoraEpub3 のリリース配布物が正規のものであることを検証する方法を説明します。

## 1. SHA-256 チェックサムの検証

各リリースには `SHA256SUMS` ファイルが添付されています。

### Windows (PowerShell)

```powershell
# チェックサム計算
Get-FileHash AozoraEpub3-*.zip -Algorithm SHA256

# SHA256SUMS ファイルの内容と比較
Get-Content SHA256SUMS
```

### Linux / macOS

```bash
# チェックサム検証（自動比較）
sha256sum -c SHA256SUMS

# 手動で確認
sha256sum AozoraEpub3-*.tar.gz
cat SHA256SUMS
```

出力されたハッシュ値が `SHA256SUMS` ファイルの値と一致すれば、ファイルは改ざんされていません。

## 2. GitHub Artifact Attestation の検証

リリース配布物は GitHub Actions で自動ビルドされ、来歴証明（artifact attestation）が付与されています。

### GitHub CLI での検証

```bash
# gh CLI が必要です: https://cli.github.com/
gh attestation verify AozoraEpub3-*.zip \
  --owner AozoraEpub3-JDK21
```

これにより、以下が確認できます:
- 配布物が GitHub Actions でビルドされたこと
- ビルド元のリポジトリとコミットが正しいこと
- ビルド後に改ざんされていないこと

## 3. タグ署名の検証

リリースタグには GPG/SSH 署名が付与されています。

```bash
# タグの署名を検証
git tag -v v1.3.2-jdk21
```

GitHub 上では、署名済みタグに "Verified" バッジが表示されます。

## 4. 正規配布元の確認

| 項目 | 値 |
|------|-----|
| 正規リポジトリ | [AozoraEpub3-JDK21/AozoraEpub3-JDK21](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21) |
| 正規配布先 | [GitHub Releases](https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21/releases) のみ |
| ビルド方法 | GitHub Actions による自動ビルド |

> **注意**: 上記以外の配布元（個人サイト、ファイル共有サービス等）から入手したファイルは正規版とは限りません。
> フォークや改変版が存在する場合がありますが、それらはこのプロジェクトのサポート対象外です。
