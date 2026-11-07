# Dev

Developer-only tooling for faster manual playtesting — none of it ships behavior players rely on; it's all
gated behind a system property or a one-shot login flag.

## Key files

- `DevWorldState.java` — a single `volatile boolean pendingStarterKit` flag shared between the client-side
  bootstrap and the server-side starter-kit grant. Safe as a plain static because both run in the same JVM
  for the integrated singleplayer server this feature targets — no networking needed.
- `DevStarterKit.java` — `onPlayerLoggedIn` (wired from `MineNow`'s `PlayerLoggedInEvent` listener): if
  `DevWorldState.pendingStarterKit` is set, clears it and hands the logging-in player a town banner, a
  structure wand, and 8 inhabitant spawn eggs. Fires at most once per fresh world (never on reopening an
  existing save).
- `client/DevWorldBootstrap.java` — gated on `-Dminenow.devWorld=true` (set by the Gradle `playtest` run
  task). Hooks `ScreenEvent.Init.Post` for the title screen and, the first time it appears, either reopens
  the existing `minenow_playtest` singleplayer world or creates it fresh using **the normal overworld preset**
  (`WorldPresets::createNormalWorldDimensions`) — not a superflat/void preset — creative mode, peaceful
  difficulty, so trees/ore/terrain the jobs interact with actually exist. Sets
  `DevWorldState.pendingStarterKit = true` only on fresh creation.
- `client/GametestPlaygroundBootstrap.java` — gated on `-Dminenow.gametestPlayground=true` (set by the Gradle
  `gametestPlayground` run task). Same title-screen hook, but opens/creates a separate `minenow_gametest_playground`
  world — **superflat** (`WorldPresets::createFlatWorldDimensions`), creative, peaceful — kept independent of
  `minenow_playtest` so bulk-running gametests never touches the normal-terrain playtest save.
- `GametestPlaygroundRunner.java` — `onPlayerLoggedIn` (wired from `MineNow`'s `PlayerLoggedInEvent` listener,
  alongside `DevStarterKit`): whenever `-Dminenow.gametestPlayground=true`, looks up every `minenow:*`
  entry in `Registries.TEST_INSTANCE` and places a `TestInstanceBlockEntity` for each in a fixed-spacing
  grid near the player, on **every** login (not one-shot like the starter kit). Unlike
  `/test runmultiple`, this only calls `placeStructure()` - it does not start the test, so every
  structure sits idle (`Status.CLEARED`) until a player walks up, right-clicks its test instance block,
  and presses "Run Test" in the vanilla GUI.

## Playtest world setup

Running `./gradlew playtest` (or equivalent run config with `-Dminenow.devWorld=true`) skips the
Singleplayer → Create New World click-through entirely: the title screen listener fires once, opens/creates
`minenow_playtest`, and — only on first creation — arms the starter kit so the very first login drops the
banner/wand/spawn eggs into the player's inventory. Reopening the same save on a later run just loads it as-is,
so in-progress test villages persist across runs.

## Gametest playground world setup

Running `./gradlew gametestPlayground` (`-Dminenow.gametestPlayground=true`) opens/creates the separate
superflat `minenow_gametest_playground` world and, on every login, lays out every MineNow gametest
structure in a grid near the player, idle and not running — walk up to whichever one you want and
right-click its test instance block to run it manually via the vanilla GUI, rather than every test
firing at once. Barrier-walled test structures (`GameTestHelpers.barrierBounds`, see `gametest/CLAUDE.md`)
keep spawned inhabitants from wandering into a neighboring test once one is actually run.

## How to extend

- New one-shot dev convenience tied to world creation: extend `DevWorldBootstrap`'s fresh-creation branch and
  gate any server-side effect through a new `DevWorldState`-style flag (or reuse `pendingStarterKit` if it's
  part of the same "first login" moment).
- New every-login (not just first-creation) dev convenience: follow `GametestPlaygroundRunner`'s pattern —
  gate directly on the run's system property in the `PlayerLoggedInEvent` handler rather than adding a
  one-shot flag.
- Keep all of this behind an explicit system property (`minenow.devWorld`, `minenow.gametestPlayground`, ...)
  — nothing here should run for a normal player.
