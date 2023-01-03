# EcsTemplate Step pipeline

## EcsTemplate 脚本示例
```groovy
def JOB_NAME = "${env.JOB_NAME}"
def BUILD_NUMBER = "${env.BUILD_NUMBER}"
def ECS_NAME = "jenkins-${JOB_NAME}-${BUILD_NUMBER}"
EcsTemplate(
        cloudName: '${cloudName}',
        attachPublicIp: true,
        chargeType: 'Spot', //
        connectionStrategy: 'PUBLIC_IP',
        credentialsId: '${credentialsId}',
        instanceCapStr: '${instanceCapStr}',
        ecsType: [$class: 'UnixData'],
        idleTerminationMinutes: '${idleTerminationMinutes}',
        image: '${image}', //
        initScript: '',
        instanceCap: '${instanceCap}',
        instanceType: 'ecs.s6-c1m1.small',
        intranetMaster: false,
        labels: "${labels}",
        name: "${name}",
        launchTimeout: '${launchTimeout}',
        maxTotalUses: '${maxTotalUses}',
        minimumNumberOfInstances: '
        mountDataDisk: true,
        dataDiskCategory: 'cloud_essd_PL0',
        dataDiskSize: '${dataDiskSize}',
        mountQuantity: '${mountQuantity}',
        noDelayProvisioning: true, // 如果填写了 true, 则一旦构建队列中有任务积压, 则不会等待已有的worker节点结束工作, 而是会立刻新建worker节点来执行队里中的构建任务. 填写 true, 优点是队列中任务能迅速引发新建节点从而迅速完成构建, 缺点是任务结束后可能导致闲置的worker节点数量过多从而引发额外费用问题; 此时建议调整节点的 "Idle Termination Time In Minutes" 参数来缓解该问题
        numExecutors: '1',
        region: '${region}',
        remoteFs: '${remoteFs}',
        securityGroup: '${securityGroup}',
        sshKey: '${sshKey}',
        systemDiskCategory: 'cloud_essd_PL0',
        systemDiskSize: 40,
        tags: [[name: '${name}', value: '${value}']],
        templateName: '${templateName}',
        userData: '',
        vpc: '${vpc}',
        vsw: '${vsw}',
        zone: '${zone}') //

        {
            timestamps {
                node(ECS_NAME) {
                    def start_build_time = new Date().format('yyyy-MM-dd HH:mm:ss')
                    echo "${start_build_time}"
                }
            }
        }
```

## 字段说明

