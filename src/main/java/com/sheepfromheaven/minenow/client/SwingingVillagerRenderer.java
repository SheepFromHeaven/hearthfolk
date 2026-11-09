package com.sheepfromheaven.minenow.client;

import net.minecraft.client.model.geom.ModelLayers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.npc.BabyVillagerModel;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.entity.state.ZombieVillagerRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Vanilla's VillagerRenderer, with arms that move: a villager holding a tool or weapon uncrosses its arms and works
 * with it (see {@link SwingingVillagerModel}, {@link HeldItemLayer}); otherwise the crossed arms rise and fall with a
 * swing. Plus the armor it wears ({@link VillagerArmorLayer}).
 */
public class SwingingVillagerRenderer extends AgeableMobRenderer<Villager, VillagerRenderState, VillagerModel> {
    private static final Identifier VILLAGER_BASE_LOCATION = Identifier.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final Identifier VILLAGER_BABY_LOCATION = Identifier.withDefaultNamespace("textures/entity/villager/villager_baby.png");

    public SwingingVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new SwingingVillagerModel(context.bakeLayer(SwingingVillagerModel.LAYER)),
                new BabyVillagerModel(context.bakeLayer(ModelLayers.VILLAGER_BABY)), 0.5F);
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache(), VillagerRenderer.CUSTOM_HEAD_TRANSFORMS));
        this.addLayer(new VillagerProfessionLayer<>(this, context.getResourceManager(), "villager",
                new SwingingVillagerModel(context.bakeLayer(SwingingVillagerModel.NO_HAT_LAYER)),
                new BabyVillagerModel(context.bakeLayer(ModelLayers.VILLAGER_BABY_NO_HAT))));
        this.addLayer(new VillagerArmorLayer(this, context));
        this.addLayer(new HeldItemLayer(this));
    }

    @Override
    public Identifier getTextureLocation(VillagerRenderState state) {
        return state.isBaby ? VILLAGER_BABY_LOCATION : VILLAGER_BASE_LOCATION;
    }

    @Override
    protected float getShadowRadius(VillagerRenderState state) {
        float radius = super.getShadowRadius(state);
        return state.isBaby ? radius * 0.5F : radius;
    }

    @Override
    public VillagerRenderState createRenderState() {
        return new SwingState();
    }

    @Override
    public void extractRenderState(Villager entity, VillagerRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        HoldingEntityRenderState.extractHoldingEntityRenderState(entity, state, this.itemModelResolver);
        state.isUnhappy = entity.getUnhappyCounter() > 0;
        state.villagerData = entity.getVillagerData();
        if (state instanceof SwingState swing) {
            swing.swing = entity.getSwingAnimation(partialTicks);
            swing.helmet = !entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty();
            swing.gripped = HeldItemLayer.gripped(entity.getMainHandItem());
            swing.aiming = entity.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem;
            if (swing.gripped) this.itemModelResolver.updateForLiving(swing.grippedItem, held(entity), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
            else swing.grippedItem.clear();
            ArmorState armor = swing.armor;
            HumanoidMobRenderer.extractHumanoidRenderState(entity, armor, partialTicks, this.itemModelResolver);
            armor.yRot = state.yRot;
            armor.xRot = state.xRot;
            armor.bodyRot = state.bodyRot;
            armor.walkAnimationPos = state.walkAnimationPos;
            armor.walkAnimationSpeed = state.walkAnimationSpeed;
            armor.ageInTicks = state.ageInTicks;
            armor.isBaby = state.isBaby;
            armor.entityType = state.entityType;
            armor.outlineColor = state.outlineColor;
            armor.swingAnimation = 0.0F;
            armor.currentSwing = null;
            armor.freeArms = swing.gripped && !state.isBaby;
            float[] pose = SwingingVillagerModel.freeArmPose(state, swing.swing);
            armor.rightArmX = pose[0];
            armor.leftArmX = pose[1];
            armor.rightArmY = pose[2];
            armor.leftArmY = pose[3];
            armor.spread = SwingingVillagerModel.SPREAD;
        }
    }

    /** Its main hand item, as a rod without its hanging string while its line is out (vanilla only does that for players). */
    private static net.minecraft.world.item.ItemStack held(Villager villager) {
        net.minecraft.world.item.ItemStack stack = villager.getMainHandItem();
        if (!stack.is(net.minecraft.world.item.Items.FISHING_ROD)) return stack;
        boolean lineOut = !villager.level().getEntitiesOfClass(com.sheepfromheaven.minenow.fisherman.VillagerBobber.class,
                villager.getBoundingBox().inflate(32.0), bobber -> bobber.getOwner() == villager).isEmpty();
        if (!lineOut) return stack;
        net.minecraft.world.item.ItemStack cast = stack.copy();
        cast.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, CAST_ROD);
        return cast;
    }

    private static final Identifier CAST_ROD = Identifier.fromNamespaceAndPath(com.sheepfromheaven.minenow.MineNow.MOD_ID, "cast_fishing_rod");

    public static class SwingState extends VillagerRenderState {
        public float swing;
        public final ArmorState armor = new ArmorState();
        public final ItemStackRenderState grippedItem = new ItemStackRenderState();
        public boolean gripped;
        public boolean helmet;
        public boolean aiming;
    }

    /** The armor's pose, with the free arms' rotations so armor sleeves follow them. */
    public static class ArmorState extends ZombieVillagerRenderState {
        public boolean freeArms;
        public float rightArmX;
        public float leftArmX;
        public float rightArmY;
        public float leftArmY;
        public float spread;
    }

    /**
     * Villager models with a pair of free arms besides the crossed ones ({@link #LAYER}, {@link #NO_HAT_LAYER}): an upper
     * arm from the crossed arms' sleeve texture and a hand from the end of their forearm bar, so every profession's
     * clothing fits. A villager holding a tool or weapon uncrosses its arms: the right holds the item a little forward
     * and chops with it when swinging, both swing while walking. Otherwise the crossed arms rise and fall with a swing.
     */
    static class SwingingVillagerModel extends VillagerModel {
        static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(com.sheepfromheaven.minenow.MineNow.MOD_ID, "villager"), "main");
        static final ModelLayerLocation NO_HAT_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(com.sheepfromheaven.minenow.MineNow.MOD_ID, "villager_no_hat"), "main");
        static final float SWING_LIFT = 0.9F;
        private static final float HOLD = -0.3F;
        private static final float CHOP = 1.3F;
        static final float SPREAD = 0.05F;
        private final ModelPart arms;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        private final @org.jspecify.annotations.Nullable ModelPart hat;

        SwingingVillagerModel(ModelPart root) {
            super(root);
            this.arms = root.getChild("arms");
            this.rightArm = root.getChild("right_arm");
            this.leftArm = root.getChild("left_arm");
            this.hat = root.hasChild("head") && root.getChild("head").hasChild("hat") ? root.getChild("head").getChild("hat") : null;
        }

        static LayerDefinition layer() {
            return LayerDefinition.create(withFreeArms(VillagerModel.createBodyModel()), 64, 64);
        }

        static LayerDefinition noHatLayer() {
            return LayerDefinition.create(withFreeArms(VillagerModel.createNoHatModel()), 64, 64);
        }

        private static MeshDefinition withFreeArms(MeshDefinition mesh) {
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                    .texOffs(44, 22).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                    .texOffs(40, 38).addBox(-3.0F, 6.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offset(-5.0F, 2.0F, 0.0F));
            root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                    .texOffs(44, 22).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                    .texOffs(40, 38).mirror().addBox(-1.0F, 6.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offset(5.0F, 2.0F, 0.0F));
            return mesh;
        }

        /** The free arms' rotations: {right x, left x, right y, left y}. With a bow, both raised the way a skeleton aims, following the head. */
        static float[] freeArmPose(VillagerRenderState state, float swing) {
            if (state instanceof SwingState held && held.aiming) {
                float pitch = (float) (-Math.PI / 2) + state.xRot * Mth.DEG_TO_RAD;
                float yaw = state.yRot * Mth.DEG_TO_RAD;
                return new float[]{pitch, pitch, -0.1F + yaw, 0.5F + yaw};
            }
            float walk = state.walkAnimationPos * 0.6662F;
            float right = HOLD + Mth.cos(walk + (float) Math.PI) * state.walkAnimationSpeed * 0.5F - Mth.sin(swing * (float) Math.PI) * CHOP;
            float left = Mth.cos(walk) * state.walkAnimationSpeed;
            return new float[]{right, left, 0.0F, 0.0F};
        }

        void translateToRightHand(PoseStack poseStack) {
            this.root().translateAndRotate(poseStack);
            this.rightArm.translateAndRotate(poseStack);
        }

        @Override
        public void setupAnim(VillagerRenderState state) {
            super.setupAnim(state);
            SwingState swing = state instanceof SwingState s ? s : null;
            if (hat != null) hat.visible = swing == null || !swing.helmet;
            boolean free = swing != null && swing.gripped && !state.isBaby;
            this.arms.visible = !free;
            this.rightArm.visible = free;
            this.leftArm.visible = free;
            if (free) {
                float[] pose = freeArmPose(state, swing.swing);
                this.rightArm.xRot = pose[0];
                this.leftArm.xRot = pose[1];
                this.rightArm.yRot = pose[2];
                this.leftArm.yRot = pose[3];
                this.rightArm.zRot = SPREAD;
                this.leftArm.zRot = -SPREAD;
            } else if (swing != null && swing.swing > 0.0F) {
                this.arms.xRot -= Mth.sin(swing.swing * (float) Math.PI) * SWING_LIFT;
            }
        }
    }
}
