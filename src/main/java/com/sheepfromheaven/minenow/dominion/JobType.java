package com.sheepfromheaven.minenow.dominion;

import com.sheepfromheaven.minenow.entity.job.FisherJob;
import com.sheepfromheaven.minenow.entity.job.Job;
import com.sheepfromheaven.minenow.entity.job.LumberjackJob;

import java.util.function.Supplier;

public enum JobType {
    FISHER("Fisher", FisherJob::new),
    LUMBERJACK("Lumberjack", LumberjackJob::new);

    private final String displayName;
    private final Supplier<Job> factory;

    JobType(String displayName, Supplier<Job> factory) {
        this.displayName = displayName;
        this.factory = factory;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Job createJob() {
        return factory.get();
    }
}
