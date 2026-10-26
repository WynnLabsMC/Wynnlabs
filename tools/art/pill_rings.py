"""Renders kit/pill_focus.png and kit/pill_error.png: the text input's focus and error rings.

The same 24x14 nine-slice as pill_thin (6 texels a GUI px), drawn over the field's pill: a firmer
edge in the ring's colour, a soft halo just inside it, and a faint cream lift of the inside.
Run from the repo root: python tools/art/pill_rings.py
"""
import json
from PIL import Image, ImageDraw

KIT = 'common/src/main/resources/assets/wynnlabs/textures/gui/sprites/kit/'
W, H, K = 144, 84, 4            # the sprite in texels, drawn K times larger and scaled down


def ring(edge, halo, name):
    big = Image.new('RGBA', (W * K, H * K), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    r = H * K // 2
    d.rounded_rectangle([0, 0, W * K - 1, H * K - 1], r, fill=(243, 230, 196, 46))        # the cream lift
    for i in range(6 * K, 0, -1):                                                        # the halo, fading inwards
        a = int(halo[3] * i / (6 * K))
        inset = 4 * K + (6 * K - i)
        d.rounded_rectangle([inset, inset, W * K - 1 - inset, H * K - 1 - inset], r - inset,
                            outline=halo[:3] + (a,), width=K)
    d.rounded_rectangle([0, 0, W * K - 1, H * K - 1], r, outline=edge, width=4 * K)      # the edge
    big.resize((W, H), Image.LANCZOS).save(KIT + name + '.png')
    meta = {'gui': {'scaling': {'type': 'nine_slice', 'width': 24, 'height': 14,
                                'border': {'left': 7, 'top': 6, 'right': 7, 'bottom': 6}, 'stretch_inner': True}}}
    open(KIT + name + '.png.mcmeta', 'w').write(json.dumps(meta, indent=2) + '\n')


ring((0x8A, 0x6A, 0x1C, 255), (0xD9, 0xA9, 0x3A, 150), 'pill_focus')
ring((0x93, 0x2C, 0x1D, 255), (0x93, 0x2C, 0x1D, 110), 'pill_error')
print('ok')
