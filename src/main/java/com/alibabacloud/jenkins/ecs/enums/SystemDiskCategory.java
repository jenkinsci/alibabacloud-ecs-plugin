package com.alibabacloud.jenkins.ecs.enums;

public enum SystemDiskCategory {
    cloud_essd, cloud_ssd, cloud_efficiency, cloud;

    public static SystemDiskCategory fromValue(String value) {
        if (value != null && !"".equals(value)) {
            SystemDiskCategory[] var1 = values();
            int var2 = var1.length;

            for (int var3 = 0; var3 < var2; ++var3) {
                SystemDiskCategory enumEntry = var1[var3];
                if (enumEntry.toString().equals(value)) {
                    return enumEntry;
                }
            }
            throw new IllegalArgumentException("Cannot create enum from " + value + " value!");
        } else {
            throw new IllegalArgumentException("Value cannot be null or empty!");
        }
    }

}
