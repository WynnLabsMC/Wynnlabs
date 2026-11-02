"""Export a patch of terrain from a Distant Horizons LOD database as a guide-route test fixture.

Offline study tool, not shipped and never run by the mod. It reads the DH SQLite database
(Distant_Horizons_server_data/<server>/<dimension>/DistantHorizons.sqlite, which WynnLODGrabber
fills with Wynncraft's terrain) read-only and writes the surface of an area as a text grid that
GuidePathTest loads. Needs Python 3.14+ (compression.zstd).

    python -I tools/dh_lod_export.py DB overview OUT.txt [detail]
    python -I tools/dh_lod_export.py DB grid X0 Z0 X1 Z1 OUT.txt

Format (DH core, FullDataSourceV2DTO, data format 2, compression mode 4 = one ZSTD frame per blob):
a section at LOD detail d covers 64x64 columns of 2^d blocks each, column index relX * 64 + relZ.
The main blob holds the inner 62x62 columns, the four *AdjData blobs the border rows. Each blob is
five passes over its columns: run counts, ids (id << 2 | light bit | discontinuity bit), heights,
mis-predicted bottom Ys (zigzag, predicted as the last run's bottom minus this run's height, so a
column's runs go top down), then light bytes. Mapping: an int count, then per id a modified-UTF
string "biome_DH-BSW_blockstate". Block Y = bottom + the row's MinY.

Overview output: a header "x0 z0 width depth", then one line per z, one token per column: the feet
height on its surface (the top of the highest run that is not air, a plant, leaves or a log), with a
suffix: w water, l lava, r a road block (as GuideLine.road), nothing for plain ground; "-" no data.
Grid output (the fixtures): the same header, then per column every run the player cannot walk
through, top down (see write_runs): a surface alone hides the paths under Wynncraft's arches,
bridges and overhangs.
"""

import sqlite3
import struct
import sys
from compression import zstd

WIDTH = 64
# barriers: Wynncraft's invisible ceiling at the top of the world and its invisible walls, not ground
AIR = ("air", "minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:barrier", "minecraft:light",
       "minecraft:structure_void")
# Wynncraft roofs the world with barriers from here up
CEILING = 250
SEP = "_DH-BSW_"
ROADS = ("dirt_path", "gravel", "cobblestone", "stone_bricks", "smooth_stone", "planks", "_slab", "_stairs")
FOLIAGE = ("leaves", "_log", "_wood", "grass", "fern", "flower", "bush", "vine", "sapling", "tulip", "poppy",
           "dandelion", "orchid", "allium", "bluet", "daisy", "cornflower", "lily_of", "rose", "peony", "lilac",
           "sugar_cane", "kelp", "seagrass", "torch", "carpet", "button", "pressure_plate", "rail",
           "sign", "banner", "lantern", "mushroom", "dead_bush", "wheat", "carrots", "potatoes", "beetroots",
           "cobweb", "string", "tripwire", "lily_pad", "snow")


class Stream:
    def __init__(self, data):
        self.b = data
        self.i = 0

    def byte(self):
        v = self.b[self.i]
        self.i += 1
        return v

    def varint(self):
        value = shift = 0
        while True:
            b = self.byte()
            value |= (b & 127) << shift
            shift += 7
            if not b & 128:
                return value

    def int(self):
        v = struct.unpack_from(">i", self.b, self.i)[0]
        self.i += 4
        return v

    def ushort(self):
        v = struct.unpack_from(">H", self.b, self.i)[0]
        self.i += 2
        return v


def zigzag(n):
    return (n >> 1) ^ -(n & 1)


