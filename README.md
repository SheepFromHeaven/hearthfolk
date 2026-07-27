# MineNow

MineNow is a Minecraft Forge mod that lets you grow a player-built settlement into a living village, staffed by NPC inhabitants who move in, work, and go about their lives. It's inspired by [MineColonies](https://github.com/ldtteam/minecolonies), but takes a lighter-weight, more hands-off approach: you build the structures, and the mod figures out who lives where and what they do.

## Concept

You place down buildings that match recognized structure types. As your settlement fills out, inhabitants move in on their own, take on jobs, and satisfy their needs — sleeping in beds, eating, working — using vanilla-style AI and pathfinding. There's no colonist recruitment screen or building menu to manage; the world itself is the interface.

## Features

- **Dominions** — a player-claimed area of land that tracks its own inhabitants, structures, and growth. Dominions level up through tiers as they grow:
  - Settlement (up to 3 inhabitants)
  - Village (up to 8 inhabitants)
  - Town (up to 20 inhabitants)
  - City (up to 50 inhabitants)
- **Structure detection** — buildings are recognized automatically by scanning for rooms and matching block/interior requirements (e.g. a `House` needs a bed and a door; a `Fishery` needs water access). No schematics or placement menus required.
- **Inhabitants** — villager-like NPCs that spawn into a dominion, path home to sleep, and hold down jobs.
- **Jobs** — inhabitants can be assigned work (e.g. Fisher) that lets them gather resources for the dominion.
- **Needs** — inhabitants track basic needs (Shelter, Food, Rest) that drive their behavior.
- **In-world markers** — structure and dominion ranges are rendered directly in the world as boundary walls, so you can see coverage and requirements without opening a UI.
- **Vanilla-styled UI** — dominion, inhabitant, and structure screens use vanilla widgets and sprites for a consistent, native feel.

## Credits

MineNow is heavily inspired by [MineColonies](https://github.com/ldtteam/minecolonies) — thank you to the LDT Team for pioneering the idea of player-built villages full of working NPCs in Minecraft. MineNow takes a different approach to that same idea:

| | MineColonies | MineNow |
|---|---|---|
| Building placement | Buildings are placed from schematics/blueprints via a Build Tool, then built up through defined levels | Buildings are ordinary player construction; any structure that meets a block/room requirement is recognized automatically |
| Colonist management | Colonists are hired/assigned through GUIs, given specific jobs and work orders by the player | Inhabitants move in and take on jobs on their own as the dominion grows; the player doesn't directly manage individuals |
| Interface | Deep GUI-driven management (colony overview, work orders, citizen screens, requests) | Minimal, in-world-first — dominion/structure ranges are shown as markers in the world; UI is a thin vanilla-styled layer, not the primary way to interact |
| Progression | Colony levels raise building caps and unlock new buildings/schematics | Dominion tiers (Settlement → Village → Town → City) raise the inhabitant cap as the population and structures grow |
| Scope | A large, deep colony-simulation mod with many buildings, jobs, and systems | A smaller, lighter-weight take focused on organic growth from player-built structures rather than menu-driven colony management |

## Building from source

This is a standard [Minecraft Forge](https://minecraftforge.net/) MDK project.

```bash
./gradlew build
```

To run the mod in a development client or server:

```bash
./gradlew runClient
./gradlew runServer
```

## Requirements

- Minecraft 1.21.11
- Forge 61.1.0+
- [GeckoLib](https://github.com/bernie-g/geckolib) 5.4+

## License

See [LICENSE.txt](LICENSE.txt).
