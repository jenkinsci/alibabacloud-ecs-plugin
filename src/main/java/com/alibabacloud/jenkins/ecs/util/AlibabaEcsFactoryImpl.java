package com.alibabacloud.jenkins.ecs.util;

import com.aliyuncs.auth.AlibabaCloudCredentials;
import hudson.Extension;
import com.alibabacloud.jenkins.ecs.client.AlibabaEcsClient;

/**
 * Created by kunlun.ykl on 2020/8/26.
 */
@Extension
public class AlibabaEcsFactoryImpl implements AlibabaEcsFactory {

    @Override
    public AlibabaEcsClient connect(AlibabaCloudCredentials credentials, String regionNo, Boolean intranetMaster) {
        return new AlibabaEcsClient(credentials, regionNo, intranetMaster);
    }
}
