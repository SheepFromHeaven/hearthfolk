# Dominion

A **Dominion** is the top-level "town": a banner block, an owning player, a tier, and every structure/inhabitant/path-node/field/mine-claim inside its claim.

## Key files

- `Dominion.java` — runtime aggregate: owner, name, banner pos, tier, `Map<UUID, X>` for structures/inhabitants/path nodes/fields/mine claims. Owns `assignWorker`/`unassignWorker`, `assignHouse`/`unassignInhabitant`, `addStructure`/`removeStructure`, immigration gating, tier growth (`checkAndApplyLevelUp`), `disband`.
- `DominionSavedData.java` — world-level `SavedData` singleton (`Map<UUID, Dominion>`); all codecs for `Dominion`/`DominionStructure`/`DominionInhabitant`/`Field`/`MineClaim`/`DominionPathNode` live here. Position lookups: `getDominionAt`, `findDominionContaining`, `findStructureWithTerritoryContaining`. Periodic ticks: `tickStructureStock`, `tickGrowth` (also rolls immigration).
- `DominionInhabitant.java` — dominion-side per-inhabitant record: id, `entityUuid` link, home/work structure ids, needs map. Pure data.
- `DominionTier.java` — `SETTLEMENT→VILLAGE→TOWN→CITY`; each has `maxInhabitants`, `radiusMultiplier` (feeds `Dominion.getEffectiveRadius()`), `getMaxStructureLevel()`, and a `DominionMilestone` gating advance.
- `NeedType.java` — `SHELTER`/`FOOD`/`REST`; decayed/regened in `InhabitantEntity.aiStep` (see `inhabitant/CLAUDE.md`).
- `ItemCategory.java` / `StructureRequest.java` — "any item matching X" category matching and per-structure pending item requests, aggregated by `Dominion.getPendingRequests()` for couriers.
- `townbanner/TownBannerBlock.java`, `TownWallBannerBlock.java` — placeable banner blocks; delegate to `TownBannerBehavior`.
- `townbanner/TownBannerBehavior.java` — `onPlaced` → `DominionSavedData.createDominion`; `onUse` → open `DominionScreen`; `onDestroyed` → `dominion.disband()` + `DominionSavedData.removeDominion`.
- `townbanner/TownBannerBlockEntity.java` — mirrors name/tier/pattern for client rendering.
- `client/DominionScreen.java` / `inventory/DominionMenu.java` — dominion info screen; the menu's only real slot is a transient "template banner" slot used to copy a design onto the dominion.
- `gametest/DominionTests.java`, `DominionGrowthTests.java` — wired via `RegisterEvent` on `Registries.TEST_FUNCTION`, not `@GameTest`.

## Claim shape & lookups

- Claim = square centered on `bannerPos`, radius `Dominion.getEffectiveRadius()` = `Config.defaultDominionRadius * tier.getRadiusMultiplier()`.
- `findDominionContaining(pos)` — point-in-claim-box test, any owner.
- `findStructureWithTerritoryContaining(pos, type, owner)` — nearest structure of `type` **owned by `owner`** whose own `structure.Territory` (not the dominion box) contains `pos`. This is how mine-claim/field marker placement finds its parent `MINE`/`FARM` — a structure's territory can extend outside the dominion's own bounds.
- **Dominion claim** gates "can a structure marker be placed here at all." **`structure.Territory`** (per-structure, grows with level) gates "which structure's work area does this claim marker extend." Don't conflate the two.

## assignWorker / unassignWorker / assignHouse / addStructure / removeStructure

- `addStructure` — registers into the map (called from `StructureMarkerBlock.setPlacedBy` after room detection).
- `removeStructure(id, level)` — unassigns every resident/worker first (tearing down jobs + home-POI reservations), then removes, then clears any `Field`/`MineClaim.assignedStructureId` pointing at it.
- `assignWorker(structure, inhabitantId, level)` — strips any existing job first, checks `structure.hasOpenWorkSlot()`, then `structure.addWorker(id)` + `ie.setWorkStructureId(...)` + **`ie.assignJob(structure)`** → forwards to `WorkGoal.assignJob` (see `jobs/CLAUDE.md`), which builds the right `Job` via `structure.getType().getJobType()`.
- `unassignWorker` — clears `workStructureId`, calls `ie.clearJob()` → `WorkGoal.clearJob()`, then `structure.removeWorker(id)`.
- `assignHouse`/`unassignInhabitant` — same one-slot pattern for residency, plus claims/releases a vanilla `PoiTypes.HOME` POI on the resident's bed.
- An inhabitant holds at most **one home and one job** at a time — both assign methods strip any existing assignment across the whole dominion first.

## Ownership

One owner per dominion, checked everywhere player-facing (`dominion.getOwnerUuid().equals(player.getUUID())`): opening the dominion screen, immigration decisions, structure interactions. No co-op model. Structure placement is gated by claim containment, not a separate flag — outside your claim, there's no dominion to add the structure to.

## Runtime sequence

1. Place banner → `TownBannerBehavior.onPlaced` → `DominionSavedData.createDominion` (tier `SETTLEMENT`).
2. Place structure markers inside the claim → `Dominion.addStructure` (see `structure/CLAUDE.md`).
3. Populate: `InvitationItem` (convert villager) / `InhabitantSpawnEggItem` (spawn fresh) / immigration (below) → `dominion.addInhabitant(...)`, capped by `tier.getMaxInhabitants()`.
4. Assign work/home via UI → `assignWorker`/`assignHouse` → live entity reacts.
5. `InhabitantEntity.aiStep` (every 200 ticks) decays/regens needs, syncs back to `DominionInhabitant`. `DominionSavedData.tickGrowth`/`tickStructureStock` run periodically for level-up, immigration rolls, and restocking.
6. Every mutating op calls `setDirty()` so vanilla autosave persists the whole graph via the hand-written codecs.
7. Destroy banner → `disband()` (revert inhabitants to `Villager`, clear structure blocks) → `removeDominion`.

Immigration gating state (`pendingImmigrantId`, `nextImmigrationAttemptTick`, `isAttractive`, `canAttemptImmigration`) lives on `Dominion`; the actual walk/approve/reject flow is driven off the live entity — see `inhabitant/CLAUDE.md`.

## How to extend

1. **New tier/milestone**: edit `DominionTier` only — new enum constant + `DominionMilestone` case. `tickGrowth` picks it up automatically.
2. **New per-structure rule**: goes on `DominionStructure` (see `structure/CLAUDE.md`), not here — `Dominion` only orchestrates slot assignment via `structure.hasOpenWorkSlot()`/`canReside()`.
3. **New immigration-attraction structure type**: add to `Dominion.ATTRACTION_TYPES`.
4. **New persisted field**: add the field/getter/setter, then a matching entry (with `optionalFieldOf` default) in the relevant codec in `DominionSavedData` — codecs are hand-written, not reflected.
5. **New claim-scoped territory check**: extend `Territory`/`TerritoryEffect`, not `Dominion.getEffectiveRadius()`.
