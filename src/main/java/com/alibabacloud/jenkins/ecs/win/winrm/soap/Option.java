package com.alibabacloud.jenkins.ecs.win.winrm.soap;

public class Option {
    private final String name;
    private final String value;

    public Option(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public String getName() {
        return name;
    }
}
