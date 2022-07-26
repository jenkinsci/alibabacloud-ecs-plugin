package com.alibabacloud.jenkins.ecs;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.CheckForNull;

import com.alibaba.fastjson.JSON;
import com.alibabacloud.credentials.plugin.auth.AlibabaCredentials;
import com.alibabacloud.credentials.plugin.auth.AlibabaKeyPairUtils;
import com.alibabacloud.credentials.plugin.auth.AlibabaPrivateKey;
import com.alibabacloud.credentials.plugin.util.CredentialsHelper;
import com.alibabacloud.jenkins.ecs.client.AlibabaEcsClient;
import com.alibabacloud.jenkins.ecs.exception.AlibabaEcsException;
import com.alibabacloud.jenkins.ecs.util.AlibabaEcsFactory;
import com.alibabacloud.jenkins.ecs.util.CloudHelper;
import com.alibabacloud.jenkins.ecs.util.NetworkUtils;
import com.aliyuncs.ecs.model.v20140526.DescribeAvailableResourceRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeKeyPairsResponse.KeyPair;
import com.aliyuncs.ecs.model.v20140526.DescribeRegionsResponse.Region;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse.SecurityGroup;
import com.aliyuncs.ecs.model.v20140526.DescribeVSwitchesResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeVSwitchesResponse.VSwitch;
import com.aliyuncs.ecs.model.v20140526.DescribeVpcsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeVpcsResponse.Vpc;
import com.aliyuncs.ecs.model.v20140526.RunInstancesRequest;
import com.aliyuncs.ecs.model.v20140526.RunInstancesResponse;
import com.aliyuncs.exceptions.ClientException;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Failure;
import hudson.model.ItemGroup;
import hudson.model.Label;
import hudson.model.PeriodicWork;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import static hudson.security.Permission.CREATE;
import static hudson.security.Permission.UPDATE;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * Created by kunlun.ykl on 2020/8/24.
 */
@Slf4j
public class AlibabaCloud extends Cloud {

    private transient ReentrantLock followerCountingLock = new ReentrantLock();
    public static final String CLOUD_ID_PREFIX = "ecs-";
    public static final String DEFAULT_REMOTE_FS = "/root";
    public static final String DEFAULT_ECS_REGION = "cn-beijing";

    @CheckForNull
    private String credentialsId;

    @CheckForNull
    private String sshKey;

    private transient AlibabaPrivateKey privateKey;

    private String region;
    private String image;
    private String vpc;
    private String securityGroup;
    private String zone;
    private String vsw;
    private String instanceType;
    private String remoteFs;
    private String initScript;
    private String labelString;
    private int minimumNumberOfInstances;

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
     * 当前Jenkins Master 是否在VPC私网环境内, 默认为公网环境.
     * 如果是私网环境, 则调用阿里云SDK接口的endpoint都需要走vpc域名.
     */
    private Boolean intranetMaster = Boolean.FALSE;

    /**
     * 标签
     */
    private List<AlibabaEcsTag> tags;



    private List<AlibabaEcsFollowerTemplate> templates;

    private transient AlibabaEcsClient connection;



    public static final String CLOUD_ID = "Alibaba Cloud ECS";

    @DataBoundConstructor
    public AlibabaCloud(String name, String credentialsId, String sshKey, String region,
                        String image, String vpc, String securityGroup, String zone, String vsw, String instanceType,
                        int minimumNumberOfInstances, String initScript, String labelString, String remoteFs,
                        String systemDiskCategory, Integer systemDiskSize,
                        Boolean attachPublicIp, Boolean intranetMaster, List<AlibabaEcsTag>tags) {
        super(StringUtils.isBlank(name) ? CLOUD_ID : name);
        this.credentialsId = credentialsId;
        this.sshKey = sshKey;
        this.region = region;
        this.image = image;
        this.intranetMaster = intranetMaster;

        if (StringUtils.isNotBlank(remoteFs)) {
            this.remoteFs = remoteFs;
        } else {
            this.remoteFs = DEFAULT_REMOTE_FS;
        }
        connection = this.connect();
        if (StringUtils.isBlank(vpc)) {
            vpc = getOrCreateDefaultVpc(region);
        }
        this.vpc = vpc;
        if (StringUtils.isBlank(securityGroup)) {
            this.securityGroup = createDefaultSecurityGroup(region, vpc);
        } else {
            this.securityGroup = securityGroup;
        }
        // get default zone
        if (StringUtils.isBlank(zone)) {
            zone = getDefaultZone();
        }
        this.zone = zone;
        if (StringUtils.isBlank(vsw)) {
            vsw = getOrCreateDefaultVsw(vpc, zone);
        }
        this.vsw = vsw;
        if (StringUtils.isBlank(instanceType)) {
            instanceType = getDefaultInstanceType(region, zone);
        }
        this.instanceType = instanceType;
        this.minimumNumberOfInstances = minimumNumberOfInstances;
        this.initScript = initScript;
        this.labelString = labelString;
        this.systemDiskCategory = systemDiskCategory;
        this.systemDiskSize = systemDiskSize;
        this.attachPublicIp = attachPublicIp;
        if (CollectionUtils.isNotEmpty(tags)){
            this.tags = tags;
        }

        AlibabaEcsFollowerTemplate template = new AlibabaEcsFollowerTemplate(region, zone, instanceType,
            minimumNumberOfInstances, vsw,
            initScript, labelString, remoteFs, systemDiskCategory, systemDiskSize, attachPublicIp, tags);
        templates = Lists.newArrayList(template);
        readResolve();
    }

