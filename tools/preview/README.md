# J menu preview

Draws the J menu without the game: a dev Minecraft client (no account, no server, no
Wynntils) loads the recorded Shaman from `fabric/src/test/resources/fixtures/shaman-44`, opens the
menu in a throwaway world and saves one screenshot per tab, then closes.

    gradle runClientGameTest

The pictures land in `build/preview/`: `jmenu-now.png`, then `jmenu-step1.png` … one per
milestone, `jmenu-tab-<n>.png` for every rail tab and `jmenu-tree-end.png` for the ability
tree scrolled to its last page. A run takes about half a minute and opens a window while it works.

- The window is 3440x1440 on GUI scale 3 (about 1146x480 logical: the menu's large layout, as
  the game is usually played here). Another size: `-PpreviewSize=1920x1080` (640x360 logical,
  the small layout). The layout matrix (`-PpreviewOnly=layout`) keeps its own list of sizes.
- `-PpreviewOnly=home`: only the Home tab (now, out of date, the options view).
- `-PpreviewOnly=crafting`: only the crafting tab's shots.
- `-PpreviewOnly=craftsuggest`: only the crafting tab's Suggestions page (cards, a picked card, a status filter, the set page).
- `-PpreviewOnly=quests`: only the quests tab, on the fixture's quest book (Jungle Fever started), plus the content kinds
  (the kind menu, a cave, Recommended with places) from a small built-in sample of caves and places.
- `-PpreviewOnly=guide`: the quest guide window (Jungle Fever) on the recorded Shaman.
- `-PpreviewOnly=about`: the About page (the rail's "?") when up to date, with an update, offline, downloading, ready
  and after a failed install, and the update window (notes, changelog, downloading, ready); states given by hand.
- `-PpreviewOnly=settings`: only the settings (a few pages, a search, the changed list).
- `-PpreviewOnly=tree`: only the tree page (three deaths seeded), its damage and deaths drawers, and its search typed in.
- The game's window stays hidden during a run (the shots are drawn the same); `-PpreviewHidden=false`
  shows it, to watch a run.
- `-PpreviewOnly=ai`: the AI assistant page (Settings › AI) in its four states: off, on and never asked, connected
  (a real request to the local API) with writing on, and the port busy.
- `-PpreviewOnly=build`: the build window on Home (pick, WynnBuilder import, loading, results, saved, rename, edit) and the road
  states, at the preview size only.
- `-PpreviewOnly=wbexport`: "Export to WynnBuilder" on Home (at rest, hovered, copied, failed) and in the build window
  (Choose items, Results with its tooltip and copied state, Saved faded, Edit), at 3440x1440 and 1380x960 in one run;
  shots in `preview/wbexport/`.
- `-PpreviewOnly=egalts`: an endgame milestone's other builds on Home: the "Build 1/N" chip closed, its menu open,
  the second card picked (menu open and closed) and that card's export copied, at 3440x1440 and 1380x960 in one run;
  shots in `preview/egalts/`.
- `-PpreviewOnly=search`: only that tab: the catalogue before typing, an item, its marks list, a tracked mob and
  the tracked list, a place, the Stats category and a stat, nothing found ("did you mean") and the filter menu.
  With `-PpreviewSize=1380x960` the card shows in its drawer.
- `-PpreviewOnly=bank`: the bank tab on the fixture's bank: whole, searched, a kind chip, the banks menu,
  a slot's drawer, the list to sell, a bank read in part and one not read (`BankPreview`).
- `-PpreviewOnly=mounts`: the Mounts tab on four seeded mounts: the road map (~700, its first step opened, the target menu, 1200+, the
  options menu), the exact feed plan not run / running / calculated / stale (road map and card), the mounts list, the type menu,
  the places and the empty page (`MountsPreview`).
- `-PpreviewOnly=feeder`: the Mount Feeder marks over a stand-in feeder (a chest titled "Mount Feeder"): the marks,
  the mouse on row 2 (its card) and row 4's card kept open (`FeederPreview`).
- `-PpreviewOnly=ride`: the mount panel on the HUD, forced on without riding: a Wyvern at rest, Speed +1 / Handling +2 /
  Boost −1 showing, a Horse with unknown maxes (Jump Height) and the move screen (`RidePreview`).
- `-PpreviewOnly=powerups`: the powerup labels drawn by the world pass, one of each powerup in front of the player
  (no models), for a Wyvern with three maxed stats (dimmed) and a Horse (Jump Height) (`PowerupPreview`).
- `-PpreviewOnly=rail`: only one shot per rail tab (the page headers and section tabs).
- Another fixture: `-Dwynnlabs.previewFixture=synthetic` in the run's VM options
  (`fabric/build.gradle`, `loom.runs` → `clientGameTest`).
- English: `-Dwynnlabs.previewLang=en`.
- Item icons are plain vanilla items: Wynntils' own stacks need Wynntils, which the dev
  client does not load. Layout, text and numbers are the real ones.

The test is `fabric/src/gametest/java/io/github/anilken/wynnlabs/ui/JMenuPreview.java`.
