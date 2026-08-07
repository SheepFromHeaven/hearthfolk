package com.sheepfromheaven.minenow.structure;

import com.sheepfromheaven.minenow.core.MineNow;
import com.sheepfromheaven.minenow.structure.block.StructureMarkerBlock;
import com.sheepfromheaven.minenow.structure.block.StructureMarkerBlockEntity;
import com.sheepfromheaven.minenow.structure.field.block.FieldMarkerBlock;
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

public class StructureBlocks {
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, MineNow.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MineNow.MOD_ID);

    static BlockBehaviour.Properties markerProps(String name) {
        return BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MineNow.MOD_ID, name)))
                .mapColor(MapColor.STONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(1.5F)
                .sound(SoundType.STONE);
    }

    private static DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> registerMarker(
            String name, StructureType type) {
        return BLOCKS.register(name, () -> new StructureMarkerBlock(markerProps(name), type));
    }

    public static final DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> STRUCTURE_MARKER =
            registerMarker("structure_marker", StructureType.HOUSE);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> FISHERY_MARKER =
            registerMarker("fishery_marker", StructureType.FISHERY);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> LUMBERYARD_MARKER =
            registerMarker("lumberyard_marker", StructureType.LUMBERYARD);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> TAVERN_MARKER =
            registerMarker("tavern_marker", StructureType.TAVERN);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> STOREHOUSE_MARKER =
            registerMarker("storehouse_marker", StructureType.STOREHOUSE);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> MINE_MARKER =
            registerMarker("mine_marker", StructureType.MINE);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> NURSERY_MARKER =
            registerMarker("nursery_marker", StructureType.NURSERY);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> CHARCOAL_KILN_MARKER =
            registerMarker("charcoal_kiln_marker", StructureType.CHARCOAL_KILN);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, StructureMarkerBlock> FARM_MARKER =
            registerMarker("farm_marker", StructureType.FARM);

    public static final DeferredHolder<net.minecraft.world.level.block.Block, FieldMarkerBlock> FIELD_MARKER =
            BLOCKS.register("field_marker", () -> new FieldMarkerBlock(markerProps("field_marker")));

    public static StructureMarkerBlock getMarkerBlock(StructureType type) {
        return switch (type) {
            case HOUSE -> STRUCTURE_MARKER.get();
            case FISHERY -> FISHERY_MARKER.get();
            case LUMBERYARD -> LUMBERYARD_MARKER.get();
            case TAVERN -> TAVERN_MARKER.get();
            case STOREHOUSE -> STOREHOUSE_MARKER.get();
            case MINE -> MINE_MARKER.get();
            case NURSERY -> NURSERY_MARKER.get();
            case CHARCOAL_KILN -> CHARCOAL_KILN_MARKER.get();
            case FARM -> FARM_MARKER.get();
        };
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StructureMarkerBlockEntity>> STRUCTURE_MARKER_BE =
            BLOCK_ENTITIES.register("structure_marker",
                    () -> new BlockEntityType<>(StructureMarkerBlockEntity::new,
                            Set.of(STRUCTURE_MARKER.get(), FISHERY_MARKER.get(), LUMBERYARD_MARKER.get(),
                                    TAVERN_MARKER.get(), STOREHOUSE_MARKER.get(), MINE_MARKER.get(),
                                    NURSERY_MARKER.get(), CHARCOAL_KILN_MARKER.get(), FARM_MARKER.get())));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
