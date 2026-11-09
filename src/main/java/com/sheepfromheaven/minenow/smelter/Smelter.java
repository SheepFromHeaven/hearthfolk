package com.sheepfromheaven.minenow.smelter;

import com.google.common.collect.ImmutableSet;
import com.sheepfromheaven.minenow.MineNow;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.poi.ExtendPoiTypesEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

/**
 * The smelter: a new profession at the blast furnace, which vanilla gives the armorer. NeoForge can only add blocks to
 * a job site, so anvils join the armorer's (its job site from now on) and the job follows the block: a villager working
 * a blast furnace is a smelter, one working an anvil is an armorer ({@link #onEntityTick}). It smelts raw ore and burns
 * logs into charcoal ({@link SmeltOres}).
 */
public final class Smelter {
    public static final ResourceKey<VillagerProfession> PROFESSION = ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "smelter"));
    private static final TagKey<Item> RAW_MATERIALS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "raw_materials"));
    private static final int CHECK_EVERY = 20;

    private static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, MineNow.MOD_ID);

    static {
        PROFESSIONS.register("smelter", () -> new VillagerProfession(
                Component.translatable("entity.minenow.villager.smelter"),
                poi -> poi.is(PoiTypes.ARMORER),
                poi -> poi.is(PoiTypes.ARMORER),
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_ARMORER,
                Int2ObjectMap.ofEntries()));
    }

    private Smelter() {}

    public static void register(IEventBus modBus) {
        PROFESSIONS.register(modBus);
        modBus.addListener(Smelter::onExtendPoiTypes);
    }

    private static void onExtendPoiTypes(ExtendPoiTypesEvent event) {
        event.addBlockToPoi(PoiTypes.ARMORER, Blocks.ANVIL);
        event.addBlockToPoi(PoiTypes.ARMORER, Blocks.CHIPPED_ANVIL);
        event.addBlockToPoi(PoiTypes.ARMORER, Blocks.DAMAGED_ANVIL);
    }

    /** Raw ore goes into its blast furnaces. */
    static boolean smelts(ItemStack stack) {
        return stack.is(RAW_MATERIALS);
    }

    /** Logs go into a plain furnace, for charcoal. */
    static boolean burns(ItemStack stack) {
        return stack.is(ItemTags.LOGS_THAT_BURN);
    }

    /** Both share the armorer's job site type, so vanilla hands out "armorer"; the block decides which it really is. */
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level) || villager.tickCount % CHECK_EVERY != 0) return;
        var profession = villager.getVillagerData().profession();
        boolean armorer = profession.is(VillagerProfession.ARMORER);
        if (!armorer && !profession.is(PROFESSION)) return;
        Optional<BlockPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
        if (site.isEmpty()) return;
        var block = level.getBlockState(site.get());
        ResourceKey<VillagerProfession> wanted = block.getBlock() instanceof BlastFurnaceBlock ? PROFESSION : block.is(BlockTags.ANVIL) ? VillagerProfession.ARMORER : null;
        if (wanted == null || profession.is(wanted)) return;
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), wanted));
        villager.refreshBrain(level);
    }
}
