package com.sheepfromheaven.minenow.fisherman;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A fisherman's bobber. Vanilla's fishing hook only works for players, so this is our own: it flies to the water,
 * floats, a fish swims towards it (vanilla's V-shaped particle trail) and bites. {@link GoFishing} tends it every tick
 * and reels in while it bites; a bobber nobody tends removes itself.
 */
public class VillagerBobber extends Projectile {
    public enum State { FLYING, FLOATING, APPROACHING, BITING }

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(VillagerBobber.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(VillagerBobber.class, EntityDataSerializers.INT);
    private static final double GRAVITY = 0.03;
    private static final int FLIGHT_TICKS = 12;
    static final int SHORTEST_BITE = 20;
    private static final int UNTENDED_LIMIT = 20;
    private static final int MAX_FLIGHT = 60;

    private int timeUntilLured;
    private int timeUntilHooked;
    private int nibble;
    private int bitingFor;
    private float fishAngle;
    private int untended;
    private int lureTicks;

    public VillagerBobber(EntityType<? extends VillagerBobber> type, Level level) {
        super(type, level);
    }

    public static VillagerBobber cast(ServerLevel level, Villager owner, Vec3 target, int lureTicks) {
        VillagerBobber bobber = new VillagerBobber(FishermanEntities.VILLAGER_BOBBER.get(), level);
        bobber.setOwner(owner);
        bobber.entityData.set(DATA_OWNER, owner.getId());
        bobber.lureTicks = lureTicks;
        Vec3 from = owner.getEyePosition().add(owner.getLookAngle().scale(0.6));
        bobber.setPos(from);
        Vec3 delta = target.subtract(from);
        bobber.setDeltaMovement(delta.x / FLIGHT_TICKS, (delta.y + GRAVITY * FLIGHT_TICKS * (FLIGHT_TICKS + 1) / 2.0) / FLIGHT_TICKS, delta.z / FLIGHT_TICKS);
        level.addFreshEntity(bobber);
        return bobber;
    }

    public State state() {
        return State.values()[entityData.get(DATA_STATE)];
    }

    public int bitingFor() {
        return state() == State.BITING ? bitingFor : 0;
    }

    public void tend() {
        untended = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, State.FLYING.ordinal());
        builder.define(DATA_OWNER, 0);
    }

    /** The spawn packet names the owner once; a client that gets the bobber before its villager finds the owner here later. */
    @Override
    public @Nullable Entity getOwner() {
        Entity owner = super.getOwner();
        if (owner == null && level().isClientSide()) owner = level().getEntity(entityData.get(DATA_OWNER));
        return owner;
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && !stillTended()) {
            discard();
            return;
        }
        BlockPos pos = blockPosition();
        FluidState fluid = level().getFluidState(pos);
        float liquidHeight = fluid.is(FluidTags.WATER) ? fluid.getHeight(level(), pos) : 0.0F;
        boolean inWater = liquidHeight > 0.0F;

        if (state() == State.FLYING) {
            if (inWater) {
                setDeltaMovement(getDeltaMovement().multiply(0.3, 0.2, 0.3));
                setState(State.FLOATING);
                return;
            }
            if (!level().isClientSide() && (onGround() || horizontalCollision || tickCount > MAX_FLIGHT)) {
                discard();
                return;
            }
        } else {
            Vec3 movement = getDeltaMovement();
            double force = getY() + movement.y - pos.getY() - liquidHeight;
            if (Math.abs(force) < 0.01) force += Math.signum(force) * 0.1;
            setDeltaMovement(movement.x * 0.9, movement.y - force * random.nextFloat() * 0.2, movement.z * 0.9);
            if (state() == State.BITING) {
                setDeltaMovement(getDeltaMovement().add(0.0, -0.1 * random.nextFloat() * random.nextFloat(), 0.0));
            }
            if (inWater && level() instanceof ServerLevel serverLevel) catchingFish(serverLevel);
        }

        if (!fluid.is(FluidTags.WATER) && !onGround()) setDeltaMovement(getDeltaMovement().add(0.0, -GRAVITY, 0.0));
        move(MoverType.SELF, getDeltaMovement());
        if (state() != State.FLYING) setDeltaMovement(getDeltaMovement().scale(0.92));
        reapplyPosition();
    }

    private boolean stillTended() {
        return getOwner() instanceof Villager owner && owner.isAlive() && distanceToSqr(owner) <= 1024.0 && ++untended <= UNTENDED_LIMIT;
    }

    private void catchingFish(ServerLevel level) {
        switch (state()) {
            case FLOATING -> lure(level);
            case APPROACHING -> approach(level);
            case BITING -> {
                bitingFor++;
                if (--nibble <= 0) {
                    timeUntilLured = 0;
                    setState(State.FLOATING);
                }
            }
            default -> {}
        }
    }

    private void lure(ServerLevel level) {
        if (timeUntilLured <= 0) {
            timeUntilLured = Math.max(20, Mth.nextInt(random, 80, 240) - lureTicks);
            return;
        }
        timeUntilLured--;
        float teaseChance = 0.15F;
        if (timeUntilLured < 20) teaseChance += (20 - timeUntilLured) * 0.05F;
        else if (timeUntilLured < 40) teaseChance += (40 - timeUntilLured) * 0.02F;
        else if (timeUntilLured < 60) teaseChance += (60 - timeUntilLured) * 0.01F;
        if (random.nextFloat() < teaseChance) {
            float angle = Mth.nextFloat(random, 0.0F, 360.0F) * Mth.DEG_TO_RAD;
            float dist = Mth.nextFloat(random, 25.0F, 60.0F);
            double fishX = getX() + Mth.sin(angle) * dist * 0.1;
            double fishY = Mth.floor(getY()) + 1.0;
            double fishZ = getZ() + Mth.cos(angle) * dist * 0.1;
            if (level.getBlockState(BlockPos.containing(fishX, fishY - 1.0, fishZ)).is(Blocks.WATER)) {
                level.sendParticles(ParticleTypes.SPLASH, fishX, fishY, fishZ, 2 + random.nextInt(2), 0.1, 0.0, 0.1, 0.0);
            }
        }
        if (timeUntilLured <= 0) {
            fishAngle = Mth.nextFloat(random, 0.0F, 360.0F);
            timeUntilHooked = Mth.nextInt(random, 20, 80);
            setState(State.APPROACHING);
        }
    }

    private void approach(ServerLevel level) {
        if (--timeUntilHooked > 0) {
            fishAngle += (float) random.triangle(0.0, 9.188);
            float angle = fishAngle * Mth.DEG_TO_RAD;
            float sin = Mth.sin(angle);
            float cos = Mth.cos(angle);
            double fishX = getX() + sin * timeUntilHooked * 0.1F;
            double fishY = Mth.floor(getY()) + 1.0;
            double fishZ = getZ() + cos * timeUntilHooked * 0.1F;
            if (level.getBlockState(BlockPos.containing(fishX, fishY - 1.0, fishZ)).is(Blocks.WATER)) {
                if (random.nextFloat() < 0.15F) level.sendParticles(ParticleTypes.BUBBLE, fishX, fishY - 0.1, fishZ, 1, sin, 0.1, cos, 0.0);
                float xMove = sin * 0.04F;
                float zMove = cos * 0.04F;
                level.sendParticles(ParticleTypes.FISHING, fishX, fishY, fishZ, 0, zMove, 0.01, -xMove, 1.0);
                level.sendParticles(ParticleTypes.FISHING, fishX, fishY, fishZ, 0, -zMove, 0.01, xMove, 1.0);
            }
            return;
        }
        playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F);
        double y = getY() + 0.5;
        int count = (int) (1.0F + getBbWidth() * 20.0F);
        level.sendParticles(ParticleTypes.BUBBLE, getX(), y, getZ(), count, getBbWidth(), 0.0, getBbWidth(), 0.2);
        level.sendParticles(ParticleTypes.FISHING, getX(), y, getZ(), count, getBbWidth(), 0.0, getBbWidth(), 0.2);
        nibble = Mth.nextInt(random, SHORTEST_BITE, 40);
        bitingFor = 0;
        setState(State.BITING);
    }

    private void setState(State state) {
        entityData.set(DATA_STATE, state.ordinal());
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return false;
    }
}
