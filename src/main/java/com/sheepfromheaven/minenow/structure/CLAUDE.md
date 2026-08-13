# Structures

## Key files

- `StructureType.java` — enum of placeable structure kinds; pairs each with an optional `JobType` and range/requirement/level config (delegates to `StructureLevelJsonConfig`/`StructureRequirementsConfig`).
- `DominionStructure.java` — the runtime instance of a placed structure (id, name, type, `markerPos`, `roomBlocks`, level, assigned resident/worker UUIDs, requests/filters). Everything a `Job` needs comes from here: `findApproachPos`, `findStorageContainers`, `findTreeShapeInRange`, `findPlantableSpotInRange`, `getTerritory()`, `getStats()`, `canWork`/`canReside`, `checkOperationalRequirements`/`checkLevelUpRequirements`.
- `StructureBlocks.java` — `DeferredRegister` for every marker `Block` + the shared `StructureMarkerBlockEntity` type; `register(modBus)` called from `MineNow`. `getMarkerBlock(StructureType)` maps type → block.
- `RoomDetector.java` — flood-fills from a placed marker to detect an enclosed room (capped at `Config.maxRoomSize`); returns sealed `Result` (`Success(blocks)` / `TooLarge` / `NotEnclosed`).
- `Territory.java` — placement bounding box around a marker, sized by granted `TerritoryEffect`s; gates where work-area/claim markers may be placed.
- `block/StructureMarkerBlock.java` — the placeable marker. `setPlacedBy`: finds the containing `Dominion`, runs `RoomDetector.detect`, on success builds `new DominionStructure(...)` and calls `dominion.addStructure(...)`. `useWithoutItem`: opens the config screen via `ModNetwork.sendOpenStructureScreen`. `playerWillDestroy`: `dominion.removeStructure(...)`.
- `item/StructureWandItem.java` — instant-build item: builds an 8x10x6 room + door + a marker of the wand's current mode, then places the marker exactly as a player would (registers normally).
- `workarea/WorkArea.java` (`sealed interface`, `permits CylinderArea`) — job-consumable region, decoupled from its source.
- `field/Field.java`, `mine/MineClaim.java` — player-placed claims that extend a `FARM`/`MINE` structure's work area via `assignedStructureId`; each has its own marker block + area detector (`FieldAreaDetector` / room-detector-style).
- `client/StructureMarkerRenderer.java` — draws range + territory gizmos from `StructureMarkerBlockEntity`'s mirrored data (`Gizmos`/`RectGizmo`); no per-tick polling of `DominionStructure` — data is pushed via `refreshStatuses`/`setStructureData`.
- `gametest/*Tests.java` — wired via `RegisterEvent` on `Registries.TEST_FUNCTION` in `MineNow`, not `@GameTest`.

## Runtime sequence: place → register → work

1. **Placement**: `StructureMarkerBlock.setPlacedBy` — look up the dominion containing `pos`, abort if none/not owned; `RoomDetector.detect` the enclosed room (abort + particles on `TooLarge`/`NotEnclosed`); on success, `new DominionStructure(...)` + `dominion.addStructure(...)`, then mirror data into the block entity.
2. **Work-area claims (MINE/FARM only)**: placing a `MineClaimMarkerBlock`/`FieldMarkerBlock` separately runs its own area detector and creates a `MineClaim`/`Field` referencing the structure via `assignedStructureId` — extends, doesn't replace, the structure's own `effectWorkAreas()`-derived `CylinderArea`.
3. **Job linkage**: `structure.getType().getJobType()` is what lets `WorkGoal.assignJob` (see `jobs/CLAUDE.md`) create the right `Job` when a worker is assigned. `HOUSE` has no job type — residents just live there.
4. **Leveling**: `checkRequirements`/`checkLevelUpRequirements`/`checkOperationalRequirements` evaluate `StructureType.getRequirements(level)` (merged per-level from JSON) against a `RequirementContext`; satisfied levels grant `StructureEffect`s (work areas, inhabitant slots, productivity multipliers, territory size) — recomputed fresh on every `getStats()` call, never cached/persisted.
5. **Interaction**: right-click resolves the `DominionStructure`, refreshes BE-mirrored statuses, opens `StructureMarkerScreen`/`StructureFilterScreen` via `ModNetwork.sendOpenStructureScreen`.
6. **Removal**: `playerWillDestroy` → `dominion.removeStructure(...)`, which unassigns any workers/residents (tearing down `Job`s via `WorkGoal.clearJob`).

## Adding a new structure type

1. New `StructureType` entry, optionally paired with a `JobType`.
2. Register a marker `Block` in `StructureBlocks` (`registerMarker(name, type)`), add it to the `BLOCK_ENTITIES` set, add a case to `getMarkerBlock`.
3. Add JSON requirement/level config (consumed by `StructureLevelJsonConfig`/`StructureRequirementsConfig`).
4. If it needs a job, see `jobs/CLAUDE.md` — no changes needed to `StructureMarkerBlock`, `RoomDetector`, or `DominionStructure` themselves.
