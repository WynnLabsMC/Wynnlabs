"""Renders the kind badge's tile (a town, a service, a mob ...): kit/tile.png, a white rounded square
tinted in code with the kind's light colour; kit/tile_shine.png, drawn over it untinted, a soft
light along the top; kit/tile_ring.png, one screen pixel of ring tinted with the kind's colour. All are
24 x 24 nine-slices (border 5: the 3 px corner and its edge), 6 texels a GUI px like every kit
sprite, drawn K times larger and scaled down so the corners are smooth.
Run from the repo root: python tools/art/tiles.py
"""
import json
from PIL import Image, ImageDraw

KIT = 'common/src/main/resources/assets/wynnlabs/textures/gui/sprites/kit/'
T = 6                       # texels a GUI px
K = 4                       # drawn this much larger, then scaled down
SCREEN = 2                  # one screen pixel at GUI 3, in texels
SIZE = 24
BORDER = 5
RADIUS = 3                  # GUI px


def mask(n, r):
    m = Image.new('L', (n, n), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, n - 1, n - 1], r, fill=255)
    return m


def tile():
    n = SIZE * T * K
    im = Image.new('RGBA', (n, n), (255, 255, 255, 0))
    im.putalpha(mask(n, RADIUS * T * K))
    return im.resize((SIZE * T, SIZE * T), Image.LANCZOS)


def shine():
    n = SIZE * T * K
    u = T * K
    sp = SCREEN * K
    r = RADIUS * u
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # a soft light under the top edge, fading over 3 px
    for i in range(3 * u):
        a = int(70 * (1 - i / (3 * u)) ** 1.6)
        d.line([(0, sp + i), (n, sp + i)], fill=(255, 255, 255, a))
    im.putalpha(Image.composite(im.getchannel('A'), Image.new('L', (n, n), 0), mask(n, r)))
    return im.resize((SIZE * T, SIZE * T), Image.LANCZOS)


def ring():
    n = SIZE * T * K
    sp = SCREEN * K
    im = Image.new('RGBA', (n, n), (255, 255, 255, 0))
    ImageDraw.Draw(im).rounded_rectangle([0, 0, n - 1, n - 1], RADIUS * T * K, outline=(255, 255, 255, 255), width=sp)
    return im.resize((SIZE * T, SIZE * T), Image.LANCZOS)


def nine(name):
    meta = {'gui': {'scaling': {'type': 'nine_slice', 'width': SIZE, 'height': SIZE, 'border': BORDER, 'stretch_inner': True}}}
    open(KIT + name + '.png.mcmeta', 'w', newline='\n').write(json.dumps(meta, indent=2) + '\n')


tile().save(KIT + 'tile.png')
nine('tile')
shine().save(KIT + 'tile_shine.png')
nine('tile_shine')
ring().save(KIT + 'tile_ring.png')
nine('tile_ring')
