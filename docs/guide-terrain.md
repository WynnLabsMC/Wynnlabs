# Guide route and Wynncraft terrain: what the LOD data taught us

The guide line plans its walking route only over loaded chunks (`ui/GuidePath`, `ui/GuideLine`).
The mod never reads Distant Horizons or WynnLODGrabber. We only used their data offline, as a
source of real Wynncraft terrain to test and tune the route's rules against.

## Where the data comes from

- **WynnLODGrabber** downloads a ready-made Distant Horizons database of the whole Wynncraft map.
  It places it in the instance's `Distant_Horizons_server_data/<server>/<dimension>/DistantHorizons.sqlite`.
  It adds no terrain logic of its own: it is a download-and-install step for Distant Horizons.
- **Distant Horizons** keeps that database in its `FullData` table. Each row is one section at
  one LOD detail level. The detail level is the base-2 log of a column's width, so at level 0 a
  section is 64x64 single-block columns. Wynncraft's export has full block detail (level 0)
  everywhere, plus the coarser levels built from it.
- **Column format** (DH core `FullDataSourceV2DTO`, data format 2, ZSTD blobs):
  - Each column is a list of *runs* (block id, bottom Y, height, light) going from the top down,
    with no gaps: air is stored as runs too.
  - The main blob holds the inner 62x62 columns. The four `*AdjData` blobs hold the border rows.
  - Each blob is five passes over its columns: run counts, ids with flags, heights, the bottom Ys
    that the "previous bottom minus this height" guess gets wrong, and light.
  - The `Mapping` blob turns ids into `biome_DH-BSW_blockstate` strings.
  - `tools/dh_lod_export.py` decodes all of this with nothing but Python 3.14.
- **Top block and water:** DH keeps every block type, so the "top solid block" is our choice, not
  DH's. Water is an ordinary run with the `minecraft:water` state. Caves and overhangs are simply
  air runs between solid ones. DH's API (`getColumnDataAtBlockPos`) hands back the same runs.

## What applies to our route rules

1. **The ground is not the top of the column.** Wynncraft's hills are full of arches, bridges and
   overhangs. In the hill fixture, the highest floor over the start is an arch top 58 blocks above
   the path. A route model that uses only the heightmap (`MOTION_BLOCKING_NO_LEAVES`) would have
   walked on the arch. Our block-by-block `stand()` (feet room, then the ground under it) already
   handles this, so it stays as it is. The straight fallback line (`GuideLine.sample`) still uses
   the heightmap, but it only draws and never routes.
2. **Barriers.** Wynncraft roofs the world with barrier blocks at y 255. It also uses barriers as
   invisible walls. They block movement, and `blocksMotion()` already treats them as solid. A
   top-of-column reading has to skip the roof, or every column's surface would be 256.
3. **Plants and decoration.** Grass, flowers, carpets, tripwire (used a lot as decoration) and
   doors are runs the player walks through. Leaves and logs are not. This matches `passable()` in
   `GuideLine`.
4. **Roads.** On real terrain, slabs, stairs and planks (counted as road) cover 30 to 55% of a
   route's steps, partly because of buildings. A sweep of `GuidePath.ROAD` over the fixtures:

   | ROAD | coast road share / length | river bank | hill road |
   |------|---------------------------|------------|-----------|
   | 0.5  | 0.53 / 67                 | 0.54 / 99  | 0.60 / 61 |
   | 0.6  | 0.51 / 66                 | 0.51 / 99  | 0.55 / 60 |
   | 0.7  | 0.44 / 65                 | 0.32 / 94  | 0.55 / 60 |
   | 1.0  | 0.22 / 64                 | 0.31 / 94  | 0.44 / 59 |

   At 0.7 the road share is double that of no preference, with the same length. Below 0.7 the
   routes grow about 5% longer for a few more road steps. **0.7 stays.**
5. **Drops.** Jumping off a bluff (18 blocks on the coast fixture) is the way a player takes to
   the shore below, since Wynncraft has no fall damage. The cost per block of drop
   (`DROP_COST` 0.15) keeps stairs ahead of short jumps and still allows the cliff jump.
   **No change.**
6. **Water.** Rivers often run across a whole area with no bridge (the river fixture: 96 blocks).
   Without swimming the route stops on the near bank. That is the main reason routes end short,
   and the swimming rule being worked on in its own change addresses it. Once that lands, the
   river test's expectation should flip to "crosses".

## Fixtures

`fabric/src/test/resources/fixtures/guide-terrain/` has three 96x96 patches: `coast` at
(-1728, -2144), `river` at (-1952, -608) and `mountain` at (-1152, -1920). Each column lists its
non-walkthrough runs, top down. `GuideTerrainTest` walks them through `TerrainGrid` with the
game's rules.

To regenerate or add a patch, run (the database is opened read-only; with the game closed it is
not being written meanwhile):

```
python -I tools/dh_lod_export.py "<instance>/Distant_Horizons_server_data/play%2Ewynncraft%2Ecom/0000000000000@minecraft@@overworld/DistantHorizons.sqlite" grid X0 Z0 X1 Z1 out.txt
python -I tools/dh_lod_export.py "<db>" overview overview.txt 4   # the whole map, 16-block columns
```
