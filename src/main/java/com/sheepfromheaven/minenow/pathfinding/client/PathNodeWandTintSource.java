package com.sheepfromheaven.minenow.pathfinding.client;

import com.mojang.serialization.MapCodec;
import com.sheepfromheaven.minenow.pathfinding.DominionPathNode;
import com.sheepfromheaven.minenow.pathfinding.item.PathNodeWandItem;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Colors the path-node wand texture based on the wand's active {@link DominionPathNode.Impact}:
 * green = LOW, yellow = MEDIUM, red = HIGH. Registered as {@code minenow:path_node_wand} and
 * referenced in the item model JSON's {@code tints} array.
 */
public final class PathNodeWandTintSource implements ItemTintSource {
    public static final PathNodeWandTintSource INSTANCE = new PathNodeWandTintSource();
    public static final MapCodec<PathNodeWandTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);

    private PathNodeWandTintSource() {}

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
        return switch (PathNodeWandItem.getImpact(stack)) {
            case LOW    -> 0xFF55FF55; // opaque green
            case MEDIUM -> 0xFFFFFF55; // opaque yellow
            case HIGH   -> 0xFFFF5555; // opaque red
        };
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
}
