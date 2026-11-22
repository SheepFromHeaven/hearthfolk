package com.sheepfromheaven.minenow.dev;

import com.sheepfromheaven.minenow.fisherman.VillagerBobber;
import com.sheepfromheaven.minenow.forester.Forester;
import com.sheepfromheaven.minenow.miner.Miner;
import com.sheepfromheaven.minenow.smelter.Smelter;
import com.sheepfromheaven.minenow.villager.FurnaceTending;
import com.sheepfromheaven.minenow.villager.Gear;
import com.sheepfromheaven.minenow.villager.IdleReason;
import com.sheepfromheaven.minenow.villager.NeedBubbles;
import com.sheepfromheaven.minenow.villager.SpeechBubbles;
import com.sheepfromheaven.minenow.villager.TradeTable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Behind the hub's arrival point: villagers frozen in place (no AI, invulnerable) in rows, one per look worth checking:
 * holding the tool of their trade, swinging it at work, in each kind of armor, fighting, a fisherman with its line
 * out and a fletcher shooting at a target, each named after what it shows, so held items, swings and armor can be inspected side by side.
 */
final class PlaygroundShowcase {
    private static final String TAG = SpeechBubbles.SHOWCASE_TAG;
    private static final int SPACING = 3;
    private static final int FIRST_ROW = 6;
    private static final int ROW_GAP = 5;
    private static final int SWING_EVERY = 15;
    private static final int BUBBLE_EVERY = 40;
    private static final int BUBBLE_TICKS = 60;
    private static final float FACING_HUB = 180.0F;

    /** What a showcase villager's speech bubble shows for good: an item, or one of our own pictures. */
    private record Icon(ItemStack item, @Nullable Identifier image) {
        static Icon of(@Nullable Item item) {
            return new Icon(item == null ? ItemStack.EMPTY : new ItemStack(item), null);
        }
    }

    private record Showing(Villager villager, Icon icon) {}

    private record Look(String name, ResourceKey<VillagerProfession> profession, ItemStack hand, List<ItemStack> armor, boolean swinging, @Nullable Function<Villager, Icon> bubble) {
        Look(String name, ResourceKey<VillagerProfession> profession, ItemStack hand, List<ItemStack> armor, boolean swinging) {
            this(name, profession, hand, armor, swinging, null);
        }

        Look(String name, ResourceKey<VillagerProfession> profession, Function<Villager, Icon> bubble) {
            this(name, profession, ItemStack.EMPTY, List.of(), false, bubble);
        }

        Look(String name, ResourceKey<VillagerProfession> profession, Item hand, boolean swinging) {
            this(name, profession, stack(hand), List.of(), swinging);
        }
    }

    private record Row(String title, List<Look> looks) {}

    private static final List<Villager> SWINGING = new ArrayList<>();
    private static final List<Showing> BUBBLES = new ArrayList<>();
    private static @Nullable Villager angler;
    private static @Nullable VillagerBobber bobber;
    private static Vec3 pool = Vec3.ZERO;
    private static final int ARCHERY_X = 6;
    private static final int RANGE = 10;
    private static final int SHOT_EVERY = 30;
    private static final int ARROW_LIFE = 80;
    private static final float ARROW_SPEED = 1.6F;
    private static final java.util.Map<AbstractArrow, Long> ARROWS = new java.util.HashMap<>();
    private static @Nullable Villager archer;
    private static Vec3 bullseye = Vec3.ZERO;

    private PlaygroundShowcase() {}