    private String getDefaultInstanceType(String region, String zone) {
        List<String> instanceTypes = connection.describeInstanceTypes(zone, 2, 8.0f);
        if (instanceTypes.isEmpty()) {
            // TODO: handle exception
            return null;
        }
        // TODO: choose instance type
        return instanceTypes.get(0);
    }

    private String getOrCreateDefaultVsw(String vpc, String zone) {
        List<VSwitch> vsws = connection.describeVsws("", vpc);
        List<String> otherSubCidrBlocks = Lists.newArrayList();
        for (VSwitch vswitch : vsws) {
            if (vswitch.getZoneId().equals(zone)) {
                return vswitch.getVSwitchId();
            }
            otherSubCidrBlocks.add(vswitch.getCidrBlock());
        }
        Vpc vpcInstance = null;
        if (StringUtils.isNotEmpty(vpc)) {
            vpcInstance = connection.describeVpc(vpc);
        }

        if (null == vpcInstance) {
            log.error("describe vpc is error");
            return "";
        }
        String cidrBlock = vpcInstance.getCidrBlock();
        String newCidrBlock = NetworkUtils.autoGenerateSubnet(cidrBlock, otherSubCidrBlocks);
        String vsw = connection.createVsw(zone, vpc, newCidrBlock);
        return vsw;
    }

    private String getDefaultZone() {
        List<String> availableZones = connection.describeAvailableZones();
        if (CollectionUtils.isEmpty(availableZones)) {
            //throw new ClientExcep
            // TODO: 这里确认异常类型
            return null;
        }
        return availableZones.get(0);
    }

