# Inhabitant

## Key files

- `InhabitantEntity.java` — the NPC `Mob` (`PathfinderMob` + `GeoEntity` + `InventoryCarrier` + `ContainerUser`). Holds `dominionId`, `homeStructureId`, `workStructureId`, `needs` map, the cached `WorkGoal`, and (only pre-membership) an `ImmigrationJob`. `registerGoals()` wires the AI goal list. Custom `pickUpItem` auto-equips job tools to main-hand. Persists everything in `addAdditionalSaveData`/`readAdditionalSaveData`.
- `InhabitantNodeEvaluator.java` — custom `WalkNodeEvaluator`: fence gates report as `DOOR_WOOD_CLOSED` (reuses door-open traversal), plus a distance-scaled malus bias toward `DominionPathNode.Kind.STREET` nodes — affects every path search, not just idle wander.
- `InhabitantPathNavigation.java` — `PathNavigation` wiring the custom evaluator; `setCanOpenDoors(true)`, wider `setRequiredPathLength(48)` so `GoHomeGoal` can route around obstacles.
- `LadderClimbingMoveControl.java` — custom `MoveControl` for ladder climbing, set in the constructor.
- `ModEntities.java` — `DeferredRegister<EntityType<?>>` for `INHABITANT` and `NPC_FISHING_BOBBER` (fisher job's bobber, `noSave()`).
- `item/InhabitantSpawnEggItem.java` — spawns inside any dominion's claim (ownership not required), capacity-checked, `dominion.addInhabitant(...)`.
- `item/InvitationItem.java` — right-click a vanilla `Villager` inside **your own** dominion to convert it, same capacity check.
- `inventory/InhabitantInventoryMenu.java` — 2-slot `SimpleContainer` + main-hand `EquipmentSlotContainer`, opened via right-click (`mobInteract`), owner-only.
- `immigration/ImmigrationJob.java` — `WALK_TO_FLAG → AWAITING_DECISION → REJECTED_WANDER_OFF` state machine, ticked directly from `InhabitantEntity.aiStep` (**not** `WorkGoal`/`Job` — no workplace yet).
- `immigration/client/ImmigrantRequestScreen.java` — owner-facing approve/reject UI, opened via `ModNetwork.sendOpenImmigrantRequestScreen`.
- `goal/GoHomeGoal.java` — priority 2; explicit `goHome()` or automatic at night with a home assigned; walks to bed (or banner if homeless), sleeps if still night, else waits and clears the request.
- `goal/GoEatGoal.java` — priority 2; when `FOOD` < 0.4, finds nearest `TAVERN` with a ready-to-eat meal (`CookingRecipes.isReadyToEat`), walks there, eats via `ContainerUtil.extractOne`, restores `FOOD` to 1.0.
- `goal/WalkToBannerGoal.java` — priority 2, active only while attached `ImmigrationJob.phase == WALK_TO_FLAG`; walks to the target banner, flips phase to `AWAITING_DECISION` on arrival.
- `goal/StrollTowardPathNodeGoal.java` — priority 4; idle-wander alternative, periodically heads toward the nearest `STREET` path node if in range, else falls back to vanilla stroll.
- `goal/OpenFenceGateGoal.java` — priority 1; mirrors vanilla's door-interact goal for fence gates (vanilla never opens these for any mob); pairs with `InhabitantNodeEvaluator`'s malus override.
- `gametest/*Tests.java` — `RestNeedTests`, `InhabitantPersistenceTests`, `InhabitantPickupTests`, `JobInterruptionTests`, `GoHomeGoalTests`, `InhabitantIssuesNetworkTests`; wired via `RegisterEvent`, not `@GameTest`.
- `client/InhabitantRenderer.java`/`InhabitantModel.java` — GeckoLib model/renderer.
- `client/InhabitantChopBodyLayer.java`/`InhabitantHeadRotationLayer.java` — render layers driven by synced `DATA_CHOPPING` flag and look direction.
- `client/InhabitantScreen.java` — inventory screen; also shows name/dominion/needs/job status, and (behind `Config.debugInhabitantView`) live AI/pathfinding debug lines.
- `client/InhabitantSelectionScreen.java`, `IssuesListScreen.java` — assign-inhabitant picker and cross-inhabitant issues list.

## Goal priority list (`registerGoals()`)

- `0`: vanilla `FloatGoal`.
- `1`: `OpenDoorGoal`, `OpenFenceGateGoal`.
- `2`: `FinishWorkGoal` (jobs/), `GoHomeGoal`, `GoEatGoal`, `WalkToBannerGoal` — mutually exclusive "personal needs" goals at the same priority; each gates its own `canUse()` so normally only one fires.
- `3`: `WorkGoal` (jobs/) — cached instance, eligible only once a job is assigned. See `jobs/CLAUDE.md`.
- `4`: `StrollTowardPathNodeGoal`.
- `5`: vanilla `WaterAvoidingRandomStrollGoal`.
- `6`/`7`: vanilla `LookAtPlayerGoal`/`RandomLookAroundGoal`.

**Persistence**: saves `dominionId`, `homeStructureId`, `workStructureId`, all three `NeedType` floats, `WorkGoal`'s tag (→ `Job.save`/`load`), `ImmigrationJob`'s tag if present, and the 2-slot inventory. **Jobs hook**: `assignJob`/`clearJob` just forward to the cached `WorkGoal` — always go through `Dominion.assignWorker`/`unassignWorker`, never call these directly (that's what keeps `homeStructureId`/`workStructureId` and the structure's resident/worker lists in sync). **Death**: `die()` removes the inhabitant from its dominion and messages the owner.

## Immigration: how a new inhabitant appears/joins

Three paths onto the roster, all ending in `dominion.addInhabitant(new DominionInhabitant(entityUuid, name))` capped by `tier.getMaxInhabitants()`:

1. **Manual conversion** (`InvitationItem`) — right-click a vanilla `Villager` inside your own dominion.
2. **Manual spawn** (`InhabitantSpawnEggItem`) — right-click inside any dominion's claim (ownership not required).
3. **Automatic**: `DominionSavedData.tickGrowth` rolls 10% for attractive (`TAVERN` or bell in claim), off-cooldown, non-full dominions with no pending candidate → `spawnImmigrationCandidate` spawns an `InhabitantEntity` on a ring just outside the claim, attaches `ImmigrationJob`, sets `dominion.pendingImmigrantId` → `WalkToBannerGoal` walks it to the banner → phase flips to `AWAITING_DECISION` → right-click opens `ImmigrantRequestScreen` → owner's choice returns via `applyImmigrationDecision`:
   - **Approve**: create+add `DominionInhabitant` (capacity re-checked), set `dominionId` on entity, clear `ImmigrationJob`, apply cooldown (+2400 ticks).
   - **Reject**: phase → `REJECTED_WANDER_OFF` (wanders ~240 ticks via `tickWanderOff()`, then `discard()`s), same cooldown.

While `immigrationJob != null`, `aiStep` **skips** need decay/job sync entirely — candidates aren't dominion members yet.

## Inventory system

Fixed 2-slot `SimpleContainer` (`InventoryCarrier.getInventory()`) plus main-hand equipment. `pickUpItem`: job tools (`ItemTags.AXES`, `Items.FISHING_ROD`) go straight to main-hand if empty; everything else into the 2 slots via `SimpleContainer.addItem` (non-fitting leftovers aren't picked up). Player access via `InhabitantInventoryMenu` (right-click, owner-only, non-immigrant): 2 inventory slots + main-hand slot + player inventory grid, laid out per `inhabitant_screen.json`. Jobs read/write this same inventory through `JobActions`/`ContainerUtil` — see `jobs/CLAUDE.md`.

## How to extend

1. **New personal-needs goal** (like `GoEatGoal`): add under `goal/`, register at priority 2 in `registerGoals()`, drive off `InhabitantEntity`/`Dominion` state — never touch `WorkGoal` directly.
2. **New `NeedType`**: add the enum constant in `dominion/NeedType.java`, decay/regen logic in `aiStep`'s 200-tick block, save/load fields in `addAdditionalSaveData`/`readAdditionalSaveData` **and** the `INHABITANT_CODEC` in `DominionSavedData` (mirrored via `syncNeedsToDominion`).
3. **New way to join a dominion**: follow the `InvitationItem`/`InhabitantSpawnEggItem` pattern — capacity check via `dominion.hasRoomForInhabitant()`, then `addInhabitant(...)`, then `setDirty()`.
4. **New immigration phase/behavior**: extend `ImmigrationJob.Phase` + add a goal gated on that phase, keep ticking from `aiStep` — immigration candidates are pre-dominion, no workplace exists yet.
5. **New job-tool auto-equip rule**: extend `InhabitantEntity.isJobTool(stack)`.
6. **New pathfinding terrain rule**: extend `InhabitantNodeEvaluator`, not per-goal logic — it applies uniformly to every path search.
