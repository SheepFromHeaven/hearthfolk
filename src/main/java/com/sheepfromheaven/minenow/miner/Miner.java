package com.sheepfromheaven.minenow.miner;

import com.google.common.collect.ImmutableSet;
import com.sheepfromheaven.minenow.MineNow;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The miner: a new profession whose job site is a furnace minecart parked off the rails ({@link FurnaceCartBlock}).
 * Using a furnace minecart on anything but a rail places it as that block.
 */
public final class Miner {
    public static final ResourceKey<PoiType> POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "miner"));
    public static final ResourceKey<VillagerProfession> PROFESSION = ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "miner"));

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MineNow.MOD_ID);
    private static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MineNow.MOD_ID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, MineNow.MOD_ID);

    public static final DeferredBlock<FurnaceCartBlock> FURNACE_CART = BLOCKS.registerBlock("furnace_minecart", FurnaceCartBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(3.5F).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> state.getValue(FurnaceBlock.LIT) ? 13 : 0));

    static {
        POI_TYPES.register("miner", () -> new PoiType(ImmutableSet.copyOf(FURNACE_CART.get().getStateDefinition().getPossibleStates()), 1, 1));
        PROFESSIONS.register("miner", () -> new VillagerProfession(
                Component.translatable("entity.minenow.villager.miner"),
                poi -> poi.is(POI),
                poi -> poi.is(POI),
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_TOOLSMITH,
                Int2ObjectMap.ofEntries()));
    }

    private Miner() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
        modBus.addListener(Miner::onAddBlocksToBlockEntityTypes);
    }

    private static void onAddBlocksToBlockEntityTypes(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityTypes.FURNACE, FURNACE_CART.get());
    }

    /** A furnace minecart used on anything but a rail is parked there as a block instead of being placed as a minecart. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        Level level = event.getLevel();
        if (!stack.is(Items.FURNACE_MINECART) || level.getBlockState(event.getPos()).is(BlockTags.RAILS)) return;
        BlockPlaceContext context = new BlockPlaceContext(event.getEntity(), event.getHand(), stack, event.getHitVec());
        if (!context.canPlace()) return;
        BlockPos pos = context.getClickedPos();
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide()) return;
        BlockState state = FURNACE_CART.get().defaultBlockState().setValue(FurnaceBlock.FACING, context.getHorizontalDirection().getOpposite());
        level.setBlock(pos, state, 3);
        level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.gameEvent(event.getEntity(), GameEvent.BLOCK_PLACE, pos);
        stack.consume(1, event.getEntity());
    }

    /** Down in a mine, where buyers can't follow without falling in. */
    public static boolean isDownAMine(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.npc.villager.Villager villager) {
        return LadderClimb.inQuarry(level, villager);
    }
}
