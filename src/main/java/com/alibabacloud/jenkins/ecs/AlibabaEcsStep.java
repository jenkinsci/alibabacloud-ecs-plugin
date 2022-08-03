package com.alibabacloud.jenkins.ecs;

import com.alibabacloud.jenkins.ecs.util.CloudHelper;
import com.aliyuncs.ecs.model.v20140526.DescribeInstancesResponse.Instance;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import lombok.extern.slf4j.Slf4j;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.alibabacloud.jenkins.ecs.AlibabaCloud.CLOUD_ID_PREFIX;

/**
 * Returns the instance provisioned. Returns the instance provisioned.
 * <p>
 * Used like:
 *
 * <pre>
 * node {
 *     def x = alibabaEcs cloud: 'ALI-myCloud', template: 'cn-beijing-a-ecs.c5.large'
 * }
 * </pre>
 * <p>
 * Created by kunlun.ykl on 2020/9/27.
 */
@Slf4j
public class AlibabaEcsStep extends Step {
    private String cloud;
    private String template;

    @DataBoundConstructor
    public AlibabaEcsStep(String cloud, String template) {
        this.cloud = cloud;
        this.template = template;
    }

    @Override
    public StepExecution start(StepContext stepContext) throws Exception {
        log.info("start AlibabaEcsStep execution");
        return new AlibabaEcsStep.Execution(this, stepContext);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "alibabaEcs";
        }

        @Override
        public String getDisplayName() {
            return "Cloud template provisioning";
        }

        public ListBoxModel doFillCloudItems() {
            ListBoxModel r = new ListBoxModel();
            r.add("", "");
            Jenkins.CloudList clouds = jenkins.model.Jenkins.get().clouds;
            for (Cloud cList : clouds) {
                if (cList instanceof AlibabaCloud) {
                    r.add(cList.getDisplayName(), cList.getDisplayName());
                }
            }
            return r;
        }

        public ListBoxModel doFillTemplateItems(@QueryParameter String cloud) {
            cloud = Util.fixEmpty(cloud);
            ListBoxModel r = new ListBoxModel();
            for (Cloud cList : jenkins.model.Jenkins.get().clouds) {
                if (cList.getDisplayName().equals(cloud)) {
                    List<AlibabaEcsFollowerTemplate> templates = ((AlibabaCloud) cList).getTemplates();
                    for (AlibabaEcsFollowerTemplate template : templates) {
                        r.add(template.getTemplateName(), template.getTemplateName());
                    }
                }
            }
            return r;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }

    }

    public static class Execution extends SynchronousNonBlockingStepExecution<Instance> {
        private final String cloud;
        private final String template;

        Execution(AlibabaEcsStep step, StepContext context) {
            super(context);
            this.cloud = step.cloud;
            this.template = step.template;
        }

        @Override
        protected Instance run() throws Exception {

            String cloudName = cloud.substring(CLOUD_ID_PREFIX.length());

            AlibabaCloud cl = CloudHelper.getCloud(cloudName);
            if (null == cl) {
                throw new IllegalArgumentException(
                        "Error in Alibaba Cloud. Please review Alibaba ECS settings in Jenkins configuration.");
            }

            AlibabaEcsFollowerTemplate t = cl.getTemplate(template);
            if (null == t) {
                throw new IllegalArgumentException(
                        "Error in Alibaba Cloud. Please review Alibaba ECS template defined in Jenkins configuration.");
            }
            List<AlibabaEcsSpotFollower> instances = t.provision(1, cl.getAttachPublicIp());
            if (instances == null) {
                throw new IllegalArgumentException(
                        "Error in Alibaba Cloud. Please review Alibaba ECS defined in Jenkins configuration.");
            }
            AlibabaEcsSpotFollower follower = instances.get(0);
            return CloudHelper.getInstanceWithRetry(follower);
        }
    }
}
