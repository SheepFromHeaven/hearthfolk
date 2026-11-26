# Settings

Every world keeps its own settings, so one world can have fighting villagers and another none.

Open the mod list, pick Hearthfolk and press Config (in a world, also through the pause menu's Mods button).

- In single player, it edits the world you are in and applies at once.
- From the main menu, it edits the defaults that new worlds start with.
- On a server or someone else's LAN world, it is read only. The server keeps its own settings.

A new world starts from the defaults in `config/minenow-local.toml`. A world saved before a switch existed keeps its behaviour for the others and gets the toml default for the new one. Settings of a world are stored in `data/minenow/settings.dat` in its save.

## Village life

| Setting | On | Off |
| --- | --- | --- |
| Villagers Defend the Village | Armed villagers fight, man archer posts and muster at the bell. | Everyone panics and hides as in vanilla. A bell only warns those in earshot. |
| Villagers Eat Meals | Two meals a day from pockets and home chest. | Nobody eats. Breeding food is still bought. |
| Emerald Economy | Villagers keep emeralds, pay each other and spend only what they can afford. | Emeralds are not counted. Villagers take what they need for free, buy whatever you sell them and keep none of what you pay. |
| Villagers Follow Emeralds | Jobless villagers follow a player holding an emerald. | They ignore it. |
| Villager Chests Locked | Chests, barrels, armor stands and wall displays are locked for players. | You can open and take from them and it is not theft. |
| Villagers Prefer Roads | Villagers prefer paths and stroll along them. | They walk as in vanilla. |
| Villagers Level Through Work | Work earns experience. | Only trading levels them up, as in vanilla. |
| Villagers Get Names | New villagers get a name, births and deaths are announced. | New villagers stay nameless. Names already given stay. |
| Villagers Heal | Recently fed villagers heal half a heart every 4 seconds. Needs Eat Meals. | They only heal through a cleric. |
| Speech Bubbles | Villagers show their needs and chat in bubbles. | No bubbles. |
| Travelling Merchants | Export goods draw a travelling merchant. | None come. |
| Villagers Summon Golems | As in vanilla. | The golemsmith builds a village's golems. Default off. |
| Ink and Quill Recipes | You can craft the ink and quill and use it in a book and quill. | Vanilla's recipe stays. Default off. |
| Merchant Tours at Once | How many village merchants may tour at once (default 4). A touring merchant keeps nearby chunks loaded. | |

## Professions

One switch per profession: farmer, fisherman, shepherd, butcher, librarian, cleric, mason, cartographer, toolsmith, fletcher, armorer, weaponsmith, leatherworker, miner, forester, smelter, golemsmith and merchant. All on by default.

For a vanilla profession, off means it works and trades as in vanilla and takes no part in villager trading. Its vanilla trades are rolled once and stay the same each time it is off, so toggling never rerolls them. Anything it already set up (locked displays, racks) stays locked.

For miner, forester, golemsmith and merchant, off means villagers lose the job and keep their items and experience. A smelter at a blast furnace becomes an armorer. Switch it back on and they take a block again at their old level. Placed job blocks stay where they are.

## The toml

`config/minenow-local.toml` holds the defaults. It has top-level keys for `villagersSummonGolems`, `travellingMerchants`, `inkAndQuillRecipes` and `maxMerchantTours`, then a `[features]` section with one key per village life switch and a `[professions]` section with one key per profession. Each key has a comment. The file reloads live, but only new worlds read it.

## Commands

`/minenow free` frees claimed items within 32 blocks, for things of yours a villager's claim has locked. See [Commands and the Inspector](Commands-and-the-Inspector).
