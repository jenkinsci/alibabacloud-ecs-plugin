package com.alibabacloud.jenkins.ecs;

import java.util.List;
import java.util.Optional;

import com.aliyuncs.ecs.model.v20140526.DescribeInstancesResponse.Instance.VpcAttributes;
import org.apache.commons.lang.StringUtils;

import com.aliyuncs.ecs.model.v20140526.DescribeInstancesResponse.Instance;

import static com.alibabacloud.jenkins.ecs.ConnectionStrategy.*;


public class EcsHostAddressProvider {

    public static String windows(Instance instance, ConnectionStrategy strategy) {
        if (strategy.equals(PRIVATE_IP)) {
            return getPrivateIpAddress(instance);
        } else if (strategy.equals(PUBLIC_IP)) {
            return getPublicIpAddress(instance);
        } else {
            throw new IllegalArgumentException("Could not windows host address for strategy = " + strategy.toString());
        }
    }


    private static String getPublicIpAddress(Instance instance) {
        List<String> publicIpAddress = instance.getPublicIpAddress();
        return publicIpAddress.get(0);
    }


    private static String getPrivateIpAddress(Instance instance) {
        VpcAttributes vpcAttributes = instance.getVpcAttributes();
        List<String> privateIpAddress = vpcAttributes.getPrivateIpAddress();
        return privateIpAddress.get(0);
    }

    private static Optional<String> filterNonEmpty(String value) {
        return Optional.ofNullable(value).filter(StringUtils::isNotEmpty);
    }
}
