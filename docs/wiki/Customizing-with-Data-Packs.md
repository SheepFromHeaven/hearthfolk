# Customizing with Data Packs

Almost everything about prices, recipes, names and what counts as what is plain JSON under the `minenow` namespace. You change it with a normal data pack: put your file at the same path and it replaces the mod's file, or add a new file where the folder merges everything. Run `/reload` in game to apply it. No restart is needed.

A data pack lives in your world's `datapacks` folder (or in a resource folder of a mod). Its files go under `data/minenow/...`. The mod's own files are in the jar under the same paths, which is the easiest place to copy an example from.

Prices everywhere are one decimal number of emeralds per item. `0.05` means 20 for an emerald, `3` means one item costs three emeralds. The mod turns it into the smallest whole-emerald deal.

## Trade files: `data/minenow/villager_trades/<profession>.json`

One file per profession. Replacing a file replaces the whole profession, so copy the original and edit it.

```json
{
  "profession": "minecraft:farmer",
  "tool_tag": "#minecraft:hoes",
  "tools": [ { "item": "minecraft:wooden_hoe", "price": 1, "level": 1 } ],
  "produce": [ { "item": "minecraft:wheat", "level": 1, "price": 0.05 } ],
  "default_produce": { "items": "#c:crops", "price": 0.05 },
  "supplies": [ { "item": "minecraft:wheat_seeds", "price": 0.0625, "max": 16, "level": 1 } ],
  "keeps": [ { "items": "#c:foods/raw_meat", "max": 64 } ],
  "crafts": [
    {
      "item": "minecraft:stone_hoe",
      "materials": [ { "item": "minecraft:cobblestone", "count": 2 }, { "item": "minecraft:stick", "count": 2 } ],
      "labor": 0.5,
      "level": 1
    }
  ]
}
```

- `tool_tag` decides what counts as the villager's tool, so modded tools work.
- `tools` are bought when the villager has none, listed worst to best. `level` is the level that unlocks it. Better tools are also bought later as upgrades.
- `produce` is what it sells and at what `price`. `default_produce` prices anything in a tag that has no `produce` entry.
- `supplies` are bought at their `price` while the villager holds less than one batch, up to `max`.
- `keeps` lists goods it makes and works on further. They are kept up to `max` and never bought.
- `crafts` are made at its workstation from `materials`. Optional fields: `count` (items per craft, default 1), `tool_uses` (wear on its tool, default 0), `stock` (how many to keep), `labor` (decimal emeralds) and `enchants: true` for a librarian's book. The price is the materials plus labor plus tool wear. A `produce` entry for the same item overrides it.
- A level unlocks its entries and keeps the earlier ones. Several entries for one item at different levels: the latest one is the recipe. Several at the same level are alternatives.

## Other data

| Path | What it does |
| --- | --- |
| `villager_names/*.json` | `{ "names": [ ... ] }`. All files are merged. Add a file for more names. |
| `wealth/breeding.json` | `base`, `per_villager`, `minimum` and `maximum`: the emeralds a villager needs before buying breeding food. Default 3 plus 1 per villager, between 4 and 40. |
| `villager_road_tiers/*.json` | `blocks` (a tag) and `cost`. Open ground costs 1.0, lower is preferred. Adding a tier is a tag plus a file. |
| `travelling_merchant/*.json` | `{ "wares": [ { "item", "price" } ] }`. All files merged. Items of missing mods are skipped. |
| `cleric_brews/*.json` | `brews`: `input` potion, `reagent`, `output`, `level`, `stock` and an optional `price`. Each must match a vanilla brewing recipe. |
| `test_environment`, `test_instance`, `structure` | Used by the mod's own tests. Leave them alone. |

## Tags

Tags decide what counts as what. Create or replace a file in the matching folder, for example `data/minenow/tags/item/exports.json`.

| Tag | Folder | Meaning |
| --- | --- | --- |
| `exports` | item | Goods only players buy that a travelling merchant takes: armor, swords, bows, saddles and so on. |
| `villager_meals` | item | What villagers eat for meals. |
| `villager_unfit_food` | item | Food that does not count towards breeding (raw meat, poisonous potato). Add other mods' food here. |
| `ink_and_quills` | item | The cartographer's tool. |
| `mine_rim` | block | Blocks that mark a mine's rim (all cobblestones by default). |
| `archer_posts` | block | Blocks archers use as marked posts (hay bales by default). |
| `farmer_fruits` | block | Fruit blocks the farmer harvests from stems. |
| `farmer_ignores` | block | Crops the farmer leaves alone. |
| `villager_roads/tier_1` to `tier_3` | block | The blocks of each road tier. |
| `butcher_livestock` | entity_type | Animals the butcher herds (cow, pig, chicken, rabbit). |

Crop mods work with the farmer without any data: anything that is a crop block or in `#minecraft:crops` counts.

## Using a pack to turn things off for good

Settings are per world and not data pack driven (see [Settings](Settings)). To remove a single trade or recipe, replace the profession's trade file with a copy that leaves it out.
