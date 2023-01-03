package com.alibabacloud.jenkins.ecs;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import com.alibabacloud.jenkins.ecs.enums.DataDiskCategory;
import com.alibabacloud.jenkins.ecs.enums.SystemDiskCategory;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Hudson.CloudList;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;

@Slf4j
public class EcsTemplateStepExecution extends AbstractStepExecutionImpl implements Serializable {

    private static final long serialVersionUID = -6139090518333729333L;

    private boolean addCloud = true;

    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "not needed on deserialization")
    private final transient EcsTemplateStep step;

    EcsTemplateStepExecution(EcsTemplateStep step, StepContext context) {
        super(context);
        this.step = step;
    }

    @Override
    public boolean start() throws Exception {
        SystemDiskCategory systemDiskCategory = SystemDiskCategory.fromValue(step.getSystemDiskCategory());
        DataDiskCategory dataDiskCategory;
        if (StringUtils.isNotBlank(step.getDataDiskCategory())) {
            dataDiskCategory = DataDiskCategory.fromValue(step.getDataDiskCategory());
        } else {
            dataDiskCategory = null;
        }

        String label = step.getLabels();
        String maxTotalUses;
        if (StringUtils.isBlank(step.getMaxTotalUses())) {
            maxTotalUses = "0";
        } else {
            maxTotalUses = step.getMaxTotalUses();
        }
        String templateName = null;
        if (StringUtils.isBlank(step.getCloudName())) {
            String name = UUID.randomUUID().toString().substring(0, 4);
            templateName = step.getTemplateName() + name;
            step.setTemplateName(templateName);
        } else {
            templateName = step.getTemplateName();
        }

        AlibabaEcsFollowerTemplate template = new AlibabaEcsFollowerTemplate(templateName, step.getImage(),
            step.getZone(), step.getVsw(), step.getChargeType(), step.getInstanceType(), step.getInitScript(), label,
            step.getRemoteFs(), systemDiskCategory, step.getSystemDiskSize(),
            Integer.parseInt(step.getMinimumNumberOfInstances()),
            step.getIdleTerminationMinutes(), step.getInstanceCap(), step.getNumExecutors(), step.getLaunchTimeout(),
            step.getTags(), step.getUserData(), step.getEcsType(), step.getConnectionStrategy(), step.getRemoteAdmin(),
            step.getDataDiskSize(), dataDiskCategory, step.getMountQuantity(), step.isMountDataDisk(),
            Integer.parseInt(maxTotalUses), null, step.getName());

        List<AlibabaEcsFollowerTemplate> templates = Lists.newArrayList(template);

        Jenkins jenkins = Jenkins.getInstance();
        AlibabaCloud alibabaCloud = null;
        String cloudName = null;
        //不指定cloudName，sshKey，credentialsId 在现有的cloud的新建template
        if (StringUtils.isBlank(step.getSshKey()) || StringUtils.isBlank(step.getCredentialsId())) {
            CloudList clouds = jenkins.clouds;
            for (Cloud cloud : clouds) {
                if (cloud instanceof AlibabaCloud) {
                    if (StringUtils.equals(step.getCloudName(), ((AlibabaCloud)cloud).getCloudName())) {
                        alibabaCloud = (AlibabaCloud)cloud;
                        break;
                    } else if (StringUtils.isBlank(step.getCloudName())) {
                        alibabaCloud = (AlibabaCloud)cloud;
                    }
                }
            }
            cloudName = alibabaCloud.getCloudName();
            alibabaCloud.setTemplates(template);
            this.addCloud = false;

        } else {
            cloudName = step.getCloudName();
            alibabaCloud = new AlibabaCloud(cloudName, step.getCredentialsId(), step.getSshKey(), step.getRegion(),
                step.getVpc(), step.getSecurityGroup(), step.getAttachPublicIp(), step.getIntranetMaster(),
                step.getInstanceCapStr(), templates);
            jenkins.clouds.add(alibabaCloud);

        }
        jenkins.save();

        List<AlibabaEcsSpotFollower> instances = alibabaCloud.createNodes(step.getTemplateName());
        if (CollectionUtils.isEmpty(instances)) {
            throw new IllegalArgumentException(
                "Error in Alibaba Cloud. Please review Alibaba ECS defined in Jenkins configuration.");
        }
        // save current Jenkins state to disk
        try {
            step.setCloudName(cloudName);
            BodyInvoker invoker = getContext().newBodyInvoker().withContexts(step).withCallback(
                new EcsTemplateCallback(step, template));
            //BodyInvoker invoker = getContext().newBodyInvoker().withContexts(step)
            invoker.start();
        } catch (Exception e) {
            log.info("EcsTemplateStepExecution start error.", e);
            throw e;
        }
        log.info("EcsTemplateStepExecution start success");

        return false;
    }

    private class EcsTemplateCallback extends BodyExecutionCallback.TailCall {

        private static final long serialVersionUID = 6043919968776851324L;

        private final EcsTemplateStep ecsTemplate;
        private final AlibabaEcsFollowerTemplate template;

        private EcsTemplateCallback(EcsTemplateStep ecsTemplate, AlibabaEcsFollowerTemplate template) {
            this.ecsTemplate = ecsTemplate;
            this.template = template;
        }

        @Override
        protected void finished(StepContext context) throws Exception {
            Jenkins jenkins = Jenkins.getInstance();
            CloudList clouds = jenkins.clouds;
            for (Cloud cloud : clouds) {
                if (cloud instanceof AlibabaCloud) {
                    if (StringUtils.equals(((AlibabaCloud)cloud).getCloudName(), ecsTemplate.getCloudName())
                        && addCloud) {
                        clouds.remove(cloud);
                    } else {
                        ((AlibabaCloud)cloud).getTemplates().remove(template);
                        jenkins.save();
                    }
                }
            }
        }

    }

}
