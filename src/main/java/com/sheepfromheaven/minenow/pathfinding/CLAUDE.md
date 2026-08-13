# Pathfinding

Player-placed soft movement hints: no physical block, just a position inhabitants prefer to walk near/through.
Unordered — nodes form a preferred zone, not an explicit path graph.

## Key files

- `DominionPathNode.java` — a node: `id` (UUID), `pos`, `Kind`, `Impact`. `Kind` is `STREET` (routing bias,
  the only kind currently consumed) or `PATROL` (reserved for a future guard/patrol use — same data model,
  no consumer yet). `Impact` (`LOW`/`MEDIUM`/`HIGH`) controls `stopRadiusSq()` — how close an inhabitant must
  get before considering the node "reached" (HIGH ≈ 1.5 blocks, MEDIUM ≈ 4, LOW ≈ 10); `next()` cycles
  LOW→MEDIUM→HIGH→LOW.
- `PathfindingItems.java` — `DeferredRegister` for `PATH_NODE_WAND`; `register(modBus)` called from
  `MineNow`.
- `item/PathNodeWandItem.java` — the wand: right-click empty space places a `STREET` node at the wand's
  current impact; right-click an existing node removes it; shift+right-click (in air or on a block) cycles
  the wand's active impact, stored in the stack's `CUSTOM_DATA`. `inventoryTick` pushes the holder's dominion's
  nodes to the client every 20 ticks via `ModNetwork.sendPathNodeSync`.
- `gametest/PathNodeTests.java` — wired via `RegisterEvent` in `MineNow`, not `@GameTest`.
- `client/PathNodeTracker.java` — client-side mirror of the nodes the server has revealed to this player
  (position → `Impact`), populated from `PathNodeSyncPacket`.
- `client/PathNodeGizmoRenderer.java` — draws a small hovering arrow gizmo (cone + cylinder shaft, colored by
  impact) at each tracked node's position, but only while the player is actually holding the wand — nodes are
  otherwise invisible per the design. Built from custom `Gizmo`s rather than a ghost block model (block-model
  rendering here was tried and confirmed not to work).
- `client/PathNodeWandTintSource.java` — colors the wand's texture (green/yellow/red) based on its active
  `Impact`; registered under `minenow:path_node_wand`, referenced by the item model JSON's `tints` array.
- `client/RangeTracker.java` — unrelated small client-only toggle set (which structure markers are currently
  showing their range gizmo); lives here for lack of a better home, not part of the path-node system proper.

## Runtime sequence: place → sync → bias pathing

1. **Placement**: `PathNodeWandItem.useOn` finds the dominion containing the clicked pos via
   `DominionSavedData.findDominionContaining`; toggles a node at that position (`dominion.addPathNode`/
   `removePathNode`), marks the saved data dirty.
2. **Sync to client**: while the wand is held, `PathNodeWandItem.inventoryTick` sends the holder's dominion's
   full node list (positions + impact ordinals) via `ModNetwork.sendPathNodeSync` → `PathNodeTracker.set` on
   the client every 20 ticks.
3. **Rendering**: `PathNodeGizmoRenderer.onClientTick()` (hooked from `MineNow`'s client tick listener) draws
   every tracked node, only while the wand is in either hand.
4. **Pathing influence** (consumers live in `inhabitant/`, not here): `InhabitantNodeEvaluator` applies a
   distance-scaled cost malus toward the nearest `STREET` node at the pathfinding-cost level, so every path
   search (going home, going to work, wandering) is nudged toward routing near/through nodes when a
   comparable alternative exists. `StrollTowardPathNodeGoal` is a separate idle-wander goal that, every so
   often, heads straight for the nearest `STREET` node instead of picking a random direction — using the
   node's `Impact.stopRadiusSq()` to decide how close counts as "arrived". See `inhabitant/CLAUDE.md` and
   `dominion/CLAUDE.md` for how `Dominion` stores/looks up nodes (`getPathNodes`, `findNearestPathNode`,
   `getPathNodeAt`).

## How to extend

- New node kind: add a `Kind` enum entry; nothing else in this package needs it (consumers filter by kind
  themselves, e.g. `.filter(n -> n.getKind() == DominionPathNode.Kind.STREET)`).
- New wand behavior: extend `PathNodeWandItem`; the tint source and gizmo renderer key off `Impact`, not the
  item class, so they need no changes for a same-shape new node kind.
- New pathing consumer: read `dominion.getPathNodes()`/`findNearestPathNode(kind, pos)` — no changes needed
  here.
