package com.sheepfromheaven.minenow.armorer;

import com.sheepfromheaven.minenow.MineNow;
import com.sheepfromheaven.minenow.villager.Economy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * The armorer (vanilla's, at its blast furnace) shows off the armor it makes on armor stands near its workstation
 * ({@link ShowArmor}). A stand it dressed is its display: while that armorer lives, players can't take the armor, and
 * right-clicking it says whose display it is. Breaking it is fine (the player may be moving it): the armor goes back to
 * the armorer, which dresses the next stand.
 */
public final class Armorer {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MineNow.MOD_ID);

    static final Supplier<AttachmentType<UUID>> DISPLAYED_BY = ATTACHMENT_TYPES.register("displayed_by",
            () -> AttachmentType.builder(() -> new UUID(0L, 0L))
                    .serialize(UUIDUtil.CODEC.fieldOf("armorer"))
                    .build());

    private static final int RECENT_TICKS = 100;
    private static final Map<UUID, Attack> LAST_ATTACK = new HashMap<>();

    /** What an armorer's display showed when a player last hit or shot it, to give back to the armorer if that breaks it. */
    private record Attack(long tick, List<ItemStack> armor) {}

    private Armorer() {}

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }

    /** The living armorer whose display this stand is; a stand whose armorer is gone is the player's again. */
    static @Nullable Villager owner(ServerLevel level, ArmorStand stand) {
        if (!stand.hasData(DISPLAYED_BY)) return null;
        if (level.getEntity(stand.getData(DISPLAYED_BY)) instanceof Villager armorer && armorer.isAlive()
                && armorer.getVillagerData().profession().is(VillagerProfession.ARMORER)) {
            return armorer;
        }
        stand.removeData(DISPLAYED_BY);
        return null;
    }

    /** How much protection a piece gives, armor plus toughness; what decides which piece is shown. */
    static double protection(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return modifiers.modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ARMOR) || entry.attribute().equals(Attributes.ARMOR_TOUGHNESS))
                .mapToDouble(entry -> entry.modifier().amount())
                .sum();
    }

    static @Nullable EquipmentSlot armorSlot(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR) return null;
        return equippable.slot();
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ArmorStand stand) || !(stand.level() instanceof ServerLevel level)) return;
        Villager armorer = owner(level, stand);
        if (armorer == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        event.getEntity().sendOverlayMessage(Component.translatableWithFallback("minenow.armor_stand.displayed_by", "This is %s's armor display", armorer.getDisplayName()));
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getTarget() instanceof ArmorStand stand) remember(stand, event.getEntity());
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof ArmorStand stand && event.getProjectile().getOwner() instanceof Player player) {
            remember(stand, player);
        }
    }

    /** A broken display's armor goes back to its armorer rather than to whoever broke it, so moving a stand costs nothing. */
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand) || !(event.getLevel() instanceof ServerLevel level)) return;
        Attack attack = LAST_ATTACK.remove(stand.getUUID());
        if (stand.getRemovalReason() != Entity.RemovalReason.KILLED) return;
        Villager armorer = owner(level, stand);
        if (armorer == null || attack == null || level.getGameTime() - attack.tick() > RECENT_TICKS) return;
        for (ItemStack piece : attack.armor()) {
            level.getEntitiesOfClass(ItemEntity.class, stand.getBoundingBox().inflate(2.0), item -> item.isAlive() && ItemStack.isSameItemSameComponents(item.getItem(), piece))
                    .stream().findFirst().ifPresent(item -> {
                        Economy.give(level, armorer, item.getItem().copy());
                        item.discard();
                    });
        }
    }

    private static void remember(ArmorStand stand, Player player) {
        if (!(stand.level() instanceof ServerLevel level) || owner(level, stand) == null) return;
        List<ItemStack> armor = EquipmentSlot.VALUES.stream().filter(slot -> slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR)
                .map(stand::getItemBySlot).filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        LAST_ATTACK.put(stand.getUUID(), new Attack(level.getGameTime(), armor));
    }
}
