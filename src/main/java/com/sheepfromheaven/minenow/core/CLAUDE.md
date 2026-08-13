# Core

Cross-cutting registration and mod bootstrapping. Per-feature registration (items/blocks/entities specific to
one system) lives next to that feature instead — e.g. `structure/StructureBlocks.java`,
`structure/StructureItems.java`, `jobs/lumberjack/LumberjackItems.java`, `storage/StorageItems.java`,
`pathfinding/PathfindingItems.java`, `inhabitant/ModEntities.java`. `core/` only holds registries that don't
belong to a single feature, plus the mod's single entry point.

## Key files

- `MineNow.java` — the `@Mod` entry point. Constructor calls every feature's `register(modBus)` in a fixed
  order, wires event-bus listeners (server tick, player login, command registration, gametest registration,
  entity attributes), and — client-side only — registers renderers/menu screens/tint sources. Also holds
  `commonSetup` (loads `StructureRequirementsConfig`/`StructureLevelJsonConfig` from disk).
- `ModCreativeTabs.java` — the single `minenow` creative tab; `displayItems` explicitly lists every item that
  should show up in it. **Registering an item elsewhere does not add it here** — `ModCreativeTabs.displayItems`
  must be updated by hand for every new item, or it's invisible in creative mode (see root `CLAUDE.md`).
- `ModDataComponents.java` — `DeferredRegister<DataComponentType<?>>` for data components shared across
  features (`wand_mode` on `StructureWandItem`, `filter_data` on `FilterItem`) — put a component here if more
  than one feature needs it, otherwise keep it local to the feature.
- `ModCriteriaTriggers.java` — custom advancement `CriterionTrigger`s for events with no vanilla criterion to
  hook into (immigration, dominion tier growth).
- `Config.java` — `@EventBusSubscriber` NeoForge `ModConfigSpec` (common config: dominion radius, max room
  size, max trunk size, debug-inhabitant-view toggle); mirrors spec values into plain static fields on
  `onLoad` so the rest of the codebase reads `Config.maxRoomSize` etc. without touching the spec API.

## Registration bootstrapping order

`MineNow`'s constructor, in order: per-feature block/item `DeferredRegister`s (`TownBannerBlocks`,
`TownBannerItems`, `StructureBlocks`, `StructureItems`, `InhabitantItems`, `PathfindingItems`,
`LumberjackItems`, `StorageItems`) → `ModDataComponents` → `ModCreativeTabs` (needs the items above to exist
for `displayItems`) → `ModEntities`/`InhabitantMenus`/`ModMenus` (menus need entities/containers) →
`ModCriteriaTriggers`. Then non-registry wiring: common-setup listener, command registration, dev
starter-kit listener, server-tick listener, config registration, entity-attribute listener, gametest
`RegisterEvent`, and (client dist only) renderers/screens/tint sources/dev-world bootstrap.

## How to extend

- New cross-feature registry entry (data component, creative tab, criterion trigger): add it to the relevant
  `core/` class.
- New item/block/entity for a specific feature: register it in that feature's own `DeferredRegister`
  (`XxxItems`/`XxxBlocks`), call `register(modBus)` from `MineNow`'s constructor, **and** add it to
  `ModCreativeTabs.displayItems` if it should be obtainable in creative mode.
- New common config value: add a `ModConfigSpec` field in `Config.java`, mirror it in `onLoad`.
