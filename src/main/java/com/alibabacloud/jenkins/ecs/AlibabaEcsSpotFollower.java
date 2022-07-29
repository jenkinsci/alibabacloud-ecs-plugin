package com.alibabacloud.jenkins.ecs;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;

import com.alibabacloud.jenkins.ecs.client.AlibabaEcsClient;
import com.alibabacloud.jenkins.ecs.util.CloudHelper;
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
import jenkins.model.Jenkins;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Created by kunlun.ykl on 2020/8/24.
 */
@Slf4j
public class AlibabaEcsSpotFollower extends Slave {
    private final String templateId;
    private final String cloudName;
    private String ecsInstanceId;
    private String instanceType;
    private String status;
    private String privateIp;
    private String publicIp;
    private String keyPairName;
    private String initScript;
    private String userData;


    private boolean isConnected = false;

    public AlibabaEcsSpotFollower(@Nonnull String ecsInstanceId, @Nonnull String name, ComputerLauncher launcher,
                                  String remoteFS,
                                  @Nonnull String cloudName, String labelString, String initScript,
                                  @Nonnull String templateId, String userData)
        throws IOException, FormException {
        super(name, remoteFS, launcher);
        this.ecsInstanceId = ecsInstanceId;
        this.cloudName = cloudName;
        this.initScript = initScript;
        this.templateId = templateId;
        this.userData = StringUtils.trimToEmpty(userData);
        setLabelString(labelString);
        DescribeInstancesResponse.Instance instance = describeNode();
        if (null == instance) {
            log.error("describeNode error. instance is null. ecsInstanceId: " + ecsInstanceId);
            return;
        }
        instanceType = instance.getInstanceType();
        status = instance.getStatus();
        List<String> privateIpAddress = instance.getVpcAttributes().getPrivateIpAddress();
        if (CollectionUtils.isNotEmpty(privateIpAddress)) {
            privateIp = privateIpAddress.get(0);
        } else {
            log.error("instance.getPrivateIpAddress is null. ecsInstanceId: " + ecsInstanceId);
        }
        List<String> publicIpAddress = instance.getPublicIpAddress();
        if (CollectionUtils.isNotEmpty(publicIpAddress)) {
            publicIp = publicIpAddress.get(0);
        }
        keyPairName = instance.getKeyPairName();

    }

    @DataBoundConstructor
    public AlibabaEcsSpotFollower(@Nonnull String ecsInstanceId, @Nonnull String name, String remoteFS,
                                  @Nonnull String cloudName, String labelString, String initScript,
                                  @Nonnull String templateId, String userData)
        throws FormException, IOException {
        // TODO: create Launcher by ami type
        this(ecsInstanceId, name, new AlibabaEcsUnixComputerLauncher(), remoteFS, cloudName, labelString, initScript,
            templateId, userData);
    }

    @Override
    protected Object readResolve() {
        log.info("readResolve invoked");
        if (ecsInstanceId == null) {
            ecsInstanceId = getNodeName();
        }
        return this;
    }

    public String getTemplateId() {
        return templateId;
    }

    @Override
    public Node reconfigure(final StaplerRequest req, JSONObject form) throws FormException {
        log.info("reconfigure invoked");
        if (form == null) {
            return null;
        }
        String status = status();
        if (!"Running".equalsIgnoreCase(status)) {
            try {
                Jenkins.get().removeNode(this);
            } catch (IOException e) {
                log.error("reconfigure error.", e);
            }
            return null;
        }
        AlibabaEcsSpotFollower result = (AlibabaEcsSpotFollower)super.reconfigure(req, form);
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
        return keyPairName;
    }

    public KeyPair getKeyPair() throws ClientException {
        AlibabaEcsClient connect = getCloud().connect();
        List<KeyPair> keyPairs = connect.describeKeyPairs(keyPairName, null);
        if (CollectionUtils.isEmpty(keyPairs)) {
            throw new ClientException("getKeyPair error. keyPairName:{}", keyPairName);
        }
        return keyPairs.get(0);
    }

    public String getPrivateIp() {
        if (StringUtils.isNotBlank(privateIp)) {
            return privateIp;
        }
        DescribeInstancesResponse.Instance instance = describeNode();
        if (null == instance) {
            return privateIp;
        }
        List<String> privateIpAddress = instance.getVpcAttributes().getPrivateIpAddress();
        if (CollectionUtils.isNotEmpty(privateIpAddress)) {
            privateIp = privateIpAddress.get(0);
        }
        return privateIp;
    }

    public String getPublicIp() {
        if (StringUtils.isNotBlank(publicIp)) {
            return publicIp;
        }
        DescribeInstancesResponse.Instance instance = describeNode();
        if (null == instance) {
            return publicIp;
        }
        List<String> publicIpAddress = instance.getPublicIpAddress();
        if (CollectionUtils.isNotEmpty(publicIpAddress)) {
            publicIp = publicIpAddress.get(0);
        }
        return publicIp;
    }

    public String status() {
        try {
            DescribeInstancesResponse.Instance instance = describeNode();
            if (null == instance) {
                return "UNKNOWN";
            }
            return instance.getStatus();
        } catch (Exception e) {
            log.error("describeNode error. instanceId: {}", ecsInstanceId, e);
        }
        return "UNKNOWN";
    }

    public String getInstanceType() {
        return instanceType;
    }

    public String getStatus() {
        return status;
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
            if(currStatus.equals("UNKNOWN")) {
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
                log.info("waitForStatus success. instanceId: {} targetStatus: {} triedTimes: {}", ecsInstanceId,
                    targetStatusList, i);
                break;
            }
            try {
                Thread.sleep(sleepMills);
            } catch (InterruptedException e) {
                log.error("waitForStatus error instanceId: {} targetStatus: {} ", ecsInstanceId, e);
            }
        }
        return status;
    }

    public String forceTerminate() {
        String currStatus = status();
        if(currStatus.equals("UNKNOWN")) {
            return currStatus;
        }
        AlibabaEcsClient connect = getCloud().connect();
        connect.terminateInstance(ecsInstanceId, true);
        return waitForStatus(Lists.newArrayList("UNKNOWN"), 180, 1000L);
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
                log.error("terminate error intanceId: {}", ecsInstanceId, e);
            }
        });
    }

    public boolean onConnected() {
        return isConnected;
    }

    @Override
    public Computer createComputer() {
        return new AlibabaEcsComputer(this);
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
