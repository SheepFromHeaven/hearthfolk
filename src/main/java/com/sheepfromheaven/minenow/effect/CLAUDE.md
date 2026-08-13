# Effect

Bonuses a `DominionStructure` accumulates from its level config — "+1 inhabitant slot", "+25% productivity",
"+21 blocks of territory", "a foraging radius around the marker". Currently the only source is level-ups
(`StructureType.getLevelEffects`, driven by `StructureLevelJsonConfig`), but nothing in the interface assumes
that.

## Key files

- `StructureEffect.java` — the interface: `apply(structure, stats)` folds a scalar contribution into
  `StructureStats`; `contributeWorkAreas(structure, out)` (default no-op) adds `WorkArea` geometry instead —
  kept separate so the cheap scalar fold in `DominionStructure.getStats()` isn't forced to materialise
  geometry on every call.
- `StructureStats.java` — the accumulator: `inhabitantSlots` (starts at 0 — even level 1 must grant its own),
  `productivityMultiplier` (starts at 1.0, multiplies), `territorySizeX/Y/Z` (starts at 0, adds). Never
  persisted — recomputed fresh from the structure's active level effects on every `getStats()` call.
- `InhabitantSlotsEffect.java` — adds `amount` inhabitant slots (worker slots for a workplace, resident slots
  for a house — the structure itself decides which role the capacity fills).
- `ProductivityMultiplierEffect.java` — multiplies productivity by `multiplier` (e.g. `1.25` = +25%); stacks
  multiplicatively across levels/sources.
- `TerritoryEffect.java` — grows `DominionStructure`'s placement `Territory` by `x`/`y`/`z` per axis; stacks
  additively across levels. The marker stays centred — an even axis total gets +1 so a single centre block
  always exists (see `DominionStructure.getTerritory()`).
- `StaticWorkAreaEffect.java` — grants a `CylinderArea` of `radius` centred on the marker (the "forage
  somewhere around the structure" shape used by lumberjack/forester, as opposed to a player-placed
  claim/field). Contributes geometry via `contributeWorkAreas`, not a `StructureStats` scalar.
- `StructureEffects.java` — registry mapping a JSON `"type"` string (`inhabitantSlots`, `productivity`,
  `territory`, `staticWorkArea`) to a `Parser` that builds the matching effect from its JSON object. Replaces
  a hardcoded switch so new effect kinds self-register in `bootstrap()` instead of editing a central parser.

## How effects are granted and applied

1. **Config**: each `StructureType`'s level JSON (loaded by `structure/StructureLevelJsonConfig`) lists a set
   of effect JSON blobs per level, e.g. `{"type": "staticWorkArea", "radius": 8}`.
2. **Parsing**: `StructureEffects.parse(jsonObj)` looks up the `"type"` string and hands the object to the
   registered `Parser`, returning the constructed `StructureEffect` (or `null` for malformed/unknown entries —
   the config loader logs which structure/level was skipped).
3. **Granting**: `StructureType.getLevelEffects(level)` returns every `StructureEffect` unlocked at or below
   the structure's current level.
4. **Folding into stats**: `DominionStructure.getStats()` walks the granted effects and calls `apply(this,
   stats)` on each into a fresh `StructureStats` — never cached, so losing a level (or an operational
   requirement going unmet) instantly reflects in the next `getStats()` call.
5. **Work areas**: `DominionStructure`'s work-area resolution separately calls `contributeWorkAreas` on each
   granted effect to collect `WorkArea`s (only `StaticWorkAreaEffect` currently contributes any) — kept off
   the `getStats()` hot path since it's only needed when a job actually resolves its work area.

## How to extend

1. Add a new `StructureEffect` implementation (override `apply` for a scalar, or `contributeWorkAreas` for
   geometry).
2. Register its JSON `"type"` string in `StructureEffects.bootstrap()`.
3. Reference that type in a structure's level JSON. No changes needed to `DominionStructure`, `StructureType`,
   or the config loader.
