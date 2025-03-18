package com.alibabacloud.jenkins.ecs.client;

import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.alibaba.fastjson.JSON;

import com.alibabacloud.credentials.plugin.auth.AlibabaSessionTokenCredentials;
import com.alibabacloud.credentials.plugin.util.CredentialsHelper;
import com.alibabacloud.jenkins.ecs.Messages;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.auth.AlibabaCloudCredentials;
import com.aliyuncs.ecs.model.v20140526.AllocatePublicIpAddressRequest;
import com.aliyuncs.ecs.model.v20140526.AllocatePublicIpAddressResponse;
import com.aliyuncs.ecs.model.v20140526.AttachDiskRequest;
import com.aliyuncs.ecs.model.v20140526.AttachDiskResponse;
import com.aliyuncs.ecs.model.v20140526.AuthorizeSecurityGroupRequest;
import com.aliyuncs.ecs.model.v20140526.CreateSecurityGroupRequest;
import com.aliyuncs.ecs.model.v20140526.CreateSecurityGroupResponse;
import com.aliyuncs.ecs.model.v20140526.CreateVSwitchRequest;
import com.aliyuncs.ecs.model.v20140526.CreateVSwitchResponse;
import com.aliyuncs.ecs.model.v20140526.CreateVpcRequest;
import com.aliyuncs.ecs.model.v20140526.CreateVpcResponse;
import com.aliyuncs.ecs.model.v20140526.DeleteInstanceRequest;
import com.aliyuncs.ecs.model.v20140526.DeleteInstanceResponse;
import com.aliyuncs.ecs.model.v20140526.DeleteVSwitchRequest;
import com.aliyuncs.ecs.model.v20140526.DeleteVpcRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeAvailableResourceRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeAvailableResourceResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeAvailableResourceResponse.AvailableZone;
import com.aliyuncs.ecs.model.v20140526.DescribeAvailableResourceResponse.AvailableZone.AvailableResource;
import com.aliyuncs.ecs.model.v20140526.DescribeAvailableResourceResponse.AvailableZone.AvailableResource.SupportedResource;
import com.aliyuncs.ecs.model.v20140526.DescribeDisksRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeDisksResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeDisksResponse.Disk;
import com.aliyuncs.ecs.model.v20140526.DescribeImagesRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeImagesResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeImagesResponse.Image;
import com.aliyuncs.ecs.model.v20140526.DescribeInstanceStatusRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeInstanceStatusResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeInstanceStatusResponse.InstanceStatus;
import com.aliyuncs.ecs.model.v20140526.DescribeInstanceTypesRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeInstanceTypesResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeInstancesRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeInstancesResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeInstancesResponse.Instance;
import com.aliyuncs.ecs.model.v20140526.DescribeKeyPairsRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeKeyPairsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeKeyPairsResponse.KeyPair;
import com.aliyuncs.ecs.model.v20140526.DescribeRegionsRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeRegionsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeRegionsResponse.Region;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeSecurityGroupsResponse.SecurityGroup;
import com.aliyuncs.ecs.model.v20140526.DescribeSnapshotsRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeSnapshotsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeSnapshotsResponse.Snapshot;
import com.aliyuncs.ecs.model.v20140526.DescribeVSwitchesRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeVSwitchesResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeVSwitchesResponse.VSwitch;
import com.aliyuncs.ecs.model.v20140526.DescribeVpcsRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeVpcsResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeVpcsResponse.Vpc;
import com.aliyuncs.ecs.model.v20140526.DescribeZonesRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeZonesResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeZonesResponse.Zone;
import com.aliyuncs.ecs.model.v20140526.ModifyInstanceAttributeRequest;
import com.aliyuncs.ecs.model.v20140526.ModifyInstanceAttributeResponse;
import com.aliyuncs.ecs.model.v20140526.RunInstancesRequest;
import com.aliyuncs.ecs.model.v20140526.RunInstancesResponse;
import com.aliyuncs.ecs.model.v20140526.StopInstanceRequest;
import com.aliyuncs.ecs.model.v20140526.StopInstanceResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.HttpClientConfig;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.google.common.collect.Lists;
import com.google.common.math.IntMath;
import hudson.util.FormValidation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

/**
 * Created by kunlun.ykl on 2020/8/26.
 */
