package com.sheepfromheaven.minenow.dominion;

import com.sheepfromheaven.minenow.entity.job.FisherJob;
import com.sheepfromheaven.minenow.entity.job.Job;
import com.sheepfromheaven.minenow.entity.job.LumberjackJob;

import java.util.function.Supplier;

public enum JobType {
    FISHER("Fisher", FisherJob::new, false),
    LUMBERJACK("Lumberjack", LumberjackJob::new, true);

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
