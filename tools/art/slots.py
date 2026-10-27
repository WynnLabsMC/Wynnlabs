"""Renders the item slot: kit/slot.png (a 38 x 38 nine-slice, border 3), the ready sizes
kit/slot_18/20/22/24.png (one quad each, for the bank's hundreds of slots) and kit/slot_ring_gold.png
(the picked slot's ring, a nine-slice drawn 2 px outside the slot).

The slot is a soft well in the page: a parchment a little darker than the page, a soft shadow inside
its top and left edges, one screen pixel of light along its bottom and right, 2 px round corners
and a faint edge. 6 texels a GUI px, like every kit sprite; drawn K times larger and scaled down.
Run from the repo root: python tools/art/slots.py
"""
import json
from PIL import Image, ImageDraw

KIT = 'common/src/main/resources/assets/wynnlabs/textures/gui/sprites/kit/'
T = 6                       # texels a GUI px
K = 4                       # drawn this much larger, then scaled down
SCREEN = 2                  # one screen pixel at GUI 3, in texels


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def well(size):
    n = size * T * K
    u = T * K                                   # one GUI px, drawn
    sp = SCREEN * K                             # one screen px, drawn
    r = 2 * u
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    top, bot = (0xAB, 0x92, 0x5F), (0xBF, 0xA8, 0x77)
    for y in range(n):
        d.line([(0, y), (n, y)], fill=lerp(top, bot, y / n) + (255,))
    mask = Image.new('L', (n, n), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, n - 1, n - 1], r, fill=255)
    im.putalpha(mask)
    # the soft shadow inside the top and left edges, 2 px deep
    sh = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sh)
    steps = 2 * u
    for i in range(steps):
        a = int(120 * (1 - i / steps) ** 1.5)
        sd.line([(0, i), (n, i)], fill=(0x4A, 0x38, 0x22, a))
        sd.line([(i, 0), (i, n)], fill=(0x4A, 0x38, 0x22, int(a * 0.8)))
    sh.putalpha(Image.composite(sh.getchannel('A'), Image.new('L', (n, n), 0), mask))
    im = Image.alpha_composite(im, sh)
    d = ImageDraw.Draw(im)
    light = (0xF6, 0xEB, 0xCB, 190)
    d.line([(r, n - sp), (n - r, n - sp)], fill=light, width=sp)
    d.line([(n - sp, r), (n - sp, n - r)], fill=light, width=sp)
    d.rounded_rectangle([0, 0, n - 1, n - 1], r, outline=(0x7C, 0x66, 0x44, 150), width=sp)
    return im.resize((size * T, size * T), Image.LANCZOS)


def ring(size):
    """The gold ring for a slot of size - 4, drawn 2 px outside it: a soft glow, a deep ring, a light line inside."""
    n = size * T * K
    u = T * K
    sp = SCREEN * K
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for i in range(u, 0, -1):                   # the glow: 1 px, fading outwards
        d.rounded_rectangle([u - i, u - i, n - 1 - u + i, n - 1 - u + i], 3 * u + i,
                            outline=(0xFF, 0xE0, 0x71, int(70 * (u - i + 1) / u)), width=K)
    d.rounded_rectangle([u, u, n - 1 - u, n - 1 - u], 3 * u, outline=(0xD9, 0xA9, 0x3A, 255), width=2 * sp)
    d.rounded_rectangle([u + 2 * sp, u + 2 * sp, n - 1 - u - 2 * sp, n - 1 - u - 2 * sp], 3 * u - 2 * sp,
                        outline=(0xFF, 0xE0, 0x71, 255), width=sp)
    return im.resize((size * T, size * T), Image.LANCZOS)


def nine(name, w, border):
    meta = {'gui': {'scaling': {'type': 'nine_slice', 'width': w, 'height': w, 'border': border, 'stretch_inner': True}}}
    open(KIT + name + '.png.mcmeta', 'w', newline='\n').write(json.dumps(meta, indent=2) + '\n')


well(38).save(KIT + 'slot.png')
nine('slot', 38, 3)
for s in (18, 20, 22, 24):
    well(s).save(KIT + 'slot_%d.png' % s)
ring(16).save(KIT + 'slot_ring_gold.png')
nine('slot_ring_gold', 16, 6)
print('ok')
