# How Villages Work

## Villages

A village grows out of its bells. Every villager's claimed bell, bed, job block and chests are its places. Places within 32 blocks of each other (48 up or down) join up, so beds and workshops between two bells make them one village. A group of places with a bell in it is a village, and its area is everything within 16 blocks of its places.

A banner on a hay bale also counts as a place nobody has to claim, and links places within 32 blocks. Use it to join a far bed to the village.

Trading, commissions and the alarm stay inside one village. Two villages never trade directly (merchants carry goods between them).

Left-click a bell with the Villager Inspector to draw the village border: lime lines for the border, orange where it would grow if the free beds and job blocks outside it were claimed. Right-click a bell for a report that lists the villagers and the free beds and job blocks.

## Homes and chests

- Each adult villager claims one chest within 8 blocks of its bed (or a barrel if no chest is free). It is locked for players.
- A working villager also claims chests within 6 blocks of its job block, as many as its level (novice 1, master 5). Trade goods go there, food and emeralds stay at home.
- Breaking a villager's chest is not theft. The contents drop as the villager's and it gathers them back.
- Placing a hopper under a villager's chest and draining it is theft, blamed on whoever placed the hopper.

## Blocks that are yours

A job block, chest or barrel you place yourself is yours and villagers leave it alone. A bed you sleep in is yours too. Sneak and right-click one while holding an emerald to hand it over: villagers may then claim it. Do it again to take it back (a villager holding it lets go first). Blocks generated with the world stay claimable.

## Money

Villagers carry emeralds. A villager gets a starting purse of 6 the first time it takes a job, once in its life. It then earns by selling goods to players and other villagers, and the village earns new emeralds by exporting goods to travelling merchants and by miners digging emerald ore.

A villager never spends below its reserve: 2 emeralds of food money plus the price of its trade's cheapest tool. A craftsman who cannot afford a tool or supply can take up to 3 emeralds on credit from a seller and pays it back from earnings.

Prices are one decimal number of emeralds per item. They turn into the smallest whole-emerald deal, so cheap goods come in big lots (raw copper is 50 for 3 emeralds).

## Food and children

Every adult eats twice a day, alternating bread and vegetables with cooked meat or fish. Food comes from its pockets or home chest. A villager with enough emeralds buys breeding food and vanilla does the rest. What counts as enough grows with the village (3 plus 1 per villager, between 4 and 40, set in `wealth/breeding.json`), so a crowded village needs to be richer to grow.

Children bring money into the village when they grow up and take a job.

## Levelling

Villagers earn experience from working (harvesting, digging, crafting, fishing and so on), capped at about 24 points per villager per day, which is about ten days of steady work from novice to master. Levels unlock bigger fields, deeper mines, longer tunnels and better recipes.

## Defence

Armed villagers fight instead of panicking. About one in three is an archer for life. Villagers buy and wear armor and keep a sword or bow at their side.

When a bell rings or a raid starts, armed villagers muster. Swordsmen gather at the meeting point or on the threatened side of the village. Archers climb to a post: hay bales you place up high are the marked posts. Without marked posts they find high ground within 12 blocks of the edge facing the threat.

## Roads

Villagers prefer walking on paths and roads, and idle ones stroll along them. Dirt paths are tier 1, gravel and cobblestone tier 2, bricks, planks, slabs and stairs tier 3. A mason with a redstone signal at its stonecutter paves paths for you.

## Travelling merchants

Once a village exports enough, a travelling merchant with two pack mules arrives at the bell, announces its market, buys export goods for new emeralds and sells goods the village cannot make (string, sugar cane, honeycomb, nether wart and others). Village merchants (a profession) do the same between your villages.
