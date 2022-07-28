package com.alibabacloud.jenkins.ecs;

import java.util.List;

import com.alibabacloud.jenkins.ecs.client.AlibabaEcsClient;
import com.alibabacloud.jenkins.ecs.exception.AlibabaEcsException;
import com.alibabacloud.jenkins.ecs.util.CloudHelper;
import com.aliyuncs.ecs.model.v20140526.RunInstancesRequest;
import com.aliyuncs.ecs.model.v20140526.RunInstancesRequest.Tag;
import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
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
    /**
     * 系统盘类型
     * <p>
     * cloud_efficiency：高效云盘。
     * cloud_ssd：SSD云盘。
     * cloud_essd：ESSD云盘。
     * cloud：普通云盘。
     * </p>
     */
    private String systemDiskCategory;

    /**
     * 系统盘大小, 以GB为单位, 取值范围：20~500。
     */
    private Integer systemDiskSize;

    /**
     * 是否创建公网IP, 目前用NAT公网IP, 后续可以考虑使用EIP
     */
    private Boolean attachPublicIp = Boolean.TRUE;

    /**
     * 标签
     */
    private List<AlibabaEcsTag> tags;

    /**
     * 实例付费类型
     */
    private String chargeType;

    private int minimumNumberOfInstances;

    private transient AlibabaCloud parent;

    public static final String SPOT_INSTANCE_CHARGE_TYPE= "Spot";



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

    public AlibabaEcsFollowerTemplate(String region, String zone, String instanceType, int minimumNumberOfInstances,
                                      String vsw, String initScript, String labelString, String remoteFs,
                                      String systemDiskCategory, Integer systemDiskSize,
                                      Boolean attachPublicIp, List<AlibabaEcsTag> tags, String chargeType) {
        this.region = region;
        this.zone = zone;
        this.instanceType = instanceType;
        this.templateId = CloudHelper.getTemplateId(zone, instanceType);
        this.minimumNumberOfInstances = minimumNumberOfInstances;
        this.vswId = vsw;
        this.initScript = initScript;
        this.labelString = labelString;
        this.remoteFs = remoteFs;
        this.systemDiskCategory = systemDiskCategory;
        this.systemDiskSize = systemDiskSize;
        this.chargeType = chargeType;
        if (attachPublicIp != null) {
            this.attachPublicIp = attachPublicIp;
        }
        if (CollectionUtils.isNotEmpty(tags)){
            this.tags = tags;
        }
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

    public String getChargeType() {
        return chargeType;
    }

    public String getRemoteFs() {
        return remoteFs;
    }


    public List<AlibabaEcsSpotFollower> provision(int amount) throws Exception {
        List<AlibabaEcsSpotFollower> list = Lists.newArrayList();
        List<String> instanceIds = provisionSpot(amount);
        for (String instanceId : instanceIds) {
            AlibabaEcsSpotFollower alibabaEcsSpotFollower = new AlibabaEcsSpotFollower(instanceId,
                templateId + "-" + instanceId,
                remoteFs,
                parent.getDisplayName(), labelString, initScript, getTemplateId());
            list.add(alibabaEcsSpotFollower);
        }
        return list;
    }

    public List<String> provisionSpot(int amount) throws Exception {
        AlibabaEcsClient connect = getParent().connect();
        if (null == connect) {
            log.error("AlibabaEcsClient  connection failure.");
            throw new AlibabaEcsException("AlibabaEcsClient connect failure.");
        }
        RunInstancesRequest request = new RunInstancesRequest();
        request.setVSwitchId(vswId);
        request.setImageId(parent.getImage());
        request.setSecurityGroupId(parent.getSecurityGroup());
        if (null == parent.getPrivateKey()) {
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
        if(StringUtils.isNotBlank(systemDiskCategory)) {
            request.setSystemDiskCategory(systemDiskCategory);
        }
        if (null != systemDiskSize) {
            request.setSystemDiskSize(systemDiskSize.toString());
        }
        if (BooleanUtils.isTrue(attachPublicIp)) {
            request.setInternetMaxBandwidthIn(10);
            request.setInternetMaxBandwidthOut(10);
        }
        request.setTags(buildEcsTags());
        if (SPOT_INSTANCE_CHARGE_TYPE.equals(chargeType)){
            request.setSpotStrategy("SpotAsPriceGo");
        }
        List<String> instanceIdSets = connect.runInstances(request);
        if (CollectionUtils.isEmpty(instanceIdSets)
            || StringUtils.isBlank(instanceIdSets.get(0))) {
            throw new AlibabaEcsException("provision error");
        }
        return instanceIdSets;
    }

    private List<Tag> buildEcsTags() {
        List<RunInstancesRequest.Tag> ecsTags = Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(tags)) {
            for (AlibabaEcsTag alibabaEcsTag : tags) {
                Tag tag = new Tag();
                tag.setKey(alibabaEcsTag.getName());
                tag.setValue(alibabaEcsTag.getValue());
                ecsTags.add(tag);
            }
        }
        Tag tag = new Tag();
        tag.setKey(AlibabaEcsTag.TAG_NAME_CREATED_FROM);
        tag.setValue(AlibabaEcsTag.TAG_VALUE_JENKINS_PLUGIN);
        ecsTags.add(tag);
        return ecsTags;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<AlibabaEcsFollowerTemplate> {

        @Override
        public String getDisplayName() {
            return "";
        }

    }
}
