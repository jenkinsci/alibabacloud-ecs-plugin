package com.alibabacloud.jenkins.ecs;

import com.alibabacloud.jenkins.ecs.client.AlibabaEcsClient;
import com.alibabacloud.jenkins.ecs.util.CloudHelper;
import com.alibabacloud.jenkins.ecs.util.DateUtils;
import com.aliyuncs.ecs.model.v20140526.DescribeInstancesResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeInstancesResponse.Instance;
import com.aliyuncs.ecs.model.v20140526.DescribeKeyPairsResponse.KeyPair;
import com.aliyuncs.exceptions.ClientException;
import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor.FormException;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by kunlun.ykl on 2020/8/24.
 */
@Slf4j
public class AlibabaEcsSpotFollower extends Slave {
    protected static final long MIN_FETCH_TIME = Long.getLong("hudson.plugins.ecs.AlibabaEcsSpotFollower.MIN_FETCH_TIME", TimeUnit.SECONDS.toMillis(300));

    /**
     * 该Slave对应的 {@linkplain AlibabaEcsFollowerTemplate#getTemplateName()}
     */
    private final String templateName;
    /**
     * 该Slave对应的 {@linkplain AlibabaCloud#getCloudName()}
     */
    private final String cloudName;
    private String ecsInstanceId;
    private String initScript;
    private String userData;
    private String idleTerminationMinutes;

    private transient String instanceType;
    private transient String status;
    private transient String privateIp;
    private transient String publicIp;
    private transient String keyPairName;

    /* The time at which we fetched the last instance data */
    protected transient long lastFetchTime;
    protected transient Instance lastFetchInstance = null;
    /**
     * in seconds
     */
    private final int launchTimeout;

    public List<AlibabaEcsTag> tags = Lists.newArrayList();
    private boolean isConnected = false;

    public AlibabaEcsSpotFollower(@Nonnull String ecsInstanceId, @Nonnull String name, ComputerLauncher launcher, String remoteFS, @Nonnull String cloudName, String labelString, String initScript, @Nonnull String templateName, int numExecutors, int launchTimeout, List<AlibabaEcsTag> tags, String idleTerminationMinutes, RetentionStrategy<AlibabaEcsComputer> retentionStrategy, String userData) throws IOException, FormException {
        super(name, remoteFS, launcher);
        this.ecsInstanceId = ecsInstanceId;
        this.cloudName = cloudName;
        this.initScript = initScript;
        this.userData = StringUtils.trimToEmpty(userData);
        this.templateName = templateName;
        this.tags = tags;
        this.launchTimeout = launchTimeout;
        this.idleTerminationMinutes = idleTerminationMinutes;
        setLabelString(labelString);
        setRetentionStrategy(retentionStrategy);
        setNumExecutors(numExecutors);
        log.info("AlibabaEcsSpotFollower created. templateName: {} ecsInstanceId: {} numExecutors: {}", templateName, ecsInstanceId, numExecutors);
        readResolve();
    }

    @DataBoundConstructor
    public AlibabaEcsSpotFollower(@Nonnull String ecsInstanceId, @Nonnull String name, String remoteFS, @Nonnull String cloudName, String labelString, String initScript, @Nonnull String templateName, int numExecutors, int launchTimeout, List<AlibabaEcsTag> tags, String idleTerminationMinutes, String userData) throws FormException, IOException {
        // TODO: create Launcher by ami type
        this(ecsInstanceId, name, new AlibabaEcsUnixComputerLauncher(), remoteFS, cloudName, labelString, initScript, templateName, numExecutors, launchTimeout, tags, idleTerminationMinutes, new AlibabaEcsRetentionStrategy(idleTerminationMinutes), userData);
    }

    @Override
    protected Object readResolve() {
        log.info("readResolve invoked");
        if (ecsInstanceId == null) {
            ecsInstanceId = getNodeName();
        }
        return this;
    }

    public String getIdleTerminationMinutes() {
        return idleTerminationMinutes;
    }

    public int getLaunchTimeout() {
        return launchTimeout;
    }

    public long getLaunchTimeoutInMillis() {
        // this should be fine as long as launchTimeout remains an int type
        return launchTimeout * 1000L;
    }

