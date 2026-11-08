package com.sheepfromheaven.minenow.forester;

import com.google.common.collect.ImmutableSet;
import com.sheepfromheaven.minenow.MineNow;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The forester: a new profession whose job site is a nursery, a sapling potted on a log ({@link NurseryBlock}).
 * Potting a sapling into a flower pot that stands on a log makes one; a pot anywhere else stays vanilla's.
 */
public final class Forester {
    public static final ResourceKey<PoiType> POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "forester"));
    public static final ResourceKey<VillagerProfession> PROFESSION = ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "forester"));

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MineNow.MOD_ID);
    private static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MineNow.MOD_ID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, MineNow.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MineNow.MOD_ID);

    /** Stumps a forester felled without a sapling to put back, replanted once it has one. */
    public static final Supplier<AttachmentType<List<BlockPos>>> REPLANT = ATTACHMENT_TYPES.register("replant",
            () -> AttachmentType.<List<BlockPos>>builder(() -> List.of())
                    .serialize(BlockPos.CODEC.listOf().fieldOf("stumps"))
                    .build());

    public static final DeferredBlock<NurseryBlock> NURSERY = BLOCKS.registerBlock("nursery", NurseryBlock::new,
            properties -> properties.instabreak().noOcclusion());

    static {
        POI_TYPES.register("forester", () -> new PoiType(ImmutableSet.copyOf(NURSERY.get().getStateDefinition().getPossibleStates()), 1, 1));
        PROFESSIONS.register("forester", () -> new VillagerProfession(
                Component.translatable("entity.minenow.villager.forester"),
                poi -> poi.is(POI),
                poi -> poi.is(POI),
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_FLETCHER,
                Int2ObjectMap.ofEntries()));
    }

    private Forester() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
    }

    public static BlockState nursery(NurseryTree tree) {
        return NURSERY.get().defaultBlockState().setValue(NurseryBlock.TREE, tree);
    }

    public static NurseryTree tree(BlockState nursery) {
        return nursery.getValue(NurseryBlock.TREE);
    }

    /** A sapling potted into a flower pot standing on a log makes a nursery instead of vanilla's potted sapling. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack stack = event.getItemStack();
        Optional<NurseryTree> tree = NurseryTree.of(stack.getItem());
        if (tree.isEmpty() || !level.getBlockState(pos).is(Blocks.FLOWER_POT) || !level.getBlockState(pos.below()).is(BlockTags.LOGS)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide()) return;
        level.setBlock(pos, nursery(tree.get()), 3);
        level.gameEvent(event.getEntity(), GameEvent.BLOCK_CHANGE, pos);
        stack.consume(1, event.getEntity());
    }
}
