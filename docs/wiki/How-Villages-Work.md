# How Villages Work

## Villages

A village grows out of the bells villagers have claimed. In the late afternoon a villager walks to a bell it can reach (within 48 blocks) and claims it as its meeting point. Every villager's claimed bell, bed, job block and chests are its places. A bell nobody has claimed makes no village.

Places within 32 blocks of each other (48 up or down) join up, whoever's they are, so beds and workshops between two bells make them one village however far apart the bells are. A group of places with a claimed bell in it is a village, and its area is everything within 16 blocks of its places. Only villagers that are loaded count, and the village list is rebuilt about every 5 seconds.

A banner standing on a hay bale counts as a place nobody has to claim, as long as some villager's place is within 32 blocks of it. It links the places near it, and the village border then also reaches 16 blocks around the banner. Use it to join a far bed to the village.

Trading, commissions and the alarm stay inside one village. Two villages do not trade directly. Merchants carry goods between them.

With the Villager Inspector, left-click a bell to draw the village border: lime lines for the border, orange where it would grow if the free beds and job blocks outside it were claimed. Right-click a bell for a report listing the village's loaded villagers (trade, level, position), the number of free beds and each free job block. Blocks and beds that are yours never count as free.

## Blocks that are yours

Job blocks, chests and barrels that you place yourself are yours, and villagers leave them alone. The job blocks this mod adds (furnace minecart, nursery, golem workshop, pack station) and anything the world generated stay claimable.

Sneak and right-click a job block, chest or barrel with an emerald in your main hand to switch it. A block of yours becomes claimable (green sparkles). Any other one becomes yours (smoke). The emerald is not used up, and a double chest switches both halves. A villager that holds a block gives it up when you make it yours, and the goods in its chest drop as the villager's belongings.

A bed you have slept in, or that is your respawn point, is yours too. The emerald does not work on beds. Break the bed to free it.

## Homes and chests

- Each adult villager with a bed claims one free plain chest within 8 blocks of it, or a barrel if no chest is free. Trapped chests, chests with a hopper under them, and blocks that are yours are skipped. It is locked for players while the Villager Chests Locked setting is on. A double chest where one half is yours is skipped too.
- A working villager also claims free chests, then barrels, within 6 blocks of its job block and 2 up or down, as many as its level (novice 1, master 5), with the same exclusions. A fisherman's own barrel counts as its first. Trade goods go to the workshop chests, food and emeralds stay at home.
- Breaking a villager's chest is not theft. The contents drop as the villager's, you cannot pick them up, and it gathers them back. `/minenow free` frees claimed items within 32 blocks.
- Draining a villager's chest with a hopper or hopper minecart, or letting a hopper swallow its dropped belongings, is theft, blamed on whoever placed the hopper. Theft does not apply when chest locks are off.

## Money

Villagers carry emeralds. A villager gets a starting purse of 6 the first time it takes a job, once in its life. It then earns by selling goods to players and other villagers. The village earns new emeralds by exporting goods to travelling merchants and through miners digging emerald ore.

When buying, a villager keeps a reserve: 2 emeralds of food money plus the price of its trade's cheapest tool. It dips into the reserve, down to the food money, only to buy a tool or fuel it has none of. A craftsman who cannot afford a tool or a lot of a supply can take it on credit, up to 3 emeralds owed in all, and pays it back from earnings.

Most prices are decimal numbers of emeralds per item. They turn into the smallest whole-emerald deal, so cheap goods come in big lots (raw copper is 50 for 3 emeralds).

## Food and children

Every adult eats twice a day, alternating bread and vegetables with cooked meat or fish. Food comes from its pockets or any of its chests.

A villager with enough emeralds that holds under 12 breeding food points buys food, and vanilla breeding does the rest. A child also needs a willing partner and a free bed in the village or within 48 blocks of its border. Beds a player has used are never free. What counts as enough emeralds grows with the village (3 plus 1 per villager, between 4 and 40, set in `wealth/breeding.json`), so a crowded village needs to be richer to grow.

Children bring money into the village when they grow up and take a job.

## Levelling

Villagers earn experience from working (harvesting, digging, crafting, fishing and so on), capped at 24 points per villager per day, about ten days of steady work from novice to master. Levels unlock bigger fields, deeper mines, longer tunnels and better recipes.

## Defence

Armed villagers fight instead of panicking, as long as they are above a third of their health. About one in three is an archer for life. Villagers buy and wear armor and keep a sword or bow at their side.

When a bell rings or a raid starts, armed villagers muster. Swordsmen gather at the meeting point or on the threatened side of the village. Archers take a post: the hay bales in the village are the marked posts. Without a free marked post they look for high ground within 12 blocks of the edge facing the threat.

## Roads

Villagers prefer walking on paths and roads, and idle ones stroll along them. Dirt paths are tier 1, gravel and cobblestone tier 2, bricks, planks, slabs and stairs tier 3. From apprentice, a mason with a redstone signal at its stonecutter paves dirt paths for you.

## Travelling merchants

When a player is nearby and a village holds enough export goods or unmet needs, a travelling merchant with two pack mules can appear on open ground near the villager with the most to trade. It buys export goods for new emeralds, announces its market at the village bell, and sells goods the village cannot make (string, sugar cane, honeycomb, nether wart and others). It comes at most every 5 minutes, and not at all if the Travelling Merchants setting is off or a wandering trader is within 96 blocks.

A village merchant, a profession you build, is a different thing: it carries goods from its own village to other loaded villages and sells them there. See [Professions](Professions).
