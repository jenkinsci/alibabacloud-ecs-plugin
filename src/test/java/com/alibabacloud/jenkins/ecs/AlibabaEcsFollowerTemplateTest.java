package com.alibabacloud.jenkins.ecs;

import java.util.ArrayList;
import java.util.List;

import com.alibabacloud.credentials.plugin.auth.AlibabaPrivateKey;
import com.alibabacloud.jenkins.ecs.client.AlibabaEcsClient;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Created by kunlun.ykl on 2020/8/26.
 */
@PowerMockIgnore(
        {"javax.crypto.*", "org.hamcrest.*", "javax.net.ssl.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({AlibabaCloud.class, AlibabaEcsClient.class,AlibabaPrivateKey.class})
public class AlibabaEcsFollowerTemplateTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Mock
    AlibabaCloud alibabaCloud;

    @Mock
    AlibabaEcsClient client;

    @Mock
    AlibabaPrivateKey key;
    List<String> instances  = new ArrayList<>();
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
        AlibabaEcsFollowerTemplate alibabaEcsFollowerTemplate = new AlibabaEcsFollowerTemplate("cn-beijing", "cn-beijing-a", "ecs.sn1.large", 1, "vsw-aaa", "",
            "", "/root", "seffaeff");

        alibabaEcsFollowerTemplate.setParent(alibabaCloud);

        List<String> instanceIds = alibabaEcsFollowerTemplate.provisionSpot(1);
        Assert.assertEquals(instanceIds,instances);
    }
}
