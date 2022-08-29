package com.alibabacloud.jenkins.ecs;

import java.util.concurrent.TimeUnit;

import hudson.model.AbstractDescribableImpl;

public abstract class EcsTypeData extends AbstractDescribableImpl<EcsTypeData> {
    public abstract boolean isWindows();

    public abstract boolean isUnix();

    public abstract String getBootDelay();

    public int getBootDelayInMillis() {
        if (getBootDelay() == null)
            return 0;
        try {
            return (int) TimeUnit.SECONDS.toMillis(Integer.parseInt(getBootDelay()));
        } catch (NumberFormatException nfe) {
            return 0;
        }
    }

}
