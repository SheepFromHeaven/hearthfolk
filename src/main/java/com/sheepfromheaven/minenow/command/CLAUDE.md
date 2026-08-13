# Command

The `/minenow` command tree. One class, no per-command subpackages (except its gametest coverage).

## Key files

- `DebugCommands.java` — registers `/minenow` via `register(CommandDispatcher)`, called from `MineNow`'s
  `RegisterCommandsEvent` listener:
  - `/minenow debugvillage [inhabitants]` — dev-only shortcut for manual playtesting: builds a real enclosed
    room (walls/floor/ceiling/door) for House/Fishery/Lumberyard/Tavern with each structure's requirements
    already inside, runs the same `RoomDetector` flood-fill a player-placed marker would use, registers each
    as a `DominionStructure`, and spawns free-standing inhabitants — so a whole testable village exists in one
    command instead of building every structure and recruiting by hand.
  - `/minenow reloadconfig` (requires `LEVEL_GAMEMASTERS`) — the one player-facing (op-only) command; re-reads
    `minenow-structures.json`/`minenow-structure-levels.json` from disk via `StructureRequirementsConfig.load`/
    `StructureLevelJsonConfig.load` without restarting the server.
- `gametest/DebugCommandsTests.java` — wired via `RegisterEvent` in `MineNow`, not `@GameTest`; checks the
  debug-village structures come out correctly stocked (e.g. lumberyard barrel has an axe).

## Command registration pattern

`Commands.literal("minenow")` with `.then(...)` subcommands; each leaf's `.executes(ctx -> ...)` returns an
int (Brigadier success/failure code). Permission-gated subcommands use `.requires(Commands.hasPermission(...))`.
Registered once, from `MineNow`'s `NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, ...)`.

## How to add a new command

1. Add a `.then(Commands.literal("name")...)` branch (with `.requires(...)` if it should be permission-gated)
   inside `DebugCommands.register`, or start a new top-level command class if it's unrelated to debug tooling
   and wire its own `register(dispatcher)` call from `MineNow`.
2. Implement the executor as a static method returning the Brigadier result code.
3. If it's dev/test-only, keep it under the existing `debugvillage`-style pattern; if it's player-facing,
   gate it with `.requires(Commands.hasPermission(...))` like `reloadconfig`.
