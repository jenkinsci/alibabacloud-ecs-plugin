package com.alibabacloud.jenkins.ecs;

import com.google.common.collect.Lists;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Created by kunlun.ykl on 2020/9/27.
 */
@PowerMockIgnore(
        {"javax.crypto.*", "org.hamcrest.*", "javax.net.ssl.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({AlibabaEcsSpotFollower.class, AlibabaEcsFollowerTemplate.class})
public class AlibabaEcsStepTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Mock
    private AlibabaCloud cl;

    @Mock
    AlibabaEcsSpotFollower follower;

    @Mock
    AlibabaEcsFollowerTemplate template;

    @Before
    public void setup() throws Exception {
        List<AlibabaEcsFollowerTemplate> templates = Lists.newArrayList();
        templates.add(template);
        when(cl.getDisplayName()).thenReturn("Alibaba Cloud ECS");
        when(cl.getCloudName()).thenReturn("Alibaba Cloud ECS");
        when(cl.getTemplates()).thenReturn(templates);
        when(cl.getTemplate(anyString())).thenReturn(template);
        r.jenkins.clouds.add(cl);

        when(follower.getNodeName()).thenReturn("nodeName");
        List<AlibabaEcsSpotFollower> slaves = Collections.singletonList(follower);
        when(template.provision(anyInt(), anyBoolean())).thenReturn(slaves);
    }

    @Test
    public void bootInstance() throws Exception {
        WorkflowJob boot = r.jenkins.createProject(WorkflowJob.class, "AlibabaCloudEcsTest");
        boot.setDefinition(new CpsFlowDefinition(
                " node('master') {\n" +
                        "    def X = alibabaEcs cloud: 'ALI-Alibaba Cloud ECS', template: 'cn-beijing-h-ecs.g5.large'\n" +
                        "}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(boot.scheduleBuild2(0));
        r.assertLogContains("SUCCESS", b);
    }

}
