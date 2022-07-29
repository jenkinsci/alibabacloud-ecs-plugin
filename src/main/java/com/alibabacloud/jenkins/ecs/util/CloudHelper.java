package com.alibabacloud.jenkins.ecs.util;

import com.alibabacloud.jenkins.ecs.AlibabaCloud;
import com.alibabacloud.jenkins.ecs.AlibabaEcsFollowerTemplate;
import com.alibabacloud.jenkins.ecs.AlibabaEcsSpotFollower;
import com.aliyuncs.ecs.model.v20140526.DescribeInstancesResponse;
import com.google.common.collect.Lists;
import hudson.model.Computer;
import hudson.model.Hudson.CloudList;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by kunlun.ykl on 2020/8/27.
 */
public class CloudHelper {
    private static final Logger LOGGER = Logger.getLogger(CloudHelper.class.getName());

    public static AlibabaCloud getCloud(String cloudName) {
        CloudList clouds = Jenkins.get().clouds;
        for (Cloud cloud : clouds) {
            if (!(cloud instanceof AlibabaCloud)) {
                continue;
            }
            if (((AlibabaCloud) cloud).getCloudName().equals(cloudName)) {
                return (AlibabaCloud) cloud;
            }
        }
        return null;
    }

    public static int getCntOfNodeByCloudName(String cloudName) {
        int nodeCnt = 0;
        Jenkins jenkinsInstance = Jenkins.get();
        for (Node node : jenkinsInstance.getNodes()) {
            if (!(node instanceof AlibabaEcsSpotFollower)) {
                continue;
            }
            AlibabaEcsSpotFollower follower = (AlibabaEcsSpotFollower) node;
            if (follower.getCloud() == null) {
                LOGGER.log(Level.INFO, "cannot get cloud for slave: {0}", new Object[]{follower.getNodeName()});
                continue;
            }
            // httodo: 判断follower状态
            if (follower.getCloud().getCloudName().equals(cloudName)) {
                nodeCnt++;
            }
        }
        return nodeCnt;
    }

    public static void attachSlavesToJenkins(List<AlibabaEcsSpotFollower> slaves, AlibabaEcsFollowerTemplate t) throws IOException {
        Jenkins jenkins = Jenkins.get();
        for (final AlibabaEcsSpotFollower slave : slaves) {
            if (slave == null) {
                LOGGER.warning("Can't raise node for " + t);
                continue;
            }
            Computer c = slave.toComputer();
            if (c != null) {
                c.connect(false);
            }
            jenkins.addNode(slave);
        }
    }


    public static List<AlibabaEcsSpotFollower> getNodesByTmp(String templateName) {
        List<AlibabaEcsSpotFollower> nodes = Lists.newArrayList();
/*
        Computer[] computers = Jenkins.get().getComputers();
        for (Computer computer : computers) {
            if (!(computer instanceof AlibabaEcsComputer)) {
                continue;
            }
            AlibabaEcsSpotFollower node = ((AlibabaEcsComputer) computer).getNode();
            if(null != node) {
                nodes.add(node);
            }
        }
*/
        Jenkins jenkinsInstance = Jenkins.get();
        for (Node node : jenkinsInstance.getNodes()) {
            if (!(node instanceof AlibabaEcsSpotFollower)) {
                continue;
            }
            AlibabaEcsSpotFollower follower = (AlibabaEcsSpotFollower) node;
            // httodo: 判断follower状态
            if (templateName.equals(follower.getTemplateName())) {
                nodes.add(follower);
            }
        }
        return nodes;
    }

    public static NodeProvisioner.PlannedNode createPlannedNode(AlibabaEcsFollowerTemplate t, AlibabaEcsSpotFollower slave) {
        return new NodeProvisioner.PlannedNode(t.getTemplateName(), Computer.threadPoolForRemoting.submit(new Callable<Node>() {
            int retryCount = 0;
            private static final int DESCRIBE_LIMIT = 2;

            public Node call() throws Exception {
                while (true) {
                    String instanceId = slave.getEcsInstanceId();
                    DescribeInstancesResponse.Instance instance = getInstanceWithRetry(slave);
                    if (instance == null) {
                        LOGGER.log(Level.WARNING, "{0} Cannot find instance with instance id {1} in cloud {2}. Terminate provisioning ", new Object[]{t, instanceId, slave.getCloudName()});
                        return null;
                    }

                    String status = instance.getStatus();
                    if (status.equals("Running")) {
                        Computer c = slave.toComputer();
                        if (c != null) {
                            c.connect(false);
                        }
                        long startCostInSeconds = EcsInstanceHelper.getStartCostInSeconds(instance);
                        LOGGER.log(Level.INFO, "{0} Node {1} moved to RUNNING state in {2} seconds and is ready to be connected by Jenkins", new Object[]{t, slave.getNodeName(), startCostInSeconds});
                        return slave;
                    }

                    if (!status.equals("Starting")) {
                        if (retryCount >= DESCRIBE_LIMIT) {
                            LOGGER.log(Level.WARNING, "Instance {0} did not move to running after {1} attempts, terminating provisioning", new Object[]{instanceId, retryCount});
                            return null;
                        }

                        LOGGER.log(Level.INFO, "Attempt {0}: {1}. Node {2} is neither pending, neither running, it''s {3}. Will try again after 5s", new Object[]{retryCount, t, slave.getNodeName(), status});
                        retryCount++;
                    }

                    Thread.sleep(5000);
                }
            }
        }), t.getNumExecutors());
    }

    public static DescribeInstancesResponse.Instance getInstanceWithRetry(AlibabaEcsSpotFollower slave) {
        // Sometimes even after a successful RunInstances, DescribeInstances
        // returns an error for a few seconds. We do a few retries instead of
        // failing instantly. See [JENKINS-15319].
        for (int i = 0; i < 5; i++) {
            DescribeInstancesResponse.Instance instance = slave.describeNode();
            if (null != instance) {
                return instance;
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
