"""grab.py で撮った PNG を、ドキュメント掲載用に幅 480px・64 色へ縮小する。

使い方: python shrink.py <撮影フォルダ> <出力フォルダ（docs/assets/narou-rs/engine-compare）>
"""
import glob
import os
import sys

from PIL import Image

src, dst = sys.argv[1], sys.argv[2]
os.makedirs(dst, exist_ok=True)
for p in sorted(glob.glob(os.path.join(src, '*.png'))):
    im = Image.open(p).convert('RGB')
    w = 480
    im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
    out = os.path.join(dst, os.path.basename(p))
    im.quantize(colors=64, method=Image.Quantize.MEDIANCUT).save(out, optimize=True)
    print(out, im.size, os.path.getsize(out))
