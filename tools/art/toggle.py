"""Renders the HD on/off switch: kit/toggle_off.png and kit/toggle_on.png (the track, a rounded pill with
an inner shade, 24 x 11 nine-slices with 6 px ends so the width can change), kit/toggle_hover.png
(a light wash over the track on hover) and kit/toggle_knob.png (a soft round cream thumb with a small
shadow, 11 x 11 with a 9 px circle centred on it, drawn on its own so it can slide). 6 texels a GUI px like every kit sprite, drawn K
times larger and scaled down. The switch is drawn 19 x 11 (the old size), the knob's circle 9 px, 1 px from the track's edges.
Run from the repo root: python tools/art/toggle.py [out dir]
"""
import json, sys
from PIL import Image, ImageDraw, ImageFilter

KIT = sys.argv[1] if len(sys.argv) > 1 else 'common/src/main/resources/assets/wynnlabs/textures/gui/sprites/kit/'
T = 6
K = 4
SCREEN = 2
W, H = 24, 11
BORDER = {'left': 6, 'right': 6, 'top': 5, 'bottom': 5}


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def track(top, bottom, edge):
    n_w, n_h = W * T * K, H * T * K
    u = T * K
    sp = SCREEN * K
    r = n_h // 2
    im = Image.new('RGBA', (n_w, n_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for y in range(n_h):
        d.line([(0, y), (n_w, y)], fill=lerp(top, bottom, y / n_h) + (255,))
    mask = Image.new('L', (n_w, n_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, n_w - 1, n_h - 1], r, fill=255)
    im.putalpha(mask)
    # the inner shade: darker under the top edge, 2 px deep
    sh = Image.new('RGBA', (n_w, n_h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sh)
    for i in range(2 * u):
        sd.line([(0, i), (n_w, i)], fill=(0x2A, 0x1C, 0x0C, int(80 * (1 - i / (2 * u)) ** 1.5)))
    sh.putalpha(Image.composite(sh.getchannel('A'), Image.new('L', (n_w, n_h), 0), mask))
    im = Image.alpha_composite(im, sh)
    ImageDraw.Draw(im).rounded_rectangle([0, 0, n_w - 1, n_h - 1], r, outline=edge + (200,), width=sp)
    return im.resize((W * T, H * T), Image.LANCZOS)


def hover():
    n_w, n_h = W * T * K, H * T * K
    im = Image.new('RGBA', (n_w, n_h), (255, 255, 255, 0))
    m = Image.new('L', (n_w, n_h), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, n_w - 1, n_h - 1], n_h // 2, fill=46)
    im.putalpha(m)
    return im.resize((W * T, H * T), Image.LANCZOS)


def knob():
    """11 x 11 (the track's height): a 9 px circle centred on it (1 px each side), its shadow hanging below."""
    s = 11
    n = s * T * K
    u = T * K
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    r0, r1 = u, n - u
    sh = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse([r0, r0 + int(0.7 * u), r1, r1 + int(0.7 * u)], fill=(0x30, 0x20, 0x0C, 100))
    sh = sh.filter(ImageFilter.GaussianBlur(0.6 * u))
    im = Image.alpha_composite(im, sh)
    body = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    bd = ImageDraw.Draw(body)
    for y in range(r0, r1):
        bd.line([(r0, y), (r1, y)], fill=lerp((0xFF, 0xFB, 0xEE), (0xEC, 0xDF, 0xC0), (y - r0) / (r1 - r0)) + (255,))
    m = Image.new('L', (n, n), 0)
    ImageDraw.Draw(m).ellipse([r0, r0, r1 - 1, r1 - 1], fill=255)
    body.putalpha(m)
    ImageDraw.Draw(body).ellipse([r0, r0, r1 - 1, r1 - 1], outline=(0x8C, 0x74, 0x4C, 170), width=SCREEN * K)
    im = Image.alpha_composite(im, body)
    return im.resize((s * T, s * T), Image.LANCZOS)


def nine(name):
    meta = {'gui': {'scaling': {'type': 'nine_slice', 'width': W, 'height': H, 'border': BORDER, 'stretch_inner': True}}}
    open(KIT + name + '.png.mcmeta', 'w', newline='\n').write(json.dumps(meta, indent=2) + '\n')


OFF = track((0xA8, 0x92, 0x66), (0xBC, 0xA6, 0x78), (0x7C, 0x66, 0x44))
ON = track((0x2E, 0x6B, 0x1E), (0x46, 0x86, 0x2C), (0x1E, 0x4A, 0x12))
OFF.save(KIT + 'toggle_off.png')
nine('toggle_off')
ON.save(KIT + 'toggle_on.png')
nine('toggle_on')
hover().save(KIT + 'toggle_hover.png')
nine('toggle_hover')
knob().save(KIT + 'toggle_knob.png')
