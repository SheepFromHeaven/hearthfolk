package com.sheepfromheaven.minenow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Draws the speech bubble the server sent for an entity ({@code villager/SpeechBubbles}) over its head, billboarded. It hooks
 * every living entity's render through NeoForge's event rather than a layer of our villager renderer, so it looks the same
 * with vanilla's renderer under Entity Model Features.
 */
@EventBusSubscriber(modid = MineNow.MOD_ID, value = Dist.CLIENT)
public final class SpeechBubbleRenderer {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/gui/speech_bubble.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityCutoutCull(TEXTURE);
    private static final ContextKey<Integer> ENTITY_ID = new ContextKey<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "bubble_entity_id"));
    private static final int FADE_IN = 5;
    private static final int FADE_OUT = 10;
    private static final float WIDTH_PX = 22;
    private static final float HEIGHT_PX = 23;
    private static final float PIXEL = 1F / 32F;
    private static final Map<Integer, Bubble> BUBBLES = new HashMap<>();

    private SpeechBubbleRenderer() {}

    private static final class Bubble {
        final ItemStack icon;
        final Optional<Identifier> imageId;
        final RenderType image;
        int total;
        int age;
        final ItemStackRenderState item = new ItemStackRenderState();

        Bubble(ItemStack icon, Optional<Identifier> image, int total) {
            this.icon = icon;
            this.imageId = image;
            this.image = image.map(id -> RenderTypes.entityCutoutCull(Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/gui/bubble/" + id.getPath() + ".png"))).orElse(null);
            this.total = total;
        }
    }

    /** One bubble per entity at a time: a new one replaces the old, the same one again only extends its time, an empty icon clears it. */
    public static void set(int entityId, ItemStack icon, Optional<Identifier> image, int ticks) {
        if (icon.isEmpty() && image.isEmpty() || ticks <= 0) BUBBLES.remove(entityId);
        else {
            Bubble shown = BUBBLES.get(entityId);
            if (shown != null && shown.imageId.equals(image) && ItemStack.matches(shown.icon, icon)) shown.total = shown.age + ticks;
            else BUBBLES.put(entityId, new Bubble(icon, image, ticks));
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        BUBBLES.values().removeIf(bubble -> ++bubble.age >= bubble.total);
        if (Minecraft.getInstance().level == null) BUBBLES.clear();
    }

    @SubscribeEvent
    static void onRegisterModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new com.google.common.reflect.TypeToken<net.minecraft.client.renderer.entity.LivingEntityRenderer<LivingEntity, net.minecraft.client.renderer.entity.state.LivingEntityRenderState, ?>>() {},
                (LivingEntity entity, net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) -> state.setRenderData(ENTITY_ID, entity.getId()));
    }

    @SubscribeEvent
    static void onRenderLiving(RenderLivingEvent.Post<?, ?, ?> event) {
        EntityRenderState state = event.getRenderState();
        Integer id = state.getRenderData(ENTITY_ID);
        Bubble bubble = id == null ? null : BUBBLES.get(id);
        if (bubble == null || state.isInvisible || state.isDiscrete) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.isShiftKeyDown()) return;

        float age = bubble.age + event.getPartialTick();
        float alpha = Mth.clamp(Math.min(age / FADE_IN, (bubble.total - age) / FADE_OUT), 0F, 1F);
        if (alpha <= 0F) return;

        double top = state.nameTagAttachment != null ? state.nameTagAttachment.y : state.boundingBoxHeight + 0.5;
        if (state.nameTag != null) top += 0.3;
        if (state.scoreText != null) top += 0.3;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0, top + 0.5, 0.0);
        poseStack.rotate(minecraft.gameRenderer.mainCamera().rotation());
        int argb = ((int) (alpha * 255F) << 24) | 0xFFFFFF;
        poseStack.pushPose();
        poseStack.translate(-WIDTH_PX * PIXEL / 2F, 0.0F, 0.0F);
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            float w = WIDTH_PX * PIXEL;
            float h = HEIGHT_PX * PIXEL;
            vertex(buffer, pose, 0, 0, 0, 1, argb);
            vertex(buffer, pose, w, 0, 1, 1, argb);
            vertex(buffer, pose, w, h, 1, 0, argb);
            vertex(buffer, pose, 0, h, 0, 0, argb);
        });
        poseStack.popPose();

        if (bubble.image != null) {
            float size = 16 * PIXEL;
            float x = (WIDTH_PX - 16) / 2F * PIXEL - WIDTH_PX * PIXEL / 2F;
            float y = HEIGHT_PX * PIXEL - (2 + 16) * PIXEL;
            poseStack.translate(x, y, -0.01F);
            event.getSubmitNodeCollector().submitCustomGeometry(poseStack, bubble.image, (pose, buffer) -> {
                vertex(buffer, pose, 0, 0, 0, 1, argb);
                vertex(buffer, pose, size, 0, 1, 1, argb);
                vertex(buffer, pose, size, size, 1, 0, argb);
                vertex(buffer, pose, 0, size, 0, 0, argb);
            });
            poseStack.popPose();
            return;
        }
        minecraft.getItemModelResolver().updateForTopItem(bubble.item, bubble.icon, ItemDisplayContext.GUI, minecraft.level, null, 0);
        if (!bubble.item.isEmpty()) {
            float centre = HEIGHT_PX * PIXEL - (2 + 8) * PIXEL;
            poseStack.translate(0.0F, centre + 0.0F, -0.01F);
            float size = 16 * PIXEL;
            poseStack.scale(size, size, size * 0.05F);
            bubble.item.submit(poseStack, event.getSubmitNodeCollector(), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        }
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float u, float v, int argb) {
        buffer.addVertex(pose, x, y, 0.0F).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
