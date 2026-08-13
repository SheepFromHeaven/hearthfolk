# MineNow

NeoForge 1.21.11 mod (NeoForge `21.11.45`, mod loader plugin `net.neoforged.moddev`). Group/mod id: `com.sheepfromheaven.minenow`. Java 21.

## Vision

Player-built villages with NPC workers ("inhabitants") who take jobs and work claimed areas — inspired by MineColonies, but built around player agency (players design layouts/structures rather than auto-placed buildings).

## Source layout (`src/main/java/com/sheepfromheaven/minenow/`)

- `core/` — registration (items, blocks, entities, etc.)
- `dominion/` — territory/town-banner ownership system
- `inhabitant/` — NPC entity, AI goals, inventory, immigration
- `jobs/` — one subpackage per job (`lumberjack`, `miner`, `farmer`, `fisher`, `cook`, `courier`, `forester`, `charcoalburner`) — each typically has `client/` and `gametest/` sub-subpackages
- `structure/` — placeable structures, work areas, mine claims, fields
- `storage/` — storage blocks/inventory
- `effect/`, `requirement/`, `pathfinding/`, `network/`, `command/`, `dev/` — supporting systems
- `*/gametest/` — in-game integration tests per feature area; `*/client/` — client-only rendering/screens

## Workflow

- **TDD**: write a failing GameTest first, then implement. See `feedback_tdd_workflow` memory.
- **Verify with GameTests, not just compile**: `./gradlew runGameTestServer` — a green compile can still hide a broken tag/behavior. Don't declare a task done on compile alone.
- `@GameTest` annotations don't register on this build; tests are wired via `RegisterEvent` + JSON (see `reference_forge_gametest_setup` memory).
- Custom block tags live in `tags/block/` (singular), not `tags/blocks/` — wrong folder fails silently.
- New registered items must be added to `ModCreativeTabs`' `displayItems`.
- UI screens: use vanilla widgets/sprites, don't hand-roll custom-drawn ones.
- ARGB colors: always include the alpha byte — omitting it renders fully transparent.
- Job AI: share tick-loop logic via static helpers (`JobActions`), not a forced state-machine base class.

## Token-cost hygiene (read before running builds/tests)

- Compile before running gametests: `./gradlew compileJava` first, `runGameTestServer` only once that's clean. A compile error inside a full gametest run produces far more noisy output than catching it standalone.
- Always add `--console=plain -q` to gradle invocations to suppress Minecraft/Forge startup spam (asset indexing, mod discovery), e.g. `./gradlew runGameTestServer --console=plain -q 2>&1 | tail -80`.
- Never dump raw `./gradlew runGameTestServer` / build output into context. Pipe through `grep`/`tail` for the failure only, e.g. `./gradlew runGameTestServer --console=plain -q 2>&1 | tail -80` or `| grep -A20 FAILED`.
- Don't `cat`/`Read` full log files under `run/logs/`, `run/crash-reports/`, or `build/`; grep for the specific error first. (These are also permission-denied for `Read` in `.claude/settings.json` — use `grep`/`tail` via Bash instead.)
- For "find X" or "where is Y defined" questions, use the `Explore` agent instead of reading many files directly in the main conversation.
- Prefer `Edit` over re-reading a file you just wrote to confirm it — the tool already errors on failure.
