# narou.rs 出力の見た目比較（撮り直し手順）

`docs/narou-rs-setup-details.md#rendering`（英語版は `docs/en/narou-rs-setup-details.md#rendering`）に載せている
比較画像 `docs/assets/narou-rs/engine-compare/*.png` の作り方。narou.rs や AozoraEpub3 の更新で撮り直すときに使う。

2026-09-27 の撮影環境: Windows 11 / narou.rs v0.4.4（GPL 版）/ AozoraEpub3-JDK21 v1.6.1 / Thorium Reader 3.5.1 / Python 3.13 + Pillow。

## 1. 本文を用意する

青空文庫の「走れメロス」（パブリックドメイン）を取得し、UTF-8 に変換する。

```bash
curl -LO https://www.aozora.gr.jp/cards/000035/files/1567_ruby_4948.zip
unzip 1567_ruby_4948.zip
iconv -f CP932 -t UTF-8 hashire_merosu.txt > meros.txt
```

## 2. narou.rs の環境を 2 つ作る

**作業はリポジトリの外（一時フォルダ）で行う。** narou.rs の設定（`.narousetting`）は HOME 配下に作られるので、
`HOME` と `USERPROFILE` を環境ごとに分けて、実機の narou.rs の設定を汚さないようにする。

- `narou/` … narou.rs **GPL 版**の zip を展開したもの（2 つの環境で共用）
- `aep3/` … AozoraEpub3-JDK21 の配布 zip を展開したもの（`init -p` で構成ファイルが書き換わるので専用コピー）
- `lib-lite/` + `home-lite/` … AozoraEpub3 を**登録しない**環境（組み込みエンジンで変換される）
- `lib-java/` + `home-java/` … AozoraEpub3 を**登録する**環境（GPL 版でも登録済みの AozoraEpub3 が優先される）

```bash
# Git Bash の例。W は作業フォルダの Windows 形式パス
cd lib-lite && echo "" | HOME="$W\\home-lite" USERPROFILE="$W\\home-lite" ../narou/narou_rs.exe init
cd lib-java && HOME="$W\\home-java" USERPROFILE="$W\\home-java" ../narou/narou_rs.exe init -p "$W\\aep3"
```

## 3. 作品データを組み立てて変換する

```bash
for v in lite java; do
  python make_novel.py meros.txt lib-$v
  printf 'device: epub\n' > lib-$v/.narou/local_setting.yaml
  (cd lib-$v && HOME="$W\\home-$v" USERPROFILE="$W\\home-$v" ../narou/narou_rs.exe convert 0 < /dev/null)
done
```

- **`convert` は `< /dev/null` を付ける。** stdin を開いたままだと入力待ちで止まることがある
- `lib-java` の出力にだけ「AozoraEpub3でEPUBに変換しています..」が出ることで、エンジンの違いを確認できる
- 出力は `lib-*/小説データ/小説家になろう/n0000aa 走れメロス/[太宰治] 走れメロス.epub`

## 4. Thorium Reader で撮る

1. Thorium（`winget install EDRLab.Thorium`）で EPUB を 1 冊だけ開き、読書ウィンドウを **700×900** にする（既定の表示設定のまま）
2. 扉（開いた直後）→ `←` で目次 → `←` 2 回で章の中扉 → `←` で第 1 話、の順にページを送り、各ページで
   `python grab.py <名前> <撮影フォルダ>` を実行する。名前は `title-lite` / `toc-lite` / `chapter-lite` / `episode-lite`（AozoraEpub3 側は `-java`）
3. 読書ウィンドウを閉じ、もう 1 冊で同じことをする（同じ書名なので、2 冊を同時に開かない）
4. `python shrink.py <撮影フォルダ> docs/assets/narou-rs/engine-compare` で掲載用に縮小する

撮り直したら、補足ページの環境の記載（冒頭の注記と「見た目の比較」の箇条書き）と、違いの表を画像に合わせて更新する。
日本語版と英語版はセットで直す。