def read_blob(blob, columns, box):
    """Fills columns[relX * 64 + relZ] with runs [id, bottom, height] (top down) for relX/relZ in box."""
    if not blob:
        return
    s = Stream(zstd.decompress(blob))
    x0, x1, z0, z1 = box
    order = [x * WIDTH + z for x in range(x0, x1) for z in range(z0, z1)]
    for i in order:
        columns[i] = [[0, 0, 0, 0, 0] for _ in range(s.varint())]
    for i in order:
        for run in columns[i]:
            e = s.varint()
            run[0], run[3], run[4] = e >> 2, e & 1, e & 2
    for i in order:
        for run in columns[i]:
            run[2] = s.varint()
    prev = 0
    for i in order:
        for run in columns[i]:
            err = zigzag(s.varint()) if run[3] else 0
            run[1] = prev - run[2] + err
            prev = run[1]
    for i in order:
        for run in columns[i]:
            if run[4]:
                s.byte()


def read_mapping(blob):
    s = Stream(zstd.decompress(blob))
    out = []
    for _ in range(s.int()):
        n = s.ushort()
        text = bytes(s.b[s.i:s.i + n]).decode("latin-1")
        s.i += n
        out.append(text.split(SEP, 1)[-1].lower())
    return out


def section(db, detail, px, pz):
    row = db.execute("select MinY, Data, NorthAdjData, SouthAdjData, EastAdjData, WestAdjData, Mapping, "
                     "CompressionMode, DataFormatVersion from FullData where DetailLevel=? and PosX=? and PosZ=?",
                     (detail, px, pz)).fetchone()
    if row is None:
        return None
    min_y, data, north, south, east, west, mapping, mode, fmt = row
    if mode != 4 or fmt != 2:
        raise SystemExit(f"unsupported section {px},{pz}: compression {mode}, format {fmt}")
    columns = [[] for _ in range(WIDTH * WIDTH)]
    read_blob(data, columns, (1, WIDTH - 1, 1, WIDTH - 1))
    read_blob(north, columns, (0, WIDTH, 0, 1))
    read_blob(south, columns, (0, WIDTH, WIDTH - 1, WIDTH))
    read_blob(east, columns, (WIDTH - 1, WIDTH, 0, WIDTH))
    read_blob(west, columns, (0, 1, 0, WIDTH))
    return min_y, columns, read_mapping(mapping)


def surface(runs, names, min_y):
    """The feet height on a column's surface and its suffix; None with no data."""
    best = None
    for run_id, bottom, height, _, _ in runs:
        name = names[run_id] if run_id < len(names) else "air"
        block = name.split("_state_")[0]
        if block in AIR or height == 0:
            continue
        liquid = block in ("minecraft:water", "minecraft:lava")
        if not liquid and any(f in block for f in FOLIAGE):
            continue
        top = bottom + height + min_y
        if best is None or top > best[0]:
            kind = "l" if block == "minecraft:lava" else "w" if liquid else "r" if any(r in block for r in ROADS) else ""
            best = (top, kind)
    return best


PASSABLE = ("grass", "fern", "flower", "bush", "sapling", "tulip", "poppy", "dandelion", "orchid", "allium",
            "bluet", "daisy", "cornflower", "lily_of", "rose", "peony", "lilac", "sugar_cane", "kelp", "seagrass",
            "torch", "carpet", "button", "pressure_plate", "rail", "sign", "banner", "lantern", "mushroom",
            "dead_bush", "wheat", "carrots", "potatoes", "beetroots", "cobweb", "string", "tripwire", "lily_pad",
            "snow", "vine", "ladder", "chain", "flower_pot", "candle", "door", "fence_gate", "trapdoor")


def runs(column, names, min_y, low):
    """The column's runs top down as (bottom, top, kind): solid ground (kind "" or "r" for a road block
    on top), water "w", lava "l"; air and what the player walks through (plants, carpets, doors)
    left out, touching solid runs merged, nothing below {low}."""
    out = []
    for run_id, bottom, height, _, _ in column:
        name = names[run_id] if run_id < len(names) else "air"
        block = name.split("_state_")[0]
        if block in AIR[:4] or height == 0 or any(p in block for p in PASSABLE):
            continue
        b, t = bottom + min_y, bottom + height + min_y
        if t <= low or block == "minecraft:barrier" and b >= CEILING:
            continue                                    # caves under the patch, Wynncraft's invisible roof
        b = max(b, low - 1)
        kind = "l" if block == "minecraft:lava" else "w" if block == "minecraft:water" else ""
        if not kind and any(r in block for r in ROADS):
            kind = "r"
        if out and kind in ("", "r") and out[-1][2] in ("", "r") and out[-1][0] == t:
            out[-1] = (b, out[-1][1], out[-1][2])      # under the run above: one run, its top's kind
        else:
            out.append((b, t, kind))
    return out


