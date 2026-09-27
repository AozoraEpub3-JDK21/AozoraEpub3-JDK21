"""青空文庫「走れメロス」（パブリックドメイン）を narou.rs の保存形式（小説家になろう）に組み立てる。

使い方: python make_novel.py <meros.txt（UTF-8）> <narou.rs の小説管理フォルダ>

通常の Web 小説と同じ経路（toc.yaml と 本文/*.yaml からの変換）で narou.rs に変換させるための検証用データ。
手順は README.md を参照。
"""
import html
import os
import re
import sys

src, lib = sys.argv[1], sys.argv[2]

lines = open(src, encoding='utf-8').read().splitlines()
# 記号説明（----- で囲まれた部分）の後から、底本情報の手前まで
seps = [i for i, l in enumerate(lines) if l.startswith('-----')]
body = lines[seps[1] + 1:]
end = next(i for i, l in enumerate(body) if l.startswith('底本'))
body = [l for l in body[:end] if l.strip() and '［＃' not in l]

KANJI = r'[一-龠々〆ヶ]'


def ruby(text):
    text = html.escape(text, quote=False)
    text = re.sub(r'｜([^｜《]+)《([^》]+)》',
                  r'<ruby>\1<rp>(</rp><rt>\2</rt><rp>)</rp></ruby>', text)
    text = re.sub(rf'({KANJI}+)《([^》]+)》',
                  r'<ruby>\1<rp>(</rp><rt>\2</rt><rp>)</rp></ruby>', text)
    return text


# 3 話に分割（2 章構成にして章の中扉も出す）
n = len(body)
cuts = [0, n // 5, n // 2, n]
episodes = [
    ('激怒', '一'),
    ('約束', '一'),
    ('疾走', '二'),
]

ncode = 'n0000aa'
title = '走れメロス'
author = '太宰治'
folder = os.path.join(lib, '小説データ', '小説家になろう', f'{ncode} {title}')
os.makedirs(os.path.join(folder, '本文'), exist_ok=True)

toc = [
    '---',
    f'title: {title}',
    f'author: {author}',
    f'toc_url: https://ncode.syosetu.com/{ncode}/',
    'story: |-',
    '  青空文庫の「走れメロス」（パブリックドメイン）を、表示比較のために 3 話に分けたサンプルです。',
    'subtitles:',
]
for no, ((sub, chap), a, b) in enumerate(zip(episodes, cuts, cuts[1:]), 1):
    meta = [
        f"index: '{no}'",
        f'href: /{ncode}/{no}/',
        # 実データと同じく、章の最初の話にだけ章名が入る
        f"chapter: '{chap}'" if no in (1, 3) else "chapter: ''",
        "subchapter: ''",
        f'subtitle: {sub}',
        f'file_subtitle: {sub}',
        'subdate: 2026/09/27 00:00',
        'download_time: 2026-09-27 00:00:00.000000000 +0000',
    ]
    toc.append('- ' + meta[0])
    toc.extend('  ' + m for m in meta[1:])
    ps = '\n'.join(f'    <p id="L{i}">{ruby(l)}</p>'
                   for i, l in enumerate(body[a:b], 1))
    section = ['---'] + meta + [
        'element:',
        '  data_type: html',
        "  introduction: ''",
        "  postscript: ''",
        '  body: |-',
        ps,
    ]
    with open(os.path.join(folder, '本文', f'{no} {sub}.yaml'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(section) + '\n')

with open(os.path.join(folder, 'toc.yaml'), 'w', encoding='utf-8', newline='\n') as f:
    f.write('\n'.join(toc) + '\n')

db = f"""---
0:
  id: 0
  author: {author}
  title: {title}
  file_title: {ncode} {title}
  toc_url: https://ncode.syosetu.com/{ncode}/
  sitename: 小説家になろう
  novel_type: 1
  end: true
  last_update: 2026-09-27 00:00:00.000000000 +09:00
  new_arrivals_date: 2026-09-27 00:00:00.000000000 +09:00
  use_subdirectory: false
  general_firstup: 2026-09-27 00:00:00.000000000 +09:00
  novelupdated_at: 2026-09-27 00:00:00.000000000 +09:00
  general_lastup: 2026-09-27 00:00:00.000000000 +09:00
  last_mail_date: null
  tags: []
  ncode: {ncode}
  domain: ncode.syosetu.com
  general_all_no: {len(episodes)}
  length: {sum(len(l) for l in body)}
  suspend: false
  is_narou: true
  last_check_date: null
"""
with open(os.path.join(lib, '.narou', 'database.yaml'), 'w', encoding='utf-8', newline='\n') as f:
    f.write(db)
print(folder, n, 'paragraphs')
