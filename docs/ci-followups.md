# CI / PR 運用の follow-ups

CI ワークフローと PR 運用まわりの未対応事項と、事故の記録。

---

## 1. スタック PR で CI が起動しない（対応方針決定済み・未実施）

### 現象

`.github/workflows/ci.yml` と `test.yml` はいずれも

```yaml
on:
  pull_request:
    branches: [ master, develop, main ]
```

となっているため、**ベースが別のフィーチャーブランチである PR では 1 つも起動しない**。
チェックが赤にも緑にもならず「未実施」のまま素通りする。

### 対応方針（2026-08-08 ユーザー承認）

`pull_request.branches` に `'**'` を追加し、スタック PR でも自動で走るようにする。

```yaml
on:
  pull_request:
    branches: [ '**' ]
```

トレードオフ: CI 実行回数が増える。

**実施時の注意**: `.github/workflows/` を含む push は HTTPS の gh トークンでは拒否される
（`workflow` スコープ無し）。SSH remote に切り替える必要がある。

```bash
git push git@github.com:AozoraEpub3-JDK21/AozoraEpub3-JDK21.git <branch>
```

### 暫定の回避策

実施までは手動で明示実行する。

```bash
gh workflow run test.yml --ref <branch>
gh run view <run-id> --json conclusion,jobs
```

---

## 2. 事故記録: スタック PR が master に届かないままマージ扱いになる (2026-08-08)

### 何が起きたか

EPUB プレビューの作業で PR を 2 段に積んだ。

| PR | ベース | マージ時刻 |
|---|---|---|
| #57 `refactor/viewer-js-split` | `master` | 14:11:25 |
| #58 `feat/preview-reveal-folder` | **`refactor/viewer-js-split`** | 14:15:30 |

#57 が先に master へマージされ、その 4 分後に #58 が**親ブランチへ**マージされた。
結果、#58 の内容（`FileRevealer.java` ほか）は `refactor/viewer-js-split` に入っただけで
**master には一切届いていない**。

GitHub は「ベースブランチにマージされた」ので **#58 を MERGED と表示する**。
PR 一覧を見ても気付けない。

### 検知方法

マージ後に、内容が master に実在するかを確認する。

```bash
git log --oneline -5
ls src/com/github/hmdev/preview/FileRevealer.java   # 実ファイルの有無で見る
git log --oneline master..origin/<親ブランチ>        # 取り残されたコミット
```

### 復旧方法

master から切り直して当該コミットをチェリーピックし、master 宛ての PR を作る
（親ブランチをそのまま PR にすると、親が古い場合に無関係な差分が混ざる。
今回は #56 の jsoup 更新より前のブランチだったため `build.gradle` が差分に出た）。

```bash
git checkout -b <new-branch> origin/master
git cherry-pick <commit>
```

### 再発防止

- **スタック PR は「親 → 子」の順にマージし、親をマージしたら子のベースが
  master に付け替わったことを確認してからマージする**
- マージ後に内容が master に存在することを実ファイルで確認する
- 上記 1 の `'**'` 対応を入れると、少なくとも子 PR にも CI が走るようになり
  「未検証のままマージ」は防げる（ただしマージ先の取り違え自体は防げない）
