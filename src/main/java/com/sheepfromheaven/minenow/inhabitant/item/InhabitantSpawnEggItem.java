package com.sheepfromheaven.minenow.inhabitant.item;

import com.sheepfromheaven.minenow.dominion.DominionInhabitant;
import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.inhabitant.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Spawn egg for {@link com.sheepfromheaven.minenow.inhabitant.InhabitantEntity}. Unlike a vanilla spawn
 * egg, an inhabitant only exists as part of a dominion, so this refuses to spawn outside a claimed
 * area and, on success, joins the new inhabitant to the containing dominion using the same
 * name-pool/addInhabitant pattern as {@link InvitationItem}'s villager-to-inhabitant conversion.
 */
public class InhabitantSpawnEggItem extends SpawnEggItem {
    private static final String[] INHABITANT_NAMES = {
            "Ada", "Bjorn", "Clara", "Daren", "Elena", "Finn", "Greta", "Hugo",
            "Ingrid", "Jonas", "Kira", "Lars", "Mara", "Nils", "Olga", "Per",
            "Quinn", "Rosa", "Sven", "Tilde", "Ulrik", "Vera", "Wren", "Ylva"
    };

    public InhabitantSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        var player = context.getPlayer();
        BlockPos clickedPos = context.getClickedPos();
        Direction direction = context.getClickedFace();
        var blockState = level.getBlockState(clickedPos);
        BlockPos spawnPos = blockState.getCollisionShape(level, clickedPos).isEmpty()
                ? clickedPos
                : clickedPos.relative(direction);

        var data = DominionSavedData.get(serverLevel);
        var dominion = data.findDominionContaining(spawnPos);
        if (dominion == null) {
            if (player != null) {
                player.displayClientMessage(
                        Component.literal("Inhabitants can only be spawned inside a dominion's claimed area."), true);
            }
            return InteractionResult.FAIL;
        }

        int max = dominion.getTier().getMaxInhabitants();
        if (dominion.getInhabitants().size() >= max) {
            if (player != null) {
                player.displayClientMessage(Component.literal(
                        "This " + dominion.getTier().getDisplayName().toLowerCase() + " can only support " + max
                                + " inhabitants. Increase its tier to add more!"), false);
            }
            return InteractionResult.FAIL;
        }

        var inhabitantEntity = ModEntities.INHABITANT.get().create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
        if (inhabitantEntity == null) {
            return InteractionResult.FAIL;
        }
        inhabitantEntity.snapTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0F, 0.0F);
        var name = INHABITANT_NAMES[(int) (inhabitantEntity.getUUID().getLeastSignificantBits() & 0x7FFFFFFF) % INHABITANT_NAMES.length];
        inhabitantEntity.setCustomName(Component.literal(name));
        inhabitantEntity.setCustomNameVisible(true);
        inhabitantEntity.setDominionId(dominion.getId());
        serverLevel.addFreshEntity(inhabitantEntity);

        var inhabitant = new DominionInhabitant(inhabitantEntity.getUUID(), name);
        if (!dominion.addInhabitant(inhabitant)) {
            // Capacity was checked above but re-check the return value in case it changed concurrently;
            // undo the spawn rather than leaving an inhabitant with no dominion record.
            inhabitantEntity.discard();
            if (player != null) {
                player.displayClientMessage(Component.literal("This dominion filled up just now - try again."), false);
            }
            return InteractionResult.FAIL;
        }
        data.setDirty();

        context.getItemInHand().shrink(1);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, spawnPos);
        if (player != null) {
            player.displayClientMessage(Component.literal(name + " has joined the dominion!"), false);
        }
        return InteractionResult.SUCCESS;
    }
}
