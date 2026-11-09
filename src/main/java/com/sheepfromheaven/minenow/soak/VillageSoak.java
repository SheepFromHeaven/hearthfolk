package com.sheepfromheaven.minenow.soak;

import com.mojang.logging.LogUtils;
import com.sheepfromheaven.minenow.forester.Forester;
import com.sheepfromheaven.minenow.forester.NurseryTree;
import com.sheepfromheaven.minenow.gametest.GameTestHelpers;
import com.sheepfromheaven.minenow.miner.Miner;
import com.sheepfromheaven.minenow.smelter.Smelter;
import com.sheepfromheaven.minenow.villager.Economy;
import com.sheepfromheaven.minenow.villager.HomeStorage;
import com.sheepfromheaven.minenow.villager.IdleReason;
import com.sheepfromheaven.minenow.villager.Meals;
import com.sheepfromheaven.minenow.villager.ModVillagers;
import com.sheepfromheaven.minenow.villager.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.HashSet;

/**
 * A long-run economy check: twenty villagers with a workshop, bed and chest each, left alone for many in-game days
 * with the server sprinting. Once a day it logs emeralds, food and goods per profession to {@code days.csv}; at the
 * end it lists every villager's pockets and chests in {@code final.txt}. It only observes: it never fails on balance.
 * Lives in its own namespace ({@code minenowsoak}) so the normal gametest run skips it; start it with
 * {@code ./gradlew runVillageSoak [-PsoakDays=50]}.
 */
public final class VillageSoak {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Identifier ID = Identifier.fromNamespaceAndPath("minenowsoak", "village_soak");

    private static final int DAY = 24000;
    private static final int SIZE = 72;
    /** Top block of the stone slab the village stands on; the miners dig down through it. */
    private static final int G = 12;
    private static final int CELL_W = 14;
    private static final int CELL_D = 16;
    private static final TagKey<Item> EXPORTS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minenow", "exports"));

    private enum Role {
        FARMER("farmer", VillagerProfession.FARMER, PoiTypes.FARMER, Blocks.COMPOSTER, Items.WOODEN_HOE),
        FISHERMAN("fisherman", VillagerProfession.FISHERMAN, PoiTypes.FISHERMAN, Blocks.BARREL, Items.FISHING_ROD),
        SHEPHERD("shepherd", VillagerProfession.SHEPHERD, PoiTypes.SHEPHERD, Blocks.LOOM, Items.SHEARS),
        BUTCHER("butcher", VillagerProfession.BUTCHER, PoiTypes.BUTCHER, Blocks.SMOKER, Items.WOODEN_AXE),
        TOOLSMITH("toolsmith", VillagerProfession.TOOLSMITH, PoiTypes.TOOLSMITH, Blocks.SMITHING_TABLE, null),
        FLETCHER("fletcher", VillagerProfession.FLETCHER, PoiTypes.FLETCHER, Blocks.FLETCHING_TABLE, null),
        FORESTER("forester", Forester.PROFESSION, Forester.POI, null, Items.WOODEN_AXE),
        MINER("miner", Miner.PROFESSION, Miner.POI, null, Items.WOODEN_PICKAXE),
        SMELTER("smelter", Smelter.PROFESSION, PoiTypes.ARMORER, Blocks.BLAST_FURNACE, null),
        MASON("mason", VillagerProfession.MASON, PoiTypes.MASON, Blocks.STONECUTTER, null),
        LEATHERWORKER("leatherworker", VillagerProfession.LEATHERWORKER, PoiTypes.LEATHERWORKER, Blocks.CAULDRON, null),
        WEAPONSMITH("weaponsmith", VillagerProfession.WEAPONSMITH, PoiTypes.WEAPONSMITH, Blocks.GRINDSTONE, null),
        ARMORER("armorer", VillagerProfession.ARMORER, PoiTypes.ARMORER, Blocks.ANVIL, null);

        final String label;
        final ResourceKey<VillagerProfession> profession;
        final ResourceKey<PoiType> poi;
        final Block station;
        final Item startingTool;

        Role(String label, ResourceKey<VillagerProfession> profession, ResourceKey<PoiType> poi, Block station, Item startingTool) {
            this.label = label;
            this.profession = profession;
            this.poi = poi;
            this.station = station;
            this.startingTool = startingTool;
        }
    }

