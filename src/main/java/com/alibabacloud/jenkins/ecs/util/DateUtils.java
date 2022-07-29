package com.alibabacloud.jenkins.ecs.util;

import lombok.extern.slf4j.Slf4j;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


@Slf4j
public class DateUtils {
    private static final String pattern = "yyyy-MM-dd'T'HH:mm'Z'";

    public static Date parse(String time) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        try {
            DateFormat df = new SimpleDateFormat(pattern);
            df.setTimeZone(tz);
            return df.parse(time);
        } catch (ParseException e) {
            log.error("parse error. {}", time, e);
        }
        return null;
    }

    public static String format(Date time) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat(pattern);
        try {
            df.setTimeZone(tz);
            return df.format(time);
        } catch (Exception e) {
            log.error("format error. {}", time, e);
        }
        return null;
    }

}