@Slf4j
public class AlibabaEcsClient {

    private IAcsClient client;
    private String regionNo;
    private static Integer MAX_PAGE_SIZE = 50;
    private static Integer MAX_RESULTS = 100;
    private static Integer INIT_PAGE_NUMBER = 1;
    private Boolean intranetMaster = Boolean.FALSE;

    public AlibabaEcsClient(AlibabaCloudCredentials credentials, String regionNo, Boolean intranetMaster) {
        IClientProfile profile;
        if (CredentialsHelper.isSessionTokenCredentials(credentials)) {
            profile = DefaultProfile.getProfile(regionNo,
                credentials.getAccessKeyId(),
                credentials.getAccessKeySecret(),
                ((AlibabaSessionTokenCredentials)credentials).getSecretToken());
        } else {
            profile = DefaultProfile.getProfile(regionNo,
                credentials.getAccessKeyId(),
                credentials.getAccessKeySecret());
        }
        if (intranetMaster != null && intranetMaster) {
            // use vpc endpoint if jenkins master in vpc private env
            profile.enableUsingVpcEndpoint();
        }
        HttpClientConfig clientConfig = HttpClientConfig.getDefault();
        clientConfig.setProtocolType(ProtocolType.HTTPS);
        clientConfig.setIgnoreSSLCerts(true);
        profile.setHttpClientConfig(clientConfig);
        this.client = new DefaultAcsClient(profile);
        this.regionNo = regionNo;
        this.intranetMaster = intranetMaster == null ? Boolean.FALSE : intranetMaster;
        log.info("AlibabaEcsClient init success. regionNo: {} intranetMaster: {}", regionNo, intranetMaster);
    }

    public List<Region> describeRegions() {
        try {

            DescribeRegionsRequest request = new DescribeRegionsRequest();
            request.setSysRegionId(regionNo);
            DescribeRegionsResponse acsResponse = client.getAcsResponse(request);
            if (CollectionUtils.isEmpty(acsResponse.getRegions())) {
                return Lists.newArrayList();
            }
            return acsResponse.getRegions();
        } catch (Exception e) {
            log.error("describeRegions error.", e);
        }
        return Lists.newArrayList();
    }

    public List<Image> describeImages(DescribeImagesRequest request) {
        try {
            List<Image> images = Lists.newArrayList();
            request.setSysRegionId(regionNo);
            request.setPageSize(MAX_RESULTS);
            DescribeImagesResponse acsResponse = client.getAcsResponse(request);
            if (CollectionUtils.isEmpty(acsResponse.getImages())) {
                return Lists.newArrayList();
            }
            images.addAll(acsResponse.getImages());
            if (acsResponse.getTotalCount() > MAX_RESULTS) {
                int numberSize = IntMath.divide(acsResponse.getTotalCount(), MAX_RESULTS, RoundingMode.CEILING);
                for (int i = 2; i <= numberSize; i++) {
                    request.setPageNumber(i);
                    DescribeImagesResponse response = client.getAcsResponse(request);
                    if (CollectionUtils.isEmpty(response.getImages())) {
                        log.warn("Images is empty. numberSize: {}, requestId: {}", numberSize,
                            response.getRequestId());
                        continue;
                    }
                    images.addAll(response.getImages());
                }
            }
            return images;
        } catch (Exception e) {
            log.error("describeImages error.", e);
        }
        return Lists.newArrayList();
    }

    public List<Vpc> describeVpcs() {
        try {
            DescribeVpcsRequest request = new DescribeVpcsRequest();
            request.setPageNumber(INIT_PAGE_NUMBER);
            request.setPageSize(MAX_PAGE_SIZE);
            request.setSysRegionId(regionNo);
            DescribeVpcsResponse acsResponse = client.getAcsResponse(request);
            if (CollectionUtils.isEmpty(acsResponse.getVpcs())) {
                return Lists.newArrayList();
            }

            List<Vpc> vpcList = Lists.newArrayList();
            vpcList.addAll(acsResponse.getVpcs());
            if (acsResponse.getTotalCount() > MAX_PAGE_SIZE) {
                int numberSize = IntMath.divide(acsResponse.getTotalCount(), MAX_PAGE_SIZE, RoundingMode.CEILING);
                for (int i = 2; i <= numberSize; i++) {
                    request.setPageNumber(i);
                    DescribeVpcsResponse response = client.getAcsResponse(request);
                    if (CollectionUtils.isEmpty(response.getVpcs())) {
                        log.warn("Vpcs is empty. numberSize: {}, requestId: {}", numberSize,
                            response.getRequestId());
                        continue;
                    }
                    vpcList.addAll(response.getVpcs());
                }
            }
            return vpcList;
        } catch (Exception e) {
            log.error("describeVpcs error.", e);
        }
        return Lists.newArrayList();
    }

