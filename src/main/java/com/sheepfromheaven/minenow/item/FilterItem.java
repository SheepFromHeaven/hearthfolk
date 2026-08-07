package com.sheepfromheaven.minenow.item;

import com.sheepfromheaven.minenow.inventory.FilterMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class FilterItem extends Item {
    public FilterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var stack = player.getItemInHand(hand);
        var data = stack.getOrDefault(ModDataComponents.FILTER_DATA, FilterData.EMPTY);
        ((ServerPlayer) player).openMenu(
                new SimpleMenuProvider((containerId, inv, p) ->
                        new FilterMenu(containerId, inv, p, hand, data), Component.literal("Filter")),
                buf -> {
                    buf.writeBoolean(data.whitelist());
                    buf.writeBoolean(data.matchMetadata());
                });
        return InteractionResult.SUCCESS;
    }
}
