package com.sheepfromheaven.minenow.jobs.fisher.client;

import com.sheepfromheaven.minenow.core.MineNow;
import com.sheepfromheaven.minenow.inhabitant.InhabitantEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.layer.builtin.ItemInHandGeoLayer;

import java.util.EnumMap;

/**
 * Renders the inhabitant's held fishing rod, swapping in the vanilla "cast" (unreeled) rod model
 * while the fisher is casting or reeling. Vanilla's cast model only triggers for a {@link net.minecraft.world.entity.player.Player}
 * whose {@code fishing} hook is out, so for an NPC we force it via the {@code minecraft:item_model}
 * component pointing at {@link #CAST_ROD_MODEL}.
 */
public class FisherRodInHandLayer<O, R extends GeoRenderState> extends ItemInHandGeoLayer<InhabitantEntity, O, R> {
    private static final Identifier CAST_ROD_MODEL = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "fishing_rod_cast");

    public FisherRodInHandLayer(GeoRenderer<InhabitantEntity, O, R> renderer, String rightHandBone, String leftHandBone) {
        super(renderer, rightHandBone, leftHandBone);
    }

    @Override
    public void addRenderData(InhabitantEntity animatable, O relatedObject, R renderState, float partialTick) {
        super.addRenderData(animatable, relatedObject, renderState, partialTick);
        if (animatable.getFisherState() == 0) {
            return;
        }
        EnumMap<EquipmentSlot, ItemStack> equipment = renderState.getGeckolibData(DataTickets.EQUIPMENT_BY_SLOT);
        if (equipment == null) {
            return;
        }
        ItemStack main = equipment.get(EquipmentSlot.MAINHAND);
        if (main != null && main.is(Items.FISHING_ROD)) {
            ItemStack cast = main.copy();
            cast.set(DataComponents.ITEM_MODEL, CAST_ROD_MODEL);
            equipment.put(EquipmentSlot.MAINHAND, cast);
        }
    }
}
