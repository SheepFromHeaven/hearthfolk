package com.sheepfromheaven.minenow.weaponsmith;

import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public final class WeaponsmithTests {
    private static final BlockPos GRINDSTONE = new BlockPos(5, 1, 12);

    private WeaponsmithTests() {}

    public static void aNoviceWeaponsmithMakesStoneSwords(GameTestHelper helper) {
        Villager smith = spawnWeaponsmith(helper, 1);
        smith.getInventory().addItem(new ItemStack(Items.COBBLESTONE, 2));
        smith.getInventory().addItem(new ItemStack(Items.STICK, 1));

        helper.succeedWhen(() -> helper.assertTrue(smith.getInventory().countItem(Items.STONE_SWORD) == 1,
                "Two cobblestone and a stick make a stone sword at its grindstone, has " + smith.getInventory().countItem(Items.STONE_SWORD)));
    }

    public static void aWeaponsmithGrindsTwoWornSwordsIntoOne(GameTestHelper helper) {
        Villager smith = spawnWeaponsmith(helper, 1);
        smith.getInventory().addItem(worn(Items.IRON_SWORD, 200));
        smith.getInventory().addItem(worn(Items.IRON_SWORD, 200));

        helper.succeedWhen(() -> {
            List<ItemStack> swords = smith.getInventory().getItems().stream().filter(stack -> stack.is(Items.IRON_SWORD)).toList();
            helper.assertTrue(swords.size() == 1, "Two worn iron swords become one, has " + swords.size());
            int damage = swords.getFirst().getDamageValue();
            int expected = 250 - (50 + 50 + 250 * 5 / 100);
            helper.assertTrue(damage <= expected, "...with both blades' life and a grindstone's bonus, damage " + damage + ", at most " + expected);
            helper.assertFalse(swords.getFirst().has(DataComponents.ENCHANTMENTS) && !swords.getFirst().get(DataComponents.ENCHANTMENTS).isEmpty(), "...and no enchantments, like a grindstone");
        });
    }

    public static void aWeaponsmithHangsItsBestSwordsOnTheWall(GameTestHelper helper) {
        Villager smith = spawnWeaponsmith(helper, 2);
        smith.getInventory().addItem(new ItemStack(Items.STONE_SWORD));
        smith.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        smith.getInventory().addItem(new ItemStack(Items.WOODEN_SWORD));
        smith.getInventory().addItem(new ItemStack(Items.ITEM_FRAME, 3));
        for (int z = 9; z <= 15; z++) {
            for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
        }

        helper.succeedWhen(() -> {
            List<ItemFrame> shown = helper.getLevel().getEntitiesOfClass(ItemFrame.class, helper.getBounds(), frame -> !frame.getItem().isEmpty());
            helper.assertTrue(shown.size() == 2, "An apprentice hangs two swords on its wall, hung " + shown.size());
            helper.assertTrue(shown.stream().anyMatch(frame -> frame.getItem().is(Items.IRON_SWORD)) && shown.stream().anyMatch(frame -> frame.getItem().is(Items.STONE_SWORD)),
                    "...its best two, " + shown.stream().map(frame -> frame.getItem().toString()).toList());
        });
    }

    private static ItemStack worn(net.minecraft.world.item.Item item, int damage) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(damage);
        return stack;
    }

    static Villager spawnWeaponsmith(GameTestHelper helper, int level) {
        helper.setTime(GameTestHelpers.WORK_HOURS);
        GameTestHelpers.fence(helper);
        helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE);
        Villager villager = GameTestHelpers.spawnEmployed(helper, GRINDSTONE.east(), GRINDSTONE, VillagerProfession.WEAPONSMITH, PoiTypes.WEAPONSMITH);
        villager.setVillagerData(villager.getVillagerData().withLevel(level));
        return villager;
    }
}
