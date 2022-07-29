package com.alibabacloud.jenkins.ecs;

import com.alibabacloud.credentials.plugin.auth.AlibabaCredentials;
import com.alibabacloud.credentials.plugin.auth.AlibabaKeyPairUtils;
import com.alibabacloud.credentials.plugin.auth.AlibabaPrivateKey;
import com.alibabacloud.credentials.plugin.util.CredentialsHelper;
import com.alibabacloud.jenkins.ecs.client.AlibabaEcsClient;
import com.alibabacloud.jenkins.ecs.exception.AlibabaEcsException;
import com.alibabacloud.jenkins.ecs.util.AlibabaEcsFactory;
import com.alibabacloud.jenkins.ecs.util.CloudHelper;
import com.alibabacloud.jenkins.ecs.util.MinimumInstanceChecker;
import com.aliyuncs.ecs.model.v20140526.DescribeKeyPairsResponse.KeyPair;
import com.aliyuncs.ecs.model.v20140526.DescribeRegionsResponse.Region;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse.SecurityGroup;
import com.aliyuncs.ecs.model.v20140526.DescribeVpcsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeVpcsResponse.Vpc;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.model.*;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.security.Permission.CREATE;
import static hudson.security.Permission.UPDATE;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

/**
 * Created by kunlun.ykl on 2020/8/24.
 */
@Slf4j
public class AlibabaCloud extends Cloud {
    private static final Logger LOGGER = Logger.getLogger(AlibabaCloud.class.getName());
    private transient ReentrantLock followerCountingLock = new ReentrantLock();
    public static final String CLOUD_ID_PREFIX = "ALI-";

    public static final String DEFAULT_ECS_REGION = "cn-beijing";

    @CheckForNull
    private final String credentialsId;

    @CheckForNull
    private final String sshKey;

    private final String region;
    private final String vpc;
    private final String securityGroup;

    private boolean noDelayProvisioning;
    /**
     * 是否创建公网IP, 目前用NAT公网IP, 后续可以考虑使用EIP
     */
    private final boolean attachPublicIp;

    /**
     * 当前Jenkins Master 是否在VPC私网环境内, 默认为公网环境.
     * 如果是私网环境, 则调用阿里云SDK接口的endpoint都需要走vpc域名.
     */
    private final boolean intranetMaster;
    /**
     * 当前Cloud下所有Node的数量上限
     */
    private final int instanceCap;
    private final List<AlibabaEcsFollowerTemplate> templates;

    private transient AlibabaPrivateKey privateKey;

    private transient AlibabaEcsClient connection;

    @DataBoundConstructor
    public AlibabaCloud(String cloudName, String credentialsId, String sshKey, String region,
                        String vpc, String securityGroup, Boolean attachPublicIp,
                        Boolean intranetMaster, String instanceCapStr, List<AlibabaEcsFollowerTemplate> templates) {
        super(createCloudId(cloudName));
        this.credentialsId = credentialsId;
        this.sshKey = sshKey;
        this.region = region;
        this.intranetMaster = intranetMaster;
        this.vpc = vpc;
        this.securityGroup = securityGroup;
        this.attachPublicIp = attachPublicIp;
        if (null == templates) {
            this.templates = Lists.newArrayList();
        } else {
            this.templates = templates;
        }
        if (StringUtils.isBlank(instanceCapStr)) {
            this.instanceCap = Integer.MAX_VALUE;
        } else {
            this.instanceCap = Integer.parseInt(instanceCapStr);
        }
        readResolve();
    }

    @CheckForNull
    public AlibabaPrivateKey resolvePrivateKey() {
        if (privateKey != null) {
            return privateKey;
        }
        if (sshKey != null) {
            try {
                BasicSSHUserPrivateKey sshCredential = getSshCredential(sshKey);
                if (null == sshCredential) {
                    throw new AlibabaEcsException("sshCredential  is null");
                }
                AlibabaCredentials credentials = CredentialsHelper.getCredentials(credentialsId);
                KeyPair keyPair = AlibabaKeyPairUtils.find(sshCredential.getPrivateKey(), credentials, getRegion(), intranetMaster);
                privateKey = new AlibabaPrivateKey(sshCredential.getPrivateKey(), keyPair.getKeyPairName());
            } catch (Exception e) {
                log.error("resolvePrivateKey error. sshKey: {}", sshKey, e);
            }
        }
        return privateKey;
    }

    @CheckForNull
    private static BasicSSHUserPrivateKey getSshCredential(String id) {

        BasicSSHUserPrivateKey credential = CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(BasicSSHUserPrivateKey.class, // (1)
                (ItemGroup) null, null, Collections.emptyList()), CredentialsMatchers.withId(id));

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