    public Vpc describeVpc(String vpcId) {
        if (StringUtils.isEmpty(vpcId)) {
            log.error("vpcId is not null");
        }
        DescribeVpcsRequest request = new DescribeVpcsRequest();
        request.setSysRegionId(regionNo);
        request.setVpcId(vpcId);
        DescribeVpcsResponse acsResponse = null;
        Vpc vpcInstance = null;
        try {
            acsResponse = client.getAcsResponse(request);
            if (null != acsResponse && null != acsResponse.getVpcs() && !acsResponse.getVpcs().isEmpty()) {
                vpcInstance = acsResponse.getVpcs().get(0);
            }

        } catch (Exception e) {
            log.error("describeVpc error.regionId is : {},vpcId is :{}", regionNo, vpcId, e);
        }
        return vpcInstance;
    }

    public String createVpc(String cidrBlock) {
        try {
            CreateVpcRequest request = new CreateVpcRequest();
            request.setSysRegionId(regionNo);
            request.setCidrBlock(cidrBlock);
            CreateVpcResponse acsResponse = client.getAcsResponse(request);
            log.info("createVpc success. region: {} cidrBlock: {} vpcId: {}", regionNo, cidrBlock,
                    acsResponse.getVpcId());
            return acsResponse.getVpcId();
        } catch (Exception e) {
            log.error("createVpc error.", e);
        }
        return null;
    }

    public boolean deleteVpc(String vpcId) {
        try {
            DeleteVpcRequest request = new DeleteVpcRequest();
            request.setVpcId(vpcId);
            client.getAcsResponse(request);
            log.info("delete vpc success. vpcId: {}", vpcId);
            return true;
        } catch (Exception e) {
            log.error("delete vpc error. vpcId: {}", vpcId, e);
        }
        return false;
    }

    public List<SecurityGroup> describeSecurityGroups(String vpc) {
        DescribeSecurityGroupsRequest request = new DescribeSecurityGroupsRequest();
        request.setSysRegionId(regionNo);
        request.setVpcId(vpc);
        request.setMaxResults(MAX_RESULTS);
        List<SecurityGroup> securityGroups = Lists.newArrayList();
        do {
            try {
                DescribeSecurityGroupsResponse acsResponse = client.getAcsResponse(request);
                if (null == acsResponse || CollectionUtils.isEmpty(acsResponse.getSecurityGroups())) {
                    break;
                }
                securityGroups.addAll(acsResponse.getSecurityGroups());
                request.setNextToken(acsResponse.getNextToken());
            } catch (Exception e) {
                log.error("describeSecurityGroups error.regionId: {}, vpcId: {}", regionNo, vpc, e);
            }
        } while (StringUtils.isNotBlank(request.getNextToken()));
        return securityGroups;
    }

    public String createSecurityGroup(String vpcId) {
        try {
            CreateSecurityGroupRequest request = new CreateSecurityGroupRequest();
            request.setSysRegionId(regionNo);
            request.setVpcId(vpcId);
            CreateSecurityGroupResponse acsResponse = client.getAcsResponse(request);
            if (StringUtils.isBlank(acsResponse.getSecurityGroupId())) {
                return null;
            }
            log.info("createSecurityGroup success. vpcId: {} securityGroupId: {}", vpcId,
                    acsResponse.getSecurityGroupId());
            return acsResponse.getSecurityGroupId();
        } catch (Exception e) {
            log.error("createSecurityGroup error. vpcId: {}", vpcId, e);
        }
        return null;
    }

