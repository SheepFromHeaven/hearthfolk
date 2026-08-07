package com.sheepfromheaven.minenow.entity;

import com.sheepfromheaven.minenow.dominion.DominionSavedData;
import com.sheepfromheaven.minenow.dominion.JobType;
import com.sheepfromheaven.minenow.dominion.NeedType;
import com.sheepfromheaven.minenow.entity.job.FinishWorkGoal;
import com.sheepfromheaven.minenow.entity.job.ImmigrationJob;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class InhabitantEntity extends PathfinderMob implements GeoEntity, InventoryCarrier, net.minecraft.world.entity.ContainerUser {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_CHOPPING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(InhabitantEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** 0 = none, 1 = casting/waiting, 2 = reeling */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DATA_FISHER_STATE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(InhabitantEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    /** {@link JobType} ordinal driving the client texture variant, or -1 when unemployed. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DATA_JOB_TYPE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(InhabitantEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);

    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);
    private final SimpleContainer inventory = new SimpleContainer(2);

    private UUID dominionId;
    private UUID homeStructureId;
    private UUID workStructureId;
    private final Map<NeedType, Float> needs = new EnumMap<>(NeedType.class);
    private boolean goHomeRequested;
    private WorkGoal workGoal;
    @Nullable
    private String lastAppliedName;
    /** Non-null only for an immigration candidate that has not yet joined {@link #dominionId}'s dominion. */
    @Nullable
    private ImmigrationJob immigrationJob;

    public InhabitantEntity(EntityType<? extends InhabitantEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(true);
        for (var need : NeedType.values()) {
            needs.put(need, 1.0f);
        }
    }

    /**
     * Vanilla's own {@code Mob#pickUpItem} only equips items into gear slots (armor/weapons), so
     * without this override enabling {@link #setCanPickUpLoot} would only ever pick up weapons and
     * armor dropped nearby. Inhabitants should pick up anything within reach - job materials dropped
     * by a player, a freshly felled tree's leaf drops that missed the lumberjack's own harvest pass, etc.
     * A job tool (axe, fishing rod, ...) goes straight into the main-hand equipment slot if that's
     * empty, exactly like one manually equipped from storage - everything else goes to the general
     * job inventory instead.
     */
    @Override
    protected void pickUpItem(ServerLevel level, net.minecraft.world.entity.item.ItemEntity itemEntity) {
        var stack = itemEntity.getItem();
        if (isJobTool(stack) && getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty()) {
            var toEquip = stack.split(1);
            setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, toEquip);
            onItemPickup(itemEntity);
            take(itemEntity, 1);
            if (stack.isEmpty()) {
                itemEntity.discard();
            }
            return;
        }
        var leftover = getInventory().addItem(stack.copy());
        int taken = stack.getCount() - leftover.getCount();
        if (taken <= 0) return;
        onItemPickup(itemEntity);
        take(itemEntity, taken);
        stack.shrink(taken);
        if (stack.isEmpty()) {
            itemEntity.discard();
        }
    }

    private static boolean isJobTool(net.minecraft.world.item.ItemStack stack) {
        return stack.is(net.minecraft.tags.ItemTags.AXES) || stack.is(net.minecraft.world.item.Items.FISHING_ROD);
    }

    private static final software.bernie.geckolib.animation.RawAnimation WALK =
            software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.inhabitant.walk");
    private static final software.bernie.geckolib.animation.RawAnimation IDLE =
            software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.inhabitant.idle");
    private static final software.bernie.geckolib.animation.RawAnimation CHOP =
            software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.inhabitant.chop");
    private static final software.bernie.geckolib.animation.RawAnimation CAST_CHAIN =
            software.bernie.geckolib.animation.RawAnimation.begin()
                    .thenPlay("animation.inhabitant.cast")
                    .thenLoop("animation.inhabitant.fish_wait");
    private static final software.bernie.geckolib.animation.RawAnimation REEL =
            software.bernie.geckolib.animation.RawAnimation.begin()
                    .thenPlay("animation.inhabitant.reel")
                    .thenLoop("animation.inhabitant.fish_wait");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // One controller owns all body bones so there is always exactly one animation driving them.
        // Multiple controllers on the same bones leave stale poses when one returns STOP — the
        // other controller's last frame stays frozen instead of yielding back.
        controllers.add(new AnimationController<>("body", 0, this::bodyPredicate));
    }

    private PlayState bodyPredicate(AnimationTest<InhabitantEntity> test) {
        byte fisherState = getFisherState();
        if (fisherState == 2) {
            return test.setAndContinue(REEL);
        }
        if (fisherState == 1) {
            return test.setAndContinue(CAST_CHAIN);
        }
        if (isChopping()) {
            return test.setAndContinue(CHOP);
        }
        if (test.isMoving()) {
            test.setControllerSpeed(Math.max(0.5f, walkAnimation.speed()));
            return test.setAndContinue(WALK);
        }
        return test.setAndContinue(IDLE);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHOPPING, false);
        builder.define(DATA_FISHER_STATE, (byte) 0);
        builder.define(DATA_JOB_TYPE, (byte) -1);
    }

    /** {@link JobType} ordinal of the inhabitant's current job for client rendering, or -1 when unemployed. */
    public byte getJobTypeId() {
        return entityData.get(DATA_JOB_TYPE);
    }

    /** Whether {@link com.sheepfromheaven.minenow.entity.job.LumberjackJob} is mid-swing on a trunk log. */
    public boolean isChopping() {
        return entityData.get(DATA_CHOPPING);
    }

    public void setChopping(boolean chopping) {
        entityData.set(DATA_CHOPPING, chopping);
    }

    /** 0 = not fishing, 1 = casting/waiting for a bite, 2 = reeling in. */
    public byte getFisherState() {
        return entityData.get(DATA_FISHER_STATE);
    }

    public void setFisherState(int state) {
        entityData.set(DATA_FISHER_STATE, (byte) state);
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
        goalSelector.addGoal(2, new WalkToBannerGoal(this));
        goalSelector.addGoal(3, workGoal);
        goalSelector.addGoal(4, new StrollTowardPathNodeGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.35));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && immigrationJob != null && immigrationJob.tickWanderOff()) {
            discard();
            return;
        }
        if (!level().isClientSide() && immigrationJob != null) {
            // Immigration candidates aren't dominion members yet - skip need decay/job bookkeeping below.
            return;
        }
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
            updateIssueIndicator();
            var jobType = workGoal != null ? workGoal.getJobType() : null;
            byte jobId = jobType != null ? (byte) jobType.ordinal() : (byte) -1;
            if (getJobTypeId() != jobId) {
                entityData.set(DATA_JOB_TYPE, jobId);
            }
        }
    }

    /**
     * Every problem currently affecting this inhabitant that the player should know about (missing
     * tools, a job that can't proceed, an unmet house requirement, ...). Used both for the name-tag
     * "!" marker and the issues list shown from the inhabitant screen; a future message board block
     * will pull the same list across every inhabitant in a dominion.
     */
    public List<String> getIssues() {
        var issues = new ArrayList<String>();
        var missingWorkItemLabel = workGoal != null ? workGoal.getMissingItemLabel() : null;
        if (missingWorkItemLabel != null) {
            issues.add("Needs " + missingWorkItemLabel);
        }
        if (workGoal != null) {
            issues.addAll(workGoal.getWarnings());
        }
        var missingHomeRequirement = findMissingHomeRequirement();
        if (missingHomeRequirement != null) {
            issues.add("There is no " + missingHomeRequirement + " in my house");
        }
        return issues;
    }

    private void updateIssueIndicator() {
        boolean hasIssues = !getIssues().isEmpty();
        var baseName = resolveBaseName();
        String fullName = hasIssues ? baseName + " §c!§r" : baseName;
        if (fullName.equals(lastAppliedName)) {
            return;
        }
        lastAppliedName = fullName;
        setCustomName(Component.literal(fullName));
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
        String name = getName().getString();
        if (dominionId != null && level() instanceof ServerLevel serverLevel) {
            var dominion = DominionSavedData.get(serverLevel).getDominionById(dominionId);
            if (dominion != null) {
                var inhabitant = dominion.getInhabitantByEntityUuid(getUUID());
                if (inhabitant != null) name = inhabitant.getName();
            }
        }
        var jobType = workGoal != null ? workGoal.getJobType() : null;
        return jobType != null ? name + " (" + jobType.getDisplayName() + ")" : name;
    }

    /**
     * {@link net.minecraft.world.entity.ContainerUser} is only implemented so jobs can bracket
     * container access with {@link net.minecraft.world.Container#startOpen}/{@code stopOpen} and
     * get the same open/close sound and lid animation a player gets - the inhabitant never keeps a
     * container "open" across ticks (see {@link com.sheepfromheaven.minenow.entity.job.ContainerUtil}),
     * so this is never actually consulted.
     */
    @Override
    public boolean hasContainerOpen(net.minecraft.world.level.block.entity.ContainerOpenersCounter opener, net.minecraft.core.BlockPos pos) {
        return false;
    }

    @Override
    public double getContainerInteractionRange() {
        return 8.0;
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

    /** Snapshot of AI/pathfinding state for the debug view (gated behind {@link com.sheepfromheaven.minenow.Config#debugInhabitantView}). */
    public List<String> getDebugLines() {
        var lines = new ArrayList<String>();
        var runningGoals = goalSelector.getAvailableGoals().stream()
                .filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
                .map(g -> g.getGoal().getClass().getSimpleName())
                .collect(Collectors.joining(", "));
        lines.add("Active goals: " + (runningGoals.isEmpty() ? "none" : runningGoals));
        lines.add("Animation: isChopping=" + isChopping() + ", fisherState=" + getFisherState()
                + ", isMoving=" + walkAnimation.isMoving() + ", xRot=" + String.format("%.1f", getXRot()));

        var nav = getNavigation();
        var path = nav.getPath();
        if (path != null) {
            lines.add("Path: node " + path.getNextNodeIndex() + "/" + path.getNodeCount() + " -> target " + path.getTarget());
        } else {
            lines.add("Path: none");
        }
        lines.add("Nav done: " + nav.isDone() + ", stuck: " + nav.isStuck());

        if (workGoal != null) {
            lines.addAll(workGoal.getDebugLines());
        }
        return lines;
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
            if (immigrationJob != null) {
                return openImmigrantRequestScreen(serverPlayer);
            }
            var dominion = dominionId != null ? DominionSavedData.get(serverLevel).getDominionById(dominionId) : null;
            if (dominion != null) {
                ModNetwork.sendOpenInhabitantScreen(serverPlayer, this, dominion);
                return InteractionResult.SUCCESS;
            }
        }
        return super.mobInteract(player, hand);
    }

    /**
     * Opens the ImmigrantRequestScreen for the dominion owner right-clicking this immigration
     * candidate while it awaits a decision. The actual approve/reject logic runs in
     * {@link #applyImmigrationDecision} once the owner picks a button in that screen.
     */
    private InteractionResult openImmigrantRequestScreen(ServerPlayer player) {
        var job = immigrationJob;
        if (job == null || job.getPhase() != ImmigrationJob.Phase.AWAITING_DECISION) {
            return InteractionResult.PASS;
        }
        var dominion = DominionSavedData.get((ServerLevel) player.level()).getDominionById(job.getDominionId());
        if (dominion == null || !dominion.getOwnerUuid().equals(player.getUUID())) {
            return InteractionResult.PASS;
        }
        ModNetwork.sendOpenImmigrantRequestScreen(player, this);
        return InteractionResult.SUCCESS;
    }

    /**
     * Applies the dominion owner's approve/reject decision for this immigration candidate, gated to
     * the same rules as before (owner only, only while awaiting a decision): approving joins the
     * dominion (subject to capacity), rejecting sends the candidate wandering off. Invoked from the
     * ImmigrantRequestScreen's buttons via {@link ModNetwork}.
     */
    public void applyImmigrationDecision(ServerLevel serverLevel, ServerPlayer player, boolean approve) {
        var job = immigrationJob;
        if (job == null || job.getPhase() != ImmigrationJob.Phase.AWAITING_DECISION) {
            return;
        }
        var data = DominionSavedData.get(serverLevel);
        var dominion = data.getDominionById(job.getDominionId());
        if (dominion == null || !dominion.getOwnerUuid().equals(player.getUUID())) {
            return;
        }
        if (!approve) {
            dominion.setPendingImmigrantId(null);
            dominion.setNextImmigrationAttemptTick(serverLevel.getGameTime() + 2400);
            job.setPhase(ImmigrationJob.Phase.REJECTED_WANDER_OFF);
            data.setDirty();
            player.displayClientMessage(Component.literal(getName().getString() + " wanders off, rejected."), false);
            return;
        }
        var inhabitant = new com.sheepfromheaven.minenow.dominion.DominionInhabitant(getUUID(), getName().getString());
        if (!dominion.addInhabitant(inhabitant)) {
            player.displayClientMessage(Component.literal("Your dominion is full - increase its tier to accept more inhabitants!"), false);
            return;
        }
        dominion.setPendingImmigrantId(null);
        dominion.setNextImmigrationAttemptTick(serverLevel.getGameTime() + 2400);
        setDominionId(dominion.getId());
        setImmigrationJob(null);
        data.setDirty();
        player.displayClientMessage(Component.literal(getName().getString() + " has joined your dominion!"), false);
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

    @Nullable
    public ImmigrationJob getImmigrationJob() {
        return immigrationJob;
    }

    public void setImmigrationJob(@Nullable ImmigrationJob immigrationJob) {
        this.immigrationJob = immigrationJob;
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
        if (immigrationJob != null) {
            var immigrationTag = new CompoundTag();
            immigrationJob.save(immigrationTag);
            output.store("ImmigrationJob", CompoundTag.CODEC, immigrationTag);
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
        input.read("ImmigrationJob", CompoundTag.CODEC).ifPresent(tag -> immigrationJob = ImmigrationJob.load(tag));
    }
}
