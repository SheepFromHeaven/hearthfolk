# Network

All custom packets, in one file, registered through NeoForge's `RegisterPayloadHandlersEvent`. There is no
per-feature network subpackage — a new packet for any feature is added here.

## Key files

- `ModNetwork.java` — `@EventBusSubscriber`; everything lives in this one class:
  - `onRegisterPayloads` — registers every packet type (`registrar.playToServer`/`playToClient`) with its
    codec and handler method reference.
  - Serverbound `handleXxx` methods — validate the acting player owns the relevant dominion, then mutate
    server state (`DominionSavedData`, `Dominion`, `DominionStructure`) and mark it dirty.
  - Clientbound `handleXxx` methods — thin delegations to `network/client/ClientNetworkHandlers` (kept
    separate so `ModNetwork`'s bytecode never references `Minecraft`/`Screen`, which would crash channel
    registration on a dedicated server).
  - `sendXxx` static helpers — the public API every other package calls (e.g.
    `ModNetwork.sendOpenStructureScreen(player, dominion, structure)`), wrapping
    `PacketDistributor.sendToPlayer`/`ClientPacketDistributor.sendToServer`.
  - Packet records at the bottom — each a `record ... implements CustomPacketPayload` with a `TYPE` and a
    `StreamCodec` built via `StreamCodec.composite`. Packets with many fields group some into a sub-record
    (e.g. `StructureLevelInfo`) because `StreamCodec.composite` tops out at a fixed argument count.
- `client/ClientNetworkHandlers.java` — the actual client-side effects of clientbound packets: opening or
  updating screens (`StructureMarkerScreen`, `InhabitantScreen`, `RequestsScreen`, `ImmigrantRequestScreen`),
  updating client-side trackers (`PathNodeTracker`, `TreeScanOutlineTracker`). Every method runs on
  `Minecraft.getInstance().execute(...)` to hop onto the render thread.

## Packet registration pattern

1. Serverbound: player action → `ModNetwork.sendXxx(...)` (client) → `ClientPacketDistributor.sendToServer`.
2. Server handles in `ModNetwork.handleXxx(msg, ctx)`: cast `ctx.player()` to `ServerPlayer`, resolve dominion/
   structure from the packet's position/id, check `dominion.getOwnerUuid().equals(player.getUUID())` before
   mutating anything, call `data.setDirty()` after a state change.
3. Clientbound: server-side helper (`ModNetwork.sendOpenXxxScreen`/`sendXxxSync`) →
   `PacketDistributor.sendToPlayer(player, new XxxPacket(...))`.
4. Client handles in `ModNetwork.handleXxx` → delegates to `ClientNetworkHandlers`.
5. Screens that can already be open (e.g. `StructureMarkerScreen`) prefer updating the existing instance
   (`screen.matchesXxx(...)` + `screen.update(...)`) over `mc.setScreen(new ...)`, so a periodic refresh
   packet doesn't flicker/reopen the screen.

## How to add a new packet

1. Add a `record XxxPacket(...) implements CustomPacketPayload` at the bottom of `ModNetwork` with a `TYPE`
   Identifier and a `StreamCodec` (use `StreamCodec.composite`; split into a sub-record if you'd exceed its
   argument limit).
2. Register it in `onRegisterPayloads` (`registrar.playToServer`/`playToClient`) with a `handleXxx` method
   reference.
3. Implement `handleXxx` — serverbound: validate ownership, mutate, `setDirty()`; clientbound: delegate to
   `ClientNetworkHandlers` if it touches `Minecraft`/`Screen`.
4. Add a public `sendXxx(...)` helper for callers elsewhere in the mod to use instead of constructing the
   packet directly.