    private void fetchLiveInstanceData(boolean force) {
        /*
         * If we've grabbed the data recently, don't bother getting it again unless we are forced
         */
        long now = System.currentTimeMillis();
        if ((lastFetchTime > 0) && (now - lastFetchTime < MIN_FETCH_TIME) && !force) {
            return;
        }

        if (getEcsInstanceId() == null || getEcsInstanceId().isEmpty()) {
            return;
        }

        Instance i = null;
        try {
            i = CloudHelper.getInstanceWithRetry(this);
        } catch (Exception e) {
            // We'll just retry next time we test for idleness.
            log.error("fetchLiveInstanceData error while get " + getEcsInstanceId(), e);
            return;
        }


        lastFetchTime = now;
        lastFetchInstance = i;
        if (i == null) {
            return;
        }
        instanceType = i.getInstanceType();
        status = i.getStatus();
        keyPairName = i.getKeyPairName();
        List<String> privateIpAddress = i.getVpcAttributes().getPrivateIpAddress();
        if (CollectionUtils.isNotEmpty(privateIpAddress)) {
            privateIp = privateIpAddress.get(0);
        } else {
            log.error("instance.getPrivateIpAddress is null. ecsInstanceId: " + ecsInstanceId);
        }
        List<String> publicIpAddress = i.getPublicIpAddress();
        if (CollectionUtils.isNotEmpty(publicIpAddress)) {
            publicIp = publicIpAddress.get(0);
        }

        /*
         * Only fetch tags from live instance if tags are set. This check is required to mitigate a race condition
         * when fetchLiveInstanceData() is called before pushLiveInstancedata().
         */
        if (!i.getTags().isEmpty()) {
            tags = Lists.newArrayList();
            for (Instance.Tag tag : i.getTags()) {
                tags.add(new AlibabaEcsTag(tag.getTagKey(), tag.getTagValue()));
            }
        }
    }

    @Override
    public Node reconfigure(final StaplerRequest req, JSONObject form) throws FormException {
        log.info("reconfigure invoked");
        if (form == null) {
            return null;
        }
        if (!isAlive()) {
            log.info("ECS instance terminated externally: " + ecsInstanceId);
            try {
                Jenkins.get().removeNode(this);
            } catch (IOException e) {
                log.error("reconfigure error.", e);
            }
            return null;
        }
        AlibabaEcsSpotFollower result = (AlibabaEcsSpotFollower) super.reconfigure(req, form);
        return result;
    }

    public DescribeInstancesResponse.Instance describeNode() {
        try {
            AlibabaEcsClient connect = getCloud().connect();
            List<Instance> instances = connect.describeInstances(Lists.newArrayList(ecsInstanceId));
            if (CollectionUtils.isEmpty(instances)) {
                log.error("describeNode error. nodeId: " + ecsInstanceId);
                return null;
            }
            return instances.get(0);
        } catch (Exception e) {
            log.error("describeNode error. nodeId: " + ecsInstanceId, e);
        }
        return null;
    }

    public String getKeyPairName() {
        fetchLiveInstanceData(false);
        return keyPairName;
    }

    public KeyPair getKeyPair() throws ClientException {
        String keyPairName1 = getKeyPairName();
        if (StringUtils.isBlank(keyPairName1)) {
            throw new ClientException("getKeyPairName error. keyPairName:{}", keyPairName1);
        }
        AlibabaEcsClient connect = getCloud().connect();
        List<KeyPair> keyPairs = connect.describeKeyPairs(keyPairName1, null);
        if (CollectionUtils.isEmpty(keyPairs)) {
            throw new ClientException("getKeyPair error. keyPairName:{}", keyPairName1);
        }
        return keyPairs.get(0);
    }

    public String getPrivateIp() {
        fetchLiveInstanceData(false);
        return privateIp;
    }

    public String getPublicIp() {
        fetchLiveInstanceData(false);
        return publicIp;
    }

    /**
     * @return Number of milli-secs since the instance was started.
     */
    public long getUptime() {
        fetchLiveInstanceData(true);
        if (null == lastFetchInstance) {
            log.warn("getUptime error. instanceId: {}", ecsInstanceId);
            return 0;
        }
        String startTime = lastFetchInstance.getStartTime();
        Date startDate = DateUtils.parse(startTime);
        return System.currentTimeMillis() - startDate.getTime();
    }

    public String status() {
        fetchLiveInstanceData(true);
        if (null == lastFetchInstance) {
            return "UNKNOWN";
        }
        return lastFetchInstance.getStatus();
    }

    public boolean isAlive() {
        return status().equalsIgnoreCase("Running");
    }

    public String getInstanceType() {
        fetchLiveInstanceData(false);
        return instanceType;
    }

    public String getEcsInstanceId() {
        return ecsInstanceId;
    }

    public String getCloudName() {
        return cloudName;
    }

    public AlibabaCloud getCloud() {
        return CloudHelper.getCloud(cloudName);
    }

    public String getInitScript() {
        return initScript;
    }

    public String getUserData() {
        return userData;
    }

