package com.sheepfromheaven.minenow.entity;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.NeedType;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class InhabitantEntity extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

    private UUID dominionId;
    private UUID homeStructureId;
    private UUID workStructureId;
    private final Map<NeedType, Float> needs = new EnumMap<>(NeedType.class);
    private boolean goHomeRequested;

    public InhabitantEntity(EntityType<? extends InhabitantEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        for (var need : NeedType.values()) {
            needs.put(need, 1.0f);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // GeckoLib requires at least one registered controller even with no animations yet.
        // This one is a no-op until animations/inhabitant.animation.json exists.
        controllers.add(new AnimationController<>("idle", 0, test -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableInstanceCache;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new GoHomeGoal(this));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.35));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && tickCount % 200 == 0) {
            if (homeStructureId == null) {
                setNeed(NeedType.SHELTER, getNeed(NeedType.SHELTER) - 0.02f);
            } else {
                setNeed(NeedType.SHELTER, Math.min(1.0f, getNeed(NeedType.SHELTER) + 0.05f));
            }
            syncShelterToDominion();
        }
    }

    private void syncShelterToDominion() {
        if (dominionId == null || !(level() instanceof ServerLevel serverLevel)) return;
        var data = DominionSavedData.get(serverLevel);
        var dominion = data.getDominionById(dominionId);
        if (dominion == null) return;
        var inhabitant = dominion.getInhabitantByEntityUuid(getUUID());
        if (inhabitant == null) return;
        inhabitant.setNeed(NeedType.SHELTER, getNeed(NeedType.SHELTER));
        data.setDirty();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && level() instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            var dominion = dominionId != null ? DominionSavedData.get(serverLevel).getDominionById(dominionId) : null;
            if (dominion != null) {
                ModNetwork.sendOpenInhabitantScreen(serverPlayer, this, dominion);
                return InteractionResult.SUCCESS;
            }
        }
        return super.mobInteract(player, hand);
    }

    public void goHome() {
        goHomeRequested = true;
    }

    boolean isGoHomeRequested() {
        return goHomeRequested;
    }

    void clearGoHomeRequest() {
        goHomeRequested = false;
    }

    BlockPos getHomeTargetPos() {
        if (dominionId == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        var dominion = DominionSavedData.get(serverLevel).getDominionById(dominionId);
        if (dominion == null) return null;
        if (homeStructureId != null) {
            var structure = dominion.getStructureById(homeStructureId);
            if (structure != null) {
                return structure.findBedPos(serverLevel);
            }
        }
        return dominion.getBannerPos();
    }

    boolean hasAssignedHome() {
        return homeStructureId != null;
    }

    @Override
    public void die(DamageSource damageSource) {
        var name = getName().getString();
        var domId = dominionId;
        super.die(damageSource);
        if (level().isClientSide() || domId == null || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        var data = DominionSavedData.get(serverLevel);
        var dominion = data.getDominionById(domId);
        if (dominion == null) {
            return;
        }
        var inhabitant = dominion.getInhabitantByEntityUuid(getUUID());
        if (inhabitant != null) {
            dominion.removeInhabitant(inhabitant.getId());
            data.setDirty();
        }
        var owner = serverLevel.getServer().getPlayerList().getPlayer(dominion.getOwnerUuid());
        if (owner != null) {
            owner.displayClientMessage(Component.literal(name + " has died!"), false);
        }
    }

    public UUID getDominionId() {
        return dominionId;
    }

    public void setDominionId(UUID dominionId) {
        this.dominionId = dominionId;
    }

    public UUID getHomeStructureId() {
        return homeStructureId;
    }

    public void setHomeStructureId(UUID homeStructureId) {
        this.homeStructureId = homeStructureId;
    }

    public UUID getWorkStructureId() {
        return workStructureId;
    }

    public void setWorkStructureId(UUID workStructureId) {
        this.workStructureId = workStructureId;
    }

    public float getNeed(NeedType type) {
        return needs.getOrDefault(type, 0.0f);
    }

    public void setNeed(NeedType type, float value) {
        needs.put(type, Math.max(0.0f, Math.min(1.0f, value)));
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (dominionId != null) output.putString("DominionId", dominionId.toString());
        if (homeStructureId != null) output.putString("HomeStructureId", homeStructureId.toString());
        if (workStructureId != null) output.putString("WorkStructureId", workStructureId.toString());
        output.putFloat("NeedShelter", getNeed(NeedType.SHELTER));
        output.putFloat("NeedFood", getNeed(NeedType.FOOD));
        output.putFloat("NeedRest", getNeed(NeedType.REST));
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        var domId = input.getStringOr("DominionId", "");
        if (!domId.isEmpty()) dominionId = UUID.fromString(domId);
        var homeId = input.getStringOr("HomeStructureId", "");
        if (!homeId.isEmpty()) homeStructureId = UUID.fromString(homeId);
        var workId = input.getStringOr("WorkStructureId", "");
        if (!workId.isEmpty()) workStructureId = UUID.fromString(workId);
        setNeed(NeedType.SHELTER, input.getFloatOr("NeedShelter", 1.0f));
        setNeed(NeedType.FOOD, input.getFloatOr("NeedFood", 1.0f));
        setNeed(NeedType.REST, input.getFloatOr("NeedRest", 1.0f));
    }
}
