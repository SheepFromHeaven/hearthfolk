# Customizing with Data Packs

Almost everything about prices, recipes, names and what counts as what is plain JSON under the `minenow` namespace. You change it with a normal data pack. Put your file at exactly the same path as the mod's file to replace it, or add a new file in a folder where all files are merged. Run `/reload` in game to apply it. No restart is needed.

A data pack lives in your world's `datapacks` folder and needs a `pack.mcmeta`. Copy that file from a pack that works on your game version. Its files go under `data/minenow/...`. The mod's own files are in the jar under the same paths, which is the easiest place to copy an example from.

Prices for `produce`, `supplies`, `default_produce`, craft `labor` and travelling merchant `wares` can be decimals of emeralds per item. `0.05` means 20 for an emerald, `3` means three emeralds each. The mod turns it into the smallest whole-emerald deal. A tool's `price` and a cleric brew's `price` must be whole emeralds.

A typo, or an item from a mod that is not installed, in a trade file or a cleric brew stops that whole file from loading, so that profession has no villager trading until you fix it (see the log). Only the travelling merchant's list skips unknown items quietly.

## Trade files: `data/minenow/villager_trades/<profession>.json`

One file per profession, keyed by its `profession`. To change a profession, use exactly the path of the mod's file (`data/minenow/villager_trades/farmer.json`) and copy the original to edit it. A second file with a different name for the same profession is not merged and may win or lose at random. `profession`, and `price` in every entry, are required. `level` runs 1 to 5.

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
- `supplies` are bought at their `price` while the villager holds less than one batch, up to `max` (required).
- `keeps` lists goods it makes and works on further. They are kept up to `max` (required) and never bought.
- `crafts` are made at its workstation from `materials` (required, each with an `item` and a `count`). Optional fields: `count` (items per craft, default 1), `tool_uses` (wear on its tool, default 0), `stock` (how many to keep), `labor` (decimal emeralds) and `enchants: true` for a librarian's book. The price is the materials plus labor plus tool wear. A `produce` entry for the same item overrides it.
- A level unlocks its entries and keeps the earlier ones. Several entries for one item at different levels: the latest one is the recipe. Several at the same level are alternatives.

## Other data

| Path | What it does |
| --- | --- |
| `villager_names/*.json` | `{ "names": [ ... ] }`. All files are merged. Add a file for more names. |
| `wealth/breeding.json` | Only this exact file is used. `base` and `per_villager` are required, `minimum` and `maximum` optional (no limit if left out): the emeralds a villager needs before buying breeding food. The shipped file is 3 plus 1 per villager, between 4 and 40. |
| `villager_road_tiers/*.json` | `blocks` (a tag, a list of blocks or one block) and `cost` from 0 to 1. Open ground costs 1, so lower is preferred and 1 is no road at all. All files apply. The tier 2 and 3 tags are also what the mason treats as roads it has already paved. |
| `travelling_merchant/*.json` | `{ "wares": [ { "item", "price" } ] }`. All files merged. Items of missing mods are skipped. |
| `cleric_brews/*.json` | `brews`: `input` potion, `reagent`, `output`, `level`, `stock` and an optional `price`. Each must match a vanilla brewing recipe. |
| `recipe/` | The ink and quill recipes, which only work while the Ink and Quill Recipes setting is on. |
| `loot_table/blocks/` | What the mod's job blocks drop when broken. |
| `test_environment`, `test_instance`, `structure` | Used by the mod's own tests. Leave them alone. |

## Tags

Tags decide what counts as what. They are ordinary tags, so a file in the matching folder, for example `data/minenow/tags/item/exports.json`, adds to the mod's list. To remove something, set `"replace": true` and list everything you want.

| Tag | Folder | Meaning |
| --- | --- | --- |
| `exports` | item | Goods only players buy that a travelling merchant takes: armor, swords, bows, saddles and so on. |
| `villager_meals` | item | What villagers eat for meals. |
| `villager_unfit_food` | item | Food that does not count towards breeding. Add other mods' food here. Vanilla's raw and unsafe foods stay excluded even if you remove them. |
| `villager_storage_key` | item | Items that open a chest a villager has locked. Empty by default. |
| `ink_and_quills` | item | The cartographer's tool. |
| `mine_rim` | block | Blocks that mark a mine's rim (all cobblestones by default). |
| `archer_posts` | block | Hay bales are the posts you mark. Blocks added here are only preferred when an archer looks for a perch near the bell. |
| `farmer_fruits` | block | Fruit blocks the farmer harvests from stems. |
| `farmer_ignores` | block | Crops the farmer leaves alone. |
| `villager_roads/tier_1` to `tier_3` | block | The blocks of each road tier. |
| `butcher_livestock` | entity_type | Animals the butcher herds (cow, pig, chicken, rabbit). |

Crop mods work with the farmer without any data: anything that is a crop block or in `#minecraft:crops` counts.

## Using a pack to turn things off for good

Settings are per world and not data pack driven (see [Settings](Settings)). To remove a single trade or recipe, replace the profession's trade file with a copy that leaves it out. Trade changes show up the next time you open a villager's trade screen. A travelling merchant already standing in the world keeps its old wares.