    public boolean authorizeSecurityGroup(String protocol, String portRange, String securityGroupId,
                                          String sourceCidrIp) {
        try {
            AuthorizeSecurityGroupRequest authRequest = new AuthorizeSecurityGroupRequest();
            authRequest.setSysRegionId(regionNo);
            authRequest.setIpProtocol(protocol);
            authRequest.setPortRange(portRange);
            authRequest.setSecurityGroupId(securityGroupId);
            authRequest.setSourceCidrIp(sourceCidrIp);
            client.getAcsResponse(authRequest);
            log.info("authorizeSecurityGroup success. protocol: {} portRange: {} securityGroupId: {} sourceCidrIp: {}",
                    protocol, portRange, securityGroupId, sourceCidrIp);
            return true;
        } catch (Exception e) {
            log.error("authorizeSecurityGroup error. securityGroupId: {}", securityGroupId, e);
        }
        return false;
    }

    /**
     * use {@linkplain AlibabaEcsClient#describeAvailableZones()} instead
     *
     * @return
     */
    @Deprecated
    public List<Zone> describeZones() {
        try {
            DescribeZonesRequest request = new DescribeZonesRequest();
            request.setSysRegionId(regionNo);
            DescribeZonesResponse acsResponse = client.getAcsResponse(request);
            if (CollectionUtils.isEmpty(acsResponse.getZones())) {
                return Lists.newArrayList();
            }
            return acsResponse.getZones();
        } catch (Exception e) {
            log.error("describeZones error.", e);
        }
        return Lists.newArrayList();
    }

    public List<String> describeAvailableZones() {
        try {
            List<String> zoneIds = Lists.newArrayList();
            DescribeAvailableResourceRequest resourceRequest = new DescribeAvailableResourceRequest();
            resourceRequest.setSysRegionId(regionNo);
            resourceRequest.setDestinationResource("Zone");
            DescribeAvailableResourceResponse acsResponse = client.getAcsResponse(resourceRequest);
            for (AvailableZone availableZone : acsResponse.getAvailableZones()) {
                if (!"Available".equalsIgnoreCase(availableZone.getStatus())) {
                    continue;
                }
                if (!"WithStock".equals(availableZone.getStatusCategory())) {
                    continue;
                }
                zoneIds.add(availableZone.getZoneId());
            }
            return zoneIds;
        } catch (Exception e) {
            log.error("describeAvailableZones error.", e);
        }
        return Lists.newArrayList();
    }

    public List<VSwitch> describeVsws(String zone, String vpc) {
        try {
            List<VSwitch> vswitchs = Lists.newArrayList();
            DescribeVSwitchesRequest request = new DescribeVSwitchesRequest();
            request.setSysRegionId(regionNo);
            if (StringUtils.isNotEmpty(zone)) {
                request.setZoneId(zone);
            }
            request.setVpcId(vpc);
            request.setPageSize(MAX_PAGE_SIZE);
            DescribeVSwitchesResponse acsResponse = client.getAcsResponse(request);
            if (CollectionUtils.isEmpty(acsResponse.getVSwitches())) {
                return Lists.newArrayList();
            }
            vswitchs.addAll(acsResponse.getVSwitches());

            if (acsResponse.getTotalCount() > MAX_RESULTS) {
                int numberSize = IntMath.divide(acsResponse.getTotalCount(), MAX_RESULTS, RoundingMode.CEILING);
                for (int i = 2; i <= numberSize; i++) {
                    request.setPageNumber(i);
                    DescribeVSwitchesResponse response = client.getAcsResponse(request);
                    if (CollectionUtils.isEmpty(response.getVSwitches())) {
                        log.warn("VSwitches is empty. numberSize: {}, requestId: {}", numberSize,
                            response.getRequestId());
                        continue;
                    }
                    vswitchs.addAll(response.getVSwitches());
                }
            }
            return vswitchs;
        } catch (Exception e) {
            log.error("describeVsws error.", e);
        }
        return Lists.newArrayList();
    }

    public String createVsw(String zone, String vpc, String cidrBlock) {
        try {
            CreateVSwitchRequest createVswRequest = new CreateVSwitchRequest();
            createVswRequest.setSysRegionId(regionNo);
            createVswRequest.setZoneId(zone);
            createVswRequest.setVpcId(vpc);
            createVswRequest.setCidrBlock(cidrBlock);
            CreateVSwitchResponse acsResponse = client.getAcsResponse(createVswRequest);
            log.info("createVsw success. zone: {} vpc: {} cidrBlock: {} vswId: {}",
                    zone, vpc, cidrBlock, acsResponse.getVSwitchId());
            return acsResponse.getVSwitchId();
        } catch (Exception e) {
            log.error("createVsw error. zone: {} vpc: {} cidrBlock: {}", zone, vpc, cidrBlock, e);
        }
        return null;
    }

