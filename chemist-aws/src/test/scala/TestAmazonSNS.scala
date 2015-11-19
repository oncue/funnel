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

import com.amazonaws.services.sns.AmazonSNS

class TestAmazonSNS extends AmazonSNS {
  def addPermission(x$1: String,x$2: String,x$3: java.util.List[String],x$4: java.util.List[String]): Unit = ???
  def addPermission(x$1: com.amazonaws.services.sns.model.AddPermissionRequest): Unit = ???
  def confirmSubscription(x$1: String,x$2: String): com.amazonaws.services.sns.model.ConfirmSubscriptionResult = ???
  def confirmSubscription(x$1: String,x$2: String,x$3: String): com.amazonaws.services.sns.model.ConfirmSubscriptionResult = ???
  def confirmSubscription(x$1: com.amazonaws.services.sns.model.ConfirmSubscriptionRequest): com.amazonaws.services.sns.model.ConfirmSubscriptionResult = ???
  def createPlatformApplication(x$1: com.amazonaws.services.sns.model.CreatePlatformApplicationRequest): com.amazonaws.services.sns.model.CreatePlatformApplicationResult = ???
  def createPlatformEndpoint(x$1: com.amazonaws.services.sns.model.CreatePlatformEndpointRequest): com.amazonaws.services.sns.model.CreatePlatformEndpointResult = ???
  def createTopic(x$1: String): com.amazonaws.services.sns.model.CreateTopicResult = ???
  def createTopic(x$1: com.amazonaws.services.sns.model.CreateTopicRequest): com.amazonaws.services.sns.model.CreateTopicResult = ???
  def deleteEndpoint(x$1: com.amazonaws.services.sns.model.DeleteEndpointRequest): Unit = ???
  def deletePlatformApplication(x$1: com.amazonaws.services.sns.model.DeletePlatformApplicationRequest): Unit = ???
  def deleteTopic(x$1: String): Unit = ???
  def deleteTopic(x$1: com.amazonaws.services.sns.model.DeleteTopicRequest): Unit = ???
  def getCachedResponseMetadata(x$1: com.amazonaws.AmazonWebServiceRequest): com.amazonaws.ResponseMetadata = ???
  def getEndpointAttributes(x$1: com.amazonaws.services.sns.model.GetEndpointAttributesRequest): com.amazonaws.services.sns.model.GetEndpointAttributesResult = ???
  def getPlatformApplicationAttributes(x$1: com.amazonaws.services.sns.model.GetPlatformApplicationAttributesRequest): com.amazonaws.services.sns.model.GetPlatformApplicationAttributesResult = ???
  def getSubscriptionAttributes(x$1: String): com.amazonaws.services.sns.model.GetSubscriptionAttributesResult = ???
  def getSubscriptionAttributes(x$1: com.amazonaws.services.sns.model.GetSubscriptionAttributesRequest): com.amazonaws.services.sns.model.GetSubscriptionAttributesResult = ???
  def getTopicAttributes(x$1: String): com.amazonaws.services.sns.model.GetTopicAttributesResult = ???
  def getTopicAttributes(x$1: com.amazonaws.services.sns.model.GetTopicAttributesRequest): com.amazonaws.services.sns.model.GetTopicAttributesResult = ???
  def listEndpointsByPlatformApplication(x$1: com.amazonaws.services.sns.model.ListEndpointsByPlatformApplicationRequest): com.amazonaws.services.sns.model.ListEndpointsByPlatformApplicationResult = ???
  def listPlatformApplications(): com.amazonaws.services.sns.model.ListPlatformApplicationsResult = ???
  def listPlatformApplications(x$1: com.amazonaws.services.sns.model.ListPlatformApplicationsRequest): com.amazonaws.services.sns.model.ListPlatformApplicationsResult = ???
  def listSubscriptions(x$1: String): com.amazonaws.services.sns.model.ListSubscriptionsResult = ???
  def listSubscriptions(): com.amazonaws.services.sns.model.ListSubscriptionsResult = ???
  def listSubscriptions(x$1: com.amazonaws.services.sns.model.ListSubscriptionsRequest): com.amazonaws.services.sns.model.ListSubscriptionsResult = ???
  def listSubscriptionsByTopic(x$1: String): com.amazonaws.services.sns.model.ListSubscriptionsByTopicResult = ???
  def listSubscriptionsByTopic(x$1: String,x$2: String): com.amazonaws.services.sns.model.ListSubscriptionsByTopicResult = ???
  def listSubscriptionsByTopic(x$1: com.amazonaws.services.sns.model.ListSubscriptionsByTopicRequest): com.amazonaws.services.sns.model.ListSubscriptionsByTopicResult = ???
  def listTopics(x$1: String): com.amazonaws.services.sns.model.ListTopicsResult = ???
  def listTopics(): com.amazonaws.services.sns.model.ListTopicsResult = ???
  def listTopics(x$1: com.amazonaws.services.sns.model.ListTopicsRequest): com.amazonaws.services.sns.model.ListTopicsResult = ???
  def publish(x$1: String,x$2: String,x$3: String): com.amazonaws.services.sns.model.PublishResult = ???
  def publish(x$1: String,x$2: String): com.amazonaws.services.sns.model.PublishResult = ???
  def publish(x$1: com.amazonaws.services.sns.model.PublishRequest): com.amazonaws.services.sns.model.PublishResult = ???
  def removePermission(x$1: String,x$2: String): Unit = ???
  def removePermission(x$1: com.amazonaws.services.sns.model.RemovePermissionRequest): Unit = ???
  def setEndpoint(x$1: String): Unit = ???
  def setEndpointAttributes(x$1: com.amazonaws.services.sns.model.SetEndpointAttributesRequest): Unit = ???
  def setPlatformApplicationAttributes(x$1: com.amazonaws.services.sns.model.SetPlatformApplicationAttributesRequest): Unit = ???
  def setRegion(x$1: com.amazonaws.regions.Region): Unit = ()
  def setSubscriptionAttributes(x$1: String,x$2: String,x$3: String): Unit = ???
  def setSubscriptionAttributes(x$1: com.amazonaws.services.sns.model.SetSubscriptionAttributesRequest): Unit = ???
  def setTopicAttributes(x$1: String,x$2: String,x$3: String): Unit = ???
  def setTopicAttributes(x$1: com.amazonaws.services.sns.model.SetTopicAttributesRequest): Unit = ???
  def shutdown(): Unit = ()
  def subscribe(x$1: String,x$2: String,x$3: String): com.amazonaws.services.sns.model.SubscribeResult = ???
  def subscribe(x$1: com.amazonaws.services.sns.model.SubscribeRequest): com.amazonaws.services.sns.model.SubscribeResult = ???
  def unsubscribe(x$1: String): Unit = ()
  def unsubscribe(x$1: com.amazonaws.services.sns.model.UnsubscribeRequest): Unit = ???
}
