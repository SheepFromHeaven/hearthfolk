# Troubleshooting

Start with `/minenow villager` on the villager or the Villager Inspector. It lists what stops it. The reasons, and what they look like in game:

| Reason | What it means | What to do |
| --- | --- | --- |
| No job site | No job block claimed. It shakes its head, even off duty. | Place a workstation and sneak and right-click it with an emerald, since blocks you place are private. Blocks from generated villages are free. |
| No tool | Its work needs a tool it lacks. If no villager nearby sells one it can afford, it walks to a player within 16 blocks of it (and 24 of its job block) and holds up an emerald if it can afford a tool. | Give it the tool, or let a toolsmith or fletcher supply one. |
| No materials | Butchers and smelters only: out of fuel. After about 30 seconds with no villager selling any, it walks to you holding an emerald if it can afford coal. | Give it any fuel, or add a miner. |
| No money | Butchers and smelters only: out of fuel, nobody sells it and it cannot afford a player's price. It shakes its head while working. | Throw it emeralds or coal. |
| Storage full | Its pockets and chests are full. It shakes its head while working. | Empty its chests or add one: a home chest within 8 blocks of its bed, or a workshop chest within 6 blocks of its job block. |
| No work area | Nothing to work on around its job block. Only farmers, fishermen, shepherds, butchers and miners report this. | Check the layout rules for its job in [Professions](Professions). |

## Common questions

- A villager is not working in the rain: outdoor work only happens on short trips from a roof in rain, so build a roof beside the field, pen or water.
- A villager will not take a job block: a block you placed is private. Sneak and right-click it with an emerald in your main hand to let villagers claim it. Also check the profession is not switched off.
- A chest will not open: it is a villager's. Right-click to see whose. Use the Inspector, or turn off Villager Chests Locked.
- A villager ignores my chest: a chest you placed is private too. Hand it over with an emerald. It must also be a plain chest or barrel with no hopper under it, within 8 blocks of the villager's bed.
- My villagers do not trade with each other: they trade inside one village. Check with the bell report that they share a village, and that a villager has claimed the bell.
- A miner did nothing: its rim must be closed within 8 blocks, made of any cobblestone, with the furnace minecart standing on one of the rim blocks. The cart is claimable straight away.
- A village stopped growing: both parents need 12 breeding food points in their pockets, a free bed, a willing partner and no recent birth. Emeralds only matter when they have to buy the food. The Breeding page of the Inspector says which is missing.
- I cannot pick up items near a villager: they are its belongings, from a broken chest. Use `/minenow free`.
