package com.sheepfromheaven.minenow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

import java.util.HashMap;
import java.util.List;
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
    private static final RenderType RENDER_TYPE = RenderTypes.text(TEXTURE);
    private static final ContextKey<Integer> ENTITY_ID = new ContextKey<>(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "bubble_entity_id"));
    private static final int FADE_IN = 5;
    private static final int FADE_OUT = 10;
    private static final float WIDTH_PX = 22;
    private static final float HEIGHT_PX = 23;
    private static final float PIXEL = 1F / 32F;
    private static final Map<Integer, Bubble> BUBBLES = new HashMap<>();

    private static final float TEX_W = 22;
    private static final float TEX_H = 23;
    /** The bubble's own texture is cut along column 14 and row 9, the stretchable ones, so it grows to fit what it holds. */
    private static final int CUT_X = 14;
    private static final int CUT_Y = 9;
    private static final Identifier CHEST = Identifier.withDefaultNamespace("textures/entity/chest/normal.png");
    private static final Identifier BED_HEAD = Identifier.withDefaultNamespace("textures/block/red_bed_head_up.png");
    private static final Identifier BED_FOOT = Identifier.withDefaultNamespace("textures/block/red_bed_foot_up.png");
    private static final Identifier EXCLAMATION = Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/gui/bubble/exclamation.png");

    /** A picture drawn in a bubble, positioned in bubble pixels from its top left. */
    private sealed interface Layer permits ItemLayer, TextureLayer, RegionLayer {}
    private record ItemLayer(Item item, boolean flip, int x, int y) implements Layer {}
    /** A part of a bigger texture: {@code u,v,w,h} in its pixels, {@code size} its side length. */
    private record RegionLayer(Identifier texture, int size, int u, int v, int w, int h, int x, int y) implements Layer {}
    private record TextureLayer(Identifier texture, int x, int y, int w, int h, boolean turned) implements Layer {
        TextureLayer(Identifier texture, int x, int y, int w, int h) {
            this(texture, x, y, w, h, false);
        }
    }

    /** A bubble drawn from layers: {@code extraWidth}/{@code extraHeight} pixels bigger than the plain bubble. */
    private record Layout(int extraWidth, int extraHeight, List<Layer> layers) {}

    /** Our own pictures that are made of several layers, by the id the server sends. */
    private static final Map<String, Layout> LAYOUTS = Map.of(
            "need_tool", new Layout(2, 4, List.of(
                    new ItemLayer(Items.IRON_PICKAXE, true, 3, 4),
                    new ItemLayer(Items.IRON_AXE, false, 4, 4),
                    new TextureLayer(EXCLAMATION, 15, 3, 5, 9))),
            "need_money", new Layout(2, 4, List.of(
                    new ItemLayer(Items.EMERALD, false, 4, 4),
                    new TextureLayer(EXCLAMATION, 15, 3, 5, 9))),
            "need_storage_full", new Layout(2, 4, List.of(
                    new RegionLayer(CHEST, 64, 42, 14, 14, 5, 5, 5),
                    new RegionLayer(CHEST, 64, 42, 33, 14, 10, 5, 10),
                    new RegionLayer(CHEST, 64, 1, 1, 2, 4, 11, 9),
                    new TextureLayer(EXCLAMATION, 15, 3, 5, 9))),
            "need_bed", new Layout(2, 4, List.of(
                    new TextureLayer(BED_HEAD, 4, 9, 8, 8, true),
                    new TextureLayer(BED_FOOT, 12, 9, 8, 8, true),
                    new TextureLayer(EXCLAMATION, 15, 3, 5, 9))),
            "need_hunger", new Layout(2, 4, List.of(
                    new ItemLayer(Items.BREAD, false, 4, 4),
                    new TextureLayer(EXCLAMATION, 15, 3, 5, 9))),
            // not lacks, but the same bubble and badge: a zombie (fright), a bell (alarm), a door in the rain (shelter)
            "fright", new Layout(2, 4, List.of(
                    status("zombie"),
                    new TextureLayer(EXCLAMATION, 15, 3, 5, 9))),
            "status_alarm", new Layout(2, 4, List.of(
                    new ItemLayer(Items.BELL, false, 4, 4),
                    new TextureLayer(EXCLAMATION, 15, 3, 5, 9))),
            "status_shelter", new Layout(2, 4, List.of(
                    status("shelter"),
                    new TextureLayer(EXCLAMATION, 15, 3, 5, 9))),
            "need_fuel", new Layout(2, 4, List.of(
                    new ItemLayer(Items.COAL, false, 4, 4),
                    new TextureLayer(EXCLAMATION, 15, 3, 5, 9))));

    private static Layer face(String name) {
        return new TextureLayer(Identifier.withDefaultNamespace("textures/block/" + name + ".png"), 4, 4, 16, 16);
    }

    /** A flat side-on picture of a block, drawn from vanilla's own model (the stonecutter with its saw, the lectern with its book): our own texture, since vanilla has no flat one. */
    private static Layer picture(String name) {
        return new TextureLayer(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/gui/bubble/job_" + name + ".png"), 4, 4, 16, 16);
    }

    private static Layer item(Item item) {
        return new ItemLayer(item, false, 4, 4);
    }

    /** What is drawn for each job block, by the item's path, back to front: the front of the block where it has one, else a picture built from vanilla's sprites. Anything else is drawn as the item's flat sprite. */
    private static final Map<String, List<Layer>> JOB_BLOCKS = Map.ofEntries(
            Map.entry("composter", List.of(face("composter_side"))), Map.entry("barrel", List.of(face("barrel_side"))), Map.entry("loom", List.of(face("loom_front"))),
            Map.entry("smoker", List.of(face("smoker_front"))), Map.entry("smithing_table", List.of(face("smithing_table_front"))), Map.entry("fletching_table", List.of(face("fletching_table_front"))),
            Map.entry("blast_furnace", List.of(face("blast_furnace_front"))), Map.entry("stonecutter", List.of(picture("stonecutter"))), Map.entry("lectern", List.of(picture("lectern"))),
            Map.entry("cartography_table", List.of(picture("cartography_table"))), Map.entry("grindstone", List.of(picture("grindstone"))), Map.entry("cauldron", List.of(item(Items.CAULDRON))),
            Map.entry("brewing_stand", List.of(item(Items.BREWING_STAND))), Map.entry("hay_block", List.of(face("hay_block_side"))), Map.entry("iron_block", List.of(face("iron_block"))),
            // the anvil seen from the side, from the model's own proportions
            Map.entry("anvil", List.of(new TextureLayer(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/gui/bubble/anvil.png"), 4, 4, 16, 16))),
            // ours: the miner's furnace minecart, the forester's potted sapling, the golemsmith's iron golem face, the merchant's saddle on a hay bale
            Map.entry("furnace_minecart", List.of(item(Items.FURNACE_MINECART))),
            Map.entry("flower_pot", List.of(new ItemLayer(Items.FLOWER_POT, false, 4, 8), new TextureLayer(Identifier.withDefaultNamespace("textures/block/oak_sapling.png"), 4, 0, 16, 16))),
            Map.entry("carved_pumpkin", List.of(new TextureLayer(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/gui/bubble/job_golemsmith.png"), 4, 1, 16, 20))),
            Map.entry("saddle", List.of(new TextureLayer(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/gui/bubble/job_merchant.png"), 4, 1, 16, 21))));

    private static Layer status(String name) {
        return new TextureLayer(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/gui/bubble/status_" + name + ".png"), 4, 4, 16, 16);
    }

    private static Layer area(String name, int x, int y, int w, int h) {
        return new TextureLayer(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/gui/bubble/area_" + name + ".png"), x, y, w, h);
    }

    /** What the work of each profession looks for, by the key item the server sends: a field, water, a sheep, a cow, a mine. */
    private static final Map<String, List<Layer>> WORK_AREAS = Map.of(
            "wheat_seeds", List.of(face("farmland_moist")),
            "water_bucket", List.of(area("water", 4, 4, 16, 16)),
            "shears", List.of(area("sheep", 6, 5, 12, 12)),
            "wheat", List.of(area("cow", 0, 3, 24, 22)),
            "iron_pickaxe", List.of(area("mine", 4, 4, 16, 16)));

    private static Layout workArea(ItemStack icon) {
        List<Layer> picture = WORK_AREAS.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(icon.getItem()).getPath());
        java.util.ArrayList<Layer> layers = new java.util.ArrayList<>(picture != null ? picture : List.of(item(icon.getItem())));
        layers.add(new TextureLayer(EXCLAMATION, 15, 3, 5, 9));
        return new Layout(2, 4, List.copyOf(layers));
    }

    /** The no job block bubble: the block, drawn like the others, with the exclamation mark. */
    private static Layout jobBlock(ItemStack icon, boolean badge) {
        List<Layer> block = JOB_BLOCKS.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(icon.getItem()).getPath());
        java.util.ArrayList<Layer> layers = new java.util.ArrayList<>(block != null ? block : List.of(item(icon.getItem())));
        if (badge) layers.add(new TextureLayer(EXCLAMATION, 15, 3, 5, 9));
        return new Layout(2, 4, List.copyOf(layers));
    }

    /** Every bubble is the same size: a plain one holds a single item or picture, like the ones above without the badge. */
    private static Layout layoutFor(ItemStack icon, Optional<Identifier> image, boolean need) {
        String path = image.map(Identifier::getPath).orElse("");
        switch (path) {
            case "need_job_block": return jobBlock(icon, true);
            case "chat_job_block": return jobBlock(icon, false);
            case "need_work_area": return workArea(icon);
            default:
        }
        Layout known = LAYOUTS.get(path);
        if (known != null) return known;
        java.util.ArrayList<Layer> layers = new java.util.ArrayList<>();
        if (image.isPresent()) layers.add(new TextureLayer(Identifier.fromNamespaceAndPath(image.get().getNamespace(), "textures/gui/bubble/" + path + ".png"), 4, 4, 16, 16));
        else layers.add(item(icon.getItem()));
        if (need) layers.add(new TextureLayer(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "textures/gui/bubble/cross.png"), 4, 4, 16, 16));
        return new Layout(2, 4, List.copyOf(layers));
    }

    private SpeechBubbleRenderer() {}

    private static final class Bubble {
        final ItemStack icon;
        final Optional<Identifier> imageId;
        final boolean need;
        final Layout layout;
        int total;
        int age;

        Bubble(ItemStack icon, Optional<Identifier> image, boolean need, int total) {
            this.icon = icon;
            this.need = need;
            this.imageId = image;
            this.layout = layoutFor(icon, image, need);
            this.total = total;
        }
    }

    /** One bubble per entity at a time: a new one replaces the old, the same one again only extends its time, an empty icon clears it. */
    public static void set(int entityId, ItemStack icon, Optional<Identifier> image, boolean need, int ticks) {
        if (icon.isEmpty() && image.isEmpty() || ticks <= 0) BUBBLES.remove(entityId);
        else {
            Bubble shown = BUBBLES.get(entityId);
            if (shown != null && shown.imageId.equals(image) && shown.need == need && ItemStack.matches(shown.icon, icon)) shown.total = shown.age + ticks;
            else BUBBLES.put(entityId, new Bubble(icon, image, need, ticks));
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
        float width = TEX_W + bubble.layout.extraWidth();
        float height = TEX_H + bubble.layout.extraHeight();
        poseStack.pushPose();
        poseStack.translate(0.0, top + 0.5, 0.0);
        poseStack.rotate(minecraft.gameRenderer.mainCamera().rotation());
        int argb = ((int) (alpha * 255F) << 24) | 0xFFFFFF;
        poseStack.translate(-width * PIXEL / 2F, 0.0F, 0.0F);
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> bubbleQuads(buffer, pose, width, height, argb));

        int depth = 1;
        for (Layer layer : bubble.layout.layers()) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 0.01F * depth++);
            drawLayer(event, minecraft, poseStack, layer, height, argb);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void drawLayer(RenderLivingEvent.Post<?, ?, ?> event, Minecraft minecraft, PoseStack poseStack, Layer layer, float height, int argb) {
        switch (layer) {
            case TextureLayer texture -> event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.text(texture.texture()), (pose, buffer) -> {
                if (texture.turned()) blitTurned(buffer, pose, texture.x(), texture.y(), texture.w(), texture.h(), height, argb);
                else blit(buffer, pose, texture.x(), texture.y(), texture.w(), texture.h(), 0, 0, 1, 1, height, argb);
            });
            case RegionLayer region -> event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.text(region.texture()), (pose, buffer) ->
                    blit(buffer, pose, region.x(), region.y(), region.w(), region.h(), (float) region.u() / region.size(), (float) region.v() / region.size(),
                            (float) (region.u() + region.w()) / region.size(), (float) (region.v() + region.h()) / region.size(), height, argb));
            case ItemLayer item -> {
                ItemStackRenderState state = new ItemStackRenderState();
                minecraft.getItemModelResolver().updateForTopItem(state, new ItemStack(item.item()), ItemDisplayContext.GUI, minecraft.level, null, 0);
                if (state.isEmpty()) return;
                TextureAtlasSprite sprite = state.pickParticleMaterial(minecraft.level.getRandom()).sprite();
                event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.text(TextureAtlas.LOCATION_ITEMS), (pose, buffer) ->
                        blit(buffer, pose, item.x(), item.y(), 16, 16, item.flip() ? sprite.getU1() : sprite.getU0(), sprite.getV0(), item.flip() ? sprite.getU0() : sprite.getU1(), sprite.getV1(), height, argb));
            }
        }
    }

    /** The bubble texture in nine pieces: the corners and the tail keep their size, column {@code CUT_X} and row {@code CUT_Y} stretch. */
    private static void bubbleQuads(VertexConsumer buffer, PoseStack.Pose pose, float width, float height, int argb) {
        float stretchW = width - (TEX_W - 1);
        float stretchH = height - (TEX_H - 1);
        float[] xs = {0, CUT_X, CUT_X + stretchW, width};
        float[] us = {0, CUT_X, CUT_X + 1, TEX_W};
        float[] ys = {0, CUT_Y, CUT_Y + stretchH, height};
        float[] vs = {0, CUT_Y, CUT_Y + 1, TEX_H};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                blit(buffer, pose, xs[i], ys[j], xs[i + 1] - xs[i], ys[j + 1] - ys[j], us[i] / TEX_W, vs[j] / TEX_H, us[i + 1] / TEX_W, vs[j + 1] / TEX_H, height, argb);
            }
        }
    }

    /** A rectangle given in bubble pixels from the top left, with the texture coordinates {@code u0,v0} (top left) to {@code u1,v1}. */
    private static void blit(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float w, float h, float u0, float v0, float u1, float v1, float bubbleHeight, int argb) {
        float left = x * PIXEL;
        float right = (x + w) * PIXEL;
        float bottom = (bubbleHeight - (y + h)) * PIXEL;
        float top = (bubbleHeight - y) * PIXEL;
        vertex(buffer, pose, left, bottom, u0, v1, argb);
        vertex(buffer, pose, right, bottom, u1, v1, argb);
        vertex(buffer, pose, right, top, u1, v0, argb);
        vertex(buffer, pose, left, top, u0, v0, argb);
    }

    /** The whole texture turned a quarter turn anticlockwise: its top edge ends up on the left. */
    private static void blitTurned(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float w, float h, float bubbleHeight, int argb) {
        float left = x * PIXEL;
        float right = (x + w) * PIXEL;
        float bottom = (bubbleHeight - (y + h)) * PIXEL;
        float top = (bubbleHeight - y) * PIXEL;
        vertex(buffer, pose, left, bottom, 0, 0, argb);
        vertex(buffer, pose, right, bottom, 0, 1, argb);
        vertex(buffer, pose, right, top, 1, 1, argb);
        vertex(buffer, pose, left, top, 1, 0, argb);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float u, float v, int argb) {
        buffer.addVertex(pose, x, y, 0.0F).setColor(argb).setUv(u, v).setLight(LightCoordsUtil.FULL_BRIGHT);
    }
}
