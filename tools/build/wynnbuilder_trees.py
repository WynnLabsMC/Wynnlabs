"""Refreshes the tables a WynnBuilder build link's ability tree and aspects are read with.

After the items, a link holds the aspects (by WynnBuilder's aspect id) and the ability tree as one
bit per node, in the order a walk of that data version's tree visits the nodes. To read them the
mod ships, per class and data version, taken from the data files the WynnBuilder site loads:

  - each tree's nodes in WynnBuilder's order: the node's name as the game writes it, its
    archetype (both as places in the class's name and archetype lists) and its parents (as
    places in the node list);
  - the aspect names by id.

Trees and aspect lists that did not change between versions are kept once. Only names,
archetypes and links are kept; nothing else of their data is shipped.

    common/src/main/resources/assets/wynnlabs/build/wynnbuilder-trees.json

Run it with wynnbuilder_items.py after WynnBuilder adds a game version, then rebuild:

    python tools/build/wynnbuilder_trees.py [--data <a local copy of the site's data/ folder>]
"""
import argparse
import datetime
import json
import pathlib
import re
import urllib.request

SITE = "https://wynnbuilder.github.io/"
OUT = pathlib.Path(__file__).resolve().parents[2] / "common/src/main/resources/assets/wynnlabs/build/wynnbuilder-trees.json"
AGENT = "WynnLabs tools (https://github.com/anilken/wynnlabs)"
CLASSES = ["Archer", "Warrior", "Mage", "Assassin", "Shaman"]
ROMAN = {"1": "I", "2": "II", "3": "III", "4": "IV"}
# WynnBuilder names the game writes differently
ALIASES = {
    "Nightcloak Knife": "Nightcloak Knives",
    "Bow Proficiency I": "Bow Proficiency",
    "More Focus I": "More Focus",
}


def game_name(name):
    """WynnBuilder's node name as the game writes it: Roman tier numbers, no "I" on a first "Cheaper"."""
    name = name.strip()
    name = re.sub(r" \((\d)\)$", lambda m: " " + ROMAN[m.group(1)], name)
    name = re.sub(r" (\d)$", lambda m: " " + ROMAN[m.group(1)], name)
    name = ALIASES.get(name, name)
    return re.sub(r"^(Cheaper .+) I$", r"\1", name)


def loader(data):
    def fetch(path):
        if data:
            return json.loads((pathlib.Path(data) / path).read_text(encoding="utf-8"))
        req = urllib.request.Request(SITE + "data/" + path, headers={"User-Agent": AGENT})
        with urllib.request.urlopen(req, timeout=120) as r:
            return json.loads(r.read().decode("utf-8"))
    return fetch


def index(table, value):
    if value not in table:
        table.append(value)
    return table.index(value)


def tree(raw, cls_table):
    """[name, archetype, parent places...] per node in WynnBuilder's order, names and archetypes by place in the class's tables."""
    place = {n["id"]: i for i, n in enumerate(raw)}
    return [[index(cls_table["names"], game_name(n["display_name"])), index(cls_table["archetypes"], n.get("archetype") or "")]
            + [place[p] for p in n["parents"]] for n in raw]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", help="a local copy of the site's data/ folder")
    args = ap.parse_args()
    fetch = loader(args.data)
    items = json.loads((OUT.parent / "wynnbuilder-items.json").read_text(encoding="utf-8"))
    versions = [v["name"] for v in items["versions"]]
    trees = {c: {"names": [], "archetypes": [""], "trees": []} for c in CLASSES}
    aspects = {c: [] for c in CLASSES}
    by_version = []
    for v in versions:
        c = fetch(f"{v}/encoding_consts.json")
        at = fetch(f"{v}/atree.json")
        asp = fetch(f"{v}/aspects.json")
        row = {"name": v, "aspectBits": c["ASPECT_ID_BITLEN"], "aspectTierBits": c["ASPECT_TIER_BITLEN"],
               "aspectSlots": c["NUM_ASPECTS"], "tree": {}, "aspects": {}}
        if c["ASPECTS_FLAG"]["BITLEN"] != 1 or c["ASPECT_SLOT_FLAG"]["BITLEN"] != 1 or c["ASPECTS_FLAG"]["HAS_ASPECTS"] != 1 \
                or c["ASPECT_SLOT_FLAG"]["USED"] != 1:
            raise SystemExit(f"{v}: the aspect flags changed")
        for cls in CLASSES:
            t = tree(at[cls], trees[cls])
            if sum(1 for n in t if len(n) == 2) != 1:
                raise SystemExit(f"{v} {cls}: not one root")
            row["tree"][cls] = index(trees[cls]["trees"], t)
            names = {}
            for a in asp.get(cls, []):
                names[a["id"]] = a["displayName"]
            top = max(names) if names else -1
            listed = [names.get(i) for i in range(top + 1)]
            if listed not in aspects[cls]:
                aspects[cls].append(listed)
            row["aspects"][cls] = aspects[cls].index(listed)
        by_version.append(row)
    OUT.write_text(json.dumps({
        "format": 1,
        "source": SITE,
        "fetched": datetime.date.today().isoformat(),
        "versions": by_version,
        "trees": trees,
        "aspects": aspects,
    }, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"{len(versions)} versions, trees {[len(trees[c]['trees']) for c in CLASSES]}, aspect lists {[len(aspects[c]) for c in CLASSES]} -> {OUT}")


if __name__ == "__main__":
    main()
