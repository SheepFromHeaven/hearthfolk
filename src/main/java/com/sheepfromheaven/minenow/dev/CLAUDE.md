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

## Playtest world setup

Running `./gradlew playtest` (or equivalent run config with `-Dminenow.devWorld=true`) skips the
Singleplayer → Create New World click-through entirely: the title screen listener fires once, opens/creates
`minenow_playtest`, and — only on first creation — arms the starter kit so the very first login drops the
banner/wand/spawn eggs into the player's inventory. Reopening the same save on a later run just loads it as-is,
so in-progress test villages persist across runs.

## How to extend

- New one-shot dev convenience tied to world creation: extend `DevWorldBootstrap`'s fresh-creation branch and
  gate any server-side effect through a new `DevWorldState`-style flag (or reuse `pendingStarterKit` if it's
  part of the same "first login" moment).
- Keep all of this behind the `minenow.devWorld` system property (or an equally explicit opt-in) — nothing
  here should run for a normal player.
