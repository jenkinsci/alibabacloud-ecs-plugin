package com.alibabacloud.jenkins.ecs;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class EcsTemplateStep extends Step implements Serializable {
    private static final long serialVersionUID = 5588861066775717487L;
    //cloud 部分

    private String cloudName;

    private String credentialsId;

    private String sshKey;

    private String region;

    private String vpc;

    private String securityGroup;

    private boolean noDelayProvisioning;
    private Boolean attachPublicIp;
    private Boolean intranetMaster;

    private String instanceCapStr;

    //template 部分

    private String templateName;

    private String image;

    private String zone;

    private String instanceType;

    private String vsw;

    private String initScript;

    private String userData;

    private String labels;

    private String remoteFs;
    /**
     * 系统盘类型
     * <p>
     * cloud_efficiency：高效云盘。 cloud_ssd：SSD云盘。 cloud_essd：ESSD云盘。 cloud：普通云盘。
     * </p>
     */

    private String systemDiskCategory;

    /**
     * 系统盘大小, 以GB为单位, 取值范围：20~500。
     */

    private Integer systemDiskSize;

    /**
     * 创建出来的ECS实例标签列表
     */

    private List<AlibabaEcsTag> tags;

    /**
     * 数据盘大小 以GB为单位, 取值范围：20~500。
     */

    private String dataDiskSize;

    /**
     * 数据盘类型 同系统盘一致
     */

    private String dataDiskCategory;

    /**
     * 数据盘挂载数量
     */

    private String mountQuantity;

    /**
     * 是否挂载数据盘
     */

    private boolean mountDataDisk;

    /**
     * 实例付费类型
     */

    private String chargeType;

    /**
     * 当前Template下可以创建的Node数量上限
     */

    private String instanceCap;
    /**
     * 当前Template下可以创建的Node数量下限
     */

    private String minimumNumberOfInstances;

    private String launchTimeout;

    /**
     * 每个Node支持的Executor数量
     */

    private String numExecutors;

    private String idleTerminationMinutes;

    public String remoteAdmin;

    public ConnectionStrategy connectionStrategy;

    public EcsTypeData ecsType;

    public String maxTotalUses;

    //public String instanceNamePrefix;

    public String name;

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }

    public String getCloudName() {
        return cloudName;
    }

    @DataBoundSetter
    public void setCloudName(String cloudName) {
        this.cloudName = cloudName;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getSshKey() {
        return sshKey;
    }

    @DataBoundSetter
    public void setSshKey(String sshKey) {
        this.sshKey = sshKey;
    }

    public String getRegion() {
        return region;
    }

    @DataBoundSetter
    public void setRegion(String region) {
        this.region = region;
    }

    public String getVpc() {
        return vpc;
    }

    @DataBoundSetter
    public void setVpc(String vpc) {
        this.vpc = vpc;
    }

    public String getSecurityGroup() {
        return securityGroup;
    }

    @DataBoundSetter
    public void setSecurityGroup(String securityGroup) {
        this.securityGroup = securityGroup;
    }

    public boolean isNoDelayProvisioning() {
        return noDelayProvisioning;
    }

    @DataBoundSetter
    public void setNoDelayProvisioning(boolean noDelayProvisioning) {
        this.noDelayProvisioning = noDelayProvisioning;
    }

    public Boolean getAttachPublicIp() {
        return attachPublicIp;
    }

    @DataBoundSetter
    public void setAttachPublicIp(Boolean attachPublicIp) {
        this.attachPublicIp = attachPublicIp;
    }

    public Boolean getIntranetMaster() {
        return intranetMaster;
    }

    @DataBoundSetter
    public void setIntranetMaster(Boolean intranetMaster) {
        this.intranetMaster = intranetMaster;
    }

    public String getInstanceCapStr() {
        return instanceCapStr;
    }

    @DataBoundSetter
    public void setInstanceCapStr(String instanceCapStr) {
        this.instanceCapStr = instanceCapStr;
    }

    public String getImage() {
        return image;
    }

    @DataBoundSetter
    public void setImage(String image) {
        this.image = image;
    }

    public String getTemplateName() {
        return templateName;
    }

    @DataBoundSetter
    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getZone() {
        return zone;
    }

    @DataBoundSetter
    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getInstanceType() {
        return instanceType;
    }

    @DataBoundSetter
    public void setInstanceType(String instanceType) {
        this.instanceType = instanceType;
    }

    public String getVsw() {
        return vsw;
    }

    @DataBoundSetter
    public void setVsw(String vsw) {
        this.vsw = vsw;
    }

    public String getInitScript() {
        return initScript;
    }

    @DataBoundSetter
    public void setInitScript(String initScript) {
        this.initScript = initScript;
    }

    public String getUserData() {
        return userData;
    }

    @DataBoundSetter
    public void setUserData(String userData) {
        this.userData = userData;
    }

    public String getLabels() {
        return labels;
    }

    @DataBoundSetter
    public void setLabels(String labels) {
        this.labels = labels;
    }

    public String getRemoteFs() {
        return remoteFs;
    }

    @DataBoundSetter
    public void setRemoteFs(String remoteFs) {
        this.remoteFs = remoteFs;
    }

    public String getSystemDiskCategory() {
        return systemDiskCategory;
    }

    @DataBoundSetter
    public void setSystemDiskCategory(String systemDiskCategory) {
        this.systemDiskCategory = systemDiskCategory;
    }

    public Integer getSystemDiskSize() {
        return systemDiskSize;
    }

    @DataBoundSetter
    public void setSystemDiskSize(Integer systemDiskSize) {
        this.systemDiskSize = systemDiskSize;
    }

    public List<AlibabaEcsTag> getTags() {
        return tags;
    }

    @DataBoundSetter
    public void setTags(List<AlibabaEcsTag> tags) {
        this.tags = tags;
    }

    public String getDataDiskSize() {
        return dataDiskSize;
    }

    @DataBoundSetter
    public void setDataDiskSize(String dataDiskSize) {
        this.dataDiskSize = dataDiskSize;
    }

    public String getDataDiskCategory() {
        return dataDiskCategory;
    }

    @DataBoundSetter
    public void setDataDiskCategory(String dataDiskCategory) {
        this.dataDiskCategory = dataDiskCategory;
    }

    public String getMountQuantity() {
        return mountQuantity;
    }

    @DataBoundSetter
    public void setMountQuantity(String mountQuantity) {
        this.mountQuantity = mountQuantity;
    }

    public boolean isMountDataDisk() {
        return mountDataDisk;
    }

    @DataBoundSetter
    public void setMountDataDisk(boolean mountDataDisk) {
        this.mountDataDisk = mountDataDisk;
    }

    public String getChargeType() {
        return chargeType;
    }

    @DataBoundSetter
    public void setChargeType(String chargeType) {
        this.chargeType = chargeType;
    }

    public String getInstanceCap() {
        return instanceCap;
    }

    @DataBoundSetter
    public void setInstanceCap(String instanceCap) {
        this.instanceCap = instanceCap;
    }

    public String getMinimumNumberOfInstances() {
        return minimumNumberOfInstances;
    }

    @DataBoundSetter
    public void setMinimumNumberOfInstances(String minimumNumberOfInstances) {
        this.minimumNumberOfInstances = minimumNumberOfInstances;
    }

    public String getLaunchTimeout() {
        return launchTimeout;
    }

    @DataBoundSetter
    public void setLaunchTimeout(String launchTimeout) {
        this.launchTimeout = launchTimeout;
    }

    public String getNumExecutors() {
        return numExecutors;
    }

    @DataBoundSetter
    public void setNumExecutors(String numExecutors) {
        this.numExecutors = numExecutors;
    }

    public String getIdleTerminationMinutes() {
        return idleTerminationMinutes;
    }

    @DataBoundSetter
    public void setIdleTerminationMinutes(String idleTerminationMinutes) {
        this.idleTerminationMinutes = idleTerminationMinutes;
    }

    public String getRemoteAdmin() {
        return remoteAdmin;
    }

    @DataBoundSetter
    public void setRemoteAdmin(String remoteAdmin) {
        this.remoteAdmin = remoteAdmin;
    }

    public ConnectionStrategy getConnectionStrategy() {
        return connectionStrategy;
    }

    @DataBoundSetter
    public void setConnectionStrategy(ConnectionStrategy connectionStrategy) {
        this.connectionStrategy = connectionStrategy;
    }

    public EcsTypeData getEcsType() {
        return ecsType;
    }

    @DataBoundSetter
    public void setEcsType(EcsTypeData ecsType) {
        this.ecsType = ecsType;
    }

    public String getMaxTotalUses() {
        return maxTotalUses;
    }

    @DataBoundSetter
    public void setMaxTotalUses(String maxTotalUses) {
        this.maxTotalUses = maxTotalUses;
    }

    //public String getInstanceNamePrefix() {
    //    return instanceNamePrefix;
    //}
    //
    //@DataBoundSetter
    //public void setInstanceNamePrefix(String instanceNamePrefix) {
    //    this.instanceNamePrefix = instanceNamePrefix;
    //}

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    @DataBoundConstructor
    public EcsTemplateStep() {}

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new EcsTemplateStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Run.class, TaskListener.class)));
        }

        @Override
        public String getFunctionName() {
            return "EcsTemplate";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }

}
