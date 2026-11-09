package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class GearTests {
    private static final BlockPos ANVIL = new BlockPos(5, 1, 12);
    private static final BlockPos GRINDSTONE = new BlockPos(5, 1, 6);
    private static final BlockPos COMPOSTER = new BlockPos(15, 1, 12);
    private static final BlockPos COMPOSTER_2 = new BlockPos(15, 1, 6);
    private static final BlockPos FLETCHING_TABLE = new BlockPos(4, 1, 4);

    private GearTests() {}

    public static void aWealthyVillagerBuysArmorAndWearsIt(GameTestHelper helper) {
        Villager armorer = armorer(helper);
        armorer.getInventory().addItem(new ItemStack(Items.IRON_CHESTPLATE));
        Villager farmer = farmer(helper, COMPOSTER);
        farmer.getInventory().addItem(new ItemStack(Items.EMERALD, 40));

        helper.succeedWhen(() -> {
            helper.assertTrue(farmer.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
                    "The farmer buys the armorer's chestplate and wears it, wearing " + farmer.getItemBySlot(EquipmentSlot.CHEST));
            helper.assertTrue(armorer.getInventory().countItem(Items.IRON_CHESTPLATE) == 0, "...out of the armorer's stock");
            helper.assertTrue(farmer.getInventory().countItem(Items.IRON_CHESTPLATE) == 0, "...not carried as goods");
            helper.assertTrue(Economy.spendable(helper.getLevel(), farmer) >= Gear.SAVINGS,
                    "...keeping savings above its reserve, spendable " + Economy.spendable(helper.getLevel(), farmer));
        });
    }

    public static void aVillagerWithoutSavingsBuysNoArmor(GameTestHelper helper) {
        Villager armorer = armorer(helper);
        armorer.getInventory().addItem(new ItemStack(Items.IRON_CHESTPLATE));
        Villager farmer = farmer(helper, COMPOSTER);
        int price = TradeTable.forVillager(armorer).flatMap(table -> table.sellPrice(Items.IRON_CHESTPLATE, 3)).orElseThrow().emeralds();
        farmer.getInventory().addItem(new ItemStack(Items.EMERALD, Economy.reserve(farmer) + price + Gear.SAVINGS - 1));

        helper.runAfterDelay(500, () -> {
            helper.assertTrue(farmer.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && armorer.getInventory().countItem(Items.IRON_CHESTPLATE) == 1,
                    "Armor is bought only with savings to spare above the reserve");
            helper.succeed();
        });
    }

    public static void armorIsBoughtBeforeASword(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setTime(GameTestHelpers.WORK_HOURS);
        helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE);
        Villager weaponsmith = GameTestHelpers.spawnEmployed(helper, GRINDSTONE.east(), GRINDSTONE, VillagerProfession.WEAPONSMITH, PoiTypes.WEAPONSMITH);
        weaponsmith.setVillagerData(weaponsmith.getVillagerData().withLevel(3));
        for (int i = 0; i < 8; i++) weaponsmith.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        Villager armored = farmer(helper, COMPOSTER);
        armored.getInventory().addItem(new ItemStack(Items.EMERALD, 40));
        armored.setData(ModVillagers.ARCHER, false);
        for (Item piece : new Item[]{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS}) Gear.wear(armored, new ItemStack(piece));
        Villager bare = farmer(helper, COMPOSTER_2);
        bare.getInventory().addItem(new ItemStack(Items.EMERALD, 40));

        helper.runAfterDelay(500, () -> {
            helper.assertTrue(Gear.sidearm(armored).is(Items.IRON_SWORD), "A villager in full armor buys a sword and keeps it at its side, has " + Gear.sidearm(armored));
            helper.assertTrue(!armored.getMainHandItem().is(Items.IRON_SWORD), "...not in its hand while there's no danger");
            helper.assertTrue(armored.getInventory().countItem(Items.IRON_SWORD) == 0, "...and not as goods to sell on");
            helper.assertTrue(Gear.sidearm(bare).isEmpty(), "One without armor buys armor first, not a sword");
            helper.succeed();
        });
    }

    public static void anArcherBuysABowAndArrowsInsteadOfASword(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setTime(GameTestHelpers.WORK_HOURS);
        helper.setBlock(FLETCHING_TABLE, Blocks.FLETCHING_TABLE);
        Villager fletcher = GameTestHelpers.spawnEmployed(helper, FLETCHING_TABLE.east(), FLETCHING_TABLE, VillagerProfession.FLETCHER, PoiTypes.FLETCHER);
        fletcher.setVillagerData(fletcher.getVillagerData().withLevel(3));
        for (int i = 0; i < 4; i++) fletcher.getInventory().addItem(new ItemStack(Items.BOW));
        fletcher.getInventory().addItem(new ItemStack(Items.ARROW, 64));
        Villager archer = farmer(helper, COMPOSTER);
        archer.setData(ModVillagers.ARCHER, true);
        archer.getInventory().addItem(new ItemStack(Items.EMERALD, 40));
        for (Item piece : new Item[]{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS}) Gear.wear(archer, new ItemStack(piece));

        helper.succeedWhen(() -> {
            helper.assertTrue(Gear.sidearm(archer).is(Items.BOW), "An archer in full armor buys a bow and keeps it at its side, has " + Gear.sidearm(archer));
            helper.assertTrue(Gear.quiver(archer).getCount() >= Gear.QUIVER_LOW, "...and arrows for its quiver, has " + Gear.quiver(archer).getCount());
            helper.assertTrue(archer.getInventory().countItem(Items.BOW) == 0 && archer.getInventory().countItem(Items.ARROW) == 0, "...neither as goods to sell on");
        });
    }

    public static void anArcherShootsWithTheBowAtItsSide(GameTestHelper helper) {
        Villager archer = farmer(helper, COMPOSTER);
        archer.setData(ModVillagers.ARCHER, true);
        Gear.wear(archer, new ItemStack(Items.BOW));
        Gear.wear(archer, new ItemStack(Items.ARROW, 16));
        Monster husk = standing(helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(9, 1, 12)));

        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "An archer farmer shoots the husk");
            helper.assertTrue(Gear.quiver(archer).getCount() < 16, "...with arrows from its quiver, " + Gear.quiver(archer).getCount() + " left");
        });
    }

    public static void wornArmorProtectsAndWearsDown(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager armored = helper.spawn(EntityTypes.VILLAGER, new BlockPos(6, 1, 6));
        Villager bare = helper.spawn(EntityTypes.VILLAGER, new BlockPos(16, 1, 16));
        Gear.wear(armored, new ItemStack(Items.IRON_CHESTPLATE));
        Monster husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(11, 1, 11));

        helper.runAfterDelay(1, () -> {
            armored.hurtServer(helper.getLevel(), husk.damageSources().mobAttack(husk), 8.0F);
            bare.hurtServer(helper.getLevel(), husk.damageSources().mobAttack(husk), 8.0F);

            helper.assertTrue(armored.getHealth() > bare.getHealth(), "Armor takes some of the blow, " + armored.getHealth() + " vs " + bare.getHealth());
            helper.assertTrue(armored.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() > 0, "...and wears down for it");
            helper.succeed();
        });
    }

    public static void anArmedVillagerFightsAHostileInsteadOfFleeing(GameTestHelper helper) {
        Villager farmer = farmer(helper, COMPOSTER);
        Gear.wear(farmer, new ItemStack(Items.IRON_SWORD));
        Monster husk = standing(helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(10, 1, 12)));
        boolean[] drawn = {false};

        helper.onEachTick(() -> drawn[0] |= farmer.getMainHandItem().is(Items.IRON_SWORD));
        helper.succeedWhen(() -> {
            helper.assertTrue(!husk.isAlive(), "The armed farmer fights the husk instead of fleeing, husk health " + husk.getHealth());
            helper.assertTrue(drawn[0], "...with its sword drawn");
            helper.assertTrue(Gear.sidearm(farmer).getDamageValue() > 0, "...which wears down");
            helper.assertTrue(!farmer.getMainHandItem().is(Items.IRON_SWORD), "...and is put away again once the danger is gone");
        });
    }

    public static void anUnarmedVillagerStillFlees(GameTestHelper helper) {
        Villager farmer = farmer(helper, COMPOSTER);
        Monster husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(10, 1, 12));
        boolean[] panicked = {false};

        helper.onEachTick(() -> panicked[0] |= farmer.getBrain().isActive(Activity.PANIC));

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(panicked[0], "Without a weapon a villager panics as in vanilla");
            helper.assertTrue(husk.getHealth() == husk.getMaxHealth(), "...and doesn't fight");
            helper.succeed();
        });
    }

    public static void aWoundedArmedVillagerFlees(GameTestHelper helper) {
        Villager farmer = farmer(helper, COMPOSTER);
        Gear.wear(farmer, new ItemStack(Items.IRON_SWORD));
        farmer.setHealth(5.0F);
        Monster husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(10, 1, 12));
        boolean[] panicked = {false};

        helper.onEachTick(() -> panicked[0] |= farmer.getBrain().isActive(Activity.PANIC));

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(panicked[0], "A badly wounded villager flees even with a sword");
            helper.assertTrue(husk.getHealth() == husk.getMaxHealth(), "...and doesn't fight");
            helper.succeed();
        });
    }

    public static void anArmedVillagerChasesNoFurtherThanEightBlocks(GameTestHelper helper) {
        Villager farmer = farmer(helper, new BlockPos(4, 1, 12));
        Gear.wear(farmer, new ItemStack(Items.IRON_SWORD));
        var start = farmer.position();
        Monster husk = standing(helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(8, 1, 12)));
        farmer.getBrain().setMemory(MemoryModuleType.HURT_BY_ENTITY, husk);
        float[] healthWhenFled = new float[1];

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(farmer.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.FIGHT), "The farmer takes on the husk that attacked it");
            var away = helper.absoluteVec(new net.minecraft.world.phys.Vec3(18.5, 1, 12.5));
            husk.teleportTo(away.x, away.y, away.z);
            healthWhenFled[0] = husk.getHealth();
        });
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(husk.getHealth() == healthWhenFled[0], "Once the husk is 15 blocks from where the fight began, it's too far to chase");
            helper.assertTrue(farmer.position().distanceTo(start) <= 9.0, "...so the farmer stays near where it was, moved " + farmer.position().distanceTo(start));
            helper.succeed();
        });
    }

    public static void armedVillagersFightRaiders(GameTestHelper helper) {
        Villager farmer = farmer(helper, COMPOSTER);
        Gear.wear(farmer, new ItemStack(Items.DIAMOND_SWORD));
        Gear.wear(farmer, new ItemStack(Items.IRON_CHESTPLATE));
        var vindicator = standing(helper.spawnWithNoFreeWill(EntityTypes.VINDICATOR, new BlockPos(10, 1, 12)));

        helper.succeedWhen(() -> helper.assertTrue(!vindicator.isAlive(), "An armed villager fights raiders too, vindicator health " + vindicator.getHealth()));
    }

    public static void anArcherShootsHostiles(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setTime(GameTestHelpers.WORK_HOURS);
        helper.setBlock(FLETCHING_TABLE, Blocks.FLETCHING_TABLE);
        Villager fletcher = GameTestHelpers.spawnEmployed(helper, FLETCHING_TABLE.east(), FLETCHING_TABLE, VillagerProfession.FLETCHER, PoiTypes.FLETCHER);
        fletcher.getInventory().addItem(new ItemStack(Items.BOW));
        fletcher.getInventory().addItem(new ItemStack(Items.ARROW, 16));
        Monster husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(5, 1, 11));

        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "A fletcher with a bow and arrows shoots the husk");
            helper.assertTrue(fletcher.getInventory().countItem(Items.ARROW) < 16, "...with its own arrows");
        });
    }

    public static void aVillagerOnHighGroundNoticesAHostileFurtherOffThanOneOnTheGround(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        helper.setTime(GameTestHelpers.WORK_HOURS);
        for (int x = 4; x <= 6; x++) for (int z = 4; z <= 6; z++) for (int y = 1; y <= 3; y++) helper.setBlock(x, y, z, Blocks.STONE_BRICKS);
        Villager high = helper.spawn(EntityTypes.VILLAGER, new BlockPos(5, 4, 5));
        Villager low = helper.spawn(EntityTypes.VILLAGER, new BlockPos(13, 1, 14));
        for (Villager villager : new Villager[]{high, low}) villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        standing(helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(15, 1, 5)));
        boolean[] panicked = {false, false};

        helper.onEachTick(() -> {
            panicked[0] |= high.getBrain().isActive(Activity.PANIC);
            panicked[1] |= low.getBrain().isActive(Activity.PANIC);
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(panicked[0], "A villager three blocks up notices a zombie 10 blocks away, though vanilla's sensor stops at 8");
            helper.assertTrue(!panicked[1], "...one on the ground doesn't");
            helper.succeed();
        });
    }

    public static void aFallenVillagersGearDrops(GameTestHelper helper) {
        GameTestHelpers.fence(helper);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 12));
        Gear.wear(villager, new ItemStack(Items.IRON_CHESTPLATE));
        Gear.wear(villager, new ItemStack(Items.IRON_SWORD));

        villager.hurtServer(helper.getLevel(), villager.damageSources().genericKill(), 100.0F);

        helper.runAfterDelay(2, () -> {
            var items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(4)).stream().map(ItemEntity::getItem).toList();
            helper.assertTrue(items.stream().anyMatch(stack -> stack.is(Items.IRON_CHESTPLATE)), "Its armor drops when it dies, " + items);
            helper.assertTrue(items.stream().anyMatch(stack -> stack.is(Items.IRON_SWORD)), "...and its sword, " + items);
            helper.succeed();
        });
    }


    private static final BlockPos BELL = new BlockPos(14, 1, 20);

    public static void theBellGathersArmedVillagersAtTheMeetingPoint(GameTestHelper helper) {
        Villager swordsman = farmer(helper, COMPOSTER_2);
        Gear.wear(swordsman, new ItemStack(Items.IRON_SWORD));
        Villager farmer = farmer(helper, COMPOSTER);
        helper.setBlock(BELL, Blocks.BELL);
        for (Villager villager : new Villager[]{swordsman, farmer}) {
            villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(BELL)));
        }

        boolean[] unarmed = {false, false};

        helper.runAfterDelay(20, () -> ((net.minecraft.world.level.block.BellBlock) Blocks.BELL).attemptToRing(helper.getLevel(), helper.absolutePos(BELL), net.minecraft.core.Direction.EAST));
        helper.onEachTick(() -> {
            unarmed[0] |= farmer.getBrain().isActive(Activity.HIDE);
            unarmed[1] |= farmer.getBrain().isActive(Activity.FIGHT);
        });
        helper.succeedWhen(() -> {
            var bell = net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(BELL));
            helper.assertTrue(swordsman.position().distanceTo(bell) <= 4.0, "The armed villager gathers at the bell, " + swordsman.position().distanceTo(bell) + " blocks away");
            helper.assertTrue(unarmed[0] && !unarmed[1], "...an unarmed one hides as in vanilla instead");
        });
    }

    public static void anArcherClimbsToHighGroundWhenTheBellRings(GameTestHelper helper) {
        Villager archer = archerAtBell(helper);
        BlockPos platform = tower(helper);

        helper.runAfterDelay(20, () -> ring(helper));
        helper.succeedWhen(() -> {
            helper.assertTrue(archer.onGround() && archer.blockPosition().getY() >= helper.absolutePos(platform).getY(),
                    "The archer climbs the stairs to the tower top, standing at " + archer.blockPosition());
            helper.assertTrue(archer.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(BELL))) > 4.0, "...instead of waiting at the bell");
        });
    }

    public static void aPerchedArcherStaysUpAndShootsRaidersFurtherThanItWouldOnTheGround(GameTestHelper helper) {
        Villager archer = archerAtBell(helper);
        BlockPos platform = tower(helper);
        helper.runAfterDelay(20, () -> ring(helper));
        Monster[] pillager = new Monster[1];

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(archer.blockPosition().getY() >= helper.absolutePos(platform).getY(), "The archer is on the tower by now, at " + archer.blockPosition());
            pillager[0] = standing(helper.spawnWithNoFreeWill(EntityTypes.PILLAGER, new BlockPos(2, 1, 15)));
        });
        helper.onEachTick(() -> {
            if (pillager[0] != null) helper.assertTrue(archer.blockPosition().getY() >= helper.absolutePos(platform).getY(), "The archer holds the tower and doesn't go down to the pillager");
        });
        helper.succeedWhen(() -> helper.assertTrue(pillager[0] != null && (!pillager[0].isAlive() || pillager[0].getHealth() < pillager[0].getMaxHealth()),
                "From the tower it sees and shoots a pillager 15 blocks off, beyond what the villager sensor reports"));
    }

    public static void anArcherClimbsIntoARoofedTower(GameTestHelper helper) {
        Villager archer = archerAtBell(helper);
        BlockPos platform = tower(helper);
        for (int x = 17; x <= 20; x++) for (int z = 14; z <= 16; z++) helper.setBlock(x, 7, z, Blocks.STONE);

        helper.runAfterDelay(20, () -> ring(helper));
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(helper.getBlockState(new BlockPos(19, 7, 15)).is(Blocks.STONE), "The tower has its roof");
            helper.assertTrue(archer.onGround() && archer.blockPosition().getY() >= helper.absolutePos(platform).getY() && archer.blockPosition().getX() >= helper.absolutePos(new BlockPos(17, 0, 0)).getX(),
                    "A roof doesn't keep the archer off the tower top, standing at " + archer.blockPosition());
            helper.succeed();
        });
    }

    public static void anArcherPrefersAMarkedPostToHigherGround(GameTestHelper helper) {
        Villager archer = archerAtBell(helper);
        tower(helper);
        helper.setBlock(15, 2, 15, Blocks.HAY_BLOCK);

        helper.runAfterDelay(20, () -> ring(helper));
        helper.runAfterDelay(300, () -> {
            var post = net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(15, 3, 15)));
            helper.assertTrue(archer.position().distanceTo(post) <= 1.5,
                    "The archer holds the hay bale post instead of the taller tower, at " + archer.position() + " not " + post);
            helper.succeed();
        });
    }

    public static void anArcherWithoutHighGroundGathersAtTheBell(GameTestHelper helper) {
        Villager archer = archerAtBell(helper);

        helper.runAfterDelay(20, () -> ring(helper));
        helper.succeedWhen(() -> helper.assertTrue(archer.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(BELL))) <= 4.0,
                "With nothing to climb the archer waits at the bell, " + archer.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(BELL)))));
    }

    /** A level 5 archer (steady aim) at the field's west end, its meeting point the bell. */
    private static Villager archerAtBell(GameTestHelper helper) {
        Villager archer = farmer(helper, new BlockPos(6, 1, 6));
        archer.setVillagerData(archer.getVillagerData().withLevel(5));
        archer.setData(ModVillagers.ARCHER, true);
        Gear.wear(archer, new ItemStack(Items.BOW));
        Gear.wear(archer, new ItemStack(Items.ARROW, 32));
        helper.setBlock(BELL, Blocks.BELL);
        archer.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(BELL)));
        return archer;
    }

    private static void ring(GameTestHelper helper) {
        ((net.minecraft.world.level.block.BellBlock) Blocks.BELL).attemptToRing(helper.getLevel(), helper.absolutePos(BELL), net.minecraft.core.Direction.EAST);
    }

    /** A ramp of full blocks, one higher each step, up to a 3x3 tower top; its floor is four blocks above the ground. Returns a standing cell on top. */
    private static BlockPos tower(GameTestHelper helper) {
        for (int x = 14; x <= 20; x++) {
            int height = Math.min(x - 13, 4);
            for (int z = x < 18 ? 15 : 14; z <= (x < 18 ? 15 : 16); z++) for (int y = 1; y <= height; y++) helper.setBlock(x, y, z, Blocks.STONE_BRICKS);
        }
        for (int x = 14; x <= 20; x++) for (int z = 14; z <= 16; z++) helper.setBlock(x, 6, z, Blocks.AIR);
        return new BlockPos(19, 5, 15);
    }

    /** A mob with no AI slides far from every knockback, out of the fight; a live one walks back in. */
    private static <T extends net.minecraft.world.entity.Mob> T standing(T mob) {
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        return mob;
    }

    private static Villager armorer(GameTestHelper helper) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(ANVIL, Blocks.ANVIL);
        Villager armorer = GameTestHelpers.spawnEmployed(helper, ANVIL.east(), ANVIL, VillagerProfession.ARMORER, PoiTypes.ARMORER);
        armorer.setVillagerData(armorer.getVillagerData().withLevel(3));
        return armorer;
    }

    private static Villager farmer(GameTestHelper helper, BlockPos composter) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(composter, Blocks.COMPOSTER);
        return GameTestHelpers.spawnEmployed(helper, composter.west(), composter, VillagerProfession.FARMER, PoiTypes.FARMER);
    }
}
