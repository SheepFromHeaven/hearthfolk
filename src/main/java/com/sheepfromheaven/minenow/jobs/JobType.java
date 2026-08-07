package com.sheepfromheaven.minenow.jobs;

import com.sheepfromheaven.minenow.jobs.cook.CookJob;
import com.sheepfromheaven.minenow.jobs.courier.CourierJob;
import com.sheepfromheaven.minenow.jobs.fisher.FisherJob;
import com.sheepfromheaven.minenow.jobs.Job;
import com.sheepfromheaven.minenow.jobs.lumberjack.LumberjackJob;
import com.sheepfromheaven.minenow.jobs.miner.MinerJob;

import java.util.function.Supplier;

public enum JobType {
    FISHER("Fisher", FisherJob::new, false),
    LUMBERJACK("Lumberjack", LumberjackJob::new, true),
    COOK("Cook", CookJob::new, false),
    COURIER("Courier", CourierJob::new, false),
    MINER("Miner", MinerJob::new, true);

    private final String displayName;
    private final Supplier<Job> factory;
    private final boolean hasWorkArea;

    JobType(String displayName, Supplier<Job> factory, boolean hasWorkArea) {
        this.displayName = displayName;
        this.factory = factory;
        this.hasWorkArea = hasWorkArea;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Job createJob() {
        return factory.get();
    }

    /** Whether this job scans a player-configurable work area (center + radius) for its own targets, rather than a fixed structure range. */
    public boolean hasWorkArea() {
        return hasWorkArea;
    }
}
