# Jobs

Shared driver pattern: adding a job never touches `WorkGoal`/`FinishWorkGoal`/`InhabitantEntity` — only add a `JobType` entry and a `Job` implementation.

## Key files (shared, not per-job)

- `jobs/JobType.java` — enum of job kinds, each holding a `Supplier<Job>` factory (`createJob()`).
- `jobs/Job.java` — interface every job implements: `canStart`, `start`, `tick` (return true = still mid-cycle), `stop`, `onInterrupted`, `save`/`load`, plus UI helpers (`getStatusDescription`, `getMissingItem*`, `getWarnings`, `getDebugLines`).
- `jobs/WorkGoal.java` — the single job-agnostic `Goal` that drives whatever `Job` is currently assigned. Owns the cached `Job` + workplace `UUID`. `assignJob(structure)`/`clearJob()` are called directly by `Dominion.assignWorker`/`unassignWorker` — not derived from entity state each tick.
- `jobs/FinishWorkGoal.java` — higher-priority goal: at night, walks the inhabitant home and stows the equipped tool.
- `jobs/JobActions.java` — static, stateless helpers shared by all jobs: `walkTo`/`goToJobsite`, `tickSwing`/`mineBlockWithTool`/`placeBlockWithTool`, `takeFromContainer`/`depositCarriedItems`/`searchStructureForItem`. Each job keeps its own phase enum + state record; `JobActions` never owns state itself. Dedupe tick-loop logic here, not via a forced base class.
- `jobs/ContainerActionPacer.java` / `jobs/ContainerUtil.java` — container cooldown pacing and low-level search/insert/extract helpers used by the `JobActions` container methods.

## Per-job shape (e.g. `jobs/lumberjack/`)

- `XxxJob.java` — the `Job` impl: a phase enum (e.g. `GOTO_HUT → FIND_AXE → GOTO_TREE → CHOPPING → ... → DEPOSIT`) driven in `tick()` via `JobActions` calls; persists phase/targets in `save`/`load`.
- `XxxItems.java` — `DeferredRegister` for the job's items; `register(modBus)` called from `MineNow`'s constructor.
- `client/` — static gizmo/outline renderer classes hooked from `MineNow`'s client tick listener (e.g. `TreeScanGizmoRenderer.onClientTick()`), not self-registered.
- `gametest/*Tests.java` — test methods wired via `RegisterEvent` on `Registries.TEST_FUNCTION` in `MineNow` (NOT `@GameTest` annotations — those don't register on this build).

## Runtime sequence

1. **Structure↔job link**: `StructureType.getJobType()` maps a structure kind to at most one `JobType` (e.g. `LUMBERYARD → LUMBERJACK`; `HOUSE` has none).
2. **Assignment**: `Dominion.assignWorker(structure, inhabitantId, level)` → `InhabitantEntity`'s cached `WorkGoal.assignJob(structure)` — stops/interrupts any previous job, then `JobType.createJob()` builds the new one. Unassign → `WorkGoal.clearJob()`.
3. **AI tick**: `InhabitantEntity.registerGoals()` wires priority 2 = `FinishWorkGoal`, priority 3 = the cached `WorkGoal`. Each vanilla goal tick: `WorkGoal.canUse()` checks `workplace.canWork(...)` + `job.canStart(...)` → `start()` → `job.start(...)` → repeated `tick()` → `job.tick(...)` → `stop()` → `job.stop(...)` (+ `job.onInterrupted(...)` if a cycle was mid-flight — jobs should fall back to a "return to workplace" phase, not resume in place).
4. **Persistence**: `InhabitantEntity` save/load → `WorkGoal.save/load` → `Job.save/load`; on load, `WorkGoal.loadJobIfMissing()` recreates the `Job` from the saved workplace id before applying saved phase state.
5. **Work area**: a job never computes its own geometry — it asks `DominionStructure workplace` (`findTreeShapeInRange`, `findPlantableSpotInRange`, `findApproachPos`, `findStorageContainers`). Structure-granted `WorkArea`s (currently only `CylinderArea`) come from `StaticWorkAreaEffect`s per level; `MineClaim`/`Field` extend a `MINE`/`FARM`'s area via `assignedStructureId`.

## Adding a new job

1. Add a `JobType` entry with a `Job::new` factory.
2. Implement `Job` under `jobs/<newjob>/` with its own phase enum + `JobActions` calls.
3. Point a `StructureType` at the new `JobType`.
4. No changes needed to `WorkGoal`, `FinishWorkGoal`, or `InhabitantEntity`.
