package com.alibabacloud.jenkins.ecs;

import jenkins.model.Jenkins;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author Alicia Doblas
 */
@RunWith(MockitoJUnitRunner.class)
public class EcsTemplateStepExecutionTest {
    @Mock
    Jenkins jenkins;

    @Test
    public void tets() throws Exception {
        EcsTemplateStep step = new EcsTemplateStep();
        step.setSystemDiskCategory("cloud_essd_PL0");
        step.setDataDiskCategory("cloud_essd_PL0");
        step.setNewDataDisk(true);
        step.setDataDiskSize("1");
        step.setMountQuantity("1");
        step.setMinimumNumberOfInstances("1");
        EcsTemplateStepExecution stepExecution = new EcsTemplateStepExecution(step, null);
        stepExecution.start();
    }
}
