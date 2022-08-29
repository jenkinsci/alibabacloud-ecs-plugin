package com.alibabacloud.jenkins.ecs;

import java.util.Objects;

import hudson.Extension;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class UnixData extends EcsTypeData {
    @DataBoundConstructor
    public UnixData() {}

    @Override
    public boolean isWindows() {
        return false;
    }

    @Override
    public boolean isUnix() {
        return true;
    }

    @Override
    public String getBootDelay() {
        return null;
    }

    @Override
    public int hashCode() {
        return Objects.hash();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj){
            return true;
        }
        if (obj == null){
            return false;
        }
        if (this.getClass() != obj.getClass()){
            return false;
        }
        return false;

    }

    @Extension
    public static class DescriptorImpl extends Descriptor<EcsTypeData> {
        @Override
        public String getDisplayName() {
            return "unix";
        }
    }
}
