package com.sheepfromheaven.minenow.villager;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
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
