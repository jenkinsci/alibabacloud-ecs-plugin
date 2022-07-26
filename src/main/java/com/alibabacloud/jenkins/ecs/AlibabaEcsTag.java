package com.alibabacloud.jenkins.ecs;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.aliyuncs.ecs.model.v20140526.RunInstancesRequest.Tag;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.antlr.v4.runtime.misc.NotNull;
import org.kohsuke.stapler.DataBoundConstructor;

public class AlibabaEcsTag extends AbstractDescribableImpl<AlibabaEcsTag> {
    private final String name;
    private final String value;

    public static final String TAG_NAME_CREATED_FROM = "CreatedFrom";
    public static final String TAG_VALUE_JENKINS_PLUGIN = "jenkins-plugin";

    @DataBoundConstructor
    public AlibabaEcsTag(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public static List<AlibabaEcsTag> formInstanceTags(List<Tag> serverTags) {
        if (null == serverTags)
            return null;
        LinkedList<AlibabaEcsTag> result = new LinkedList<>();
        for (Tag tag : serverTags) {
            result.add(new AlibabaEcsTag(tag.getKey(), tag.getValue()));
        }
        return result;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "AlibabaEcsTag: " + name + "->" + value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null)
            return false;
        if (this.getClass() != o.getClass())
            return false;

        AlibabaEcsTag other = (AlibabaEcsTag) o;
        if ((name == null && other.name != null) || (name != null && !name.equals(other.name)))
            return false;
        return (value != null || other.value == null) && (value == null || value.equals(other.value));
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AlibabaEcsTag> {
        @Override
        public @NotNull String getDisplayName() {
            return "";
        }

    }
}
