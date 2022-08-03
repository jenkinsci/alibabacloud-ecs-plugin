package com.alibabacloud.jenkins.ecs;

import com.alibabacloud.jenkins.ecs.exception.AlibabaEcsException;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by kunlun.ykl on 2020/8/24.
 */
public abstract class AlibabaEcsComputerLauncher extends ComputerLauncher {
    private static final Logger LOGGER = Logger.getLogger(AlibabaEcsComputerLauncher.class.getName());

    @Override
    public void launch(SlaveComputer slaveComputer, TaskListener listener) {
        try {
            launchScript((AlibabaEcsComputer) slaveComputer, listener);
        } catch (AlibabaEcsException | IOException e) {
            e.printStackTrace(listener.error(e.getMessage()));
            Slave node = slaveComputer.getNode();
            if (node != null && node instanceof AlibabaEcsSpotFollower) {
                LOGGER.log(Level.WARNING, String.format("Terminating the ecs agent %s due a problem launching or connecting to it", slaveComputer.getName()), e);
                ((AlibabaEcsSpotFollower) node).terminate();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace(listener.error(e.getMessage()));
            Slave node = slaveComputer.getNode();
            if (node != null && node instanceof AlibabaEcsSpotFollower) {
                LOGGER.log(Level.WARNING, String.format("Terminating the ecs agent %s due a problem launching or connecting to it", slaveComputer.getName()), e);
                ((AlibabaEcsSpotFollower) node).terminate();
            }
        }
    }

    /**
     * Stage 2 of the launch. Called after the ECS instance comes up.
     */
    protected abstract void launchScript(AlibabaEcsComputer computer, TaskListener listener) throws AlibabaEcsException, IOException, InterruptedException;
}
