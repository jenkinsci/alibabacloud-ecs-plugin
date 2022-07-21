package com.alibabacloud.jenkins.ecs.util;

import com.aliyuncs.auth.AlibabaCloudCredentials;
import hudson.ExtensionPoint;
import com.alibabacloud.jenkins.ecs.client.AlibabaEcsClient;
import jenkins.model.Jenkins;

/**
 * Created by kunlun.ykl on 2020/8/26.
 */
public interface AlibabaEcsFactory extends ExtensionPoint {
    static AlibabaEcsFactory getInstance() {
        AlibabaEcsFactory instance = null;
        for (AlibabaEcsFactory implementation : Jenkins.get().getExtensionList(AlibabaEcsFactory.class)) {
            if (instance != null) {
                throw new IllegalStateException("Multiple implementations of " + AlibabaEcsFactory.class.getName()
                    + " found. If overriding, please consider using ExtensionFilter");
            }
            instance = implementation;
        }
        return instance;
    }

    AlibabaEcsClient connect(AlibabaCloudCredentials credentials, String regionNo, Boolean intranetMaster);
}
