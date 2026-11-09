package com.sheepfromheaven.minenow.golemsmith;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Where a golemsmith raises a golem: the T's base, the way it faces (the golemsmith works from the cell behind the base)
 * and whether a poppy marked the spot, so it goes back once the golem stands up.
 */
public record GolemSite(BlockPos base, Direction facing, boolean marked) {
    public static final Codec<GolemSite> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("base").forGetter(GolemSite::base),
            Direction.CODEC.fieldOf("facing").forGetter(GolemSite::facing),
            Codec.BOOL.fieldOf("marked").forGetter(GolemSite::marked)
    ).apply(i, GolemSite::new));

    public BlockPos stand() {
        return base.relative(facing.getOpposite());
    }

    /** Vanilla's T in the order a golemsmith stacks it: base, body, both arms, the pumpkin last. */
    public List<BlockPos> steps() {
        Direction side = facing.getClockWise();
        BlockPos body = base.above();
        return List.of(base, body, body.relative(side), body.relative(side.getOpposite()), body.above());
    }

    public List<BlockPos> ironCells() {
        return steps().subList(0, 4);
    }

    /** The cells beside the T that vanilla's pattern needs empty. */
    public List<BlockPos> clearings() {
        Direction side = facing.getClockWise();
        return List.of(base.relative(side), base.relative(side.getOpposite()), base.above(2).relative(side), base.above(2).relative(side.getOpposite()));
    }
}