|  名称                     |  类型      | 必填    |  描述                                                                                                                                                                                                                                                                                            | 示例值     |
| :-----                   | :-----    | :-----  | :-----                                                                                                                                                                                                                                                                                          |  :-----   |
| cloudName                | String    | 是      | 模版名称                                                                                                                                                                                                                                                                                          | 示例值：<br> jenkins|
| attachPublicIp           | Boolean   | 是      | 是否使用公网ip, 取值范围:<br> * true <br> * false                                                                                                                                                                                                                                                   | 示例值: <br> true     |
| chargeType               | String    | 是      | 选择实例付费类型,取值范围：<br> * OnDemand : 按量实例 <br> * Spot : 抢占式示例                                                                                                                                                                                                                          |示例值: <br> Spot |
| connectionStrategy       | String    | 是      | 连接策略, 取值范围：PRIVATE_IP: 私网连接 <br> PUBLIC_IP: 公网连接                                                                                                                                                                                                                                     |示例值: <br> PUBLIC_IP|
| credentialsId            | String    | 是      | 阿里 ECS 凭证,用于连接 ECS 的阿里云 iam access key                                                                                                                                                                                                                                                  |示例值: <br> jenkins-credentials|
| instanceCapStr           | String    | 否      | 限制从该模板启动的正在运行的实例总数,不填表示不限制.                                                                                                                                                                                                                                                     |示例值: <br> 2 |
| idleTerminationMinutes   | String    | 否      | 空闲终止时间 单位: 分钟                                                                                                                                                                                                                                                                             |示例值: <br> 30|
| image                    | String    | 是      | 镜像ID, 启动实例时选择的镜像资源.  <br> 更多镜像请参见 [公共镜像](https://help.aliyun.com/document_detail/25459.html)                                                                                                                                                                                    |示例值: <br> ubuntu_22_04_x64_20G_alibase_20220628.vhd|
| instanceCap              | String    | 否      | 当前Template下可以创建的Node数量上限,不填表示不限制                                                                                                                                                                                                                                                    |示例值: <br> 2|
| instanceType             | String    | 是      | 待创建实例的实例规格的ID, <br> 已上线的实例规格请参见 [选择实例规格](https://help.aliyun.com/document_detail/108399.html")                                                                                                                                                                                |示例值: <br> ecs.s6-c1m1.small|
| intranetMaster           | Boolean   | 是      | 当前Jenkins Master是否部署在VPC内网环境中(即是否有访问公网的权限),取值范围 <br> * true：Jenkins Master部署在内网环境中(即没有访问公网权限) <br> * false： 后续插件调用阿里云SDK会使用VPC私网域名进行请求.如果在公网环境中(即有访问公网权限), 后续调用阿里云SDK会使用公网域名进行请求.                                                |示例值: <br> false|
| labels                   | String    | 否      | 节点标签                                                                                                                                                                                                                                                                                         |示例值: <br> jenkins|
| name                     | String    | 否      | 实例名称,和需要运行的任务名称之和不能超过128个英文或中文字符. <br> 必须以大小字母或中文开头. <br> 不能以http://或https://开头. <br> 可以包含数字、半角冒号(:),下划线(_)或者短划线(-).                                                                                                                                   |示例值: <br> Jenkins-test|
| launchTimeout            | String    | 否      | 启动超时（单位为秒） 等待SSH与新的从节点实例的链接完成的秒数,不填或为零时表示没有超时时间.<br> 如果在该时间范围内无法与worker节点建立SSH, 则默认会删除掉该节点.                                                                                                                                                          |示例值: <br> 600
| maxTotalUses             | String    | 否      | 单节点最大可复用次数. 取值范围: <br> * 0: 代表不限制复用次数, 即该节点会常驻, 可以执行不限次数的构建, 直到手动销毁或者空闲时长超过阈值导致销毁. <br> * 1: 代表该节点只能执行一次构建, 构建结束, 节点就立即销毁. <br>* 2~N: 代表节点可以执行2~N次构建. 没执行一次构建, 剩余构建次数递减1, 直到为0时销毁.                                     | 示例值: <br> 1|
| minimumNumberOfInstances | String    | 是      | 最小实例数是用于生成节点的数量. 此插件将根据填写的数字创建子节点.取值范围: <br> 0~N <br> N 不能 > instanceCap                                                                                                                                                                                                 |示例值: <br> 0|
| mountDataDisk            | Boolean   | 是      | 挂载数据盘 取值范围: <br> * true <br> * false                                                                                                                                                                                                                                                      |示例值: <br> true|
| dataDiskCategory         | String    | 否      | 数据盘规格, 当mountDataDisk 为 true时, 必填. 取值范围: <br> * cloud_essd_PL0 <br> * cloud_essd_PL1 <br> * cloud_essd_PL2 <br> * cloud_essd_PL3 <br> * cloud_ssd <br> * cloud_efficiency <br> * cloud                                                                                                 |示例值: <br> cloud_essd_PL0|
| dataDiskSize             | String    | 否      | 数据盘大小, 当mountDataDisk 为 true时, 必填. 取值范围: <br> * cloud_efficiency：20~32768 <br> * cloud_ssd：20~32768 <br> * cloud_essd_PL0：40~32768 <br> * cloud_essd_PL1：20~32768 <br> * cloud_essd_PL2：461~32768 <br> * cloud_essd_PL3：1261~32768 <br> * cloud：5~2000 <br> * cloud_auto：40~32768 |示例值: <br> 40|
| mountQuantity            | String    | 否      | 挂载数据盘数量, 当mountDataDisk 为 true时, 必填.                                                                                                                                                                                                                                                    |示例值: <br >1|
| noDelayProvisioning      | Boolean   | 是      | 无延迟供应,取值范围: <br> * true <br>* false                                                                                                                                                                                                                                                       | 示例值: <br> true|
| numExecutors             | String    | 否      | 执行者数量,Jenkins可以在此节点上执行的并发构建的最大数量.                                                                                                                                                                                                                                                | 示例值: <br> 1|
| region                   | String    | 是      | 地域, 标识阿里云应用所在的地域. <br> 查看[阿里云地域](https://help.aliyun.com/document_detail/188196.html#section-6tn-8lg-r4h) 获取所有支持的地域列表.                                                                                                                                                      |示例值: <br> cn-hangzhou|
| remoteFs                 | String    | 否      | 远程根目录                                                                                                                                                                                                                                                                                        |示例值: <br>/root|
| securityGroup            | String    | 是      | 安全组,新创建实例所属于的安全组ID.<br> 同一个安全组内的实例之间可以互相访问,一个安全组能容纳的实例数量视安全组类型而定.                                                                                                                                                                                             |示例值: <br> sg-bp15ed6xe1yxeycg7****|
| sshKey                   | String    | 是      | 阿里云SSH密钥, 阿里云SSH密钥对是一种安全便捷的登录认证方式,由公钥和私钥组成,仅支持Linux实例.                                                                                                                                                                                                                  |示例值: <br> jenkins-sshKey|
| systemDiskCategory       | String    | 是      | 系统盘类型, 取值范围: <br>  * cloud_essd_PL0 <br> * cloud_essd_PL1 <br> * cloud_essd_PL2 <br> * cloud_essd_PL3 <br> * cloud_ssd <br> * cloud_efficiency <br> * cloud                                                                                                                               |示例值: <br> cloud_essd_PL0|
| systemDiskSize           | Integer   | 是      | 系统盘大小 单位为GiB.<br> 取值范围：20~500.                                                                                                                                                                                                                                                          |示例值: <br> 40|
| tags                     | Object [] | 否      | 实例、云盘和主网卡的标签信息.                                                                                                                                                                                                                                                                        | 示例值: <br> [[name: 'jenkins', value: 'value']]|
| templateName             | String    | 是      | 描述                                                                                                                                                                                                                                                                                             | 示例值: <br> ecs-template| 
| userData                 | String    | 否      | 实例自定义数据. 不需要进行Base64编码,源码传入即可,原始数据最多为16 KB.                                                                                                                                                                                                                                    |示例值: <br> #!/bin/sh <br>echo 'hello world!'|
| vpc                      | String    | 是      | VPC ID 将用于创建ECS实例.                                                                                                                                                                                                                                                                          |示例值: <br> vpc-dweqdxdaadqdxxxxx|
| vsw                      | String    | 是      | 虚拟交换机ID, 交换机（vSwitch）是组成专有网络的基础网络设备,用来连接不同的云资源实例. <br> 专有网络是地域级别的资源,专有网络不可以跨地域,但包含所属地域的所有可用区.<br> 您可以在每个可用区内创建一个或多个交换机来划分子网.                                                                                                        |示例值: <br> vsw-bp1s5fnvk4gn2tws0****|
| zone                     | String    | 是      | 待创建实例所属的可用区ID. <br> 查看[阿里云地域&可用区](https://help.aliyun.com/document_detail/188196.html#section-6tn-8lg-r4h) 获取所有支持的可用区列表                                                                                                                                                     |示例值: <br> cn-hangzhou-i|