package com.sheepfromheaven.minenow.core;

import com.sheepfromheaven.minenow.core.MineNow;
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
import com.sheepfromheaven.minenow.structure.block.StructureMarkerBlock;
import com.sheepfromheaven.minenow.structure.block.StructureMarkerBlockEntity;
import com.sheepfromheaven.minenow.dominion.townbanner.TownBannerBlock;
import com.sheepfromheaven.minenow.dominion.townbanner.TownBannerBlockEntity;
import com.sheepfromheaven.minenow.dominion.townbanner.TownWallBannerBlock;

public class ModBlocks {
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK, MineNow.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, MineNow.MOD_ID);

    private static BlockBehaviour.Properties markerProps(String name) {
        return BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, name)))
                .mapColor(MapColor.STONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(1.5F)
                .sound(SoundType.STONE);
    }

    private static DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> registerMarker(
            String name, com.sheepfromheaven.minenow.structure.StructureType type) {
        return BLOCKS.register(name, () -> new StructureMarkerBlock(markerProps(name), type));
    }

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
            registerMarker("structure_marker", com.sheepfromheaven.minenow.structure.StructureType.HOUSE);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> FISHERY_MARKER =
            registerMarker("fishery_marker", com.sheepfromheaven.minenow.structure.StructureType.FISHERY);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> LUMBERYARD_MARKER =
            registerMarker("lumberyard_marker", com.sheepfromheaven.minenow.structure.StructureType.LUMBERYARD);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> TAVERN_MARKER =
            registerMarker("tavern_marker", com.sheepfromheaven.minenow.structure.StructureType.TAVERN);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> STOREHOUSE_MARKER =
            registerMarker("storehouse_marker", com.sheepfromheaven.minenow.structure.StructureType.STOREHOUSE);

    public static StructureMarkerBlock getMarkerBlock(com.sheepfromheaven.minenow.structure.StructureType type) {
        return switch (type) {
            case HOUSE -> STRUCTURE_MARKER.get();
            case FISHERY -> FISHERY_MARKER.get();
            case LUMBERYARD -> LUMBERYARD_MARKER.get();
            case TAVERN -> TAVERN_MARKER.get();
            case STOREHOUSE -> STOREHOUSE_MARKER.get();
        };
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TownBannerBlockEntity>> TOWN_BANNER_BE =
            BLOCK_ENTITIES.register("town_banner",
                    () -> new BlockEntityType<>(TownBannerBlockEntity::new,
                            Set.of(TOWN_BANNER.get(), TOWN_WALL_BANNER.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StructureMarkerBlockEntity>> STRUCTURE_MARKER_BE =
            BLOCK_ENTITIES.register("structure_marker",
                    () -> new BlockEntityType<>(StructureMarkerBlockEntity::new,
                            Set.of(STRUCTURE_MARKER.get(), FISHERY_MARKER.get(), LUMBERYARD_MARKER.get(),
                                    TAVERN_MARKER.get(), STOREHOUSE_MARKER.get())));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
