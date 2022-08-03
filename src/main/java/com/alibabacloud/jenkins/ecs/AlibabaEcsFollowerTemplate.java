package com.alibabacloud.jenkins.ecs;

import com.alibaba.fastjson.JSON;
import com.alibabacloud.credentials.plugin.auth.AlibabaCredentials;
import com.alibabacloud.credentials.plugin.util.CredentialsHelper;
import com.alibabacloud.jenkins.ecs.client.AlibabaEcsClient;
import com.alibabacloud.jenkins.ecs.enums.SystemDiskCategory;
import com.alibabacloud.jenkins.ecs.exception.AlibabaEcsException;
import com.alibabacloud.jenkins.ecs.util.MinimumInstanceChecker;
import com.aliyuncs.ecs.model.v20140526.DescribeImagesRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeImagesResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeVSwitchesResponse;
import com.aliyuncs.ecs.model.v20140526.RunInstancesRequest;
import com.aliyuncs.ecs.model.v20140526.RunInstancesRequest.Tag;
import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.RelativePath;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.model.listeners.SaveableListener;
import hudson.security.Permission;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Created by kunlun.ykl on 2020/8/25.
 */
@Slf4j
public class AlibabaEcsFollowerTemplate implements Describable<AlibabaEcsFollowerTemplate> {

    private final String templateName;

    private final String image;
    private final String zone;
    private final String instanceType;
    private final String vsw;
    private final String initScript;
    private final String userData;
    private final String labels;


    private final String remoteFs;
    /**
     * 系统盘类型
     * <p>
     * cloud_efficiency：高效云盘。
     * cloud_ssd：SSD云盘。
     * cloud_essd：ESSD云盘。
     * cloud：普通云盘。
     * </p>
     */
    private final SystemDiskCategory systemDiskCategory;

    /**
     * 系统盘大小, 以GB为单位, 取值范围：20~500。
     */
    private final Integer systemDiskSize;

    /**
     * 创建出来的ECS实例标签列表
     */
    private final List<AlibabaEcsTag> tags;

    /**
     * 实例付费类型
     */
    private final String chargeType;

    /**
     * 当前Template下可以创建的Node数量上限
     */
    private final int instanceCap;
    /**
     * 当前Template下可以创建的Node数量下限
     */
    private final int minimumNumberOfInstances;
    private final int launchTimeout;

    /**
     * 每个Node支持的Executor数量
     */
    private final String numExecutors;

    private final String idleTerminationMinutes;


    private transient AlibabaCloud parent;
    private transient Set<LabelAtom> labelSet;
    public static final String SPOT_INSTANCE_CHARGE_TYPE = "Spot";

    public static final String ON_DEMAND_INSTANCE_CHARGE_TYPE = "OnDemand";
    public static final Integer DEFAULT_NUM_OF_EXECUTORS = 4;

    @DataBoundConstructor
    public AlibabaEcsFollowerTemplate(String templateName, String image, String zone, String vsw, String chargeType, String instanceType, String initScript, String labelString, String remoteFs, SystemDiskCategory systemDiskCategory, Integer systemDiskSize, int minimumNumberOfInstances, String idleTerminationMinutes, String instanceCapStr, String numExecutors, String launchTimeoutStr, List<AlibabaEcsTag> tags, String userData) {
        this.templateName = templateName;
        this.image = image;
        this.zone = zone;
        this.userData = StringUtils.trimToEmpty(userData);
        this.instanceType = instanceType;
        this.minimumNumberOfInstances = minimumNumberOfInstances;
        this.vsw = vsw;
        this.initScript = initScript;
        this.labels = Util.fixNull(labelString);
        this.systemDiskCategory = systemDiskCategory;
        this.systemDiskSize = systemDiskSize;
        this.chargeType = chargeType;
        if (CollectionUtils.isEmpty(tags)) {
            this.tags = Lists.newArrayList();
        } else {
            this.tags = tags;
        }
        this.numExecutors = Util.fixNull(numExecutors).trim();
        this.idleTerminationMinutes = idleTerminationMinutes;

        if (StringUtils.isNotBlank(remoteFs)) {
            this.remoteFs = remoteFs;
        } else {
            this.remoteFs = DescriptorImpl.defaultRemoteFs;
        }
        if (null == instanceCapStr || instanceCapStr.isEmpty()) {
            this.instanceCap = Integer.MAX_VALUE;
        } else {
            this.instanceCap = Integer.parseInt(instanceCapStr);
        }
        if (StringUtils.isBlank(launchTimeoutStr)) {
            this.launchTimeout = Integer.MAX_VALUE;
        } else {
            this.launchTimeout = Integer.parseInt(launchTimeoutStr);
        }

    }

