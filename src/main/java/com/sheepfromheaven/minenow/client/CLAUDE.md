# Client (top-level)

Generic client-side UI building blocks not tied to any one feature. Feature-specific screens/renderers live
in that feature's own `client/` subpackage instead (e.g. `structure/client/StructureMarkerScreen.java`,
`inhabitant/client/InhabitantScreen.java`, `pathfinding/client/PathNodeGizmoRenderer.java`) — this package is
only for widgets/screens shared across features or with no single feature owner.

## Key files

- `RequestsScreen.java` — cross-dominion view of all pending item requests, grouped by structure (urgent in
  red, soft/proactive in grey); opened via `ModNetwork.sendRequestRequestsScreen`/`OpenRequestsScreenPacket`.
  Not feature-specific — it aggregates requests from every structure in a dominion.
- `ConfigScreen.java` — the NeoForge mod-config screen (`IConfigScreenFactory`) for `core/Config`'s values
  (dominion radius, max room size, max trunk size, debug-inhabitant-view). Registered from `MineNow` via
  `registerExtensionPoint`, kept out of `MineNow`'s constructor body so that class's bytecode never
  references `Screen` (which would crash mod construction on a dedicated server).
- `BeveledBox.java` — shared drawing/label helpers (bevel border colors, `withAlertMark` for appending a red
  "!" to a label) reused by multiple screens.
- `NiceTabButton.java` — a folder-style tab button using vanilla's own `TabButton` sprites, sized for a small
  floating panel rather than a full-screen tab bar; used wherever a screen needs tabs without vanilla's
  full-width tab navigation.

## How to extend

- A screen/widget used by exactly one feature: put it in that feature's own `client/` subpackage, not here.
- A screen/widget genuinely shared across features (like `BeveledBox`'s alert-mark styling, or a new generic
  aggregate view like `RequestsScreen`): add it here.
- Anything referencing `Minecraft`/`Screen`/render classes must never be reachable from a class NeoForge loads
  on a dedicated server (e.g. `MineNow`'s constructor body) — register through a client-only listener or
  extension point instead, as `ConfigScreen.registerExtensionPoint` and `MineNow`'s `Dist.CLIENT` branch do.