    static void build(ServerLevel level, BlockPos origin) {
        level.getEntitiesOfClass(Villager.class, new AABB(origin).inflate(64), villager -> villager.entityTags().contains(TAG)).forEach(Villager::discard);
        SWINGING.clear();
        BUBBLES.clear();
        if (bobber != null) bobber.discard();
        bobber = null;
        angler = null;
        archer = null;
        ARROWS.keySet().forEach(AbstractArrow::discard);
        ARROWS.clear();
        List<Row> rows = rows();
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            int z = FIRST_ROW + r * ROW_GAP;
            List<Look> looks = row.looks();
            int half = (looks.size() - 1) * SPACING / 2;
            for (int x = -half - 3; x <= half + 3; x++) level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            PlaygroundLabels.spawn(level, Vec3.atBottomCenterOf(origin.offset(0, 3, z + 1)), Component.literal(row.title()), 1.5F);
            for (int i = 0; i < looks.size(); i++) spawn(level, origin.offset(i * SPACING - half, 0, z), looks.get(i));
        }
        fishing(level, origin.offset(0, 0, FIRST_ROW + rows.size() * ROW_GAP));
        archery(level, origin.offset(ARCHERY_X, 0, FIRST_ROW + rows.size() * ROW_GAP));
    }

    /** A fisherman with its line out over a little pool in front of it, so the line from rod to bobber can be checked. */
    private static void fishing(ServerLevel level, BlockPos at) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -6; z <= 1; z++) {
                boolean pool = Math.abs(x) <= 1 && z >= -5 && z <= -3;
                level.setBlock(at.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(at.offset(x, -1, z), pool ? Blocks.WATER.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState(), 2);
            }
        }
        PlaygroundLabels.spawn(level, Vec3.atBottomCenterOf(at.offset(0, 3, 1)), Component.literal("Fishing"), 1.5F);
        Villager fisherman = spawn(level, at, new Look("Fisherman: line out", VillagerProfession.FISHERMAN, Items.FISHING_ROD, false));
        if (fisherman != null) {
            fisherman.setXRot(20.0F);
            angler = fisherman;
            pool = Vec3.atCenterOf(at.offset(0, -1, -4)).add(0, 0.4, 0);
        }
    }

    /** A fletcher shooting at a target block down the line, east of the fishing pool where nothing stands in the way. */
    private static void archery(ServerLevel level, BlockPos at) {
        for (int x = -1; x <= RANGE + 1; x++) {
            for (int z = -1; z <= 1; z++) level.setBlock(at.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        }
        BlockPos target = at.offset(RANGE, 0, 0);
        level.setBlock(target, Blocks.TARGET.defaultBlockState(), 2);
        PlaygroundLabels.spawn(level, Vec3.atBottomCenterOf(at.offset(RANGE / 2, 3, 0)), Component.literal("Archery"), 1.5F);
        Villager fletcher = spawn(level, at, new Look("Fletcher: shooting at a target", VillagerProfession.FLETCHER, Items.BOW, false));
        if (fletcher == null) return;
        fletcher.snapTo(Vec3.atBottomCenterOf(at), -90.0F, 0.0F);
        fletcher.setYHeadRot(-90.0F);
        fletcher.setYBodyRot(-90.0F);
        archer = fletcher;
        bullseye = Vec3.atCenterOf(target);
    }

    private static void shoot(ServerLevel level, Villager fletcher) {
        Arrow arrow = new Arrow(level, fletcher, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        Vec3 aim = bullseye.subtract(arrow.position());
        double flat = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
        arrow.shoot(aim.x, aim.y + 0.5 * 0.05 / (ARROW_SPEED * ARROW_SPEED) * flat * flat, aim.z, ARROW_SPEED, 1.0F);
        level.addFreshEntity(arrow);
        fletcher.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        level.playSound(null, fletcher.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F);
        ARROWS.put(arrow, level.getGameTime());
    }

    static void tick(MinecraftServer server) {
        if (archer != null && archer.isAlive() && archer.level() instanceof ServerLevel level) {
            if (level.getGameTime() % SHOT_EVERY == 0) shoot(level, archer);
            ARROWS.entrySet().removeIf(shot -> {
                if (level.getGameTime() - shot.getValue() < ARROW_LIFE) return false;
                shot.getKey().discard();
                return true;
            });
        }
        if (angler != null && angler.isAlive() && angler.level() instanceof ServerLevel level) {
            if (bobber == null || !bobber.isAlive()) bobber = VillagerBobber.cast(level, angler, pool, 0);
            bobber.tend();
        }
        if (server.getTickCount() % BUBBLE_EVERY == 0) {
            BUBBLES.removeIf(showing -> !showing.villager().isAlive());
            BUBBLES.forEach(PlaygroundShowcase::showBubble);
        }
        if (server.getTickCount() % SWING_EVERY != 0) return;
        SWINGING.removeIf(villager -> !villager.isAlive());
        SWINGING.forEach(villager -> villager.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false));
    }

    private static void showBubble(Showing showing) {
        if (showing.icon().image() != null) SpeechBubbles.show(showing.villager(), showing.icon().image(), BUBBLE_TICKS);
        else SpeechBubbles.show(showing.villager(), showing.icon().item(), BUBBLE_TICKS);
    }

    private static @Nullable Villager spawn(ServerLevel level, BlockPos pos, Look look) {
        Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        if (villager == null) return null;
        villager.snapTo(Vec3.atBottomCenterOf(pos), FACING_HUB, 0.0F);
        villager.setYHeadRot(FACING_HUB);
        villager.setYBodyRot(FACING_HUB);
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), look.profession()).withLevel(3));
        villager.setNoAi(true);
        villager.setPermanentlyInvulnerable(true);
        villager.setSilent(true);
        villager.setPersistenceRequired();
        villager.setCustomName(Component.literal(look.name()));
        villager.setCustomNameVisible(true);
        villager.addTag(TAG);
        villager.setItemSlot(EquipmentSlot.MAINHAND, look.hand());
        villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        look.armor().forEach(piece -> Gear.wear(villager, piece));
        level.addFreshEntity(villager);
        if (look.swinging()) SWINGING.add(villager);
        if (look.bubble() != null) BUBBLES.add(new Showing(villager, look.bubble().apply(villager)));
        return villager;
    }

    private static List<Row> rows() {
        return List.of(
                new Row("Holding the tool of their trade", tools(false)),
                new Row("Working (swinging)", tools(true)),
                new Row("Armor", List.of(
                        new Look("Dyed leather", VillagerProfession.FARMER, ItemStack.EMPTY, armor(dyed(Items.LEATHER_HELMET), dyed(Items.LEATHER_CHESTPLATE), dyed(Items.LEATHER_LEGGINGS), dyed(Items.LEATHER_BOOTS)), false),
                        new Look("Leather", VillagerProfession.SHEPHERD, ItemStack.EMPTY, armor(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS), false),
                        new Look("Copper", VillagerProfession.TOOLSMITH, ItemStack.EMPTY, armor(Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS), false),
                        new Look("Iron", VillagerProfession.MASON, ItemStack.EMPTY, armor(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS), false),
                        new Look("Diamond", VillagerProfession.ARMORER, ItemStack.EMPTY, armor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS), false),
                        new Look("Gold", VillagerProfession.CLERIC, ItemStack.EMPTY, armor(Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS), false),
                        new Look("Chainmail", VillagerProfession.LIBRARIAN, ItemStack.EMPTY, armor(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS), false),
                        new Look("Mixed pieces", VillagerProfession.NONE, ItemStack.EMPTY, armor(Items.IRON_HELMET, Items.LEATHER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.DIAMOND_BOOTS), false),
                        new Look("Chestplate only", Miner.PROFESSION, ItemStack.EMPTY, armor(Items.IRON_CHESTPLATE), false),
                        new Look("Helmet only", Forester.PROFESSION, ItemStack.EMPTY, armor(Items.IRON_HELMET), false),
                        new Look("Farmer in iron with hoe", VillagerProfession.FARMER, stack(Items.IRON_HOE), armor(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS), true))),
                new Row("Fighting", List.of(
                        new Look("Sword drawn", VillagerProfession.FARMER, Items.IRON_SWORD, false),
                        new Look("Sword swinging", VillagerProfession.FARMER, Items.IRON_SWORD, true),
                        new Look("Iron armor, sword swinging", VillagerProfession.MASON, stack(Items.IRON_SWORD), armor(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS), true),
                        new Look("Diamond armor, diamond sword", VillagerProfession.WEAPONSMITH, stack(Items.DIAMOND_SWORD), armor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS), true),
                        new Look("Bow drawn", VillagerProfession.FLETCHER, stack(Items.BOW), armor(dyed(Items.LEATHER_CHESTPLATE)), false),
                        new Look("Bow shooting", VillagerProfession.FLETCHER, Items.BOW, true))),
                new Row("Showing goods (vanilla crossed arms)", List.of(
                        new Look("Emerald", VillagerProfession.LIBRARIAN, Items.EMERALD, false),
                        new Look("Bread", VillagerProfession.FARMER, Items.BREAD, false),
                        new Look("Iron ingot", Smelter.PROFESSION, Items.IRON_INGOT, false),
                        new Look("Nothing", VillagerProfession.NITWIT, Items.AIR, false))),
                new Row("Speech bubbles: needs", needBubbles()),
                new Row("Speech bubbles: status", List.of(
                        fixed("Hungry", VillagerProfession.FARMER, Items.BREAD),
                        fixed("Seeking shelter", VillagerProfession.FISHERMAN, Items.WATER_BUCKET),
                        new Look("Frightened", VillagerProfession.LIBRARIAN, villager -> new Icon(ItemStack.EMPTY, NeedBubbles.FRIGHT)),
                        fixed("Alarm", VillagerProfession.WEAPONSMITH, Items.BELL))),
                new Row("Speech bubbles: chat", List.of(
                        fixed("Chat: my goods", VillagerProfession.FARMER, Items.WHEAT),
                        fixed("Chat: what I bought", VillagerProfession.BUTCHER, Items.WHEAT),
                        new Look("Chat: new job", VillagerProfession.FARMER, villager -> Icon.of(NeedBubbles.jobBlockIcon(villager))),
                        fixed("Chat: a birth", VillagerProfession.CLERIC, Items.CAKE),
                        fixed("Chat: a death", VillagerProfession.MASON, Items.POPPY))));
    }

    private static Look fixed(String name, ResourceKey<VillagerProfession> profession, Item icon) {
        return new Look(name, profession, villager -> Icon.of(icon));
    }

    /** The icons come from the code that picks them, so the row stays true when one changes. */
    private static List<Look> needBubbles() {
        List<Look> looks = new ArrayList<>(List.of(
                new Look("Needs a tool", VillagerProfession.FARMER, villager -> Icon.of(NeedBubbles.toolIcon(villager))),
                new Look("Needs fuel", VillagerProfession.BUTCHER, villager -> Icon.of(TradeTable.forVillager(villager)
                        .flatMap(table -> table.suppliesUpTo(villager.getVillagerData().level()).stream()
                                .filter(supply -> FurnaceTending.isFuel(new ItemStack(supply.item()))).findFirst())
                        .map(TradeTable.Supply::item).orElse(null))),
                fixed("Needs money", VillagerProfession.LIBRARIAN, Items.EMERALD),
                fixed("Storage full", VillagerProfession.FISHERMAN, Items.CHEST),
                new Look("No job block", VillagerProfession.FARMER, villager -> Icon.of(NeedBubbles.jobBlockIcon(villager))),
                new Look("No job block (unemployed)", VillagerProfession.NONE, villager -> Icon.of(NeedBubbles.jobBlockIcon(villager)))));
        for (ResourceKey<VillagerProfession> profession : IdleReason.workAreaProfessions()) {
            looks.add(new Look("No work area: " + profession.identifier().getPath(), profession, villager -> Icon.of(IdleReason.workAreaIcon(villager))));
        }
        return looks;
    }

    private static List<Look> tools(boolean swinging) {
        String state = swinging ? ", working" : "";
        return List.of(
                new Look("Farmer: hoe" + state, VillagerProfession.FARMER, Items.IRON_HOE, swinging),
                new Look("Fisherman: rod" + state, VillagerProfession.FISHERMAN, Items.FISHING_ROD, swinging),
                new Look("Shepherd: shears" + state, VillagerProfession.SHEPHERD, Items.SHEARS, swinging),
                new Look("Butcher: axe" + state, VillagerProfession.BUTCHER, Items.IRON_AXE, swinging),
                new Look("Forester: axe" + state, Forester.PROFESSION, Items.STONE_AXE, swinging),
                new Look("Miner: pickaxe" + state, Miner.PROFESSION, Items.IRON_PICKAXE, swinging),
                new Look("Fletcher: bow" + state, VillagerProfession.FLETCHER, Items.BOW, swinging),
                new Look("Weaponsmith: sword" + state, VillagerProfession.WEAPONSMITH, Items.DIAMOND_SWORD, swinging),
                new Look("Toolsmith: shovel" + state, VillagerProfession.TOOLSMITH, Items.IRON_SHOVEL, swinging));
    }

    private static ItemStack stack(Item item) {
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static List<ItemStack> armor(Item... pieces) {
        return java.util.Arrays.stream(pieces).map(ItemStack::new).toList();
    }

    private static List<ItemStack> armor(ItemStack... pieces) {
        return List.of(pieces);
    }

    private static ItemStack dyed(Item piece) {
        return DyedItemColor.applyDyes(new ItemStack(piece), List.of(DyeColor.RED));
    }
}