    private String getOrCreateDefaultVpc(String region) {
        // 1. get vpc if exists
        List<Vpc> vpcs = connection.describeVpcs();
        if (CollectionUtils.isNotEmpty(vpcs)) {
            Vpc vpc = vpcs.get(0);
            log.info("getOrCreateDefaultVpc use default vpc. region: {} vpc: {}", region, vpc);
            return vpc.getVpcId();
        }

        // 2. create vpc if not exists, use default cidr 172.16.0.0/12
        String cidrBlock = "172.16.0.0/12";
        return connection.createVpc(cidrBlock);
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

    private String createDefaultSecurityGroup(String region, String vpcId) {
        // 1. get or create default sg
        String sgId;
        List<SecurityGroup> securityGroups = connection.describeSecurityGroups(vpcId);
        if (CollectionUtils.isEmpty(securityGroups)) {
            sgId = connection.createSecurityGroup(vpcId);
        } else {
            sgId = securityGroups.get(0).getSecurityGroupId();
        }

        if (StringUtils.isBlank(sgId)) {
            log.error("createSecurityGroup error. region: {} vpcId: {}", region, vpcId);
            return null;
        }
        // 2. acl for ssh
        boolean success = connection.authorizeSecurityGroup("tcp", "22/22", sgId, "0.0.0.0/0");
        if (!success) {
            log.error("authorizeSecurityGroup error. region: {} vpcId: {}", region, vpcId);
        }
        return sgId;
    }

    @CheckForNull
    public AlibabaPrivateKey resolvePrivateKey() {
        if (sshKey != null) {
            try {
                BasicSSHUserPrivateKey sshCredential = getSshCredential(sshKey);
                if (null == sshCredential) {
                    throw new AlibabaEcsException("sshCredential  is null");
                }
                AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
                KeyPair keyPair = AlibabaKeyPairUtils.find(sshCredential.getPrivateKey(), credentials,
                    getRegion(), intranetMaster);
                privateKey = new AlibabaPrivateKey(sshCredential.getPrivateKey(), keyPair.getKeyPairName());
            } catch (Exception e) {
                log.error("resolvePrivateKey error. sshKey: {}", sshKey, e);
            }
        }
        return privateKey;
    }

    @CheckForNull
    private static BasicSSHUserPrivateKey getSshCredential(String id) {

        BasicSSHUserPrivateKey credential = CredentialsMatchers.firstOrNull(
            CredentialsProvider.lookupCredentials(
                BasicSSHUserPrivateKey.class, // (1)
                (ItemGroup)null,
                null,
                Collections.emptyList()),
            CredentialsMatchers.withId(id));

        if (credential == null) {
            log.error("getSshCredential error. id: {}", id);
        }
        return credential;
    }

    // 注意, readResolve必须返回this
    protected Object readResolve() {
        this.followerCountingLock = new ReentrantLock();
        for (AlibabaEcsFollowerTemplate template : templates) {
            template.setParent(this);
        }
        resolvePrivateKey();
        connect();
        return this;
    }

    public String getZone() {
        return zone;
    }

    public String getVsw() {
        return vsw;
    }

    public String getInstanceType() {
        return instanceType;
    }

    public String getSystemDiskCategory() {
        return systemDiskCategory;
    }

    public Integer getSystemDiskSize() {
        return systemDiskSize;
    }

    public Boolean getAttachPublicIp() {
        return attachPublicIp;
    }

    public List<AlibabaEcsTag> getTags() {
        return tags;
    }

    public Boolean getIntranetMaster() {
        return intranetMaster;
    }

    public String getRegion() {
        if (region == null) {
            region = DEFAULT_ECS_REGION;
        }
        if (region.indexOf('_') > 0) {
            return region.replace('_', '-').toLowerCase(Locale.ENGLISH);
        }
        return region;
    }

    public String getImage() {
        return image;
    }

    public String getSecurityGroup() {
        return securityGroup;
    }

    public AlibabaPrivateKey getPrivateKey() {
        return privateKey;
    }

    @Override
    public Collection<PlannedNode> provision(Label label, int excessWorkload) {
        return null;
    }

    @Override
    public boolean canProvision(Label label) {
        log.debug("canProvision invoked");
        return true;
    }

    public String getVpc() {
        return vpc;
    }

    private static String createCloudId(String cloudName) {
        return CLOUD_ID_PREFIX + cloudName.trim();
    }

    public AlibabaEcsClient connect() {
        if (this.connection != null) {
            return connection;
        }
        return reconnectToAlibabaCloudEcs();
    }

    public AlibabaEcsClient reconnectToAlibabaCloudEcs() {
        synchronized (this) {
            connection = AlibabaEcsFactory.getInstance().connect(getCredentials(), getRegion(), intranetMaster);
            return connection;
        }
    }

    public AlibabaCredentials getCredentials() {
        return CredentialsHelper.getCredentials(credentialsId);
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @CheckForNull
    public String getSshKey() {
        return sshKey;
    }

    public String getRemoteFs() {
        return remoteFs;
    }

    @RequirePOST
    public HttpResponse doProvision(@QueryParameter String template) throws Exception {
        log.info("doProvision invoked template: {}", template);
        AlibabaEcsFollowerTemplate alibabaEcsFollowerTemplate = getTemplate();
        final Jenkins jenkinsInstance = Jenkins.get();
        if (jenkinsInstance.isQuietingDown()) {
            throw HttpResponses.error(SC_BAD_REQUEST, "Jenkins instance is quieting down");
        }
        if (jenkinsInstance.isTerminating()) {
            throw HttpResponses.error(SC_BAD_REQUEST, "Jenkins instance is terminating");
        }
        try {
            followerCountingLock.lock();
            // check how many spare followers should provision
            int aliveCount = 0;
            Computer[] computers = jenkinsInstance.getComputers();
            for (Computer computer : computers) {
                if (computer instanceof AlibabaEcsComputer) {
                    AlibabaEcsSpotFollower follower = ((AlibabaEcsComputer)computer).getNode();
                    if (null == follower) {
                        continue;
                    }
                    String templateId = follower.getTemplateId();
                    if (StringUtils.isEmpty(templateId)) {
                        continue;
                    }
                    if (template.equals(templateId)) {
                        aliveCount++;
                    }
                }
            }
            int provisionCount = alibabaEcsFollowerTemplate.getMinimumNumberOfInstances() - aliveCount;
            if (provisionCount <= 0) {
                log.info("no need provision. minimumNumberOfInstances:{} aliveCount:{}",
                    alibabaEcsFollowerTemplate.getMinimumNumberOfInstances(), aliveCount);
                return HttpResponses.redirectViaContextPath("/computer/");
            }
            List<AlibabaEcsSpotFollower> provision = alibabaEcsFollowerTemplate.provision(provisionCount);
            if (CollectionUtils.isEmpty(provision)) {
                throw HttpResponses.error(SC_BAD_REQUEST, "followerTemplate.provision error");
            }
            // 将节点都加入jenkins里
            for (AlibabaEcsSpotFollower alibabaEcsSpotFollower : provision) {
                jenkinsInstance.addNode(alibabaEcsSpotFollower);
            }
        } finally {
            followerCountingLock.unlock();
        }
        return HttpResponses.redirectViaContextPath("/computer/");
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return "Alibaba Cloud ECS";
        }

        @RequirePOST
        public FormValidation doCheckCloudName(@QueryParameter String value) {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            try {
                Jenkins.checkGoodName(value);
            } catch (Failure e) {
                return FormValidation.error(e.getMessage());
            }

            //String cloudId = createCloudId(value);
            int found = 0;
            for (Cloud c : Jenkins.get().clouds) {
                if (c.name.equals(value)) {
                    found++;
                }
            }
            if (found > 1) {
                return FormValidation.error("Duplicate Cloud Name");
            }
            return FormValidation.ok();
        }

        @Override
        public boolean configure(final StaplerRequest req, final JSONObject formData) throws FormException {
            log.info("configure invoked");
            req.bindJSON(this, formData);
            save();
            return super.configure(req, formData);
        }

        @RequirePOST
        public ListBoxModel doFillCredentialsIdItems() {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            return new StandardListBoxModel()
                .withEmptySelection()
                .withMatching(
                    CredentialsMatchers.always(),
                    CredentialsProvider.lookupCredentials(AlibabaCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM,
                        Collections.emptyList()));
        }

        public ListBoxModel doFillSshKeyItems(@QueryParameter String sshKey) {
            StandardListBoxModel result = new StandardListBoxModel();

            return result
                .includeMatchingAs(Jenkins.getAuthentication(), Jenkins.get(), BasicSSHUserPrivateKey.class,
                    Collections.<DomainRequirement>emptyList(), CredentialsMatchers.always())
                .includeMatchingAs(ACL.SYSTEM, Jenkins.get(), BasicSSHUserPrivateKey.class,
                    Collections.<DomainRequirement>emptyList(), CredentialsMatchers.always())
                .includeCurrentValue(sshKey);
        }

        @RequirePOST
        public FormValidation doTestConnection(@QueryParameter String credentialsId, @QueryParameter String sshKey,
                                               @QueryParameter String region, @QueryParameter Boolean intranetMaster) {
            if (!Jenkins.get().hasPermission(CREATE) && Jenkins.get().hasPermission(UPDATE)) {
                return FormValidation.error("permission is error");
            }

            if (StringUtils.isBlank(credentialsId)) {
                return FormValidation.error("Credentials not specified");
            }
            if (StringUtils.isBlank(region)) {
                region = DEFAULT_ECS_REGION;
            }
            // 1. 校验AK/SK是否正确
            AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
            if (credentials == null) {
                log.error(
                    "doTestConnection error. credentials not found. region: {} credentialsId: {}",
                    DEFAULT_ECS_REGION, credentialsId);
                return FormValidation.error("Credentials not found");
            }
            AlibabaEcsClient client = new AlibabaEcsClient(credentials, region, intranetMaster);
            List<Region> regions = client.describeRegions();
            if (CollectionUtils.isEmpty(regions)) {
                return FormValidation.error("Illegal ak/sk: " + credentialsId);
            }

            if (StringUtils.isBlank(sshKey)) {
                return FormValidation.error("SSH PrivateKey not specified");
            }

            try {
                // 2. 校验SSHKey是否存在
                BasicSSHUserPrivateKey sshCredential = getSshCredential(sshKey);
                KeyPair keyPair = AlibabaKeyPairUtils.find(sshCredential.getPrivateKey(), credentials,
                    region, intranetMaster);
                if (null == keyPair) {
                    return FormValidation.error("Illegal SSH PrivateKey: " + sshKey);
                }
                return FormValidation.ok("connection ok");
            } catch (Exception e) {
                log.error("SSH PrivateKey validate error", e);
            }
            return FormValidation.error("SSH PrivateKey validate error");
        }

        @RequirePOST
        public ListBoxModel doFillRegionItems(@QueryParameter String credentialsId) {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            ListBoxModel model = new ListBoxModel();
            try {
                AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
                if (credentials == null) {
                    log.error(
                        "doFillImageItems error. credentials not found. region: {} credentialsId: {}",
                        DEFAULT_ECS_REGION, credentialsId);
                    return model;
                }
                model.add("华北1（青岛）","cn-qingdao");
                model.add("华北2（北京）","cn-beijing");
                model.add( "华北3（张家口）","cn-zhangjiakou");
                model.add("华北5（呼和浩特）","cn-huhehaote");
                model.add("华北6（乌兰察布）","cn-wulanchabu");
                model.add("华东1（杭州）","cn-hangzhou");
                model.add("华东2（上海）","cn-shanghai");
                model.add("华南1（深圳）","cn-shenzhen");
                model.add( "华南2（河源）","cn-heyuan");
                model.add("华南3（广州）","cn-guangzhou");
                model.add("西南1（成都）","cn-chengdu");
                model.add("中国（香港）", "cn-hongkong");
                model.add("亚太东北 1 (东京)","ap-northeast-1");
                model.add( "韩国（首尔）","ap-northeast-2");
                model.add( "亚太东南 1 (新加坡)","ap-southeast-1");
                model.add("亚太东南 2 (悉尼)","ap-southeast-2");
                model.add("亚太东南 3 (吉隆坡)","ap-southeast-3");
                model.add("菲律宾（马尼拉）","ap-southeast-6");
                model.add("亚太东南 5 (雅加达)","ap-southeast-5");
                model.add("亚太南部 1 (孟买)","ap-south-1");
                model.add("泰国（曼谷）","ap-southeast-7");
                model.add("美国东部 1 (弗吉尼亚)","us-east-1");
                model.add("美国西部 1 (硅谷)","us-west-1");
                model.add( "英国 (伦敦)","eu-west-1");
                model.add("中东东部 1 (迪拜)","me-east-1");
                model.add("沙特（利雅得)","me-central-1");
                model.add( "欧洲中部 1 (法兰克福)","eu-central-1");
            } catch (Exception ex) {
                // Ignore, as this may happen before the credentials are specified
            }
            return model;
        }

        /**
        @RequirePOST
        public ListBoxModel doFillImageItems(@QueryParameter String credentialsId, @QueryParameter String region, @QueryParameter Boolean intranetMaster) {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            ListBoxModel model = new ListBoxModel();
            if (StringUtils.isBlank(credentialsId) || StringUtils.isBlank(region)) {
                return model;
            }
            try {
                AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
                if (credentials == null) {
                    log.error(
                        "doFillImageItems error. credentials not found. region: {} credentialsId: {}",
                        region, credentialsId);
                    return model;
                }
                AlibabaEcsClient client = new AlibabaEcsClient(credentials, region, intranetMaster);
                DescribeImagesRequest req = new DescribeImagesRequest();
                req.setOSType("linux");
                req.setStatus("Available");
                List<Image> images = client.describeImages(req);
                for (DescribeImagesResponse.Image image : images) {
                    model.add(image.getImageName(), image.getImageId());
                }
            } catch (Exception ex) {
                // Ignore, as this may happen before the credentials are specified
            }
            return model;
        }
         **/

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
        public ListBoxModel doFillKeyPairItems() {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            return new StandardListBoxModel()
                .withEmptySelection()
                .withMatching(
                    CredentialsMatchers.always(),
                    CredentialsProvider.lookupCredentials(AlibabaCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM,
                        Collections.emptyList()));
        }

        @RequirePOST
        public ListBoxModel doFillVpcItems(@QueryParameter String credentialsId, @QueryParameter String region, @QueryParameter Boolean intranetMaster) {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            ListBoxModel model = new ListBoxModel();
            model.add("<not specified>", "");
            if (StringUtils.isBlank(credentialsId) || StringUtils.isBlank(region)) {
                return model;
            }
            try {
                AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
                if (credentials == null) {
                    log.error(
                        "doFillVpcItems error. credentials not found. region: {} credentialsId: {}",
                        region, credentialsId);
                    return model;
                }
                AlibabaEcsClient client = new AlibabaEcsClient(credentials, region, intranetMaster);
                List<Vpc> vpcs = client.describeVpcs();

                for (DescribeVpcsResponse.Vpc vpc : vpcs) {
                    model.add(vpc.getVpcId(), vpc.getVpcId());
                }
            } catch (Exception ex) {
                // Ignore, as this may happen before the credentials are specified
            }

            return model;
        }

        @RequirePOST
        public ListBoxModel doFillSecurityGroupItems(@QueryParameter String credentialsId, @QueryParameter String region,
                                                     @QueryParameter String vpc, @QueryParameter Boolean intranetMaster) {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            ListBoxModel model = new ListBoxModel();
            model.add("<not specified>", "");
            if (StringUtils.isBlank(credentialsId) || StringUtils.isBlank(region) || StringUtils.isBlank(vpc)) {
                return model;
            }
            try {
                AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
                if (credentials == null) {
                    log.error(
                        "doFillSecurityGroupItems error. credentials not found. region: {} vpc: {} credentialsId: {}",
                        region, vpc, credentialsId);
                    return model;
                }
                AlibabaEcsClient client = new AlibabaEcsClient(credentials, region, intranetMaster);
                List<SecurityGroup> securityGroups = client.describeSecurityGroups(vpc);
                for (DescribeSecurityGroupsResponse.SecurityGroup securityGroup : securityGroups) {
                    model.add(securityGroup.getSecurityGroupId(), securityGroup.getSecurityGroupId());
                }
            } catch (Exception e) {
                log.error("DescribeSecurityGroups error. region: {} vpc: {}", region, vpc, e);
            }
            return model;
        }

        @RequirePOST
        public ListBoxModel doFillZoneItems(
            @QueryParameter String credentialsId, @QueryParameter String region, @QueryParameter Boolean intranetMaster) {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            ListBoxModel model = new ListBoxModel();
            model.add("<not specified>", "");
            if (StringUtils.isBlank(credentialsId) || StringUtils.isBlank(region)) {
                return model;
            }
            AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
            if (credentials == null) {
                log.error(
                    "doFillZoneItems error. credentials not found. region: {} credentialsId: {}",
                    region, credentialsId);
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
        public ListBoxModel doFillVswItems(
            @QueryParameter String credentialsId, @QueryParameter String region, @QueryParameter String vpc,
            @QueryParameter String zone, @QueryParameter Boolean intranetMaster) {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            ListBoxModel model = new ListBoxModel();
            model.add("<not specified>", "");
            if (StringUtils.isBlank(credentialsId) || StringUtils.isBlank(region) || StringUtils.isBlank(zone)) {
                return model;
            }
            AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
            if (credentials == null) {
                log.error(
                    "doFillVswItems error. credentials not found. region: {} credentialsId: {}",
                    region, credentialsId);
                return model;
            }
            AlibabaEcsClient client = new AlibabaEcsClient(credentials, region, intranetMaster);
            List<VSwitch> vSwitches = client.describeVsws(zone, vpc);
            for (DescribeVSwitchesResponse.VSwitch vsw : vSwitches) {
                model.add(vsw.getVSwitchId(), vsw.getVSwitchId());
            }
            return model;
        }

        @RequirePOST
        public ListBoxModel doFillInstanceTypeItems(@QueryParameter String credentialsId, @QueryParameter String region,
                                                    @QueryParameter String zone, @QueryParameter Boolean intranetMaster) {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            ListBoxModel items = new ListBoxModel();
            items.add("<not specified>", "");
            if (StringUtils.isBlank(credentialsId) || StringUtils.isBlank(region) || StringUtils.isBlank(zone)) {
                return items;
            }
            AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
            if (credentials == null) {
                log.error(
                    "doFillInstanceTypeItems error. credentials not found. region: {} credentialsId: {}",
                    region, credentialsId);
                return items;
            }
            AlibabaEcsClient client = new AlibabaEcsClient(credentials, region, intranetMaster);
            DescribeAvailableResourceRequest resourceRequest = new DescribeAvailableResourceRequest();
            resourceRequest.setZoneId(zone);
            resourceRequest.setCores(2);
            resourceRequest.setMemory(8.0f);
            List<String> instanceTypes = client.describeInstanceTypes(zone, 2, 8.0f);
            for (String instanceType : instanceTypes) {
                items.add(instanceType, instanceType);
            }
            return items;
        }

        @RequirePOST
        public FormValidation doDryRun(@QueryParameter String credentialsId, @QueryParameter String sshKey,
                                       @QueryParameter String region) {
            // TODO: use param to dryrun create instance
            return FormValidation.ok();
        }

        @RequirePOST
        public FormValidation doDryRunInstance(@QueryParameter String credentialsId, @QueryParameter Boolean intranetMaster,
                                                     @QueryParameter String region, @QueryParameter String image, @QueryParameter String vpc, @QueryParameter String securityGroup,
                                                     @QueryParameter String zone, @QueryParameter String vsw, @QueryParameter String instanceType,
                                                     @QueryParameter  Integer minimumNumberOfInstances, @QueryParameter String initScript, @QueryParameter String labelString, @QueryParameter String remoteFs,
                                                     @QueryParameter  String systemDiskCategory, @QueryParameter String systemDiskSize, @QueryParameter Boolean attachPublicIp) {
            log.info("doDryRunInstance info param credentialsId：{},  intranetMaster：{}, region：{}",credentialsId, intranetMaster, region);
            if (StringUtils.isBlank(credentialsId)) {
                return FormValidation.error("credentialsId is null");
            }
            AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
            if (credentials == null) {
                log.error(
                        "doDryRunInstance error. credentials not found. region: {} credentialsId: {}",
                        region, credentialsId);
                return FormValidation.error("Credentials not found");
            }
            AlibabaEcsClient client = new AlibabaEcsClient(credentials, region, intranetMaster);
            RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
            runInstancesRequest.setRegionId(region);
            runInstancesRequest.setImageId(image);
            runInstancesRequest.setSecurityGroupId(securityGroup);
            runInstancesRequest.setZoneId(zone);
            runInstancesRequest.setVSwitchId(vsw);
            runInstancesRequest.setInstanceType(instanceType);
            runInstancesRequest.setMinAmount(minimumNumberOfInstances);
            runInstancesRequest.setSystemDiskCategory(systemDiskCategory);
            runInstancesRequest.setSystemDiskSize(systemDiskSize);
            if (attachPublicIp) {
                runInstancesRequest.setInternetMaxBandwidthOut(10);
            }
            log.info("doDryRunInstance dryRun param runInstancesRequest:{}", JSON.toJSONString(runInstancesRequest));
            return client.druRunInstances(runInstancesRequest);
        }
    }

    public List<AlibabaEcsFollowerTemplate> getTemplates() {
        return this.templates;
    }

    public AlibabaEcsFollowerTemplate getTemplate(String template) {
        for (AlibabaEcsFollowerTemplate alibabaEcsFollowerTemplate : templates) {
            if (alibabaEcsFollowerTemplate.getTemplateId().equals(template)) {
                return alibabaEcsFollowerTemplate;
            }
        }
        return null;
    }

    public AlibabaEcsFollowerTemplate getTemplate() {
        return getTemplate(CloudHelper.getTemplateId(zone, instanceType));
    }

    @Extension
    public static class AlibabaEcsConnectionUpdater extends PeriodicWork {
        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.SECONDS.toMillis(60);
        }

        @Override
        protected void doRun() throws Exception {
            Jenkins instance = Jenkins.get();
            if (instance.clouds == null) {
                log.warn("no clouds found, AlibabaEcsConnectionUpdater skipped");
                return;
            }
            for (Cloud cloud : instance.clouds) {
                if (!(cloud instanceof AlibabaCloud)) {
                    continue;
                }
                AlibabaCloud alibabaCloud = (AlibabaCloud)cloud;
                log.info("Checking Alibaba Cloud Connection on: {}", alibabaCloud.getDisplayName());
                List<Region> regions = Lists.newArrayList();
                if (alibabaCloud.connection != null) {
                    regions = alibabaCloud.connection.describeRegions();
                }
                if (CollectionUtils.isEmpty(regions)) {
                    log.warn("Reconnecting to Alibaba Cloud on: {}", alibabaCloud.getDisplayName());
                    alibabaCloud.reconnectToAlibabaCloudEcs();
                }
            }
        }
    }
}
