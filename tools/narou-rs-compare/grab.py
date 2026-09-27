"""Thorium Reader の本文エリア（上のツールバーと下のスクロールバーを除く）を PNG で切り出す（Windows 専用）。

使い方: python grab.py <出力ファイル名（拡張子なし）> [出力フォルダ]
要 Pillow。手順は README.md を参照。
"""
import ctypes
import os
import sys
from ctypes import wintypes

from PIL import ImageGrab

ctypes.windll.user32.SetProcessDPIAware()
found = []


@ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
def enum(hwnd, _):
    buf = ctypes.create_unicode_buffer(256)
    ctypes.windll.user32.GetWindowTextW(hwnd, buf, 256)
    if buf.value.startswith('Thorium - ') and ctypes.windll.user32.IsWindowVisible(hwnd):
        found.append(hwnd)
    return True


ctypes.windll.user32.EnumWindows(enum, 0)
if len(found) != 1:
    sys.exit(f'Thorium の読書ウィンドウがちょうど 1 つ開いている必要があります（{len(found)} 個）')
rect = wintypes.RECT()
pt = wintypes.POINT(0, 0)
ctypes.windll.user32.GetClientRect(found[0], ctypes.byref(rect))
ctypes.windll.user32.ClientToScreen(found[0], ctypes.byref(pt))
# Thorium 3.5.1 の上ツールバー（約 86px）と下のスクロールバー（約 58px）を除く
TOP, BOTTOM = 86, 58
box = (pt.x, pt.y + TOP, pt.x + rect.right, pt.y + rect.bottom - BOTTOM)
outdir = sys.argv[2] if len(sys.argv) > 2 else 'shots'
os.makedirs(outdir, exist_ok=True)
out = os.path.join(outdir, sys.argv[1] + '.png')
ImageGrab.grab(bbox=box).save(out)
print(out, box)
