package com.alibabacloud.jenkins.ecs;

import com.alibabacloud.credentials.plugin.auth.AlibabaPrivateKey;
import com.alibabacloud.jenkins.ecs.client.AlibabaEcsClient;
import com.alibabacloud.jenkins.ecs.enums.SystemDiskCategory;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Created by kunlun.ykl on 2020/8/26.
 */
@PowerMockIgnore(
        {"javax.crypto.*", "org.hamcrest.*", "javax.net.ssl.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({AlibabaCloud.class, AlibabaEcsClient.class, AlibabaPrivateKey.class})
public class AlibabaEcsFollowerTemplateTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Mock
    AlibabaCloud alibabaCloud;

    @Mock
    AlibabaEcsClient client;

    @Mock
    AlibabaPrivateKey key;
    List<String> instances = new ArrayList<>();

    @Before
    public void setup() {
        when(alibabaCloud.connect()).thenReturn(client);
        when(alibabaCloud.getPrivateKey()).thenReturn(key);
        when(key.getKeyPairName()).thenReturn("keyName");
        instances.add("hello");
        when(client.runInstances(any())).thenReturn(instances);
    }

    @Test
    public void provisionSpotTest() throws Exception {
        String remoteFS = "/root";
//        String cloudName = "alibaba-ecs-cloud";
        String labelString = "myCI";
        String initScript = "";
        String templateName = "alibaba-ecs-cloud-t1";
        int numExecutors = 1;
        String image = "ubuntu.vhd";
        int launchTimeout = 10000;
        List<AlibabaEcsTag> tags = Lists.newArrayList();
        String idleTerminationMinutes = "30";
        String zone = "cn-beijing-h";
        String chargeType = "PostPaid";
        String instanceType = "ecs.c6.large";
        SystemDiskCategory systemDiskCategory = SystemDiskCategory.cloud_essd;
        Integer systemDiskSize = 40;
        String vsw = "vsw-1";
        int minimumNumberOfInstances = 1;
        String instanceCapStr = "2";

        AlibabaEcsFollowerTemplate follower = new AlibabaEcsFollowerTemplate(templateName, image, zone, vsw, chargeType, instanceType, initScript, labelString, remoteFS, systemDiskCategory, systemDiskSize, minimumNumberOfInstances,
                idleTerminationMinutes, instanceCapStr, numExecutors + "", launchTimeout + "", tags, "userData");
        follower.setParent(alibabaCloud);
        List<String> instanceIds = follower.provisionSpot(1, true);
        Assert.assertEquals(instanceIds, instances);
    }
}
