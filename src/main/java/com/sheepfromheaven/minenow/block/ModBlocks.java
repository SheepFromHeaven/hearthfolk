package com.sheepfromheaven.minenow.block;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

public class ModBlocks {
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK, MineNow.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, MineNow.MOD_ID);

    private static BlockBehaviour.Properties bannerProps(String name) {
        return BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, name)))
                .mapColor(MapColor.WOOD)
                .forceSolidOn()
                .instrument(NoteBlockInstrument.BASS)
                .noCollision()
                .strength(1.0F)
                .sound(SoundType.WOOD);
    }

    public static final DeferredHolder<net.minecraft.world.level.block.Block, TownBannerBlock> TOWN_BANNER =
            BLOCKS.register("town_banner", () -> new TownBannerBlock(bannerProps("town_banner")));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, TownWallBannerBlock> TOWN_WALL_BANNER =
            BLOCKS.register("town_wall_banner", () -> new TownWallBannerBlock(bannerProps("town_wall_banner")));

    public static final DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> STRUCTURE_MARKER =
            BLOCKS.register("structure_marker", () -> new StructureMarkerBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "structure_marker")))
                            .mapColor(MapColor.STONE)
                            .instrument(NoteBlockInstrument.BASEDRUM)
                            .strength(1.5F)
                            .sound(SoundType.STONE)
            ));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TownBannerBlockEntity>> TOWN_BANNER_BE =
            BLOCK_ENTITIES.register("town_banner",
                    () -> new BlockEntityType<>(TownBannerBlockEntity::new,
                            Set.of(TOWN_BANNER.get(), TOWN_WALL_BANNER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StructureMarkerBlockEntity>> STRUCTURE_MARKER_BE =
            BLOCK_ENTITIES.register("structure_marker",
                    () -> new BlockEntityType<>(StructureMarkerBlockEntity::new,
                            Set.of(STRUCTURE_MARKER.get())));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
