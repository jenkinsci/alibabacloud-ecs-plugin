package com.alibabacloud.jenkins.ecs;

import java.util.List;

import com.alibabacloud.credentials.plugin.auth.AlibabaCredentials;
import com.alibabacloud.credentials.plugin.util.CredentialsHelper;
import com.google.common.collect.Lists;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.BDDMockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertNotNull;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Created by kunlun.ykl on 2020/9/29.
 */
@PowerMockIgnore(
    {"javax.crypto.*", "org.hamcrest.*", "javax.net.ssl.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({CredentialsHelper.class})
public class AlibabaCloudTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void cloudTest() {
        String credentialsId = "sampleCredentialsId";
        String sshKey = null;
        AlibabaCredentials credentials = new AlibabaCredentials("ak",
            "sk");
        PowerMockito.mockStatic(CredentialsHelper.class);
        BDDMockito.given(CredentialsHelper.getCredentials(credentialsId)).willReturn(credentials);
        when(CredentialsHelper.getCredentials(credentialsId)).thenReturn(credentials);
        List<AlibabaEcsTag> tags = Lists.newArrayList();
//        AlibabaCloud cloud = new AlibabaCloud("testCloud", credentialsId, sshKey, "cn-beijing", "centos", "test-vpc",
//            "test-sg", "cn-beijing-a", "test-vsw", "ecs.c5.large", 1,
//            "", "", "", "", 20, false, false, tags ,"Spot");
//        assertNotNull(cloud);
    }
}
