//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
package funnel
package chemist
package aws

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeInstancesResult
import com.amazonaws.services.ec2.model.{Reservation,Instance => EC2Instance}

object TestAmazonEC2 {
  def apply(i: EC2Instance): AmazonEC2 =
    apply(Seq(i))

  def apply(i: Seq[EC2Instance]): AmazonEC2 =
    new TestAmazonEC2 {
      override def describeInstances(arg: com.amazonaws.services.ec2.model.DescribeInstancesRequest): DescribeInstancesResult = {
        new DescribeInstancesResult().withReservations(
          i.map(in => new Reservation().withInstances(in)):_*)
      }
    }
}

class TestAmazonEC2 extends AmazonEC2 {
  def describeInstances(x$1: com.amazonaws.services.ec2.model.DescribeInstancesRequest): DescribeInstancesResult = sys.error("not yet implemented.")

  //////////////////// erroneous methods /////////////////////////

  def acceptVpcPeeringConnection(): com.amazonaws.services.ec2.model.AcceptVpcPeeringConnectionResult = ???
  def acceptVpcPeeringConnection(x$1: com.amazonaws.services.ec2.model.AcceptVpcPeeringConnectionRequest): com.amazonaws.services.ec2.model.AcceptVpcPeeringConnectionResult = ???
  def allocateAddress(): com.amazonaws.services.ec2.model.AllocateAddressResult = ???
  def allocateAddress(x$1: com.amazonaws.services.ec2.model.AllocateAddressRequest): com.amazonaws.services.ec2.model.AllocateAddressResult = ???
  def assignPrivateIpAddresses(x$1: com.amazonaws.services.ec2.model.AssignPrivateIpAddressesRequest): Unit = ???
  def associateAddress(x$1: com.amazonaws.services.ec2.model.AssociateAddressRequest): com.amazonaws.services.ec2.model.AssociateAddressResult = ???
  def associateDhcpOptions(x$1: com.amazonaws.services.ec2.model.AssociateDhcpOptionsRequest): Unit = ???
  def associateRouteTable(x$1: com.amazonaws.services.ec2.model.AssociateRouteTableRequest): com.amazonaws.services.ec2.model.AssociateRouteTableResult = ???
  def attachClassicLinkVpc(x$1: com.amazonaws.services.ec2.model.AttachClassicLinkVpcRequest): com.amazonaws.services.ec2.model.AttachClassicLinkVpcResult = ???
  def attachInternetGateway(x$1: com.amazonaws.services.ec2.model.AttachInternetGatewayRequest): Unit = ???
  def attachNetworkInterface(x$1: com.amazonaws.services.ec2.model.AttachNetworkInterfaceRequest): com.amazonaws.services.ec2.model.AttachNetworkInterfaceResult = ???
  def attachVolume(x$1: com.amazonaws.services.ec2.model.AttachVolumeRequest): com.amazonaws.services.ec2.model.AttachVolumeResult = ???
  def attachVpnGateway(x$1: com.amazonaws.services.ec2.model.AttachVpnGatewayRequest): com.amazonaws.services.ec2.model.AttachVpnGatewayResult = ???
  def authorizeSecurityGroupEgress(x$1: com.amazonaws.services.ec2.model.AuthorizeSecurityGroupEgressRequest): Unit = ???
  def authorizeSecurityGroupIngress(x$1: com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest): Unit = ???
  def bundleInstance(x$1: com.amazonaws.services.ec2.model.BundleInstanceRequest): com.amazonaws.services.ec2.model.BundleInstanceResult = ???
  def cancelBundleTask(x$1: com.amazonaws.services.ec2.model.CancelBundleTaskRequest): com.amazonaws.services.ec2.model.CancelBundleTaskResult = ???
  def cancelConversionTask(x$1: com.amazonaws.services.ec2.model.CancelConversionTaskRequest): Unit = ???
  def cancelExportTask(x$1: com.amazonaws.services.ec2.model.CancelExportTaskRequest): Unit = ???
  def cancelImportTask(): com.amazonaws.services.ec2.model.CancelImportTaskResult = ???
  def cancelImportTask(x$1: com.amazonaws.services.ec2.model.CancelImportTaskRequest): com.amazonaws.services.ec2.model.CancelImportTaskResult = ???
  def cancelReservedInstancesListing(x$1: com.amazonaws.services.ec2.model.CancelReservedInstancesListingRequest): com.amazonaws.services.ec2.model.CancelReservedInstancesListingResult = ???
  def cancelSpotFleetRequests(x$1: com.amazonaws.services.ec2.model.CancelSpotFleetRequestsRequest): com.amazonaws.services.ec2.model.CancelSpotFleetRequestsResult = ???
  def cancelSpotInstanceRequests(x$1: com.amazonaws.services.ec2.model.CancelSpotInstanceRequestsRequest): com.amazonaws.services.ec2.model.CancelSpotInstanceRequestsResult = ???
  def confirmProductInstance(x$1: com.amazonaws.services.ec2.model.ConfirmProductInstanceRequest): com.amazonaws.services.ec2.model.ConfirmProductInstanceResult = ???
  def copyImage(x$1: com.amazonaws.services.ec2.model.CopyImageRequest): com.amazonaws.services.ec2.model.CopyImageResult = ???
  def copySnapshot(x$1: com.amazonaws.services.ec2.model.CopySnapshotRequest): com.amazonaws.services.ec2.model.CopySnapshotResult = ???
  def createCustomerGateway(x$1: com.amazonaws.services.ec2.model.CreateCustomerGatewayRequest): com.amazonaws.services.ec2.model.CreateCustomerGatewayResult = ???
  def createDhcpOptions(x$1: com.amazonaws.services.ec2.model.CreateDhcpOptionsRequest): com.amazonaws.services.ec2.model.CreateDhcpOptionsResult = ???
  def createFlowLogs(x$1: com.amazonaws.services.ec2.model.CreateFlowLogsRequest): com.amazonaws.services.ec2.model.CreateFlowLogsResult = ???
  def createImage(x$1: com.amazonaws.services.ec2.model.CreateImageRequest): com.amazonaws.services.ec2.model.CreateImageResult = ???
  def createInstanceExportTask(x$1: com.amazonaws.services.ec2.model.CreateInstanceExportTaskRequest): com.amazonaws.services.ec2.model.CreateInstanceExportTaskResult = ???
  def createInternetGateway(): com.amazonaws.services.ec2.model.CreateInternetGatewayResult = ???
  def createInternetGateway(x$1: com.amazonaws.services.ec2.model.CreateInternetGatewayRequest): com.amazonaws.services.ec2.model.CreateInternetGatewayResult = ???
  def createKeyPair(x$1: com.amazonaws.services.ec2.model.CreateKeyPairRequest): com.amazonaws.services.ec2.model.CreateKeyPairResult = ???
  def createNetworkAcl(x$1: com.amazonaws.services.ec2.model.CreateNetworkAclRequest): com.amazonaws.services.ec2.model.CreateNetworkAclResult = ???
  def createNetworkAclEntry(x$1: com.amazonaws.services.ec2.model.CreateNetworkAclEntryRequest): Unit = ???
  def createNetworkInterface(x$1: com.amazonaws.services.ec2.model.CreateNetworkInterfaceRequest): com.amazonaws.services.ec2.model.CreateNetworkInterfaceResult = ???
  def createPlacementGroup(x$1: com.amazonaws.services.ec2.model.CreatePlacementGroupRequest): Unit = ???
  def createReservedInstancesListing(x$1: com.amazonaws.services.ec2.model.CreateReservedInstancesListingRequest): com.amazonaws.services.ec2.model.CreateReservedInstancesListingResult = ???
  def createRoute(x$1: com.amazonaws.services.ec2.model.CreateRouteRequest): com.amazonaws.services.ec2.model.CreateRouteResult = ???
  def createRouteTable(x$1: com.amazonaws.services.ec2.model.CreateRouteTableRequest): com.amazonaws.services.ec2.model.CreateRouteTableResult = ???
  def createSecurityGroup(x$1: com.amazonaws.services.ec2.model.CreateSecurityGroupRequest): com.amazonaws.services.ec2.model.CreateSecurityGroupResult = ???
  def createSnapshot(x$1: com.amazonaws.services.ec2.model.CreateSnapshotRequest): com.amazonaws.services.ec2.model.CreateSnapshotResult = ???
  def createSpotDatafeedSubscription(x$1: com.amazonaws.services.ec2.model.CreateSpotDatafeedSubscriptionRequest): com.amazonaws.services.ec2.model.CreateSpotDatafeedSubscriptionResult = ???
  def createSubnet(x$1: com.amazonaws.services.ec2.model.CreateSubnetRequest): com.amazonaws.services.ec2.model.CreateSubnetResult = ???
  def createTags(x$1: com.amazonaws.services.ec2.model.CreateTagsRequest): Unit = ???
  def createVolume(x$1: com.amazonaws.services.ec2.model.CreateVolumeRequest): com.amazonaws.services.ec2.model.CreateVolumeResult = ???
  def createVpc(x$1: com.amazonaws.services.ec2.model.CreateVpcRequest): com.amazonaws.services.ec2.model.CreateVpcResult = ???
  def createVpcEndpoint(x$1: com.amazonaws.services.ec2.model.CreateVpcEndpointRequest): com.amazonaws.services.ec2.model.CreateVpcEndpointResult = ???
  def createVpcPeeringConnection(): com.amazonaws.services.ec2.model.CreateVpcPeeringConnectionResult = ???
  def createVpcPeeringConnection(x$1: com.amazonaws.services.ec2.model.CreateVpcPeeringConnectionRequest): com.amazonaws.services.ec2.model.CreateVpcPeeringConnectionResult = ???
  def createVpnConnection(x$1: com.amazonaws.services.ec2.model.CreateVpnConnectionRequest): com.amazonaws.services.ec2.model.CreateVpnConnectionResult = ???
  def createVpnConnectionRoute(x$1: com.amazonaws.services.ec2.model.CreateVpnConnectionRouteRequest): Unit = ???
  def createVpnGateway(x$1: com.amazonaws.services.ec2.model.CreateVpnGatewayRequest): com.amazonaws.services.ec2.model.CreateVpnGatewayResult = ???
  def deleteCustomerGateway(x$1: com.amazonaws.services.ec2.model.DeleteCustomerGatewayRequest): Unit = ???
  def deleteDhcpOptions(x$1: com.amazonaws.services.ec2.model.DeleteDhcpOptionsRequest): Unit = ???
  def deleteFlowLogs(x$1: com.amazonaws.services.ec2.model.DeleteFlowLogsRequest): com.amazonaws.services.ec2.model.DeleteFlowLogsResult = ???
  def deleteInternetGateway(x$1: com.amazonaws.services.ec2.model.DeleteInternetGatewayRequest): Unit = ???
  def deleteKeyPair(x$1: com.amazonaws.services.ec2.model.DeleteKeyPairRequest): Unit = ???
  def deleteNetworkAcl(x$1: com.amazonaws.services.ec2.model.DeleteNetworkAclRequest): Unit = ???
  def deleteNetworkAclEntry(x$1: com.amazonaws.services.ec2.model.DeleteNetworkAclEntryRequest): Unit = ???
  def deleteNetworkInterface(x$1: com.amazonaws.services.ec2.model.DeleteNetworkInterfaceRequest): Unit = ???
  def deletePlacementGroup(x$1: com.amazonaws.services.ec2.model.DeletePlacementGroupRequest): Unit = ???
  def deleteRoute(x$1: com.amazonaws.services.ec2.model.DeleteRouteRequest): Unit = ???
  def deleteRouteTable(x$1: com.amazonaws.services.ec2.model.DeleteRouteTableRequest): Unit = ???
  def deleteSecurityGroup(x$1: com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest): Unit = ???
  def deleteSnapshot(x$1: com.amazonaws.services.ec2.model.DeleteSnapshotRequest): Unit = ???
  def deleteSpotDatafeedSubscription(): Unit = ???
  def deleteSpotDatafeedSubscription(x$1: com.amazonaws.services.ec2.model.DeleteSpotDatafeedSubscriptionRequest): Unit = ???
  def deleteSubnet(x$1: com.amazonaws.services.ec2.model.DeleteSubnetRequest): Unit = ???
  def deleteTags(x$1: com.amazonaws.services.ec2.model.DeleteTagsRequest): Unit = ???
  def deleteVolume(x$1: com.amazonaws.services.ec2.model.DeleteVolumeRequest): Unit = ???
  def deleteVpc(x$1: com.amazonaws.services.ec2.model.DeleteVpcRequest): Unit = ???
  def deleteVpcEndpoints(x$1: com.amazonaws.services.ec2.model.DeleteVpcEndpointsRequest): com.amazonaws.services.ec2.model.DeleteVpcEndpointsResult = ???
  def deleteVpcPeeringConnection(x$1: com.amazonaws.services.ec2.model.DeleteVpcPeeringConnectionRequest): com.amazonaws.services.ec2.model.DeleteVpcPeeringConnectionResult = ???
  def deleteVpnConnection(x$1: com.amazonaws.services.ec2.model.DeleteVpnConnectionRequest): Unit = ???
  def deleteVpnConnectionRoute(x$1: com.amazonaws.services.ec2.model.DeleteVpnConnectionRouteRequest): Unit = ???
  def deleteVpnGateway(x$1: com.amazonaws.services.ec2.model.DeleteVpnGatewayRequest): Unit = ???
  def deregisterImage(x$1: com.amazonaws.services.ec2.model.DeregisterImageRequest): Unit = ???
  def describeAccountAttributes(): com.amazonaws.services.ec2.model.DescribeAccountAttributesResult = ???
  def describeAccountAttributes(x$1: com.amazonaws.services.ec2.model.DescribeAccountAttributesRequest): com.amazonaws.services.ec2.model.DescribeAccountAttributesResult = ???
  def describeAddresses(): com.amazonaws.services.ec2.model.DescribeAddressesResult = ???
  def describeAddresses(x$1: com.amazonaws.services.ec2.model.DescribeAddressesRequest): com.amazonaws.services.ec2.model.DescribeAddressesResult = ???
  def describeAvailabilityZones(): com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult = ???
  def describeAvailabilityZones(x$1: com.amazonaws.services.ec2.model.DescribeAvailabilityZonesRequest): com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult = ???
  def describeBundleTasks(): com.amazonaws.services.ec2.model.DescribeBundleTasksResult = ???
  def describeBundleTasks(x$1: com.amazonaws.services.ec2.model.DescribeBundleTasksRequest): com.amazonaws.services.ec2.model.DescribeBundleTasksResult = ???
  def describeClassicLinkInstances(): com.amazonaws.services.ec2.model.DescribeClassicLinkInstancesResult = ???
  def describeClassicLinkInstances(x$1: com.amazonaws.services.ec2.model.DescribeClassicLinkInstancesRequest): com.amazonaws.services.ec2.model.DescribeClassicLinkInstancesResult = ???
  def describeConversionTasks(): com.amazonaws.services.ec2.model.DescribeConversionTasksResult = ???
  def describeConversionTasks(x$1: com.amazonaws.services.ec2.model.DescribeConversionTasksRequest): com.amazonaws.services.ec2.model.DescribeConversionTasksResult = ???
  def describeCustomerGateways(): com.amazonaws.services.ec2.model.DescribeCustomerGatewaysResult = ???
  def describeCustomerGateways(x$1: com.amazonaws.services.ec2.model.DescribeCustomerGatewaysRequest): com.amazonaws.services.ec2.model.DescribeCustomerGatewaysResult = ???
  def describeDhcpOptions(): com.amazonaws.services.ec2.model.DescribeDhcpOptionsResult = ???
  def describeDhcpOptions(x$1: com.amazonaws.services.ec2.model.DescribeDhcpOptionsRequest): com.amazonaws.services.ec2.model.DescribeDhcpOptionsResult = ???
  def describeExportTasks(): com.amazonaws.services.ec2.model.DescribeExportTasksResult = ???
  def describeExportTasks(x$1: com.amazonaws.services.ec2.model.DescribeExportTasksRequest): com.amazonaws.services.ec2.model.DescribeExportTasksResult = ???
  def describeFlowLogs(): com.amazonaws.services.ec2.model.DescribeFlowLogsResult = ???
  def describeFlowLogs(x$1: com.amazonaws.services.ec2.model.DescribeFlowLogsRequest): com.amazonaws.services.ec2.model.DescribeFlowLogsResult = ???
  def describeImageAttribute(x$1: com.amazonaws.services.ec2.model.DescribeImageAttributeRequest): com.amazonaws.services.ec2.model.DescribeImageAttributeResult = ???
  def describeImages(): com.amazonaws.services.ec2.model.DescribeImagesResult = ???
  def describeImages(x$1: com.amazonaws.services.ec2.model.DescribeImagesRequest): com.amazonaws.services.ec2.model.DescribeImagesResult = ???
  def describeImportImageTasks(): com.amazonaws.services.ec2.model.DescribeImportImageTasksResult = ???
  def describeImportImageTasks(x$1: com.amazonaws.services.ec2.model.DescribeImportImageTasksRequest): com.amazonaws.services.ec2.model.DescribeImportImageTasksResult = ???
  def describeImportSnapshotTasks(): com.amazonaws.services.ec2.model.DescribeImportSnapshotTasksResult = ???
  def describeImportSnapshotTasks(x$1: com.amazonaws.services.ec2.model.DescribeImportSnapshotTasksRequest): com.amazonaws.services.ec2.model.DescribeImportSnapshotTasksResult = ???
  def describeInstanceAttribute(x$1: com.amazonaws.services.ec2.model.DescribeInstanceAttributeRequest): com.amazonaws.services.ec2.model.DescribeInstanceAttributeResult = ???
  def describeInstanceStatus(): com.amazonaws.services.ec2.model.DescribeInstanceStatusResult = ???
  def describeInstanceStatus(x$1: com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest): com.amazonaws.services.ec2.model.DescribeInstanceStatusResult = ???
  def describeInstances(): com.amazonaws.services.ec2.model.DescribeInstancesResult = ???
  def describeInternetGateways(): com.amazonaws.services.ec2.model.DescribeInternetGatewaysResult = ???
  def describeInternetGateways(x$1: com.amazonaws.services.ec2.model.DescribeInternetGatewaysRequest): com.amazonaws.services.ec2.model.DescribeInternetGatewaysResult = ???
  def describeKeyPairs(): com.amazonaws.services.ec2.model.DescribeKeyPairsResult = ???
  def describeKeyPairs(x$1: com.amazonaws.services.ec2.model.DescribeKeyPairsRequest): com.amazonaws.services.ec2.model.DescribeKeyPairsResult = ???
  def describeMovingAddresses(): com.amazonaws.services.ec2.model.DescribeMovingAddressesResult = ???
  def describeMovingAddresses(x$1: com.amazonaws.services.ec2.model.DescribeMovingAddressesRequest): com.amazonaws.services.ec2.model.DescribeMovingAddressesResult = ???
  def describeNetworkAcls(): com.amazonaws.services.ec2.model.DescribeNetworkAclsResult = ???
  def describeNetworkAcls(x$1: com.amazonaws.services.ec2.model.DescribeNetworkAclsRequest): com.amazonaws.services.ec2.model.DescribeNetworkAclsResult = ???
  def describeNetworkInterfaceAttribute(x$1: com.amazonaws.services.ec2.model.DescribeNetworkInterfaceAttributeRequest): com.amazonaws.services.ec2.model.DescribeNetworkInterfaceAttributeResult = ???
  def describeNetworkInterfaces(): com.amazonaws.services.ec2.model.DescribeNetworkInterfacesResult = ???
  def describeNetworkInterfaces(x$1: com.amazonaws.services.ec2.model.DescribeNetworkInterfacesRequest): com.amazonaws.services.ec2.model.DescribeNetworkInterfacesResult = ???
  def describePlacementGroups(): com.amazonaws.services.ec2.model.DescribePlacementGroupsResult = ???
  def describePlacementGroups(x$1: com.amazonaws.services.ec2.model.DescribePlacementGroupsRequest): com.amazonaws.services.ec2.model.DescribePlacementGroupsResult = ???
  def describePrefixLists(): com.amazonaws.services.ec2.model.DescribePrefixListsResult = ???
  def describePrefixLists(x$1: com.amazonaws.services.ec2.model.DescribePrefixListsRequest): com.amazonaws.services.ec2.model.DescribePrefixListsResult = ???
  def describeRegions(): com.amazonaws.services.ec2.model.DescribeRegionsResult = ???
  def describeRegions(x$1: com.amazonaws.services.ec2.model.DescribeRegionsRequest): com.amazonaws.services.ec2.model.DescribeRegionsResult = ???
  def describeReservedInstances(): com.amazonaws.services.ec2.model.DescribeReservedInstancesResult = ???
  def describeReservedInstances(x$1: com.amazonaws.services.ec2.model.DescribeReservedInstancesRequest): com.amazonaws.services.ec2.model.DescribeReservedInstancesResult = ???
  def describeReservedInstancesListings(): com.amazonaws.services.ec2.model.DescribeReservedInstancesListingsResult = ???
  def describeReservedInstancesListings(x$1: com.amazonaws.services.ec2.model.DescribeReservedInstancesListingsRequest): com.amazonaws.services.ec2.model.DescribeReservedInstancesListingsResult = ???
  def describeReservedInstancesModifications(): com.amazonaws.services.ec2.model.DescribeReservedInstancesModificationsResult = ???
  def describeReservedInstancesModifications(x$1: com.amazonaws.services.ec2.model.DescribeReservedInstancesModificationsRequest): com.amazonaws.services.ec2.model.DescribeReservedInstancesModificationsResult = ???
  def describeReservedInstancesOfferings(): com.amazonaws.services.ec2.model.DescribeReservedInstancesOfferingsResult = ???
  def describeReservedInstancesOfferings(x$1: com.amazonaws.services.ec2.model.DescribeReservedInstancesOfferingsRequest): com.amazonaws.services.ec2.model.DescribeReservedInstancesOfferingsResult = ???
  def describeRouteTables(): com.amazonaws.services.ec2.model.DescribeRouteTablesResult = ???
  def describeRouteTables(x$1: com.amazonaws.services.ec2.model.DescribeRouteTablesRequest): com.amazonaws.services.ec2.model.DescribeRouteTablesResult = ???
  def describeSecurityGroups(): com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult = ???
  def describeSecurityGroups(x$1: com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest): com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult = ???
  def describeSnapshotAttribute(x$1: com.amazonaws.services.ec2.model.DescribeSnapshotAttributeRequest): com.amazonaws.services.ec2.model.DescribeSnapshotAttributeResult = ???
  def describeSnapshots(): com.amazonaws.services.ec2.model.DescribeSnapshotsResult = ???
  def describeSnapshots(x$1: com.amazonaws.services.ec2.model.DescribeSnapshotsRequest): com.amazonaws.services.ec2.model.DescribeSnapshotsResult = ???
  def describeSpotDatafeedSubscription(): com.amazonaws.services.ec2.model.DescribeSpotDatafeedSubscriptionResult = ???
  def describeSpotDatafeedSubscription(x$1: com.amazonaws.services.ec2.model.DescribeSpotDatafeedSubscriptionRequest): com.amazonaws.services.ec2.model.DescribeSpotDatafeedSubscriptionResult = ???
  def describeSpotFleetInstances(x$1: com.amazonaws.services.ec2.model.DescribeSpotFleetInstancesRequest): com.amazonaws.services.ec2.model.DescribeSpotFleetInstancesResult = ???
  def describeSpotFleetRequestHistory(x$1: com.amazonaws.services.ec2.model.DescribeSpotFleetRequestHistoryRequest): com.amazonaws.services.ec2.model.DescribeSpotFleetRequestHistoryResult = ???
  def describeSpotFleetRequests(): com.amazonaws.services.ec2.model.DescribeSpotFleetRequestsResult = ???
  def describeSpotFleetRequests(x$1: com.amazonaws.services.ec2.model.DescribeSpotFleetRequestsRequest): com.amazonaws.services.ec2.model.DescribeSpotFleetRequestsResult = ???
  def describeSpotInstanceRequests(): com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult = ???
  def describeSpotInstanceRequests(x$1: com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest): com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult = ???
  def describeSpotPriceHistory(): com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryResult = ???
  def describeSpotPriceHistory(x$1: com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryRequest): com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryResult = ???
  def describeSubnets(): com.amazonaws.services.ec2.model.DescribeSubnetsResult = ???
  def describeSubnets(x$1: com.amazonaws.services.ec2.model.DescribeSubnetsRequest): com.amazonaws.services.ec2.model.DescribeSubnetsResult = ???
  def describeTags(): com.amazonaws.services.ec2.model.DescribeTagsResult = ???
  def describeTags(x$1: com.amazonaws.services.ec2.model.DescribeTagsRequest): com.amazonaws.services.ec2.model.DescribeTagsResult = ???
  def describeVolumeAttribute(x$1: com.amazonaws.services.ec2.model.DescribeVolumeAttributeRequest): com.amazonaws.services.ec2.model.DescribeVolumeAttributeResult = ???
  def describeVolumeStatus(): com.amazonaws.services.ec2.model.DescribeVolumeStatusResult = ???
  def describeVolumeStatus(x$1: com.amazonaws.services.ec2.model.DescribeVolumeStatusRequest): com.amazonaws.services.ec2.model.DescribeVolumeStatusResult = ???
  def describeVolumes(): com.amazonaws.services.ec2.model.DescribeVolumesResult = ???
  def describeVolumes(x$1: com.amazonaws.services.ec2.model.DescribeVolumesRequest): com.amazonaws.services.ec2.model.DescribeVolumesResult = ???
  def describeVpcAttribute(x$1: com.amazonaws.services.ec2.model.DescribeVpcAttributeRequest): com.amazonaws.services.ec2.model.DescribeVpcAttributeResult = ???
  def describeVpcClassicLink(): com.amazonaws.services.ec2.model.DescribeVpcClassicLinkResult = ???
  def describeVpcClassicLink(x$1: com.amazonaws.services.ec2.model.DescribeVpcClassicLinkRequest): com.amazonaws.services.ec2.model.DescribeVpcClassicLinkResult = ???
  def describeVpcEndpointServices(): com.amazonaws.services.ec2.model.DescribeVpcEndpointServicesResult = ???
  def describeVpcEndpointServices(x$1: com.amazonaws.services.ec2.model.DescribeVpcEndpointServicesRequest): com.amazonaws.services.ec2.model.DescribeVpcEndpointServicesResult = ???
  def describeVpcEndpoints(): com.amazonaws.services.ec2.model.DescribeVpcEndpointsResult = ???
  def describeVpcEndpoints(x$1: com.amazonaws.services.ec2.model.DescribeVpcEndpointsRequest): com.amazonaws.services.ec2.model.DescribeVpcEndpointsResult = ???
  def describeVpcPeeringConnections(): com.amazonaws.services.ec2.model.DescribeVpcPeeringConnectionsResult = ???
  def describeVpcPeeringConnections(x$1: com.amazonaws.services.ec2.model.DescribeVpcPeeringConnectionsRequest): com.amazonaws.services.ec2.model.DescribeVpcPeeringConnectionsResult = ???
  def describeVpcs(): com.amazonaws.services.ec2.model.DescribeVpcsResult = ???
  def describeVpcs(x$1: com.amazonaws.services.ec2.model.DescribeVpcsRequest): com.amazonaws.services.ec2.model.DescribeVpcsResult = ???
  def describeVpnConnections(): com.amazonaws.services.ec2.model.DescribeVpnConnectionsResult = ???
  def describeVpnConnections(x$1: com.amazonaws.services.ec2.model.DescribeVpnConnectionsRequest): com.amazonaws.services.ec2.model.DescribeVpnConnectionsResult = ???
  def describeVpnGateways(): com.amazonaws.services.ec2.model.DescribeVpnGatewaysResult = ???
  def describeVpnGateways(x$1: com.amazonaws.services.ec2.model.DescribeVpnGatewaysRequest): com.amazonaws.services.ec2.model.DescribeVpnGatewaysResult = ???
  def detachClassicLinkVpc(x$1: com.amazonaws.services.ec2.model.DetachClassicLinkVpcRequest): com.amazonaws.services.ec2.model.DetachClassicLinkVpcResult = ???
  def detachInternetGateway(x$1: com.amazonaws.services.ec2.model.DetachInternetGatewayRequest): Unit = ???
  def detachNetworkInterface(x$1: com.amazonaws.services.ec2.model.DetachNetworkInterfaceRequest): Unit = ???
  def detachVolume(x$1: com.amazonaws.services.ec2.model.DetachVolumeRequest): com.amazonaws.services.ec2.model.DetachVolumeResult = ???
  def detachVpnGateway(x$1: com.amazonaws.services.ec2.model.DetachVpnGatewayRequest): Unit = ???
  def disableVgwRoutePropagation(x$1: com.amazonaws.services.ec2.model.DisableVgwRoutePropagationRequest): Unit = ???
  def disableVpcClassicLink(x$1: com.amazonaws.services.ec2.model.DisableVpcClassicLinkRequest): com.amazonaws.services.ec2.model.DisableVpcClassicLinkResult = ???
  def disassociateAddress(x$1: com.amazonaws.services.ec2.model.DisassociateAddressRequest): Unit = ???
  def disassociateRouteTable(x$1: com.amazonaws.services.ec2.model.DisassociateRouteTableRequest): Unit = ???
  def dryRun[X <: com.amazonaws.AmazonWebServiceRequest](x$1: com.amazonaws.services.ec2.model.DryRunSupportedRequest[X]): com.amazonaws.services.ec2.model.DryRunResult[X] = ???
  def enableVgwRoutePropagation(x$1: com.amazonaws.services.ec2.model.EnableVgwRoutePropagationRequest): Unit = ???
  def enableVolumeIO(x$1: com.amazonaws.services.ec2.model.EnableVolumeIORequest): Unit = ???
  def enableVpcClassicLink(x$1: com.amazonaws.services.ec2.model.EnableVpcClassicLinkRequest): com.amazonaws.services.ec2.model.EnableVpcClassicLinkResult = ???
  def getCachedResponseMetadata(x$1: com.amazonaws.AmazonWebServiceRequest): com.amazonaws.ResponseMetadata = ???
  def getConsoleOutput(x$1: com.amazonaws.services.ec2.model.GetConsoleOutputRequest): com.amazonaws.services.ec2.model.GetConsoleOutputResult = ???
  def getPasswordData(x$1: com.amazonaws.services.ec2.model.GetPasswordDataRequest): com.amazonaws.services.ec2.model.GetPasswordDataResult = ???
  def importImage(): com.amazonaws.services.ec2.model.ImportImageResult = ???
  def importImage(x$1: com.amazonaws.services.ec2.model.ImportImageRequest): com.amazonaws.services.ec2.model.ImportImageResult = ???
  def importInstance(x$1: com.amazonaws.services.ec2.model.ImportInstanceRequest): com.amazonaws.services.ec2.model.ImportInstanceResult = ???
  def importKeyPair(x$1: com.amazonaws.services.ec2.model.ImportKeyPairRequest): com.amazonaws.services.ec2.model.ImportKeyPairResult = ???
  def importSnapshot(): com.amazonaws.services.ec2.model.ImportSnapshotResult = ???
  def importSnapshot(x$1: com.amazonaws.services.ec2.model.ImportSnapshotRequest): com.amazonaws.services.ec2.model.ImportSnapshotResult = ???
  def importVolume(x$1: com.amazonaws.services.ec2.model.ImportVolumeRequest): com.amazonaws.services.ec2.model.ImportVolumeResult = ???
  def modifyImageAttribute(x$1: com.amazonaws.services.ec2.model.ModifyImageAttributeRequest): Unit = ???
  def modifyInstanceAttribute(x$1: com.amazonaws.services.ec2.model.ModifyInstanceAttributeRequest): Unit = ???
  def modifyNetworkInterfaceAttribute(x$1: com.amazonaws.services.ec2.model.ModifyNetworkInterfaceAttributeRequest): Unit = ???
  def modifyReservedInstances(x$1: com.amazonaws.services.ec2.model.ModifyReservedInstancesRequest): com.amazonaws.services.ec2.model.ModifyReservedInstancesResult = ???
  def modifySnapshotAttribute(x$1: com.amazonaws.services.ec2.model.ModifySnapshotAttributeRequest): Unit = ???
  def modifySubnetAttribute(x$1: com.amazonaws.services.ec2.model.ModifySubnetAttributeRequest): Unit = ???
  def modifyVolumeAttribute(x$1: com.amazonaws.services.ec2.model.ModifyVolumeAttributeRequest): Unit = ???
  def modifyVpcAttribute(x$1: com.amazonaws.services.ec2.model.ModifyVpcAttributeRequest): Unit = ???
  def modifyVpcEndpoint(x$1: com.amazonaws.services.ec2.model.ModifyVpcEndpointRequest): com.amazonaws.services.ec2.model.ModifyVpcEndpointResult = ???
  def monitorInstances(x$1: com.amazonaws.services.ec2.model.MonitorInstancesRequest): com.amazonaws.services.ec2.model.MonitorInstancesResult = ???
  def moveAddressToVpc(x$1: com.amazonaws.services.ec2.model.MoveAddressToVpcRequest): com.amazonaws.services.ec2.model.MoveAddressToVpcResult = ???
  def purchaseReservedInstancesOffering(x$1: com.amazonaws.services.ec2.model.PurchaseReservedInstancesOfferingRequest): com.amazonaws.services.ec2.model.PurchaseReservedInstancesOfferingResult = ???
  def rebootInstances(x$1: com.amazonaws.services.ec2.model.RebootInstancesRequest): Unit = ???
  def registerImage(x$1: com.amazonaws.services.ec2.model.RegisterImageRequest): com.amazonaws.services.ec2.model.RegisterImageResult = ???
  def rejectVpcPeeringConnection(x$1: com.amazonaws.services.ec2.model.RejectVpcPeeringConnectionRequest): com.amazonaws.services.ec2.model.RejectVpcPeeringConnectionResult = ???
  def releaseAddress(x$1: com.amazonaws.services.ec2.model.ReleaseAddressRequest): Unit = ???
  def replaceNetworkAclAssociation(x$1: com.amazonaws.services.ec2.model.ReplaceNetworkAclAssociationRequest): com.amazonaws.services.ec2.model.ReplaceNetworkAclAssociationResult = ???
  def replaceNetworkAclEntry(x$1: com.amazonaws.services.ec2.model.ReplaceNetworkAclEntryRequest): Unit = ???
  def replaceRoute(x$1: com.amazonaws.services.ec2.model.ReplaceRouteRequest): Unit = ???
  def replaceRouteTableAssociation(x$1: com.amazonaws.services.ec2.model.ReplaceRouteTableAssociationRequest): com.amazonaws.services.ec2.model.ReplaceRouteTableAssociationResult = ???
  def reportInstanceStatus(x$1: com.amazonaws.services.ec2.model.ReportInstanceStatusRequest): Unit = ???
  def requestSpotFleet(x$1: com.amazonaws.services.ec2.model.RequestSpotFleetRequest): com.amazonaws.services.ec2.model.RequestSpotFleetResult = ???
  def requestSpotInstances(x$1: com.amazonaws.services.ec2.model.RequestSpotInstancesRequest): com.amazonaws.services.ec2.model.RequestSpotInstancesResult = ???
  def resetImageAttribute(x$1: com.amazonaws.services.ec2.model.ResetImageAttributeRequest): Unit = ???
  def resetInstanceAttribute(x$1: com.amazonaws.services.ec2.model.ResetInstanceAttributeRequest): Unit = ???
  def resetNetworkInterfaceAttribute(x$1: com.amazonaws.services.ec2.model.ResetNetworkInterfaceAttributeRequest): Unit = ???
  def resetSnapshotAttribute(x$1: com.amazonaws.services.ec2.model.ResetSnapshotAttributeRequest): Unit = ???
  def restoreAddressToClassic(x$1: com.amazonaws.services.ec2.model.RestoreAddressToClassicRequest): com.amazonaws.services.ec2.model.RestoreAddressToClassicResult = ???
  def revokeSecurityGroupEgress(x$1: com.amazonaws.services.ec2.model.RevokeSecurityGroupEgressRequest): Unit = ???
  def revokeSecurityGroupIngress(): Unit = ???
  def revokeSecurityGroupIngress(x$1: com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest): Unit = ???
  def runInstances(x$1: com.amazonaws.services.ec2.model.RunInstancesRequest): com.amazonaws.services.ec2.model.RunInstancesResult = ???
  def setEndpoint(x$1: String): Unit = ???
  def setRegion(x$1: com.amazonaws.regions.Region): Unit = ???
  def shutdown(): Unit = ???
  def startInstances(x$1: com.amazonaws.services.ec2.model.StartInstancesRequest): com.amazonaws.services.ec2.model.StartInstancesResult = ???
  def stopInstances(x$1: com.amazonaws.services.ec2.model.StopInstancesRequest): com.amazonaws.services.ec2.model.StopInstancesResult = ???
  def terminateInstances(x$1: com.amazonaws.services.ec2.model.TerminateInstancesRequest): com.amazonaws.services.ec2.model.TerminateInstancesResult = ???
  def unassignPrivateIpAddresses(x$1: com.amazonaws.services.ec2.model.UnassignPrivateIpAddressesRequest): Unit = ???
  def unmonitorInstances(x$1: com.amazonaws.services.ec2.model.UnmonitorInstancesRequest): com.amazonaws.services.ec2.model.UnmonitorInstancesResult = ???

}