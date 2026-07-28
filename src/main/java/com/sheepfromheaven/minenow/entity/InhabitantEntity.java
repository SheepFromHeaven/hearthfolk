package com.sheepfromheaven.minenow.entity;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.JobType;
import com.sheepfromheaven.minenow.dominion.NeedType;
import com.sheepfromheaven.minenow.entity.job.FinishWorkGoal;
import com.sheepfromheaven.minenow.entity.job.WorkGoal;
import com.sheepfromheaven.minenow.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class InhabitantEntity extends PathfinderMob implements GeoEntity, InventoryCarrier {
    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);
    private final SimpleContainer inventory = new SimpleContainer(2);

    private UUID dominionId;
    private UUID homeStructureId;
    private UUID workStructureId;
    private final Map<NeedType, Float> needs = new EnumMap<>(NeedType.class);
    private boolean goHomeRequested;
    private WorkGoal workGoal;
    @Nullable
    private String activeWarning;

    public InhabitantEntity(EntityType<? extends InhabitantEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        for (var need : NeedType.values()) {
            needs.put(need, 1.0f);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Stubs for the animations still to be authored in animations/inhabitant.animation.json:
        // idle, walk, cast_rod. Each returns STOP until its animation exists and the predicate is filled in.
        controllers.add(new AnimationController<>("idle", 0, this::idlePredicate));
        controllers.add(new AnimationController<>("walk", 0, this::walkPredicate));
        controllers.add(new AnimationController<>("cast_rod", 0, this::castRodPredicate));
    }

    private PlayState idlePredicate(AnimationTest<InhabitantEntity> test) {
        return PlayState.STOP;
    }

    private PlayState walkPredicate(AnimationTest<InhabitantEntity> test) {
        return PlayState.STOP;
    }

    private PlayState castRodPredicate(AnimationTest<InhabitantEntity> test) {
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableInstanceCache;
    }

    @Override
    public SimpleContainer getInventory() {
        return inventory;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        var navigation = new InhabitantPathNavigation(this, level);
        navigation.setCanOpenDoors(true);
        // Default path search budget/region (tied to FOLLOW_RANGE, 16 blocks) is too small once
        // a detour around a player-built structure is longer than the straight-line distance to
        // the target; widen it so GoHomeGoal can route around houses instead of getting stuck.
        navigation.setRequiredPathLength(48.0F);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        workGoal = new WorkGoal(this);
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(1, new OpenFenceGateGoal(this));
        goalSelector.addGoal(2, new FinishWorkGoal(this));
        goalSelector.addGoal(2, new GoHomeGoal(this));
        goalSelector.addGoal(2, new GoEatGoal(this));
        goalSelector.addGoal(3, workGoal);
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.35));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
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
            setNeed(NeedType.FOOD, getNeed(NeedType.FOOD) - 0.01f);
            if (isSleeping()) {
                setNeed(NeedType.REST, getNeed(NeedType.REST) + 0.1f);
            } else {
                setNeed(NeedType.REST, getNeed(NeedType.REST) - 0.01f);
            }
            syncNeedsToDominion();
        }
        if (!level().isClientSide() && tickCount % 20 == 0) {
            updateWarnings();
        }
    }

    private void updateWarnings() {
        var missingWorkItem = workGoal != null ? workGoal.getMissingItem() : null;
        var missingHomeRequirement = missingWorkItem == null ? findMissingHomeRequirement() : null;
        String warning = missingWorkItem != null
                ? "Needs " + missingWorkItem.getDefaultInstance().getHoverName().getString()
                : missingHomeRequirement != null
                        ? "There is no " + missingHomeRequirement + " in my house"
                        : null;
        if (java.util.Objects.equals(warning, activeWarning)) {
            return;
        }
        activeWarning = warning;
        var baseName = resolveBaseName();
        if (warning != null) {
            setCustomName(Component.literal(baseName + " §c!§r " + warning));
        } else {
            setCustomName(Component.literal(baseName));
        }
        setCustomNameVisible(true);
    }

    /** Returns the display name of the first unmet requirement of this inhabitant's assigned house, if any. */
    @Nullable
    private String findMissingHomeRequirement() {
        if (homeStructureId == null || dominionId == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        var dominion = DominionSavedData.get(serverLevel).getDominionById(dominionId);
        if (dominion == null) return null;
        var structure = dominion.getStructureById(homeStructureId);
        if (structure == null) return null;
        for (var status : structure.checkRequirements(serverLevel)) {
            if (!status.satisfied()) {
                return status.name();
            }
        }
        return null;
    }

    private String resolveBaseName() {
        if (dominionId != null && level() instanceof ServerLevel serverLevel) {
            var dominion = DominionSavedData.get(serverLevel).getDominionById(dominionId);
            if (dominion != null) {
                var inhabitant = dominion.getInhabitantByEntityUuid(getUUID());
                if (inhabitant != null) return inhabitant.getName();
            }
        }
        return getName().getString();
    }

    /** Plays a short villager noise so switching to a new AI goal is audible. */
    public void playGoalSwitchSound() {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, blockPosition(), SoundEvents.VILLAGER_AMBIENT, getSoundSource(), 1.0F, getVoicePitch());
        }
    }

    public String getJobStatus() {
        return workGoal != null ? workGoal.getStatusDescription() : "Idle";
    }

    @Nullable
    public Item getMissingWorkItem() {
        return workGoal != null ? workGoal.getMissingItem() : null;
    }

    private void syncNeedsToDominion() {
        if (dominionId == null || !(level() instanceof ServerLevel serverLevel)) return;
        var data = DominionSavedData.get(serverLevel);
        var dominion = data.getDominionById(dominionId);
        if (dominion == null) return;
        var inhabitant = dominion.getInhabitantByEntityUuid(getUUID());
        if (inhabitant == null) return;
        inhabitant.setNeed(NeedType.SHELTER, getNeed(NeedType.SHELTER));
        inhabitant.setNeed(NeedType.FOOD, getNeed(NeedType.FOOD));
        inhabitant.setNeed(NeedType.REST, getNeed(NeedType.REST));
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

    public boolean isGoHomeRequested() {
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
            dominion.removeInhabitant(inhabitant.getId(), serverLevel);
            data.setDirty();
        }
        var owner = serverLevel.getServer().getPlayerList().getPlayer(dominion.getOwnerUuid());
        if (owner != null) {
            owner.displayClientMessage(Component.literal(name + " has died!"), false);
        }
    }

    /**
     * Reverts this inhabitant back to a vanilla villager at its current position and discards it.
     * Used when the owning dominion is disbanded.
     */
    public void convertToVillager() {
        if (level() instanceof ServerLevel serverLevel) {
            var villager = EntityType.VILLAGER.create(serverLevel, EntitySpawnReason.CONVERSION);
            if (villager != null) {
                villager.snapTo(getX(), getY(), getZ(), getYRot(), getXRot());
                serverLevel.addFreshEntity(villager);
            }
        }
        discard();
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
        if (workGoal != null) {
            var jobTag = new CompoundTag();
            workGoal.save(jobTag);
            if (!jobTag.isEmpty()) {
                output.store("WorkGoal", CompoundTag.CODEC, jobTag);
            }
        }
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
        if (workGoal != null) {
            input.read("WorkGoal", CompoundTag.CODEC).ifPresent(workGoal::load);
        }
    }
}
