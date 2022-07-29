package com.alibabacloud.jenkins.ecs.util;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;

public class DateUtilsTest {
    @Test
    public void parseTest() {
        String createTime = "2017-12-10T04:04Z";
        Date parse = DateUtils.parse(createTime);
        System.out.println(parse);

        createTime = "2022-08-01T03:25Z";
        parse = DateUtils.parse(createTime);
        System.out.println(parse.getTime());
    }

    @Test
    public void formatTest() {
        String format = DateUtils.format(Calendar.getInstance().getTime());
        System.out.println(format);
        Date parse = DateUtils.parse(format);
        System.out.println(parse);
    }
}
