package com.sheepfromheaven.minenow.armorer;

import com.sheepfromheaven.minenow.MineNow;
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
 * ({@link ShowArmor}). A stand it dressed is its display: while that armorer lives, players can't take the armor or
 * break the stand, and right-clicking it says whose display it is.
 */
public final class Armorer {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MineNow.MOD_ID);

    static final Supplier<AttachmentType<UUID>> DISPLAYED_BY = ATTACHMENT_TYPES.register("displayed_by",
            () -> AttachmentType.builder(() -> new UUID(0L, 0L))
                    .serialize(UUIDUtil.CODEC.fieldOf("armorer"))
                    .build());

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
        if (event.getTarget() instanceof ArmorStand stand && stand.level() instanceof ServerLevel level && owner(level, stand) != null) event.setCanceled(true);
    }
}
