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
- `-PpreviewOnly=quests`: only the quests tab, on the fixture's quest book (Jungle Fever started).
- `-PpreviewOnly=settings`: only the settings (a few pages, a search, the changed list).
- `-PpreviewOnly=rail`: only one shot per rail tab (the page headers and section tabs).
- Another fixture: `-Dwynnlabs.previewFixture=synthetic` in the run's VM options
  (`fabric/build.gradle`, `loom.runs` → `clientGameTest`).
- English: `-Dwynnlabs.previewLang=en`.
- Item icons are plain vanilla items: Wynntils' own stacks need Wynntils, which the dev
  client does not load. Layout, text and numbers are the real ones.

The test is `fabric/src/gametest/java/io/github/anilken/wynnlabs/ui/JMenuPreview.java`.
