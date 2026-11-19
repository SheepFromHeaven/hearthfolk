package com.sheepfromheaven.minenow.golemsmith;

import com.google.common.collect.ImmutableSet;
import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.MineNowConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import com.sheepfromheaven.minenow.villager.StorageTheft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The golemsmith: a new profession whose job site is a golem workshop, an iron block under a carved pumpkin.
 * Using a carved pumpkin on an iron block while sneaking makes one (without sneaking, golem building stays vanilla).
 * It mends hurt iron golems with ingots and, from journeyman, builds them ({@link TendGolems}). Breaking the iron of a
 * golem it has started is allowed, but it remembers who did. Villagers no longer summon golems out of thin air: the
 * golemsmith is where a village's golems come from (unless {@link MineNowConfig#villagersSummonGolems()} says otherwise).
 */
public final class Golemsmith {
    public static final ResourceKey<PoiType> POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "golemsmith"));
    public static final ResourceKey<VillagerProfession> PROFESSION = ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "golemsmith"));

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MineNow.MOD_ID);
    private static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MineNow.MOD_ID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, MineNow.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MineNow.MOD_ID);
    private static final int SITE_SEARCH = 16;

    /** The golem a golemsmith has started; only set while one is half-built. */
    public static final Supplier<AttachmentType<GolemSite>> SITE = ATTACHMENT_TYPES.register("golem_site",
            () -> AttachmentType.builder(() -> new GolemSite(BlockPos.ZERO, Direction.NORTH, false))
                    .serialize(GolemSite.CODEC.fieldOf("site"))
                    .build());

    public static final DeferredBlock<Block> WORKSHOP = BLOCKS.registerBlock("golem_workshop", Block::new,
            properties -> properties.mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL));

    static {
        POI_TYPES.register("golemsmith", () -> new PoiType(ImmutableSet.copyOf(WORKSHOP.get().getStateDefinition().getPossibleStates()), 1, 1));
        PROFESSIONS.register("golemsmith", () -> new VillagerProfession(
                Component.translatable("entity.minenow.villager.golemsmith"),
                poi -> poi.is(POI),
                poi -> poi.is(POI),
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_ARMORER,
                Int2ObjectMap.ofEntries()));
    }

    private Golemsmith() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
    }

    /**
     * Vetoes vanilla's villager-summoned golems (gossip about golems, panicking villagers) unless the
     * {@code villagersSummonGolems} config switch brings them back. Golems built from iron and a
     * pumpkin are spawned as {@code TRIGGERED}, and those placed with the village structure as {@code STRUCTURE}.
     */
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (MineNowConfig.villagersSummonGolems()) return;
        if (event.getEntity() instanceof IronGolem && event.getSpawnType() == EntitySpawnReason.MOB_SUMMONED) event.setSpawnCancelled(true);
    }

    /** A player breaking the iron of a half-built golem gets it, at the cost of the golemsmith's goodwill, as for a chest. */
    public static void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.getState().is(Blocks.IRON_BLOCK) || event.getPlayer() == null) return;
        for (Villager owner : level.getEntitiesOfClass(Villager.class, new AABB(event.getPos()).inflate(SITE_SEARCH),
                villager -> villager.hasData(SITE) && villager.getData(SITE).ironCells().contains(event.getPos()))) {
            StorageTheft.punish(level, owner, event.getPlayer().getUUID(), StorageTheft.BREAK_GOSSIP);
        }
    }

    /** A carved pumpkin used while sneaking on an iron block turns it into a workshop. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!event.getEntity().isShiftKeyDown() || !event.getItemStack().is(Items.CARVED_PUMPKIN) || !level.getBlockState(pos).is(Blocks.IRON_BLOCK)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide()) return;
        level.setBlock(pos, WORKSHOP.get().defaultBlockState(), 3);
        level.playSound(null, pos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(event.getEntity(), GameEvent.BLOCK_PLACE, pos);
        event.getItemStack().consume(1, event.getEntity());
    }
}