class Area:
    def __init__(self, db, detail=0):
        self.db = db
        self.detail = detail
        self.cache = {}

    def column(self, x, z):
        """(min_y, runs, names) of a column, None with no data."""
        size = WIDTH << self.detail
        key = (x // size, z // size)
        if key not in self.cache:
            self.cache[key] = section(self.db, self.detail, *key)
        sec = self.cache[key]
        if sec is None:
            return None
        min_y, columns, names = sec
        return min_y, columns[((x % size) >> self.detail) * WIDTH + ((z % size) >> self.detail)], names

    def at(self, x, z):
        size = WIDTH << self.detail
        key = (x // size, z // size)
        if key not in self.cache:
            self.cache[key] = section(self.db, self.detail, *key)
        sec = self.cache[key]
        if sec is None:
            return None
        min_y, columns, names = sec
        rx = (x % size) >> self.detail
        rz = (z % size) >> self.detail
        return surface(columns[rx * WIDTH + rz], names, min_y)


def main():
    db = sqlite3.connect("file:" + sys.argv[1].replace("%", "%25") + "?mode=ro", uri=True)
    if sys.argv[2] == "overview":
        # the whole map, one token per 2^detail blocks (default 16)
        detail = int(sys.argv[4]) if len(sys.argv) > 4 else 4
        rows = db.execute("select min(PosX), max(PosX), min(PosZ), max(PosZ) from FullData where DetailLevel=?",
                          (detail,)).fetchone()
        area = Area(db, detail)
        step = 1 << detail
        size = WIDTH << detail
        x0, x1, z0, z1 = rows[0] * size, (rows[1] + 1) * size, rows[2] * size, (rows[3] + 1) * size
        write(sys.argv[3], area, x0, z0, x1, z1, step)
    else:
        x0, z0, x1, z1 = map(int, sys.argv[3:7])
        write_runs(sys.argv[7], Area(db), x0, z0, x1, z1)


def write_runs(path, area, x0, z0, x1, z1):
    """Each column's runs, top down, as "bottom:top" plus a kind letter (w water, l lava, r a road
    block on top), joined by "/"; "-" with no data. Runs wholly below the lowest surface less 8 are
    left out (the caves under the patch), the lowest kept one cut there."""
    cols = {(x, z): area.column(x, z) for x in range(x0, x1) for z in range(z0, z1)}
    tops = [surface(c[1], c[2], c[0]) for c in cols.values() if c is not None]
    low = min(t[0] for t in tops if t is not None) - 8
    with open(path, "w", newline="\n") as out:
        out.write(f"{x0} {z0} {x1 - x0} {z1 - z0}\n")
        for z in range(z0, z1):
            tokens = []
            for x in range(x0, x1):
                c = cols[(x, z)]
                rs = None if c is None else runs(c[1], c[2], c[0], low)
                tokens.append("-" if not rs else "/".join(f"{b}:{t}{k}" for b, t, k in rs))
            out.write(" ".join(tokens) + "\n")


def write(path, area, x0, z0, x1, z1, step):
    with open(path, "w", newline="\n") as out:
        out.write(f"{x0} {z0} {(x1 - x0) // step} {(z1 - z0) // step}\n")
        for z in range(z0, z1, step):
            tokens = []
            for x in range(x0, x1, step):
                s = area.at(x, z)
                tokens.append("-" if s is None else f"{s[0]}{s[1]}")
            out.write(" ".join(tokens) + "\n")


if __name__ == "__main__":
    main()
