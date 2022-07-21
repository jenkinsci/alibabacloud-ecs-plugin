package com.alibabacloud.jenkins.ecs.util;

import com.alibabacloud.jenkins.ecs.client.AlibabaEcsClient;
import com.aliyuncs.auth.AlibabaCloudCredentials;
import com.aliyuncs.auth.BasicCredentials;
import lombok.extern.slf4j.Slf4j;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by kunlun.ykl on 2020/8/26.
 */
@Slf4j
public class AlibabaEcsFactoryTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void getInstanceTest() {
        AlibabaEcsFactory instance = AlibabaEcsFactory.getInstance();
        assertTrue(instance instanceof AlibabaEcsFactoryImpl);
    }

    @Test
    public void connectTest() {
        String ak = "<sample-ak>";
        String sk = "<sample-sk>";
        AlibabaCloudCredentials credentials = new BasicCredentials(ak, sk);
        String endpointName = "cn-hangzhou";
        AlibabaEcsClient connect = AlibabaEcsFactory.getInstance().connect(credentials, endpointName, true);
        assertNotNull(connect);
    }
}