    public boolean deleteVsw(String vSwitchId) {
        try {
            DeleteVSwitchRequest deleteVSwitchRequest = new DeleteVSwitchRequest();
            deleteVSwitchRequest.setVSwitchId(vSwitchId);
            client.getAcsResponse(deleteVSwitchRequest);
            log.info("deleteVSW success. vswId: {}", vSwitchId);
            return true;
        } catch (Exception e) {
            log.error("deleteVsw error. vswId: {}", vSwitchId, e);
        }
        return false;
    }

    public List<String> describeInstanceTypes(String zone, Integer core, Float memInGb) {
        try {
            DescribeAvailableResourceRequest resourceRequest = new DescribeAvailableResourceRequest();
            resourceRequest.setDestinationResource("InstanceType");
            resourceRequest.setIoOptimized("optimized");
            resourceRequest.setNetworkCategory("vpc");
            resourceRequest.setResourceType("instance");
            resourceRequest.setSpotStrategy("SpotAsPriceGo");
            resourceRequest.setInstanceChargeType("PostPaid");
            if (null != core) {
                resourceRequest.setCores(core);
            }
            if (null != memInGb) {
                resourceRequest.setCores(core);
            }
            resourceRequest.setMemory(memInGb);
            DescribeAvailableResourceResponse acsResponse = client.getAcsResponse(resourceRequest);
            if (CollectionUtils.isEmpty(acsResponse.getAvailableZones())) {
                return Lists.newArrayList();
            }
            List<String> instanceTypes = Lists.newArrayList();

            for (AvailableZone availableZone : acsResponse.getAvailableZones()) {
                if (!zone.equals(availableZone.getZoneId())) {
                    continue;
                }
                for (AvailableResource availableResource : availableZone.getAvailableResources()) {
                    if (!"InstanceType".equals(availableResource.getType())) {
                        continue;
                    }
                    for (SupportedResource supportedResource : availableResource.getSupportedResources()) {
                        if ("Available".equals(supportedResource.getStatus()) && "WithStock".equals(
                                supportedResource.getStatusCategory())) {
                            instanceTypes.add(supportedResource.getValue());
                        }
                    }
                }
            }
            return instanceTypes;
        } catch (Exception e) {
            log.error("describeSpotInstanceTypes error.", e);
        }
        return Lists.newArrayList();
    }

    public DescribeInstanceTypesResponse describeInstanceTypes(String instanceType) {
        try {
            DescribeInstanceTypesRequest describeInstanceTypesRequest = new DescribeInstanceTypesRequest();
            describeInstanceTypesRequest.setMaxResults(10L);
            describeInstanceTypesRequest.setInstanceTypess(java.util.Arrays.asList(instanceType));
            return client.getAcsResponse(describeInstanceTypesRequest);
        } catch (Exception e) {
            log.error("describeSpotInstanceTypes error.", e);
        }
        return new DescribeInstanceTypesResponse();
    }

    public List<KeyPair> describeKeyPairs(@Nullable String keyPairName, @Nullable String pfp) {
        try {
            DescribeKeyPairsRequest request = new DescribeKeyPairsRequest();
            request.setSysRegionId(regionNo);
            request.setKeyPairName(keyPairName);
            request.setKeyPairFingerPrint(pfp);
            DescribeKeyPairsResponse acsResponse = client.getAcsResponse(request);
            log.info(JSON.toJSONString(acsResponse));
            List<KeyPair> keyPairs = acsResponse.getKeyPairs();
            if (CollectionUtils.isEmpty(keyPairs)) {
                return Lists.newArrayList();
            }
            return keyPairs;
        } catch (Exception e) {
            log.error("listKeyPairs error", e);
        }
        return Lists.newArrayList();
    }

