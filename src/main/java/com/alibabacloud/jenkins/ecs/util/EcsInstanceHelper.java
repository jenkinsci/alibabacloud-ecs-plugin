package com.alibabacloud.jenkins.ecs.util;

import com.aliyuncs.ecs.model.v20140526.DescribeInstancesResponse;

import java.util.Date;

public class EcsInstanceHelper {

    public static long getStartCostInSeconds(DescribeInstancesResponse.Instance instance) {
        String creationTime = instance.getCreationTime();
        Date create = DateUtils.parse(creationTime);
        String startTime = instance.getStartTime();
        Date start = DateUtils.parse(startTime);
        if (create == null || start == null) {
            return 0;
        }
        return (start.getTime() - create.getTime()) / 1000;
    }


}
