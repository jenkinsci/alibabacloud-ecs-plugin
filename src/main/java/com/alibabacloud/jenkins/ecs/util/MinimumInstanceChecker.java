package com.alibabacloud.jenkins.ecs.util;

import com.alibabacloud.jenkins.ecs.AlibabaCloud;
import com.alibabacloud.jenkins.ecs.AlibabaEcsComputer;
import com.alibabacloud.jenkins.ecs.AlibabaEcsFollowerTemplate;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Queue;
import jenkins.model.Jenkins;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

@Restricted(NoExternalUse.class)
@Slf4j
public class MinimumInstanceChecker {

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "Needs to be overridden from tests")
    public static Clock clock = Clock.systemDefaultZone();

    private static Stream<Computer> agentsForTemplate(@NonNull AlibabaEcsFollowerTemplate agentTemplate) {
        return (Stream<Computer>) Arrays.stream(Jenkins.get().getComputers()).filter(computer -> computer instanceof AlibabaEcsComputer).filter(computer -> {
            AlibabaEcsFollowerTemplate computerTemplate = ((AlibabaEcsComputer) computer).getSlaveTemplate();
            return computerTemplate != null && Objects.equals(computerTemplate.getTemplateName(), agentTemplate.getTemplateName());
        });
    }

    public static int countCurrentNumberOfAgents(@NonNull AlibabaEcsFollowerTemplate agentTemplate) {
        return (int) agentsForTemplate(agentTemplate).count();
    }

    public static int countCurrentNumberOfSpareAgents(@NonNull AlibabaEcsFollowerTemplate agentTemplate) {
        return (int) agentsForTemplate(agentTemplate).filter(computer -> computer.countBusy() == 0).filter(computer -> computer.isOnline()).count();
    }

    public static int countCurrentNumberOfProvisioningAgents(@NonNull AlibabaEcsFollowerTemplate agentTemplate) {
        return (int) agentsForTemplate(agentTemplate).filter(computer -> computer.countBusy() == 0).filter(computer -> computer.isOffline()).filter(computer -> computer.isConnecting()).count();
    }

    public static int countQueueItemsForAgentTemplate(@NonNull AlibabaEcsFollowerTemplate agentTemplate) {
        return (int) Queue.getInstance().getBuildableItems().stream().map((Queue.Item item) -> item.getAssignedLabel()).filter(Objects::nonNull).filter((Label label) -> label.matches(agentTemplate.getLabelSet())).count();
    }


    public static void checkForMinimumInstances() {
        Jenkins.get().clouds.stream().filter(cloud -> cloud instanceof AlibabaCloud).map(cloud -> (AlibabaCloud) cloud).forEach(cloud -> {
            cloud.getTemplates().forEach(agentTemplate -> {
                // Minimum instances now have a time range, check to see
                // if we are within that time range and return early if not.
                int requiredMinAgents = agentTemplate.getMinimumNumberOfInstances();
//                        int requiredMinSpareAgents = agentTemplate.getMinimumNumberOfSpareInstances();
                int requiredMinSpareAgents = 0;
                int currentNumberOfAgentsForTemplate = countCurrentNumberOfAgents(agentTemplate);
                int currentNumberOfSpareAgentsForTemplate = countCurrentNumberOfSpareAgents(agentTemplate);
                int currentNumberOfProvisioningAgentsForTemplate = countCurrentNumberOfProvisioningAgents(agentTemplate);
                int currentBuildsWaitingForTemplate = countQueueItemsForAgentTemplate(agentTemplate);
                int provisionForMinAgents = 0;
                int provisionForMinSpareAgents = 0;

                // Check if we need to provision any agents because we
                // don't have the minimum number of agents
                provisionForMinAgents = requiredMinAgents - currentNumberOfAgentsForTemplate;
                if (provisionForMinAgents < 0) {
                    provisionForMinAgents = 0;
                }

                // Check if we need to provision any agents because we
                // don't have the minimum number of spare agents.
                // Don't double provision if minAgents and minSpareAgents are set.
                provisionForMinSpareAgents = (requiredMinSpareAgents + currentBuildsWaitingForTemplate) - (currentNumberOfSpareAgentsForTemplate + provisionForMinAgents + currentNumberOfProvisioningAgentsForTemplate);
                if (provisionForMinSpareAgents < 0) {
                    provisionForMinSpareAgents = 0;
                }
                int numberToProvision = provisionForMinAgents + provisionForMinSpareAgents;
                log.info("{} checkForMinimumInstances started. requiredMinAgents: {} " +
                                "currentNumberOfAgentsForTemplate: {} currentNumberOfSpareAgentsForTemplate: {} currentNumberOfProvisioningAgentsForTemplate: {} " +
                                "currentBuildsWaitingForTemplate: {} " +
                                "provisionForMinSpareAgents: {} numberToProvision: {}",
                        agentTemplate.getTemplateName(),
                        requiredMinAgents,
                        currentNumberOfAgentsForTemplate,
                        currentNumberOfSpareAgentsForTemplate,
                        currentNumberOfProvisioningAgentsForTemplate,
                        currentBuildsWaitingForTemplate,
                        provisionForMinSpareAgents,
                        numberToProvision);
                if (numberToProvision > 0) {
                    cloud.provision(agentTemplate, numberToProvision);
                }
            });
        });
    }

}
