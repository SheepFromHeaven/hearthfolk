package com.sheepfromheaven.minenow.item;

import com.sheepfromheaven.minenow.entity.job.TreeScannerDebugger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Dev tool: right-click a log to slowly narrate {@link TreeScannerDebugger}'s scan up the trunk. */
public class TreeScannerWandItem extends Item {
    public TreeScannerWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (context.getLevel() instanceof ServerLevel serverLevel && context.getPlayer() instanceof ServerPlayer serverPlayer) {
            TreeScannerDebugger.start(serverPlayer, serverLevel, context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }
}
