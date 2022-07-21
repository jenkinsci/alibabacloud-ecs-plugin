package com.alibabacloud.jenkins.ecs.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.alibabacloud.credentials.plugin.auth.AlibabaCredentials;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.ecs.model.v20140526.DescribeKeyPairsRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeKeyPairsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeKeyPairsResponse.KeyPair;
import com.aliyuncs.ecs.model.v20140526.DescribeVpcsResponse.Vpc;
import com.aliyuncs.exceptions.ClientException;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Created by kunlun.ykl on 2020/8/27.
 */
@Slf4j
@PowerMockIgnore(
        {"javax.crypto.*", "org.hamcrest.*", "javax.net.ssl.*", "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest(
        {DefaultAcsClient.class})
public class AlibabaEcsClientTest {
    @Mock
    DefaultAcsClient acsClient;

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void listKeyPairsTest() throws ClientException {
        String ak = "<your-access-key>";
        String sk = "<your-secret-key>";
        AlibabaCredentials credentials = new AlibabaCredentials(ak, sk);
        AlibabaEcsClient client = new AlibabaEcsClient(credentials, "cn-beijing", false);
        Whitebox.setInternalState(client, "client", acsClient);

        DescribeKeyPairsResponse acsResponse = new DescribeKeyPairsResponse();
        List<KeyPair> keyPairs = Lists.newArrayList();
        keyPairs.add(new KeyPair());
        acsResponse.setKeyPairs(keyPairs);
        when(acsClient.getAcsResponse(any(DescribeKeyPairsRequest.class))).thenReturn(acsResponse);

        List<DescribeKeyPairsResponse.KeyPair> keyPairsRes = client.describeKeyPairs("testKeyPairName", null);
        assertNotNull(keyPairsRes);
        assertEquals(1, keyPairsRes.size());
    }

    private AlibabaEcsClient getAlibabaEcsClient() {
        String ak = "<your-access-key>";
        String sk = "<your-secret-key>";
        AlibabaCredentials credentials = new AlibabaCredentials(ak, sk);
        AlibabaEcsClient client = new AlibabaEcsClient(credentials, "regionId", false);
        return client;
    }

    // This test is based on the testVswWithReverting method, read the instruction of it before you execute this test
    @Test
    public void createVpcAndVswTest() {
        AlibabaEcsClient client = getAlibabaEcsClient();
        String vpcCidrBlock;
        List<String> otherVswCidrBlocks;

        vpcCidrBlock = "172.16.0.0/12";
        otherVswCidrBlocks = Lists.newArrayList("172.16.0.0/16", "172.17.0.0/16");
        testVswWithReverting(vpcCidrBlock, otherVswCidrBlocks, client);

        vpcCidrBlock = "192.168.0.0/16";
        otherVswCidrBlocks = Lists.newArrayList("192.168.0.0/18", "192.168.64.0/18");
        testVswWithReverting(vpcCidrBlock, otherVswCidrBlocks, client);

        vpcCidrBlock = "10.0.0.0/8";
        otherVswCidrBlocks = Lists.newArrayList("10.0.0.0/16", "10.1.0.0/16");
        testVswWithReverting(vpcCidrBlock, otherVswCidrBlocks, client);

        vpcCidrBlock = "10.0.0.0/8";
        otherVswCidrBlocks = Lists.newArrayList("10.0.0.0/22", "10.0.4.0/22");
        testVswWithReverting(vpcCidrBlock, otherVswCidrBlocks, client);

        vpcCidrBlock = "192.168.0.0/16";
        otherVswCidrBlocks = Lists.newArrayList("192.168.0.0/17");
        testVswWithReverting(vpcCidrBlock, otherVswCidrBlocks, client);

        vpcCidrBlock = "172.16.0.0/12";
        otherVswCidrBlocks = Lists.newArrayList("172.16.0.0/17");
        testVswWithReverting(vpcCidrBlock, otherVswCidrBlocks, client);

        vpcCidrBlock = "10.0.0.0/8";
        otherVswCidrBlocks = Lists.newArrayList("10.0.0.0/17");
        testVswWithReverting(vpcCidrBlock, otherVswCidrBlocks, client);

        vpcCidrBlock = "172.16.0.0/12";
        otherVswCidrBlocks = Lists.newArrayList("172.16.128.0/18", "172.16.64.0/18", "172.16.32.0/19", "172.16.192.0/18");
        testVswWithReverting(vpcCidrBlock, otherVswCidrBlocks, client);

        vpcCidrBlock = "192.168.0.0/16";
        otherVswCidrBlocks = Lists.newArrayList("192.168.0.0/18", "192.168.144.0/20", "192.168.64.0/18", "192.168.128.0/20");
        testVswWithReverting(vpcCidrBlock, otherVswCidrBlocks, client);

        vpcCidrBlock = "10.0.0.0/8";
        otherVswCidrBlocks = Lists.newArrayList("10.0.0.0/18", "10.0.128.0/18", "10.0.64.0/18");
        testVswWithReverting(vpcCidrBlock, otherVswCidrBlocks, client);

        vpcCidrBlock = "10.0.0.0/8";
        otherVswCidrBlocks = Lists.newArrayList("10.0.128.0/18", "10.0.0.0/18", "10.0.192.0/18", "10.0.64.0/19", "10.1.0.0/16");
        testVswWithReverting(vpcCidrBlock, otherVswCidrBlocks, client);
    }

    // Test a specific case and delete the vsws and vpcs when finished
    // This method will delete all vpcs when a test case finished, please use it cautiously
    private void testVswWithReverting(String vpcCidrBlock, List<String> otherVswCidrBlocks, AlibabaEcsClient client) {
        log.info("Unit Test Case Begins");
        List<String> vswIds = new ArrayList<>();
        String vpcId = client.createVpc(vpcCidrBlock);
        if (StringUtils.isBlank(vpcId)) {
            log.warn("Create vpc {} failed, skip this test", vpcCidrBlock);
            return;
        }
        waitForStatusChange(3L);
        for (String vsw : otherVswCidrBlocks) {
            String vswId = client.createVsw("cn-beijing-a", vpcId, vsw);
            if (StringUtils.isBlank(vswId)) {
                log.warn("Blank vpcId for vpc {}, skip", vsw);
                continue;
            }
            vswIds.add(vswId);
        }
        waitForStatusChange(3L);
        for (String vswId : vswIds) {
            client.deleteVsw(vswId);
        }
        waitForStatusChange(3L);
        deleteAllVPCs(client);
        log.info("Unit Test Case Ends");
    }

    // Wait for some time to wait for the VPC's or VSW's status changing
    private void waitForStatusChange(long timeout) {
        try {
            TimeUnit.SECONDS.sleep(timeout);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testDescribeAvailableZones() {
        AlibabaEcsClient client = getAlibabaEcsClient();
        System.out.println(client.describeAvailableZones());
    }

    @Test
    public void createVswTest() {
        AlibabaEcsClient client = getAlibabaEcsClient();
        client.createVsw("<zone-id>", "<vpc-id>", "<cidr-block-address>");
    }

    @Test
    public void deleteVpcTest() {
        AlibabaEcsClient client = getAlibabaEcsClient();
        client.deleteVpc("vpc-2zetc2cyjbfwntmwrudcj");
    }

    @Test
    public void deleteAllVpcTest() {
        AlibabaEcsClient client = getAlibabaEcsClient();
        deleteAllVPCs(client);
    }

    // Delete all existing VPCs
    private void deleteAllVPCs(AlibabaEcsClient client) {
        for (Vpc vpc : client.describeVpcs()) {
            client.deleteVpc(vpc.getVpcId());
        }
    }
}
