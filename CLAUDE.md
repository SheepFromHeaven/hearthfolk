# MineNow

NeoForge 26.3 mod (NeoForge `26.3.0.101`, mod loader plugin `net.neoforged.moddev`). Group/mod id: `com.sheepfromheaven.minenow`. Java 25.

## Vision

Vanilla villagers that get better at their existing professions in the farms and workshops the player builds. No custom
mob, no town banner, no screens. Vanilla already provides houses (beds), workplaces (workstations), growth (breeding)
and schedules; we only add behaviours to the villager brain. Professions are added one at a time, farmer first.

## Source layout (`src/main/java/com/sheepfromheaven/minenow/`)

- `MineNow.java` — mod entry point, event wiring, gametest registration
- `villager/BrainHook` — adds our behaviours to every adult villager's brain (re-applied whenever vanilla rebuilds it)
- `villager/HomeStorage` — each adult villager claims one chest within 8 blocks of its bed (custom `home_storage` POI on chests), locks it for players, releases it on death/conversion; right-clicking it says whose it is; a player breaking it is theft (negative gossip). `ModVillagers` holds the POI type and the `storage`/`placed_by` data attachments
- `villager/Economy` — a villager's goods are its inventory plus its home chest: counting, taking, giving, and the one-time starting purse granted the first time a player opens trading
- `villager/TradeTable` — per-profession prices and level unlocks from `data/<ns>/villager_trades/<profession>.json` (reloads with `/reload`); `tools` are bought when the villager has none, `produce` is sold per emerald; a level unlocks its entries and keeps earlier ones
- `villager/VillagerNames` — every villager without a custom name gets one from `data/<ns>/villager_names/*.json` (all files merged) when it joins the world; vanilla then shows it on hover and in the trade title ("Jim - Novice")
- `villager/StorageTheft` — any unexplained drop in an owned chest's contents is theft, blamed on whoever placed the hopper or hopper minecart under it. Code that takes items out of a villager's chest must call `StorageTheft.check` before and `recordContents` after, or it will be counted as theft
- `farmer/Field` — recomputed on every scan (when the farmer starts work or runs out of it); a farmer's field is the ground touching a side of its composter (corners don't count) and what connects to it: farmland plus tillable ground (`turns_into_farmland` tag) within vanilla's 4-block watering range, joined through water, surface only, within a range that grows with the farmer's level (novice 10, apprentice 13, journeyman 16, expert 20, master 24). Water is the boundary players build with
- `farmer/` — `TendFields` behaviour (harvest/replant/till its connected field, unload into the farmer's own home chest; needs a hoe, holds it, swings it while reaping for as long as the hoe's mining speed dictates, wears it down, fetches a spare from the home chest; lives in CORE right after `MoveToTargetSink` so standing still to reap isn't undone by WORK's walk back to the job site), `PickUpGiftedHoe` (picks up a hoe thrown to a hoe-less farmer), `FarmerTrades` (offers rebuilt from needs, goods and villager level on every right-click: buys an undamaged hoe when it has none, sells crops beyond what it keeps; real items move on `TradeWithVillagerEvent`, and a bought hoe arrives with its enchantments), `FarmerRules` (denies farmers mob-griefing, which switches off vanilla's tool-free `HarvestFarmland` and their vanilla loot pickup) and its gametests
- `client/` — `SwingingVillagerRenderer` replaces vanilla's villager renderer so the crossed arms (and held tool) move with swings; registered in `MineNowClient`
- `dev/GametestPlayground` + `client/GametestPlaygroundWorld` — the `runGametestPlayground` run: opens a fresh creative test world and lays out every `minenow:*` gametest in a grid, idle, to start and watch from its test instance block
- `gametest/` — shared gametest helpers (`fence` lays the stone floor and barrier walls; spawning employed/housed villagers). The test structure's own floor doesn't load, so every test must call `fence` first
- `src/main/resources/data/minenow/test_instance/` — one JSON per gametest; `structure/field_24x6x24.nbt` is the empty stone-floored test space

## Workflow

- **TDD**: write a failing test first, then implement — a GameTest if the behavior needs a live `Level`
  (blocks, entities, ticking), a plain JUnit test under `src/test/java` only if it's pure logic that touches no
  items: on 26.3 item components are bound when datapacks load, so `new ItemStack(...)` throws "Components not bound
  yet" in unit tests. Anything with items is a gametest (they can be instant, see `better_hoes_reap_faster`). See
  `feedback_tdd_workflow` memory.
- **Verify with GameTests, not just compile**: `./gradlew runGameTestServer` — a green compile can still hide a broken tag/behavior. Don't declare a task done on compile alone. For pure-logic changes covered by `src/test/java`, `./gradlew test` is the equivalent gate.
- `@GameTest` annotations don't register on this build; tests are wired via `RegisterEvent` + JSON (see `reference_forge_gametest_setup` memory).
- Custom block tags live in `tags/block/` (singular), not `tags/blocks/` — wrong folder fails silently.
- ARGB colors: always include the alpha byte — omitting it renders fully transparent.
- Villager AI: add `Behavior<Villager>`s to vanilla brain activities via `BrainHook`; no mixins unless a vanilla hook is truly missing.
- Per-villager state that must survive reloads goes in a NeoForge data attachment, not a custom brain `MemoryModuleType`: the brain drops saved memories that aren't registered at rebuild time, and ours are only added afterwards.

## Token-cost hygiene (read before running builds/tests)

- Compile before running gametests: `./gradlew compileJava` first, `runGameTestServer` only once that's clean. A compile error inside a full gametest run produces far more noisy output than catching it standalone.
- Always add `--console=plain -q` to gradle invocations to suppress Minecraft/Forge startup spam (asset indexing, mod discovery), e.g. `./gradlew runGameTestServer --console=plain -q 2>&1 | tail -80`.
- Never dump raw `./gradlew runGameTestServer` / build output into context. Pipe through `grep`/`tail` for the failure only, e.g. `./gradlew runGameTestServer --console=plain -q 2>&1 | tail -80` or `| grep -A20 FAILED`.
- Don't `cat`/`Read` full log files under `run/logs/`, `run/crash-reports/`, or `build/`; grep for the specific error first. (These are also permission-denied for `Read` in `.claude/settings.json` — use `grep`/`tail` via Bash instead.)
- For "find X" or "where is Y defined" questions, use the `Explore` agent instead of reading many files directly in the main conversation.
- Prefer `Edit` over re-reading a file you just wrote to confirm it — the tool already errors on failure.
