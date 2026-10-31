# Wynn Labs

An in-game assistant for [Wynncraft](https://wynncraft.com): a client-side mod for Minecraft
1.21.11 on Fabric or NeoForge. It reads your own character, bag and bank and tells you what to
wear, sell, level and do next.

**Read-only.** Wynn Labs never clicks, moves or casts for you. The one exception is the optional
**Refresh mount info** setting (off by default): while you ride, it asks the server for your held
mount now and then so the ride panel stays exact.

## Features

**In your tooltips**
- Wear, sell, identify or keep on every item, with the reason. Sells follow your own rules: the
  Trade Market minimum, your free listing slots and the lowest roll worth listing.
- Unidentified items and boxes: what can be inside, what each is worth, and whether to reveal, list
  or sell to the Blacksmith.
- Hover a badge or a game word for what it means.

**In the menu** (press **Y**)

| Tab | What it does |
|---|---|
| Home | Your build score, the best upgrade for each slot at every 10 levels up to the cap, and why |
| Today | Daily objectives and rewards for every character, session XP per hour |
| Ability Tree | The tree with the plan's order, an archetype picker, and a WynnBuilder link as the target build |
| Damage | Your damage, health and mana, and what +10 in each skill would change |
| Quests / Secret Discoveries | Step-by-step guides in English or Turkish, with waypoints |
| Search | Items, mobs, places, dungeons and quests; where to get them, tracked drops and a compass |
| Bank | Everything you store, searchable and filtered by what to sell, gear, quest and profession items |
| Crafting | Recipe odds, best layouts, and a planner to level each profession |
| Endgame | Raid and lootrun records, mythic dry streaks, aspects, this week's pools |
| Mounts | Your mounts' stats, a road map to train them, and where to go |
| Deaths | Where and why you die: above-level areas, burst damage, no pots, long fights |
| AI Assistant | The local API for Claude Code or any other assistant |
| Settings | Every feature can be switched off here |

**Keys:** **Y** opens the menu, **`** searches items (or the one you hover), **T** opens the box and
market window. Rebind them under Options › Controls.

## Install

Take the jar for your loader from the [latest release](https://github.com/WynnLabsMC/Wynnlabs/releases/latest)
and put it into `mods`:

- **Fabric:** [Fabric Loader](https://fabricmc.net) 0.18.4 or newer for 1.21.11, Fabric API, and
  [Wynntils](https://modrinth.com/mod/wynntils) 4.1.0 or newer.
- **NeoForge:** [NeoForge](https://neoforged.net) 21.11.48 or newer, and Wynntils for NeoForge 4.1.0 or newer.
- Optional: [Wynnventory](https://modrinth.com/mod/wynnventory).

The mod follows the game's language (English and Turkish). New versions can be downloaded from
inside the game: click the link in chat or use Settings › About.

## Privacy

The mod contacts only:

- `www.wynnventory.com`, when **Live prices** is on (you're asked once): listings and the weekly pools.
- `wynncraft.wiki.gg`, when **Quest guides from the wiki** is on: quest and discovery pages, cached for a week.
- `api.github.com` / `github.com`, when **Update reminder** is on: once per session to check for a
  release, and to download it when you ask.

Everything it remembers is under `config/wynnlabs/`. Delete it to start fresh.

## AI assistant and local API

Turn on the local API in the **AI Assistant** tab. It serves your game state as JSON on
`127.0.0.1:47591` (`GET /` lists the endpoints). **Install for Claude Code** copies the skill
([`ai/skills/wynnlabs/SKILL.md`](ai/skills/wynnlabs/SKILL.md)); **Copy prompt** works with any other
assistant. Writing (`POST /advice`, `/rules`, `/compass`) needs the **AI may write advice and rules**
switch and the header `X-Wynninv: 1`.

## Development

Java 21. Put the Wynntils Fabric jar into `libs/`, then:

```bash
gradle build -Penabled_platforms=fabric -x test   # the Fabric jar
gradle test                                       # quick tests, about half a minute
gradle test -Pheavy                               # adds the road map, crafting and mount searches
gradle test --tests '*RoadmapLocksTest'           # one class, whatever its tags
gradle build                                      # both loaders, every test and check
```

Many tests replay sessions recorded from the real game (`fabric/src/test/resources/fixtures/`);
approve an intended change with `-PupdateFixtures`. `-PreferenceBuilds` runs the build benchmarks
against community builds. `install-when-closed.sh` copies the built jar into your instance once
Minecraft is closed.

Commits and PR titles follow [Conventional Commits](https://www.conventionalcommits.org/); PRs are
squash-merged and the title sets the next version. Releases run from Actions › Release (tick
`dry_run` to preview the version and notes).

## License

Copyright (c) 2026 anilken. [GNU LGPL v3.0](LICENSE) (with the [GPL v3.0](COPYING) it builds on).
Not affiliated with Wynncraft, Wynntils, Wynnventory or WynnBuilder.

- [Noto Sans](https://github.com/notofonts/latin-greek-cyrillic), SIL OFL 1.1: `assets/wynnlabs/font/OFL.txt`.
- [Tabler Icons](https://tabler.io/icons), MIT: `assets/wynnlabs/textures/gui/sprites/icon/NOTICE.txt`.
- `assets/wynnlabs/trees/`: ability tree layout from `api.wynncraft.com/v3/ability/map/<class>`.
- `assets/wynnlabs/quests/` and `assets/wynnlabs/discoveries/`: adapted from the
  [Wynncraft Wiki](https://wynncraft.wiki.gg) under
  [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/); every entry links its page
  (see each folder's `NOTICE.txt`). Not covered by the LGPL, not for commercial use.