    public List<Instance> describeInstances(List<String> instanceIds) {
        try {
            DescribeInstancesRequest request = new DescribeInstancesRequest();
            request.setInstanceIds(JSON.toJSONString(instanceIds));
            DescribeInstancesResponse acsResponse = client.getAcsResponse(request);
            if (CollectionUtils.isEmpty(acsResponse.getInstances())) {
                log.error("describeInstances error. instanceIds: {} acsResponse: {}", JSON.toJSONString(instanceIds), JSON.toJSONString(acsResponse));
                return Lists.newArrayList();
            }
            return acsResponse.getInstances();
        } catch (Exception e) {
            log.error("describeInstances error. instanceIds: {}", JSON.toJSONString(instanceIds), e);
        }
        return Lists.newArrayList();
    }

    public List<Disk> describeDisk(String region, String zone, String diskId) {
        try {
            DescribeDisksRequest request = new DescribeDisksRequest();
            request.setSysRegionId(region);
            request.setZoneId(zone);
            if (StringUtils.isNotBlank(diskId)) {
                List<String> diskIds = Lists.newArrayList(diskId);
                request.setDiskIds(JSON.toJSONString(diskIds));
            }
            DescribeDisksResponse response = client.getAcsResponse(request);
            List<Disk> disks = response.getDisks();
            if (CollectionUtils.isEmpty(disks)) {
                log.error("disks is empty. region: {}, zone: {}, response: {}", region, zone,
                    JSON.toJSONString(response));
                return Lists.newArrayList();
            }
            return disks;
        } catch (Exception e) {
            log.error("describeDisk error. region: {}, zone: {}", region, zone, e);
        }
        return Lists.newArrayList();
    }

    public List<String> describeSnapshotIds(String region) {
        try {
            DescribeSnapshotsRequest request = new DescribeSnapshotsRequest();
            request.setSysRegionId(region);
            request.setMaxResults(MAX_RESULTS);
            List<String> snapshotIds = Lists.newArrayList();
            do {
                try {
                    DescribeSnapshotsResponse acsResponse = client.getAcsResponse(request);
                    if (null == acsResponse || CollectionUtils.isEmpty(acsResponse.getSnapshots())) {
                        break;
                    }
                    snapshotIds.addAll(
                        acsResponse.getSnapshots().stream().map(Snapshot::getSnapshotId).collect(Collectors.toList()));
                    request.setNextToken(acsResponse.getNextToken());
                } catch (Exception e) {
                    log.error("describeSnapshotIds error.regionId: {}", regionNo, e);
                }
            } while (StringUtils.isNotBlank(request.getNextToken()));
            return snapshotIds;
        } catch (Exception e) {
            log.error("describeSnapshots error. region: {}", region, e);
        }
        return Lists.newArrayList();
    }

    public void stopIntance(String instanceId) {
        try {
            StopInstanceRequest request = new StopInstanceRequest();
            request.setInstanceId(instanceId);
            StopInstanceResponse acsResponse = client.getAcsResponse(request);
            log.info("stopInstance success. instanceId: {} response: {}", instanceId, JSON.toJSONString(acsResponse));
        } catch (Exception e) {
            log.error("stopInstance error. instanceId: {}", instanceId, e);
        }
    }

    /**
     * @param instanceId
     */
    public boolean terminateInstance(String instanceId, boolean force) {
        try {
            log.info("terminateInstance start. instanceId: {} force: {}", instanceId, force);
            DeleteInstanceRequest request = new DeleteInstanceRequest();
            request.setInstanceId(instanceId);
            request.setForce(force);
            DeleteInstanceResponse acsResponse = client.getAcsResponse(request);
            log.info("terminateInstance success. instanceId: {} resp: {}", instanceId, JSON.toJSONString(acsResponse));
            // true: 代表请求已经成功提交, 阿里云开始执行释放实例
            return true;
        } catch (Exception e) {
            log.error("terminateInstance error. instanceId: {}", instanceId, e);
            // false: 代表当前状态有误, 不支持释放, 请求提交失败, 阿里云未开始执行释放实例
            return false;
        }
    }

    public List<String> runInstances(RunInstancesRequest request) {
        try {
            request.setAcceptFormat(FormatType.JSON);
            request.setIoOptimized("optimized");
            request.setInstanceChargeType("PostPaid");
            RunInstancesResponse acsResponse = client.getAcsResponse(request);
            List<String> instanceIdSets = acsResponse.getInstanceIdSets();
            log.info("runInstances success. instanceIdSets: {}", JSON.toJSONString(instanceIdSets));
            return instanceIdSets;
        } catch (Exception e) {
            log.error("runInstances error. request: {}", JSON.toJSONString(request), e);
        }
        return Lists.newArrayList();
    }

