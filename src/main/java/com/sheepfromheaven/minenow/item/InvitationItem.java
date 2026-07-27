package com.sheepfromheaven.minenow.item;

import com.sheepfromheaven.minenow.dominion.DominionInhabitant;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.entity.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class InvitationItem extends Item {
    private static final String[] INHABITANT_NAMES = {
            "Ada", "Bjorn", "Clara", "Daren", "Elena", "Finn", "Greta", "Hugo",
            "Ingrid", "Jonas", "Kira", "Lars", "Mara", "Nils", "Olga", "Per",
            "Quinn", "Rosa", "Sven", "Tilde", "Ulrik", "Vera", "Wren", "Ylva"
    };

    public InvitationItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Villager villager)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        var data = DominionSavedData.get(serverLevel);
        var dominion = data.findDominionContaining(player.blockPosition());
        if (dominion == null || !dominion.getOwnerUuid().equals(player.getUUID())) {
            player.displayClientMessage(Component.literal("You must be inside your dominion to recruit!"), true);
            return InteractionResult.FAIL;
        }

        int max = dominion.getTier().getMaxInhabitants();
        if (dominion.getInhabitants().size() >= max) {
            player.displayClientMessage(Component.literal(
                    "Your " + dominion.getTier().getDisplayName().toLowerCase() + " can only support " + max
                            + " inhabitants. Increase your dominion tier to recruit more!"), false);
            return InteractionResult.FAIL;
        }

        var entityUuid = villager.getUUID();
        var name = INHABITANT_NAMES[(int) (entityUuid.getLeastSignificantBits() & 0x7FFFFFFF) % INHABITANT_NAMES.length];

        var inhabitantEntity = ModEntities.INHABITANT.get().create(serverLevel, EntitySpawnReason.CONVERSION);
        if (inhabitantEntity == null) {
            return InteractionResult.FAIL;
        }
        inhabitantEntity.snapTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot());
        inhabitantEntity.setCustomName(Component.literal(name));
        inhabitantEntity.setCustomNameVisible(true);
        inhabitantEntity.setDominionId(dominion.getId());
        serverLevel.addFreshEntity(inhabitantEntity);
        villager.discard();

        var inhabitant = new DominionInhabitant(inhabitantEntity.getUUID(), name);
        dominion.addInhabitant(inhabitant);
        data.setDirty();

        if (!player.isCreative()) {
            stack.shrink(1);
        }

        player.displayClientMessage(Component.literal(name + " has joined your dominion!"), false);
        return InteractionResult.SUCCESS;
    }
}
