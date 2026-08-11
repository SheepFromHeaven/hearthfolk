package com.sheepfromheaven.minenow.jobs;

import com.sheepfromheaven.minenow.jobs.charcoalburner.CharcoalBurnerJob;
import com.sheepfromheaven.minenow.jobs.cook.CookJob;
import com.sheepfromheaven.minenow.jobs.courier.CourierJob;
import com.sheepfromheaven.minenow.jobs.farmer.FarmerJob;
import com.sheepfromheaven.minenow.jobs.fisher.FisherJob;
import com.sheepfromheaven.minenow.jobs.forester.ForesterJob;
import com.sheepfromheaven.minenow.jobs.Job;
import com.sheepfromheaven.minenow.jobs.lumberjack.LumberjackJob;
import com.sheepfromheaven.minenow.jobs.miner.MinerJob;

import java.util.function.Supplier;

public enum JobType {
    FISHER("Fisher", FisherJob::new),
    LUMBERJACK("Lumberjack", LumberjackJob::new),
    COOK("Cook", CookJob::new),
    COURIER("Courier", CourierJob::new),
    MINER("Miner", MinerJob::new),
    FORESTER("Forester", ForesterJob::new),
    CHARCOAL_BURNER("Charcoal Burner", CharcoalBurnerJob::new),
    FARMER("Farmer", FarmerJob::new);

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