    /**
     * Row by row. Villagers only buy from sellers within 48 blocks and the corners are further apart than that, so the
     * toolsmith and smelter, whom nearly everyone buys from, take the middle cells.
     */
    private static final Role[] ROSTER = {
            Role.FARMER, Role.FARMER, Role.SHEPHERD, Role.SHEPHERD, Role.BUTCHER,
            Role.FISHERMAN, Role.FISHERMAN, Role.TOOLSMITH, Role.FARMER, Role.MINER,
            Role.FARMER, Role.FLETCHER, Role.SMELTER, Role.FORESTER, Role.MINER,
            Role.FORESTER, Role.MASON, Role.LEATHERWORKER, Role.WEAPONSMITH, Role.ARMORER,
    };

    private record Resident(Role role, int index, Villager villager) {
        String name() {
            return role.label + "#" + index;
        }
    }

    private VillageSoak() {}

    public static void run(GameTestHelper helper) {
        if (!Boolean.getBoolean("minenow.soak.only")) {
            helper.succeed();
            return;
        }
        int days = Integer.getInteger("minenow.soak.days", 50);
        boolean starterTools = !Boolean.getBoolean("minenow.soak.noTools");
        Path out = Path.of(System.getProperty("minenow.soak.out", "build/soak"));
        ServerLevel level = helper.getLevel();
        LOGGER.info("[soak] building a village of {} for {} days", ROSTER.length, days);

        GameTestHelpers.fence(helper);
        buildGround(helper);
        List<Resident> residents = new ArrayList<>();
        Map<Role, Integer> counts = new java.util.EnumMap<>(Role.class);
        for (int i = 0; i < ROSTER.length; i++) {
            Role role = ROSTER[i];
            int n = counts.merge(role, 1, Integer::sum);
            int ox = 1 + (i % 5) * CELL_W;
            int oz = 1 + (i / 5) * CELL_D;
            residents.add(new Resident(role, n, settle(helper, role, ox, oz, starterTools)));
        }

        try {
            Files.createDirectories(out);
            Files.writeString(out.resolve("days.csv"), "day,profession,alive,emeralds,food,goods,export_goods,starving,idle,idle_reasons\n");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write the soak report to " + out, e);
        }

        long totalTicks = (long) days * DAY;
        Set<String> trace = new HashSet<>(List.of(System.getProperty("minenow.soak.trace", "").split(",")));
        trace.remove("");
        long traceFrom = Long.getLong("minenow.soak.traceFrom", -1L);
        try {
            Files.deleteIfExists(out.resolve("trace.txt"));
        } catch (IOException e) {
            LOGGER.error("Cannot clear the soak trace", e);
        }
        LOGGER.info("[soak] village built, running {} ticks", totalTicks);
        Set<UUID> merchantsSeen = new HashSet<>();
        long[] tick = {0};
        long started = System.nanoTime();
        helper.getLevel().getServer().tickRateManager().requestGameToSprint((int) Math.min(Integer.MAX_VALUE, totalTicks + 40));

        helper.succeedWhen(() -> {
            tick[0]++;
            helper.setTime(tick[0] % DAY);
            if (tick[0] % 200 == 0) {
                for (WanderingTrader trader : level.getEntitiesOfClass(WanderingTrader.class, helper.getBounds().inflate(128),
                        t -> t.getData(ModVillagers.TRAVELLING_MERCHANT))) {
                    merchantsSeen.add(trader.getUUID());
                }
            }
            if (!trace.isEmpty() && (tick[0] % 1200 == 0 || (tick[0] >= traceFrom && tick[0] < traceFrom + 1200 && tick[0] % 20 == 0))) trace(level, helper, out, tick[0], residents, trace);
            if (tick[0] % 2000 == 0) {
                double seconds = (System.nanoTime() - started) / 1e9;
                LOGGER.info("[soak] tick {} ({} ticks/s, sprinting {})", tick[0], String.format("%.0f", tick[0] / Math.max(seconds, 0.001)),
                        level.getServer().tickRateManager().isSprinting());
            }
            if (tick[0] % DAY == 0) {
                int day = (int) (tick[0] / DAY);
                dailyReport(level, helper, out, day, residents, merchantsSeen.size());
                double seconds = (System.nanoTime() - started) / 1e9;
                LOGGER.info("[soak] day {}/{} done after {}s ({} ticks/s)", day, days, String.format("%.0f", seconds),
                        String.format("%.0f", tick[0] / Math.max(seconds, 0.001)));
            }
            if (tick[0] >= totalTicks) {
                finalReport(level, helper, out, residents);
                return;
            }
            helper.assertTrue(false, "soaking, tick " + tick[0]);
        });
    }

    // ---------------------------------------------------------------- world

