# AlibabaEcs Step pipeline

## 使用步骤
1. 配置ecs 模版配置
   ![](docs/images/jenkins.cloudDetail.png)
2. 新建一个流水线任务
   ![](docs/images/jenkins.item.png)
3. 点击流水线语法
   ![](docs/images/jenkins.pipelineSyntax.png)
4. 示例步骤 选择 alibabaEcs: Cloud template provisioning
   ![](docs/images/jenkins.sampleStep.png)
5. Alibaba Cloud name 选择刚创建模版名词
   ![](docs/images/jenkins.cloudName.png)
6. Template name 选择刚创建模版配置中的ecs模版描述
   ![](docs/images/jenkins.templateName.png)
7. 点击生成流水线脚本
   ![](docs/images/jenkins.generatePipelineScript%20.png)
8. 复制框中生成的脚本
9. 粘贴脚本到流水线中的脚本文本框内
   ![](docs/images/jenkins.script.png)
10. 不使用Groovy沙盘
    
    ![](docs/images/jenkins.groovySandbox.png)
11. 点击保存
12. 点击立即构建