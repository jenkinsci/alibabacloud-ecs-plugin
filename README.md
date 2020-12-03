# Alibabacloud ECS plugin
# Table of contents
   * [Introduction](#introduction)
   * [Usage](#usage)
      * [Create a key pair](#Create-a-key-pair-of-ECS)
      * [Download plugin](#Download-Alibaba-Cloud-ECS-plugin)
      * [Configure clouds](#Configure-clouds)
   * [Some known problems in use](#Some-known-problems-in-use)
   * [Contact us](#Contact-us)

# Introduction
This plugin extends Jenkins slave node generation (node provision), in the case of high concurrent 
project integration,use this plugin to form jenins master-follower mode for project integration

Before using this plugin, you need to install the 'alibabacloud credentials plugin'.
Please refer to https://github.com/aliyun/alibabacloud-credentials-jenkins-plugin

# Usage

Before using this product, it is assumed that you have registered your alibaba cloud 
account and obtained the relevant AK and sk.

### Create a key pair of ECS 
* Create a key pair of ECS with address: https://ecs.console.aliyun.com/
* Select "network and security" \ > "key pair"
![](docs/images/alibabacloud.keypair.png)
* Click the upper right corner to create a key pair, generate the key according to the prompt, 
and save the generated key pair ".PEM" file
![](docs/images/alibabacloud.keypairgene.png)
The address of the key pair is as follows:
https://help.aliyun.com/document_detail/51792.html?spm=5176.ecsbuyv3.system.2.82213675Kunhoi



### Download Alibaba Cloud ECS plugin
* Enter the installed Jenkins client and go to the "manage Jenkins" / > "manage plugins" / > "available" page
![](docs/images/jenkins.avail.png)
* Enter "Alibaba Cloud ECS" plugin in the search box to download and install

### Configure clouds 
* after installing the plugin, go to "manage Jenkins" / > "manage node and clouds" / > 
"configure clouds" / > on the launched Jenkins
![](docs/images/jenkins.cloudsConfigure.png)

* Click "Alibaba Cloud ECS" 
![](docs/images/jenkins.cloudDetail.png)
* Follow the prompts for relevant configuration. When you click the Add button of Alibaba cloud credentials to obtain 
Jenkins authentication, add "alibaba cloud credential" and fill in AK and sk of alicloud account.
![](docs/images/jenkins.Credentials.png)
![](docs/images/jenkins.Credentials.check.png)
![](docs/images/jenkins.right.png)
* In the drop-down box, select the name of the credential you just filled in.
![](docs/images/jenkins.testCre.png)
* Then select region, image, VPC, security group

* When configuring the ECS SSH key, click the Add button to get Jenkins, authenticate, and add the contents of the ECS.
PEM file obtained from the ECS console of alibabacloud and fill in.
* Currently, only "SSH username with private key" is supported.
![](docs/images/jenkins.SSH.png)
* You can click test to see if it works.
![](docs/images/jenkins.conn.png)

* Select availability zone, VSW, choose instance type.
* Minimum number of instances is the number of follower nodes used to generate the follower. 
The server will generate the follower according to the number filled in. The number should be at least 1

* Init script is the shell script to be run on the newly launched follower node
instance, before Jenkins starts launching a follower node. This is also a good place to install additional 
packages that you need for your builds and tests.

* After saving successfully, enter new nodes to add nodes. The initialization status is as follows:
![](docs/images/jenkins.nodes.png)
* After clicking the instance of sprovision via pot, the follower node will be initialized as follows:
![](docs/images/jenkins.spot.png)
* After a while, the state will be restored after the connection.
![](docs/images/jenkins.rightspot.png)
* Click the node drop-down box to perform relevant operations on the follower node.
![](docs/images/jenkins.configSpot.png)
* Click Configure to view the follower node information
![](docs/images/jenkins.detailFollower.png)
#Some known problems in use
* When you click the Save button, if the "SSH username with private key" connection test fails, the save 
will succeed, but "provision node" will report an error of "a problem occurred while processing the request". 
Therefore, please ensure that the connection is successful before saving.
* Examples are as follows:
![](docs/images/jenkins.cloud.primaryKey.png)
![](docs/images/jenkins.provision.png)
![](docs/images/jenkins.error.png)
#Contact us
* If you have any questions or requirements, please scan the following QR code with nails：
![](docs/images/nail_group_qr_code.png)