    private static void buildGround(GameTestHelper helper) {
        java.util.Random random = new java.util.Random(7);
        for (int x = 1; x < SIZE - 1; x++) {
            for (int z = 1; z < SIZE - 1; z++) {
                for (int y = 1; y <= G; y++) {
                    helper.setBlock(x, y, z, y <= G - 2 ? ore(random) : Blocks.STONE);
                }
            }
        }
    }

    private static Block ore(java.util.Random random) {
        int roll = random.nextInt(1000);
        if (roll < 30) return Blocks.COAL_ORE;
        if (roll < 50) return Blocks.IRON_ORE;
        if (roll < 65) return Blocks.COPPER_ORE;
        if (roll < 70) return Blocks.GOLD_ORE;
        if (roll < 75) return Blocks.REDSTONE_ORE;
        if (roll < 80) return Blocks.LAPIS_ORE;
        if (roll < 82) return Blocks.DIAMOND_ORE;
        if (roll < 90) return Blocks.GRAVEL;
        return Blocks.STONE;
    }

    private static BlockPos at(int ox, int oz, int x, int y, int z) {
        return new BlockPos(ox + x, G + y, oz + z);
    }

    private static void set(GameTestHelper helper, int ox, int oz, int x, int y, int z, Block block) {
        helper.setBlock(at(ox, oz, x, y, z), block);
    }

    private static Villager settle(GameTestHelper helper, Role role, int ox, int oz, boolean starterTools) {
        BlockPos station = at(ox, oz, 3, 1, 6);
        BlockPos spawn = at(ox, oz, 2, 1, 6);
        switch (role) {
            case FARMER -> {
                station = at(ox, oz, 3, 1, 5);
                spawn = at(ox, oz, 2, 1, 5);
                CropBlock[] crops = {(CropBlock) Blocks.WHEAT, (CropBlock) Blocks.CARROTS, (CropBlock) Blocks.POTATOES, (CropBlock) Blocks.BEETROOTS};
                for (int x = 4; x <= 10; x++) {
                    CropBlock crop = crops[x % crops.length];
                    BlockState ripe = crop.getStateForAge(crop.getMaxAge());
                    for (int z = 2; z <= 8; z++) {
                        if (x == 7 && z == 5) {
                            set(helper, ox, oz, x, 0, z, Blocks.WATER);
                            continue;
                        }
                        set(helper, ox, oz, x, 0, z, Blocks.FARMLAND);
                        helper.setBlock(at(ox, oz, x, 1, z), ripe);
                    }
                }
            }
            case FISHERMAN -> {
                for (int x = 5; x <= 9; x++) for (int z = 4; z <= 8; z++) set(helper, ox, oz, x, 0, z, Blocks.WATER);
            }
            case SHEPHERD -> {
                station = at(ox, oz, 3, 1, 5);
                spawn = at(ox, oz, 2, 1, 5);
                pen(helper, ox, oz);
                for (int i = 0; i < 4; i++) helper.spawn(EntityTypes.SHEEP, at(ox, oz, 7 + i % 2, 1, 3 + i / 2 * 2));
            }
            case BUTCHER -> {
                station = at(ox, oz, 3, 1, 5);
                spawn = at(ox, oz, 2, 1, 5);
                pen(helper, ox, oz);
                for (int i = 0; i < 2; i++) {
                    helper.spawn(EntityTypes.COW, at(ox, oz, 6 + i, 1, 3));
                    helper.spawn(EntityTypes.PIG, at(ox, oz, 6 + i, 1, 5));
                    helper.spawn(EntityTypes.CHICKEN, at(ox, oz, 9 + i, 1, 3));
                    helper.spawn(EntityTypes.RABBIT, at(ox, oz, 9 + i, 1, 5));
                }
            }
            case FORESTER -> {
                station = at(ox, oz, 3, 2, 6);
                spawn = at(ox, oz, 5, 1, 6);
                set(helper, ox, oz, 3, 1, 6, Blocks.OAK_LOG);
                for (int[] base : new int[][] {{8, 3}, {9, 9}, {11, 6}}) tree(helper, ox, oz, base[0], base[1]);
            }
            case MINER -> {
                station = at(ox, oz, 4, 1, 7);
                spawn = at(ox, oz, 3, 1, 7);
                for (int x = 4; x <= 12; x++) {
                    for (int z = 3; z <= 11; z++) {
                        if (x == 4 || x == 12 || z == 3 || z == 11) set(helper, ox, oz, x, 0, z, Blocks.COBBLESTONE);
                    }
                }
            }
            default -> {}
        }
        if (role == Role.FORESTER) {
            helper.setBlock(station, Forester.nursery(NurseryTree.OAK));
        } else if (role == Role.MINER) {
            helper.setBlock(station, Miner.FURNACE_CART.get());
        } else {
            helper.setBlock(station, role.station);
        }

        BlockPos bed = at(ox, oz, 3, 1, 13);
        helper.setBlock(at(ox, oz, 5, 1, 13), Blocks.CHEST);
        Villager villager = GameTestHelpers.spawnEmployed(helper, spawn, station, role.profession, role.poi);
        GameTestHelpers.giveHome(helper, villager, bed);
        GameTestHelpers.giveStartingPurse(helper, villager);
        GameTestHelpers.allowWorkXp(villager);
        if (starterTools && role.startingTool != null) villager.getInventory().addItem(new ItemStack(role.startingTool));
        return villager;
    }

