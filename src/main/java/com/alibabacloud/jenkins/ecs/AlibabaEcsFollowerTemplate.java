package com.alibabacloud.jenkins.ecs;

import java.util.List;

import com.alibabacloud.jenkins.ecs.client.AlibabaEcsClient;
import com.alibabacloud.jenkins.ecs.exception.AlibabaEcsException;
import com.alibabacloud.jenkins.ecs.util.CloudHelper;
import com.aliyuncs.ecs.model.v20140526.RunInstancesRequest;
import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

/**
 * Created by kunlun.ykl on 2020/8/25.
 */
@Slf4j
public class AlibabaEcsFollowerTemplate implements Describable<AlibabaEcsFollowerTemplate> {
    private String templateId;
    private String region;
    private String zone;
    private String instanceType;
    private String vswId;
    private String initScript;
    private String labelString;
    private String remoteFs;

    private int minimumNumberOfInstances;

    private transient AlibabaCloud parent;

    public AlibabaEcsFollowerTemplate(String region, String zone, String instanceType, int minimumNumberOfInstances,
                                      String vsw, String initScript, String labelString, String remoteFs) {
        this.region = region;
        this.zone = zone;
        this.instanceType = instanceType;
        this.templateId = CloudHelper.getTemplateId(zone, instanceType);
        this.minimumNumberOfInstances = minimumNumberOfInstances;
        this.vswId = vsw;
        this.initScript = initScript;
        this.labelString = labelString;
        this.remoteFs = remoteFs;
    }

    public AlibabaCloud getParent() {
        return parent;
    }

    public void setParent(AlibabaCloud parent) {
        this.parent = parent;
    }

    @Override
    public Descriptor<AlibabaEcsFollowerTemplate> getDescriptor() {
        return Jenkins.get().getDescriptor(getClass());
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getRegion() {
        return region;
    }

    public String getVswId() {
        return vswId;
    }

    public String getZone() {
        return zone;
    }

    public String getInstanceType() {
        return instanceType;
    }

    public int getMinimumNumberOfInstances() {
        return minimumNumberOfInstances;
    }

    public String getInitScript() {
        return initScript;
    }

    public String getLabelString() {
        return labelString;
    }

    public String getRemoteFs() {
        return remoteFs;
    }

    public List<AlibabaEcsSpotFollower> provision(int amount) throws Exception {
        List<AlibabaEcsSpotFollower> list = Lists.newArrayList();
        List<String> instanceIds = provisionSpot(amount);
        for (String instanceId : instanceIds) {
            AlibabaEcsSpotFollower alibabaEcsSpotFollower = new AlibabaEcsSpotFollower(instanceId, templateId + "-" + instanceId,
                remoteFs,
                parent.getDisplayName(), labelString, initScript, getTemplateId());
            list.add(alibabaEcsSpotFollower);
        }
        return list;
    }

    public List<String> provisionSpot(int amount) throws Exception {
        AlibabaEcsClient connect = getParent().connect();
        if(null == connect){
            log.error("AlibabaEcsClient  connection failure.");
            throw new AlibabaEcsException("AlibabaEcsClient connect failure.");
        }
        RunInstancesRequest request = new RunInstancesRequest();
        request.setVSwitchId(vswId);
        request.setImageId(parent.getImage());
        request.setSecurityGroupId(parent.getSecurityGroup());
        if(null == parent.getPrivateKey()){
            log.error("provision error privateKey is empty.");
            throw new AlibabaEcsException("provision error privateKey is empty.");
        }
        String keyPairName = parent.getPrivateKey().getKeyPairName();
        if (StringUtils.isBlank(keyPairName)) {
            log.error("provision error keyPairName is empty.");
            throw new AlibabaEcsException("provision error keyPairName is empty.");
        }
        request.setAmount(amount);
        request.setKeyPairName(keyPairName);
        request.setInstanceType(instanceType);
        connect.runInstances(request);
        List<String> instanceIdSets = connect.runInstances(request);
        if (CollectionUtils.isEmpty(instanceIdSets)
            || StringUtils.isBlank(instanceIdSets.get(0))) {
            throw new AlibabaEcsException("provision error");
        }
        return instanceIdSets;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<AlibabaEcsFollowerTemplate> {

        @Override
        public String getDisplayName() {
            return "";
        }

    }
}
