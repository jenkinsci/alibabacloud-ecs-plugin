package com.alibabacloud.jenkins.ecs.monitor;

import com.alibabacloud.jenkins.ecs.AlibabaEcsSpotFollower;
import com.alibabacloud.jenkins.ecs.util.MinimumInstanceChecker;
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Node;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class AlibabaEcsFollowerMonitor extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(AlibabaEcsFollowerMonitor.class.getName());

    private final Long recurrencePeriod;

    public AlibabaEcsFollowerMonitor() {
        super("AlibabaEcsFollowerMonitor alive agents monitor");
        recurrencePeriod = Long.getLong("jenkins.ecs.checkAlivePeriod", TimeUnit.MINUTES.toMillis(10));
        LOGGER.log(Level.FINE, "ECS check alive period is {0}ms", recurrencePeriod);
    }

    @Override
    public long getRecurrencePeriod() {
        return recurrencePeriod;
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        removeDeadNodes();
        MinimumInstanceChecker.checkForMinimumInstances();
    }

    private void removeDeadNodes() {
        for (Node node : Jenkins.get().getNodes()) {
            if (node instanceof AlibabaEcsSpotFollower) {
                final AlibabaEcsSpotFollower ecsSlave = (AlibabaEcsSpotFollower) node;
                try {
                    if (!ecsSlave.isAlive()) {
                        LOGGER.info("ECS instance is dead: " + ecsSlave.getEcsInstanceId());
                        ecsSlave.terminate();
                    }
                } catch (Exception e) {
                    LOGGER.info("ECS instance is dead and failed to terminate: " + ecsSlave.getEcsInstanceId());
                    removeNode(ecsSlave);
                }
            }
        }
    }
    private void removeNode(AlibabaEcsSpotFollower ecsFollower) {
        try {
            Jenkins.get().removeNode(ecsFollower);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to remove node: " + ecsFollower.getEcsInstanceId());
        }
    }

}