    /** A 7x7 fence pen on grass with a gate in the middle of its west side. */
    private static void pen(GameTestHelper helper, int ox, int oz) {
        for (int i = 0; i < 7; i++) {
            set(helper, ox, oz, 5 + i, 1, 1, Blocks.OAK_FENCE);
            set(helper, ox, oz, 5 + i, 1, 7, Blocks.OAK_FENCE);
            set(helper, ox, oz, 5, 1, 1 + i, Blocks.OAK_FENCE);
            set(helper, ox, oz, 11, 1, 1 + i, Blocks.OAK_FENCE);
        }
        for (int x = 6; x <= 10; x++) for (int z = 2; z <= 6; z++) set(helper, ox, oz, x, 0, z, Blocks.GRASS_BLOCK);
        helper.setBlock(at(ox, oz, 5, 1, 4), Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST));
    }

    /** A small oak on dirt: four logs and a crown of natural leaves. */
    private static void tree(GameTestHelper helper, int ox, int oz, int x, int z) {
        set(helper, ox, oz, x, 0, z, Blocks.DIRT);
        for (int y = 1; y <= 4; y++) set(helper, ox, oz, x, y, z, Blocks.OAK_LOG);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 3; y <= 5; y++) {
                    BlockPos pos = at(ox, oz, x + dx, y, z + dz);
                    if (helper.getBlockState(pos).isAir()) {
                        helper.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1));
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- measuring

    /** Hasn't eaten for a whole day: a missed meal or two, not just waiting for breakfast. */
    private static boolean starving(Villager villager) {
        long last = villager.getExistingData(ModVillagers.LAST_MEAL).orElse(villager.level().getGameTime());
        return villager.level().getGameTime() - last > DAY;
    }

    private static void trace(ServerLevel level, GameTestHelper helper, Path out, long tick, List<Resident> residents, Set<String> who) {
        StringBuilder line = new StringBuilder();
        for (Resident resident : residents) {
            if (!who.contains(resident.name())) continue;
            Villager v = resident.villager();
            line.append(tick).append(' ').append(resident.name()).append(" at ").append(helper.relativePos(v.blockPosition()).toShortString())
                    .append(' ').append(v.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("-"))
                    .append(' ').append(v.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).filter(n -> !n.isEmpty()).toList())
                    .append(" walk=").append(v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).map(w -> w.getTarget().currentBlockPosition().toShortString()).orElse("-"))
                    .append(" goods=").append(list(v.getInventory()))
                    .append(resident.role() == Role.MINER ? " " + Miner.describeLadder(level, v).replaceAll("-?\\d+, (-?\\d+), -?\\d+ facing", "(top y $1) facing") + " ladderAt " + Miner.ladderTop(level, v).map(p -> helper.relativePos(p).toShortString()).orElse("-") : "").append('\n');
        }
        try {
            Files.writeString(out.resolve("trace.txt"), line, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Cannot append to the soak trace", e);
        }
    }

    private static List<Container> containers(ServerLevel level, Villager villager) {
        List<Container> all = new ArrayList<>();
        all.add(villager.getInventory());
        for (BlockPos chest : Storage.chests(level, villager)) {
            Container container = HomeStorage.chestContainer(level, chest);
            if (container != null) all.add(container);
        }
        return all;
    }

    private record Tally(int emeralds, int food, int goods, int exportGoods) {}

    private static Tally tally(ServerLevel level, Villager villager) {
        int emeralds = 0, food = 0, goods = 0, exports = 0;
        for (Container container : containers(level, villager)) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty()) continue;
                if (stack.is(Items.EMERALD)) emeralds += stack.getCount();
                else if (Meals.isMeal(stack)) food += stack.getCount();
                else goods += stack.getCount();
                if (stack.is(EXPORTS)) exports += stack.getCount();
            }
        }
        return new Tally(emeralds, food, goods, exports);
    }

    private static void dailyReport(ServerLevel level, GameTestHelper helper, Path out, int day, List<Resident> residents, int merchants) {
        Map<String, int[]> byRole = new TreeMap<>();
        Map<String, Map<String, Integer>> reasons = new TreeMap<>();
        int[] all = new int[8];
        for (Resident resident : residents) {
            Villager v = resident.villager();
            int[] row = byRole.computeIfAbsent(resident.role().label, k -> new int[8]);
            if (!v.isAlive()) continue;
            Tally t = tally(level, v);
            List<IdleReason> idle = IdleReason.of(level, v);
            int[] add = {1, t.emeralds(), t.food(), t.goods(), t.exportGoods(), starving(v) ? 1 : 0, idle.isEmpty() ? 0 : 1, 0};
            for (int i = 0; i < 7; i++) {
                row[i] += add[i];
                all[i] += add[i];
            }
            for (IdleReason reason : idle) reasons.computeIfAbsent(resident.role().label, k -> new TreeMap<>()).merge(reason.name(), 1, Integer::sum);
        }
        StringBuilder csv = new StringBuilder();
        for (var entry : byRole.entrySet()) {
            int[] r = entry.getValue();
            csv.append(day).append(',').append(entry.getKey());
            for (int i = 0; i < 7; i++) csv.append(',').append(r[i]);
            csv.append(",\"").append(reasons.getOrDefault(entry.getKey(), Map.of())).append("\"\n");
        }
        Map<String, String> herd = new TreeMap<>();
        for (net.minecraft.world.entity.animal.Animal animal : level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, helper.getBounds())) {
            herd.merge(animal.getType().toShortString(), animal.isBaby() ? "+1 young" : "+1", (a, b) -> a + b);
        }
        csv.append(day).append(",livestock,0,0,0,0,0,0,0,\"").append(herd).append("\"\n");
        csv.append(day).append(",ALL");
        for (int i = 0; i < 7; i++) csv.append(',').append(all[i]);
        csv.append(",\"merchants seen so far: ").append(merchants).append("\"\n");
        try {
            Files.writeString(out.resolve("days.csv"), csv, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Cannot append to the soak report", e);
        }
        LOGGER.info("[soak] day {}: alive {}/{} emeralds {} food {} goods {} exports {} starving {} idle {} merchants {}",
                day, all[0], residents.size(), all[1], all[2], all[3], all[4], all[5], all[6], merchants);
    }

    private static void finalReport(ServerLevel level, GameTestHelper helper, Path out, List<Resident> residents) {
        StringBuilder text = new StringBuilder();
        for (Resident resident : residents) {
            Villager v = resident.villager();
            text.append("== ").append(resident.name()).append(" (").append(v.getName().getString()).append(") level ")
                    .append(v.getVillagerData().level()).append(v.isAlive() ? "" : " DEAD").append(" at ")
                    .append(helper.relativePos(v.blockPosition()).toShortString()).append('\n');
            if (!v.isAlive()) continue;
            text.append("  idle: ").append(IdleReason.of(level, v)).append(", starving: ").append(starving(v)).append('\n');
            text.append("  activity: ").append(v.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("-"))
                    .append(", running: ").append(v.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).filter(n -> !n.isEmpty()).toList()).append('\n');
            text.append("  pockets: ").append(list(v.getInventory())).append('\n');
            for (BlockPos chest : Storage.chests(level, v)) {
                Container container = HomeStorage.chestContainer(level, chest);
                text.append("  chest ").append(helper.relativePos(chest).toShortString()).append(": ")
                        .append(container == null ? "(gone)" : list(container)).append('\n');
            }
        }
        Map<String, Integer> loose = new TreeMap<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8))) {
            loose.merge(item.getItem().getItem().toString(), item.getItem().getCount(), Integer::sum);
        }
        text.append("== loose items in the world: ").append(loose).append('\n');
        text.append("== travelling merchants and mules still around: ").append(
                level.getEntitiesOfClass(WanderingTrader.class, helper.getBounds().inflate(128)).size()).append(" traders\n");
        try {
            Files.writeString(out.resolve("final.txt"), text);
        } catch (IOException e) {
            LOGGER.error("Cannot write the final soak report", e);
        }
        LOGGER.info("[soak] finished, report in {}", out);
    }

    private static String list(Container container) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) items.merge(stack.getItem().toString().replace("minecraft:", ""), stack.getCount(), Integer::sum);
        }
        return items.isEmpty() ? "empty" : items.toString();
    }
}