    public boolean modifyInstanceAttr(ModifyInstanceAttributeRequest request) {
        try {
            if (StringUtils.isBlank(request.getInstanceId())) {
                log.error("modifyInstanceAttr param error. instanceId is required.");
                return false;
            }
            log.info("modifyInstanceAttr start. request: {}", JSON.toJSONString(request));
            request.setAcceptFormat(FormatType.JSON);
            ModifyInstanceAttributeResponse acsResponse = client.getAcsResponse(request);
            log.info("modifyInstanceAttr finished. request: {} requestId: {}", JSON.toJSONString(request),
                acsResponse.getRequestId());
        } catch (Exception e) {
            log.error("modifyInstanceAttr error. request: {}", JSON.toJSONString(request), e);
        }
        return false;
    }

    public void attachDisk(String instanceId, String diskId) {
        try {
            if (StringUtils.isBlank(instanceId) && StringUtils.isBlank(diskId)) {
                log.error("attachDisk param error. instanceId and diskId is required.");
            }
            log.info("attachDisk start. instanceId: {}, diskId: {}", instanceId, diskId);
            AttachDiskRequest request = new AttachDiskRequest();
            request.setDiskId(diskId);
            request.setInstanceId(instanceId);
            AttachDiskResponse response = client.getAcsResponse(request);
            log.info("attachDisk finished. requestId: {}", response.getRequestId());
        } catch (Exception e) {
            log.error("attachDisk error. instanceId: {}, diskId: {}", instanceId, diskId, e);
        }

    }

    public FormValidation druRunInstances(RunInstancesRequest request) {
        try {
            List<RunInstancesRequest.Tag> tags = Lists.newArrayList();
            RunInstancesRequest.Tag tag = new RunInstancesRequest.Tag();
            tag.setKey("CreatedFrom");
            tag.setValue("jenkins-plugin");
            tags.add(tag);
            request.setTags(tags);
            request.setAcceptFormat(FormatType.JSON);
            request.setIoOptimized("optimized");
            request.setInstanceChargeType("PostPaid");
            request.setDryRun(true);
            RunInstancesResponse acsResponse = client.getAcsResponse(request);
            log.info("druRunInstances success. acsResponse: {}", JSON.toJSONString(acsResponse));
            return FormValidation.ok(Messages.AlibabaECSCloud_Success());
        } catch (ClientException e) {
            if ("DryRunOperation".equals(e.getErrCode())) {
                log.info("druRunInstances success. acsResponse: {}", JSON.toJSONString(request));
                return FormValidation.ok(Messages.AlibabaECSCloud_Success());
            }
            log.error("druRunInstances error:{}. request: {}", e, JSON.toJSONString(request));
            return FormValidation.error(Messages.AlibabaECSCloud_Error() + e);
        }
    }

    public String allocatePublicIp(String instanceId) {
        try {
            AllocatePublicIpAddressRequest ipRequest = new AllocatePublicIpAddressRequest();
            ipRequest.setInstanceId(instanceId);
            AllocatePublicIpAddressResponse acsResponse = client.getAcsResponse(ipRequest);
            String ipAddress = acsResponse.getIpAddress();
            log.info("allocatePublicIp success. instanceId: {} ipAddress: {}", instanceId, ipAddress);
            return ipAddress;
        } catch (Exception e) {
            log.error("allocatePublicIp error. instanceId: {}", instanceId, e);
        }
        return null;
    }

    public boolean describeInstanceStatus(String instanceId) {
        try {
            DescribeInstanceStatusRequest request = new DescribeInstanceStatusRequest();
            request.setSysRegionId(regionNo);
            request.setInstanceIds(Lists.newArrayList(instanceId));
            DescribeInstanceStatusResponse response = client.getAcsResponse(request);
            List<InstanceStatus> instanceStatuses = response.getInstanceStatuses();
            if (CollectionUtils.isEmpty(instanceStatuses)) {
                log.error("describeInstanceStatus error. region: {}, instanceId: {}, requestId: {}", regionNo,
                    instanceId, response.getRequestId());
                return false;
            }
            String status = instanceStatuses.get(0).getStatus();
            if (status.equals("Running")) {
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;

    }

}