    public boolean stop() {
        try {
            AlibabaEcsClient connect = getCloud().connect();
            for (int i = 0; i < 60; i++) {
                String status = status();
                if ("Running".equalsIgnoreCase(status)) {
                    connect.stopIntance(ecsInstanceId);
                } else if ("Stopped".equalsIgnoreCase(status) || "Pending".equalsIgnoreCase(status)) {
                    return true;
                } else if ("UNKNOWN".equalsIgnoreCase(status)) {
                    return false;
                }
                // "Stopping"状态, 就多等几次
                Thread.sleep(1000L);
            }
        } catch (Exception e) {
            log.error("stop error. instanceId: {}", ecsInstanceId, e);
        }
        return false;
    }

    public String gracefulTerminate() {
        try {
            String currStatus = status();
            if (currStatus.equals("UNKNOWN")) {
                return currStatus;
            }
            boolean stop = stop();
            if (!stop) {
                log.error("instance status illegal, failed terminate");
                return status();
            }
            AlibabaEcsClient connect = getCloud().connect();
            waitForStatus(Lists.newArrayList("Pending", "Stopped"), 30, 1000L);

            connect.terminateInstance(ecsInstanceId, true);
            String status = waitForStatus(Lists.newArrayList("UNKNOWN"), 30, 1000L);
            return status;
        } catch (Exception e) {
            log.error("terminate error intanceId: {}", ecsInstanceId, e);
        }
        return status();
    }

    public String waitForStatus(List<String> targetStatusList, int retryTimes, long sleepMills) {
        String status = null;
        for (int i = 0; i < retryTimes; i++) {
            status = status();
            if (targetStatusList.contains(status)) {
                log.info("waitForStatus success. instanceId: {} targetStatus: {} triedTimes: {}", ecsInstanceId, targetStatusList, i);
                break;
            }
            try {
                Thread.sleep(sleepMills);
            } catch (InterruptedException e) {
                log.error("waitForStatus error instanceId: {} targetStatus: {} ", ecsInstanceId, e);
            }
        }
        if (!targetStatusList.contains(status)) {
            log.error("waitForStatus error. retryTimes: {} targetStatusList: {} status: {}", retryTimes, targetStatusList, status);
        }
        return status;
    }

    public String forceTerminate() {
        String currStatus = status();
        if (currStatus.equals("UNKNOWN")) {
            log.info("forceTerminate success. currStatus is UNKNOWN");
            return currStatus;
        }

        int maxRetryTimes = 180;
        long sleepMills = 5000L;
        boolean submitResult = submitTerminateRequest(this.ecsInstanceId, maxRetryTimes, sleepMills);
        if (!submitResult) {
            currStatus = status();
            return currStatus;
        }
        return waitForStatus(Lists.newArrayList("UNKNOWN"), maxRetryTimes, sleepMills);
    }

    private boolean submitTerminateRequest(String ecsInstanceId, int maxRetryTimes, long sleepMills) {
        AlibabaEcsClient connect = getCloud().connect();
        boolean b = connect.terminateInstance(ecsInstanceId, true);
        int i = 0;
        while (!b && i <= maxRetryTimes) {
            try {
                Thread.sleep(sleepMills);
            } catch (InterruptedException e) {
                log.error("", e);
            }
            b = connect.terminateInstance(ecsInstanceId, true);
            i++;
        }
        if (!b) {
            log.error("forceTerminate submit failed. maxRetryTimes: {} sleepMills: {}", maxRetryTimes, sleepMills);
        } else {
            log.info("forceTerminate submit success. retryTimes: {} sleepMills: {}", i, sleepMills);
        }
        return b;
    }

    void idleTimeout() {
        log.info("ECS instance idle time expired: " + getEcsInstanceId());
        terminate();
    }

    void launchTimeout() {
        log.info("ECS instance failed to launch: " + getEcsInstanceId());
        terminate();
    }

    public void terminate() {
        Computer.threadPoolForRemoting.submit(() -> {
            try {
                // String currStatus = gracefulTerminate();
                String currStatus = forceTerminate();
                if ("UNKNOWN".equals(currStatus)) {
                    Jenkins.getInstanceOrNull().removeNode(this);
                    log.info("delete node: {} success.", getNodeName());
                } else {
                    log.info("delete node: {} failed. status: {}", getNodeName(), status);
                }
            } catch (Exception e) {
                log.error("terminate error instanceId: {}", ecsInstanceId, e);
            }
        });
    }

    /**
     * Called when the agent is connected to Jenkins
     */
    public void onConnected() {
        log.info("AlibabaEcsSpotFollower onConnected. ecsInstanceId: {}", getEcsInstanceId());
        isConnected = true;
    }

    @Override
    public Computer createComputer() {
        return new AlibabaEcsComputer(this);
    }

    public String getTemplateName() {
        return templateName;
    }

    public List<AlibabaEcsTag> getTags() {
        fetchLiveInstanceData(true);
        return Collections.unmodifiableList(tags);
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {
        @Override
        public String getDisplayName() {
            return "sample follower";
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }
    }
}
