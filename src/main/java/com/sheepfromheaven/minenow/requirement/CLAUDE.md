# Requirement

Something that gates a build step, a structure's operational status, or a level-up: enough room, a specific
block present, a work-area marker assigned. Every requirement evaluates fresh against live world/dominion
state each time it's checked — nothing here is cached, so a requirement can never go stale independently of
the world.

## Key files

- `Requirement.java` — the interface: `getDisplayName()` (for GUI rows) + `evaluate(ctx)` → `RequirementStatus`.
- `RequirementContext.java` — a record bundling `Level`, a nullable `Dominion` (null if the structure isn't
  attached to one yet), and the `DominionStructure` being checked. A requirement needing the dominion should
  report unmet rather than throw when it's null.
- `RequirementStatus.java` — a record `(name, required, found)`; `satisfied()` is `found >= required`.
- `BlockCountRequirement.java` — wraps a `StructureType.Requirement` (block/tag matcher + display name + icon
  + count); counts matches across `structure.getInteriorAndWalls()`.
- `RoomSizeRequirement.java` — requires the detected interior room (`structure.getRoomBlocks()`) to be at
  least `minSize` blocks.
- `WorkAreaMarkerRequirement.java` — requires at least `count` work-area markers of a given `MarkerKind`
  (mine claim / field) to be assigned to the structure and sitting inside its `Territory`. This is how a
  Mine/Farm turns "go place your claim/field markers" into an actual work condition — unmet, the structure is
  non-operational exactly like an unmet block requirement. Reports unmet (found 0) if the structure has no
  dominion/territory yet.

## How requirements are evaluated

- `StructureType.getRequirements(level)`/`getLevelUpRequirements(level)` (built from `StructureRequirementsConfig`
  / `StructureLevelJsonConfig` — see `structure/CLAUDE.md`) return the `Requirement` list for a level.
- `DominionStructure.checkRequirements` / `checkLevelUpRequirements` / `checkOperationalRequirements` each
  evaluate that list against a fresh `RequirementContext` and return `List<RequirementStatus>`; callers reduce
  with `.stream().allMatch(RequirementStatus::satisfied)` to get a single boolean (level-up eligibility,
  "is this structure currently operational").
- `checkOperationalRequirements` filters out `RoomSizeRequirement` — room size only matters at build/level-up
  time, not for staying operational afterward.
- Consumers: `WorkGoal.canUse()` calls `workplace.canWork(...)` (operational requirements) before starting a
  `Job`; the structure screen (`ModNetwork.sendOpenStructureScreen`) encodes both requirement lists to show
  progress bars per requirement.

## How to extend

1. Implement `Requirement` (block-count-style, count-style, or something entirely new — evaluate against
   `RequirementContext` however makes sense).
2. Wire it into `StructureType`'s requirement/level-up list, either directly or via a JSON-config code path
   like `BlockCountRequirement`/`WorkAreaMarkerRequirement` (see `StructureRequirementsConfig`,
   `StructureLevelJsonConfig`).
3. No changes needed to `DominionStructure`'s check methods — they just fold whatever `Requirement`s the
   `StructureType` hands them.