    public boolean getAttachPublicIp() {
        return attachPublicIp;
    }

    public boolean getIntranetMaster() {
        return intranetMaster;
    }

    public String getRegion() {
        return region;
    }

    public boolean isNoDelayProvisioning() {
        return noDelayProvisioning;
    }

    public String getInstanceCapStr() {
        if (instanceCap == Integer.MAX_VALUE) return "";
        else return String.valueOf(instanceCap);
    }

    public int getInstanceCap() {
        return instanceCap;
    }

    @DataBoundSetter
    public void setNoDelayProvisioning(boolean noDelayProvisioning) {
        this.noDelayProvisioning = noDelayProvisioning;
    }

    public String getSecurityGroup() {
        return securityGroup;
    }

    public AlibabaPrivateKey getPrivateKey() {
        return privateKey;
    }

    /**
     * 由于
     */
    @Override
    public Collection<PlannedNode> provision(Label label, int excessWorkload) {
        log.info("provision start. label: {} excessWorkload: {}", label, excessWorkload);
        final Collection<AlibabaEcsFollowerTemplate> matchingTemplates = getTemplates(label);
        List<NodeProvisioner.PlannedNode> plannedNodes = new ArrayList<>();

        Jenkins jenkinsInstance = Jenkins.get();
        if (jenkinsInstance.isQuietingDown()) {
            LOGGER.log(Level.WARNING, "Not provisioning nodes, Jenkins instance is quieting down");
            return Collections.emptyList();
        } else if (jenkinsInstance.isTerminating()) {
            LOGGER.log(Level.WARNING, "Not provisioning nodes, Jenkins instance is terminating");
            return Collections.emptyList();
        }

        for (AlibabaEcsFollowerTemplate t : matchingTemplates) {
            try {
                LOGGER.log(Level.INFO, "{0}. Attempting to provision slave needed by excess workload of " + excessWorkload + " units", t);
                int number = Math.max(excessWorkload / t.getNumExecutors(), 1);
                final List<AlibabaEcsSpotFollower> slaves = getNewOrExistingAvailableSlave(t, number);

                if (slaves == null || slaves.isEmpty()) {
                    LOGGER.warning("Can't raise nodes for " + t);
                    continue;
                }
                for (final AlibabaEcsSpotFollower slave : slaves) {
                    if (slave == null) {
                        LOGGER.warning("Can't raise node for " + t);
                        continue;
                    }
                    PlannedNode plannedNode = CloudHelper.createPlannedNode(t, slave);
                    plannedNodes.add(plannedNode);
                    excessWorkload -= t.getNumExecutors();
                }
                CloudHelper.attachSlavesToJenkins(slaves, t);
                LOGGER.log(Level.INFO, "{0}. Attempting provision finished, excess workload: " + excessWorkload, t);
                if (excessWorkload <= 0) break;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, t + ". Exception during provisioning", e);
            }
        }
        LOGGER.log(Level.INFO, "We have now {0} computers, waiting for {1} more", new Object[]{jenkinsInstance.getComputers().length, plannedNodes.size()});
        return plannedNodes;
    }


    // httodo: UT
    private List<AlibabaEcsSpotFollower> getNewOrExistingAvailableSlave(AlibabaEcsFollowerTemplate t, int number) {
        try {
            followerCountingLock.lock();
            int possibleSlavesCount = getPossibleNewSlavesCount(t);
            if (possibleSlavesCount <= 0) {
                LOGGER.log(Level.INFO, "{0}. Cannot provision - no capacity for instances: " + possibleSlavesCount, t);
                return null;
            }

            try {
                if (number > possibleSlavesCount) {
                    LOGGER.log(Level.INFO, String.format("%d nodes were requested for the template %s, " + "but because of instance cap only %d can be provisioned", number, t, possibleSlavesCount));
                    number = possibleSlavesCount;
                }
                return t.provision(number, attachPublicIp);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, t + ". Exception during provisioning", e);
                return null;
            }
        } finally {
            followerCountingLock.unlock();
        }
    }


    private int getPossibleNewSlavesCount(AlibabaEcsFollowerTemplate t) {
        // 当前cloud下, 可新建的slave数量
        int instanceCnt = CloudHelper.getCntOfNodeByCloudName(getCloudName());
        int availableTotalSlave = instanceCap - instanceCnt;

        // 当前template下, 可新建的slave数量
        int instanceCntForTemplate = MinimumInstanceChecker.countCurrentNumberOfAgents(t);
        int availableTmpSlave = t.getInstanceCap() - instanceCntForTemplate;
        LOGGER.log(Level.INFO, "{0} cloudInstanceCap: {1} cloudInstanceCnt: {2}  availableTotalSlave: {3}  templateInstanceCap: {4}  instanceCntForTemplate: {5} availableTmpSlave: {6}", new Object[]{
                t.getTemplateName(),
                instanceCap,
                instanceCnt,
                availableTotalSlave,
                t.getInstanceCap(),
                instanceCntForTemplate,
                availableTmpSlave
        });
        return Math.min(availableTotalSlave, availableTmpSlave);
    }

    @Override
    public boolean canProvision(Label label) {
        log.debug("canProvision invoked");
        return !getTemplates(label).isEmpty();
    }

    public String getVpc() {
        return vpc;
    }

    private static String createCloudId(String cloudName) {
        return CLOUD_ID_PREFIX + cloudName.trim();
    }

    public String getCloudName() {
        return this.name.substring(CLOUD_ID_PREFIX.length());
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

    @RequirePOST
    public HttpResponse doProvision(@QueryParameter String templateName) throws Exception {
        checkPermission(PROVISION);
        log.info("doProvision invoked template: {}", templateName);
        AlibabaEcsFollowerTemplate alibabaEcsFollowerTemplate = getTemplate(templateName);
        if (alibabaEcsFollowerTemplate == null) {
            throw hudson.util.HttpResponses.error(SC_BAD_REQUEST, "No such template: " + templateName);
        }
        final Jenkins jenkinsInstance = Jenkins.get();
        if (jenkinsInstance.isQuietingDown()) {
            throw HttpResponses.error(SC_BAD_REQUEST, "Jenkins instance is quieting down");
        }
        if (jenkinsInstance.isTerminating()) {
            throw HttpResponses.error(SC_BAD_REQUEST, "Jenkins instance is terminating");
        }
        try {
            // check how many spare followers should provision
            List<AlibabaEcsSpotFollower> nodes = getNewOrExistingAvailableSlave(alibabaEcsFollowerTemplate, 1);
            if (nodes == null || nodes.isEmpty())
                throw hudson.util.HttpResponses.error(SC_BAD_REQUEST, "Cloud or Image instance cap would be exceeded for: " + templateName);
            // 将节点都加入jenkins里
            CloudHelper.attachSlavesToJenkins(nodes, alibabaEcsFollowerTemplate);
            return HttpResponses.redirectViaContextPath("/computer/" + nodes.get(0).getNodeName());
        } catch (Exception e) {
            throw HttpResponses.error(SC_INTERNAL_SERVER_ERROR, e);
        }
    }

    public void provision(AlibabaEcsFollowerTemplate t, int number) {
        Jenkins jenkinsInstance = Jenkins.get();
        if (jenkinsInstance.isQuietingDown()) {
            LOGGER.log(Level.WARNING, "Not provisioning nodes, Jenkins instance is quieting down");
            return;
        } else if (jenkinsInstance.isTerminating()) {
            LOGGER.log(Level.WARNING, "Not provisioning nodes, Jenkins instance is terminating");
            return;
        }
        try {
            LOGGER.log(Level.INFO, "{0}. Attempting to provision {1} slave(s)", new Object[]{t, number});
            final List<AlibabaEcsSpotFollower> nodes = getNewOrExistingAvailableSlave(t, number);

            if (nodes == null || nodes.isEmpty()) {
                LOGGER.warning("Can't raise nodes for " + t);
                return;
            }
            CloudHelper.attachSlavesToJenkins(nodes, t);
            LOGGER.log(Level.INFO, "{0}. Attempting provision finished", t);
            LOGGER.log(Level.INFO, "We have now {0} computers, waiting for {1} more", new Object[]{Jenkins.get().getComputers().length, number});
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, t + ". Exception during provisioning", e);
        }

    }


    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        public static final int defaultInstanceCapForCloud = 10;
        public static final boolean defaultNoDelayProvisioning = true;

        @Override
        public String getDisplayName() {
            return "Alibaba Cloud ECS";
        }

        public FormValidation doCheckCloudName(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            try {
                Jenkins.checkGoodName(value);
            } catch (Failure e) {
                return FormValidation.error(e.getMessage());
            }

            String cloudId = createCloudId(value);
            int found = 0;
            for (Cloud c : Jenkins.get().clouds) {
                if (c.name.equals(cloudId)) {
                    found++;
                }
            }
            if (found > 1) {
                return FormValidation.error("Duplicate Cloud Name");
            }
            return FormValidation.ok();
        }

        @RequirePOST
        public ListBoxModel doFillCredentialsIdItems() {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            return new StandardListBoxModel().withEmptySelection().withMatching(CredentialsMatchers.always(), CredentialsProvider.lookupCredentials(AlibabaCredentials.class, Jenkins.get(), ACL.SYSTEM, Collections.emptyList()));
        }

        public ListBoxModel doFillSshKeyItems(@QueryParameter String sshKey) {
            StandardListBoxModel result = new StandardListBoxModel();

            return result.includeMatchingAs(Jenkins.getAuthentication(), Jenkins.get(), BasicSSHUserPrivateKey.class, Collections.<DomainRequirement>emptyList(), CredentialsMatchers.always()).includeMatchingAs(ACL.SYSTEM, Jenkins.get(), BasicSSHUserPrivateKey.class, Collections.<DomainRequirement>emptyList(), CredentialsMatchers.always()).includeCurrentValue(sshKey);
        }

        @RequirePOST
        public FormValidation doTestConnection(@QueryParameter String credentialsId, @QueryParameter String sshKey, @QueryParameter String region, @QueryParameter Boolean intranetMaster) {
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
                log.error("doTestConnection error. credentials not found. region: {} credentialsId: {}", DEFAULT_ECS_REGION, credentialsId);
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
                KeyPair keyPair = AlibabaKeyPairUtils.find(sshCredential.getPrivateKey(), credentials, region, intranetMaster);
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
            model.add("华北1（青岛）", "cn-qingdao");
            model.add("华北2（北京）", "cn-beijing");
            model.add("华北3（张家口）", "cn-zhangjiakou");
            model.add("华北5（呼和浩特）", "cn-huhehaote");
            model.add("华北6（乌兰察布）", "cn-wulanchabu");
            model.add("华东1（杭州）", "cn-hangzhou");
            model.add("华东2（上海）", "cn-shanghai");
            model.add("华南1（深圳）", "cn-shenzhen");
            model.add("华南2（河源）", "cn-heyuan");
            model.add("华南3（广州）", "cn-guangzhou");
            model.add("西南1（成都）", "cn-chengdu");
            model.add("中国（香港）", "cn-hongkong");
            model.add("亚太东北 1 (东京)", "ap-northeast-1");
            model.add("韩国（首尔）", "ap-northeast-2");
            model.add("亚太东南 1 (新加坡)", "ap-southeast-1");
            model.add("亚太东南 2 (悉尼)", "ap-southeast-2");
            model.add("亚太东南 3 (吉隆坡)", "ap-southeast-3");
            model.add("菲律宾（马尼拉）", "ap-southeast-6");
            model.add("亚太东南 5 (雅加达)", "ap-southeast-5");
            model.add("亚太南部 1 (孟买)", "ap-south-1");
            model.add("泰国（曼谷）", "ap-southeast-7");
            model.add("美国东部 1 (弗吉尼亚)", "us-east-1");
            model.add("美国西部 1 (硅谷)", "us-west-1");
            model.add("英国 (伦敦)", "eu-west-1");
            model.add("中东东部 1 (迪拜)", "me-east-1");
            model.add("沙特（利雅得)", "me-central-1");
            model.add("欧洲中部 1 (法兰克福)", "eu-central-1");
            return model;
        }

        @RequirePOST
        public ListBoxModel doFillKeyPairItems() {
            Jenkins.get().checkPermission(Permission.CREATE);
            Jenkins.get().checkPermission(Permission.UPDATE);
            return new StandardListBoxModel().withEmptySelection().withMatching(CredentialsMatchers.always(), CredentialsProvider.lookupCredentials(AlibabaCredentials.class, Jenkins.get(), ACL.SYSTEM, Collections.emptyList()));
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
                    log.error("doFillVpcItems error. credentials not found. region: {} credentialsId: {}", region, credentialsId);
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
        public ListBoxModel doFillSecurityGroupItems(@QueryParameter String credentialsId, @QueryParameter String region, @QueryParameter String vpc, @QueryParameter Boolean intranetMaster) {
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
                    log.error("doFillSecurityGroupItems error. credentials not found. region: {} vpc: {} credentialsId: {}", region, vpc, credentialsId);
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
    }

    public List<AlibabaEcsFollowerTemplate> getTemplates() {
        return this.templates;
    }

    public AlibabaEcsFollowerTemplate getTemplate(String templateName) {
        for (AlibabaEcsFollowerTemplate alibabaEcsFollowerTemplate : templates) {
            if (alibabaEcsFollowerTemplate.getTemplateName().equals(templateName)) {
                return alibabaEcsFollowerTemplate;
            }
        }
        return null;
    }

    public Collection<AlibabaEcsFollowerTemplate> getTemplates(Label label) {
        List<AlibabaEcsFollowerTemplate> matchingTemplates = new ArrayList<>();
        for (AlibabaEcsFollowerTemplate t : templates) {
            if (label == null || label.matches(t.getLabelSet())) {
                matchingTemplates.add(t);
            }
        }
        return matchingTemplates;
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
                AlibabaCloud alibabaCloud = (AlibabaCloud) cloud;
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
