# Storage

Generic container/inventory plumbing used across features, plus the Filter item that lets a player restrict
what a storage structure accepts. Not structure-specific — `structure/` and `jobs/` are the main consumers.

## Key files

- `StorageItems.java` — `DeferredRegister` for `FILTER`; `register(modBus)` called from `MineNow`.
- `ContainerUtil` / `ContainerActionPacer` (in `jobs/`, not here) — the actual container search/insert/extract
  helpers jobs use; `storage/` supplies the container/inventory *shapes* they operate on, not the search logic
  itself.
- `inventory/VanillaRowMenu.java` — a concrete, reusable menu: some number of leading one-slot "tool"
  containers (e.g. an equipment-slot adapter) on the first row, a one-cell gap, then a `columns`x`rows` grid
  of general slots — all laid out on vanilla's own 18px chest grid so the screen can reuse the real
  `generic_54.png` texture instead of a custom background. No subclassing needed to use it.
- `inventory/VanillaRowInventoryKind.java` — declares one reusable `VanillaRowMenu` *shape* (tool-slot count
  baked into the bound `MenuType`, general grid size) and exposes `createView(title, toolContainers,
  generalInventory)` to open it for a player with real per-instance containers — no menu/screen subclass
  required per usage site. (Not yet instantiated anywhere in the codebase — a ready-made pattern for the next
  vanilla-chest-shaped inventory screen, per its own class doc example.)
- `inventory/EquipmentSlotContainer.java` — adapts a single `LivingEntity` equipment slot into a one-slot
  `Container`, for plugging an equipped item into a `VanillaRowMenu`'s tool-slot row.
- `inventory/ModMenus.java` — `DeferredRegister<MenuType<?>>` for `FILTER_TYPE`, plus `STRUCTURE_FILTER_TYPE`
  and `DOMINION_TYPE` (owned conceptually by `structure/`/`dominion/` but registered here since there's no
  other shared menu-registry home); `register(modBus)` called from `MineNow`.
- `inventory/FilterMenu.java` — real drag-and-drop menu for configuring a `FilterItem`: a 3x3 grid of
  template slots backed by a throwaway `SimpleContainer` (never the item's own data directly) plus the
  player's inventory. Every interaction with a template slot is intercepted (`clicked`/`quickMoveStack`) to
  copy only the clicked stack's *identity* into the templates, never actually transferring a real item;
  `removed()` writes the final template list + whitelist/matchMetadata flags onto the held `FilterItem`'s
  `ModDataComponents.FILTER_DATA` component.
- `item/FilterItem.java` — shift-right-click opens a `FilterMenu` seeded from the stack's current
  `FilterData`.
- `item/FilterData.java` — the persisted/networked record: up to 9 template `ItemStack`s + `whitelist` +
  `matchMetadata`, with both a `Codec` (save) and `StreamCodec` (network).
- `client/FilterScreen.java` — `SpecContainerScreen` (screenspec-driven) for `FilterMenu`; toggle buttons for
  whitelist/blacklist and metadata-matching send button clicks through the menu.
- `gametest/FisheryStorageTests.java`, `gametest/VanillaRowMenuTests.java` — wired via `RegisterEvent` in
  `MineNow`, not `@GameTest`.

## Storage block/container/inventory pattern

There's no mod-specific storage *block* here — storage structures (storehouse, fishery, etc., see
`structure/CLAUDE.md`) use vanilla containers (barrels/chests) found via `DominionStructure.findStorageContainers`,
and jobs move items through them via `jobs/ContainerUtil`. This package's job is purely the *menu/inventory*
layer: reusable container shapes (`VanillaRowMenu`) and the Filter item that a storage structure consults to
accept/reject a deposit (see `jobs/CLAUDE.md`'s `ContainerUtil` note and the `CourierJob` filter-check tests).

## How to extend

- New vanilla-chest-shaped inventory screen (tool slots + a general grid): declare a `VanillaRowInventoryKind`
  bound to a newly registered `MenuType` in `ModMenus`, then call `createView(...)` wherever you'd open it —
  no new menu/screen class needed.
- New filter-like configurable item: follow `FilterItem`/`FilterMenu`/`FilterData`'s split (item → opens menu
  seeded from a data component → menu intercepts template-slot interactions → `removed()` writes the component
  back).