    public int getLaunchTimeout() {
        return launchTimeout <= 0 ? Integer.MAX_VALUE : launchTimeout;
    }

    public long getLaunchTimeoutInMillis() {
        // this should be fine as long as launchTimeout remains an int type
        return launchTimeout * 1000L;
    }

    public String getLaunchTimeoutStr() {
        if (launchTimeout == Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(launchTimeout);
        }
    }

    public int getNumExecutors() {
        if (StringUtils.isBlank(numExecutors)) {
            return DEFAULT_NUM_OF_EXECUTORS;
        }
        try {
            return Integer.parseInt(numExecutors);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return DEFAULT_NUM_OF_EXECUTORS;
        }
    }

    protected Object readResolve() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        labelSet = Label.parse(labels);
        return this;
    }

    public int getInstanceCap() {
        return instanceCap;
    }

    public String getInstanceCapStr() {
        if (instanceCap == Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(instanceCap);
        }
    }

    public Set<LabelAtom> getLabelSet() {
        if (labelSet == null) {
            labelSet = Label.parse(labels);
        }
        return labelSet;
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

    public String getTemplateName() {
        return templateName;
    }

    public String getVsw() {
        return vsw;
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

    public String getUserData() {
        return userData;
    }

    public String getLabelString() {
        return labels;
    }

    public String getChargeType() {
        return chargeType;
    }

    public String getRemoteFs() {
        return remoteFs;
    }

    public String getImage() {
        return image;
    }

    public SystemDiskCategory getSystemDiskCategory() {
        return systemDiskCategory;
    }

    public Integer getSystemDiskSize() {
        return systemDiskSize;
    }

    public String getIdleTerminationMinutes() {
        return idleTerminationMinutes;
    }

    public List<AlibabaEcsTag> getTags() {
        return tags;
    }

    public List<AlibabaEcsSpotFollower> provision(int amount, boolean attachPublicIp) throws Exception {
        List<AlibabaEcsSpotFollower> list = Lists.newArrayList();
        List<String> instanceIds = provisionSpot(amount, attachPublicIp);
        for (String instanceId : instanceIds) {
            AlibabaEcsSpotFollower alibabaEcsSpotFollower = new AlibabaEcsSpotFollower(instanceId, templateName + "-" + instanceId, remoteFs, parent.getCloudName(), labels, initScript, getTemplateName(), getNumExecutors(), getLaunchTimeout(), getTags(), getIdleTerminationMinutes(), userData);
            list.add(alibabaEcsSpotFollower);
        }
        return list;
    }

    public List<String> provisionSpot(int amount, boolean attachPublicIp) throws Exception {
        log.info("provisionSpot start. templateName: {} amount: {} attachPublicIp: {}", templateName, amount, attachPublicIp);
        AlibabaEcsClient connect = getParent().connect();
        if (null == connect) {
            log.error("AlibabaEcsClient  connection failure.");
            throw new AlibabaEcsException("AlibabaEcsClient connect failure.");
        }
        RunInstancesRequest request = new RunInstancesRequest();
        request.setVSwitchId(vsw);
        request.setImageId(image);
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
        if (null != systemDiskCategory) {
            request.setSystemDiskCategory(systemDiskCategory.name());
        }
        if (null != systemDiskSize) {
            request.setSystemDiskSize(systemDiskSize.toString());
        }
        if (BooleanUtils.isTrue(attachPublicIp)) {
            request.setInternetMaxBandwidthIn(10);
            request.setInternetMaxBandwidthOut(10);
        }
        if (StringUtils.isNotBlank(userData)) {
            //set user data
            String uData = Base64.getEncoder().encodeToString(userData.getBytes(StandardCharsets.UTF_8));
            request.setUserData(uData);
        }
        request.setTags(buildEcsTags());
        if (SPOT_INSTANCE_CHARGE_TYPE.equals(chargeType)) {
            request.setSpotStrategy("SpotAsPriceGo");
        }
        List<String> instanceIdSets = connect.runInstances(request);
        if (CollectionUtils.isEmpty(instanceIdSets) || StringUtils.isBlank(instanceIdSets.get(0))) {
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
    public static final class OnSaveListener extends SaveableListener {
        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof Jenkins) {
                MinimumInstanceChecker.checkForMinimumInstances();
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<AlibabaEcsFollowerTemplate> {
        public static final int defaultSystemDiskSize = 40;
        public static final String defaultRemoteFs = "/root";

        @Override
        public String getDisplayName() {
            return "";
        }
        public FormValidation doCheckTemplateName(@QueryParameter String templateName) {
            Jenkins.get().hasPermission(Jenkins.ADMINISTER);
            if (StringUtils.isBlank(templateName)) {
                return FormValidation.error("Description not specified");
            }
            try {
                Jenkins.checkGoodName(templateName);
            } catch (Failure e) {
                return FormValidation.error(e.getMessage());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckMinimumNumberOfInstances(@QueryParameter String value, @QueryParameter String instanceCapStr) {
            if (Util.fixEmptyAndTrim(value) == null) return FormValidation.ok();
            try {
                int val = Integer.parseInt(value);
                if (val >= 0) {
                    int instanceCap;
                    try {
                        instanceCap = Integer.parseInt(instanceCapStr);
                    } catch (NumberFormatException ignore) {
                        instanceCap = Integer.MAX_VALUE;
                    }
                    if (val > instanceCap) {
                        return FormValidation.error("Minimum number of instances must not be larger than AMI Instance Cap %d", instanceCap);
                    }
                    return FormValidation.ok();
                }
            } catch (NumberFormatException e) {
                log.info("doCheckMinimumNumberOfInstances error:" + JSON.toJSONString(e));
            }
            return FormValidation.error("Minimum number of instances must be a non-negative integer (or null)");
        }

        @RequirePOST
        public ListBoxModel doFillSystemDiskCategoryItems() {
            ListBoxModel model = new ListBoxModel();
            List<String> systemDiskCategorys = Lists.newArrayList("cloud_essd", "cloud_ssd", "cloud_efficiency", "cloud");
            for (String systemDiskCategory : systemDiskCategorys) {
                model.add(systemDiskCategory, systemDiskCategory);
            }
            return model;
        }

        @RequirePOST
        public ListBoxModel doFillZoneItems(@RelativePath("..") @QueryParameter String credentialsId, @RelativePath("..") @QueryParameter String region, @RelativePath("..") @QueryParameter Boolean intranetMaster) {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            ListBoxModel model = new ListBoxModel();
            model.add("<not specified>", "");
            if (StringUtils.isBlank(credentialsId) || StringUtils.isBlank(region)) {
                return model;
            }
            AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
            if (credentials == null) {
                log.error("doFillZoneItems error. credentials not found. region: {} credentialsId: {}", region, credentialsId);
                return model;
            }
            AlibabaEcsClient client = new AlibabaEcsClient(credentials, region, intranetMaster);
            List<String> zones = client.describeAvailableZones();
            for (String zone : zones) {
                model.add(zone, zone);
            }
            return model;
        }

        @RequirePOST
        public ComboBoxModel doFillImageItems(@RelativePath("..") @QueryParameter String credentialsId, @RelativePath("..") @QueryParameter String region, @RelativePath("..") @QueryParameter Boolean intranetMaster) {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            ComboBoxModel model = new ComboBoxModel();
            if (StringUtils.isBlank(credentialsId) || StringUtils.isBlank(region)) {
                return model;
            }
            try {
                AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
                if (credentials == null) {
                    log.error("doFillImageItems error. credentials not found. region: {} credentialsId: {}", region, credentialsId);
                    return model;
                }
                AlibabaEcsClient client = new AlibabaEcsClient(credentials, region, intranetMaster);
                DescribeImagesRequest req = new DescribeImagesRequest();
                req.setOSType("linux");
                req.setStatus("Available");
                List<DescribeImagesResponse.Image> images = client.describeImages(req);
                for (DescribeImagesResponse.Image image : images) {
                    model.add(image.getImageId());
                }
            } catch (Exception ex) {
                // Ignore, as this may happen before the credentials are specified
            }
            return model;
        }

        @RequirePOST
        public ListBoxModel doFillVswItems(@RelativePath("..") @QueryParameter String credentialsId, @RelativePath("..") @QueryParameter String region, @RelativePath("..") @QueryParameter String vpc, @RelativePath("..") @QueryParameter Boolean intranetMaster, @QueryParameter String zone) {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            ListBoxModel model = new ListBoxModel();
            model.add("<not specified>", "");
            if (StringUtils.isBlank(credentialsId) || StringUtils.isBlank(region) || StringUtils.isBlank(zone)) {
                return model;
            }
            AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
            if (credentials == null) {
                log.error("doFillVswItems error. credentials not found. region: {} credentialsId: {}", region, credentialsId);
                return model;
            }
            AlibabaEcsClient client = new AlibabaEcsClient(credentials, region, intranetMaster);
            List<DescribeVSwitchesResponse.VSwitch> vSwitches = client.describeVsws(zone, vpc);
            for (DescribeVSwitchesResponse.VSwitch vsw : vSwitches) {
                model.add(vsw.getVSwitchId(), vsw.getVSwitchId());
            }
            return model;
        }

        @RequirePOST
        public ComboBoxModel doFillInstanceTypeItems(@RelativePath("..") @QueryParameter String credentialsId, @RelativePath("..") @QueryParameter String region, @RelativePath("..") @QueryParameter Boolean intranetMaster, @QueryParameter String zone) {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            ComboBoxModel items = new ComboBoxModel();
            if (StringUtils.isBlank(credentialsId) || StringUtils.isBlank(region) || StringUtils.isBlank(zone)) {
                return items;
            }
            AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
            if (credentials == null) {
                log.error("doFillInstanceTypeItems error. credentials not found. region: {} credentialsId: {}", region, credentialsId);
                return items;
            }
            AlibabaEcsClient client = new AlibabaEcsClient(credentials, region, intranetMaster);
            List<String> instanceTypes = client.describeInstanceTypes(zone, null, null);
            for (String instanceType : instanceTypes) {
                items.add(instanceType);
            }
            return items;
        }

        @RequirePOST
        public ListBoxModel doFillChargeTypeItems() {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            ListBoxModel model = new ListBoxModel();
            model.add("Spot Instance", SPOT_INSTANCE_CHARGE_TYPE);
            model.add("On Demand", ON_DEMAND_INSTANCE_CHARGE_TYPE);
            return model;
        }

        @RequirePOST
        public FormValidation doDryRunInstance(@RelativePath("..") @QueryParameter String credentialsId, @RelativePath("..") @QueryParameter Boolean intranetMaster, @RelativePath("..") @QueryParameter String region, @RelativePath("..") @QueryParameter String securityGroup, @RelativePath("..") @QueryParameter Boolean attachPublicIp, @QueryParameter String image, @QueryParameter String zone, @QueryParameter String vsw, @QueryParameter String instanceType, @QueryParameter String systemDiskCategory, @QueryParameter String systemDiskSize, @QueryParameter String chargeType) {
            log.info("doDryRunInstance info param credentialsId：{},  intranetMaster：{}, region：{}", credentialsId, intranetMaster, region);
            if (StringUtils.isBlank(credentialsId)) {
                return FormValidation.error("credentialsId is null");
            }
            AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
            if (credentials == null) {
                log.error("doDryRunInstance error. credentials not found. region: {} credentialsId: {}", region, credentialsId);
                return FormValidation.error("Credentials not found");
            }
            AlibabaEcsClient client = new AlibabaEcsClient(credentials, region, intranetMaster);
            RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
            if (SPOT_INSTANCE_CHARGE_TYPE.equals(chargeType)) {
                runInstancesRequest.setSpotStrategy("SpotAsPriceGo");
            }
            runInstancesRequest.setSysRegionId(region);
            runInstancesRequest.setImageId(image);
            runInstancesRequest.setSecurityGroupId(securityGroup);
            runInstancesRequest.setZoneId(zone);
            runInstancesRequest.setVSwitchId(vsw);
            runInstancesRequest.setInstanceType(instanceType);
            // dry run only support 1
            runInstancesRequest.setMinAmount(1);
            runInstancesRequest.setSystemDiskCategory(systemDiskCategory);
            runInstancesRequest.setSystemDiskSize(systemDiskSize);
            if (attachPublicIp) {
                runInstancesRequest.setInternetMaxBandwidthOut(10);
                runInstancesRequest.setInternetMaxBandwidthIn(10);
            }
            log.info("doDryRunInstance dryRun param runInstancesRequest:{}", JSON.toJSONString(runInstancesRequest));
            return client.druRunInstances(runInstancesRequest);
        }
    }

    @Override
    public String toString() {
        return "AlibabaEcsFollowerTemplate{" + "templateName='" + templateName + '\'' + '}';
    }
}
