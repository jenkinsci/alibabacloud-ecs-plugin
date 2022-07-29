package com.alibabacloud.jenkins.ecs;

import java.io.IOException;
import java.io.PrintStream;

import javax.annotation.Nonnull;

import hudson.model.Descriptor.FormException;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Created by kunlun.ykl on 2020/8/25.
 */
@Ignore
public class AlibabaEcsUnixComputerLauncherTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void launchTest() throws IOException, FormException {
        AlibabaEcsUnixComputerLauncher launcher = new AlibabaEcsUnixComputerLauncher();
        AlibabaEcsSpotFollower follower = new AlibabaEcsSpotFollower("follower", "follower", launcher, "remoteFS", "ECS Spot", "",
            "echo hello",
            "sample", "efsefsf");
        r.jenkins.addNode(follower);

        SlaveComputer slaveComputer = new SlaveComputer(follower);
        TaskListener listener = new TaskListener() {
            @Nonnull
            @Override
            public PrintStream getLogger() {
                return System.out;
            }
        };
        launcher.launch(slaveComputer, listener);
    }
}
