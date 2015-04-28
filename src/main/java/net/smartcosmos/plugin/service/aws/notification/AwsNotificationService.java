package net.smartcosmos.plugin.service.aws.notification;

/*
 * *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*
 * SMART COSMOS AWS SNS Notification Service Plugin
 * ===============================================================================
 * Copyright (C) 2013 - 2015 Smartrac Technology Fletcher, Inc.
 * ===============================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#
 */

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.ConfirmSubscriptionRequest;
import com.amazonaws.services.sns.model.ConfirmSubscriptionResult;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.DeleteTopicRequest;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sns.model.UnsubscribeRequest;
import com.google.common.base.Preconditions;
import net.smartcosmos.model.base.EntityReferenceType;
import net.smartcosmos.model.context.IAccount;
import net.smartcosmos.model.event.EventType;
import net.smartcosmos.model.integration.INotificationEndpoint;
import net.smartcosmos.platform.api.oauth.INotificationResultObject;
import net.smartcosmos.platform.api.service.IEventService;
import net.smartcosmos.platform.api.service.INotificationService;
import net.smartcosmos.platform.base.AbstractAwsService;
import net.smartcosmos.platform.pojo.oauth.NotificationResultObject;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.smartcosmos.Field.EVENT_TYPE;

public class AwsNotificationService extends AbstractAwsService<AWSCredentials>
        implements INotificationService
{
    private static final Logger LOG = LoggerFactory.getLogger(AwsNotificationService.class);

    public AwsNotificationService()
    {
        super("8AC7970C42538B3B0142538CFDC5000A", "AWS SNS Notification Service");
    }

    protected static String stripUrnUuidPrefix(IAccount account)
    {
        return account.getUrn().substring(9);
    }

    @Override
    public String createTopic(INotificationEndpoint notificationEndpoint)
    {
        Preconditions.checkArgument((notificationEndpoint != null),
                "Notification endpoint must not be null");

        Preconditions.checkArgument((notificationEndpoint.getTopicArn() == null),
                "Notification already has a notification URL defined");

        AmazonSNS sns = new AmazonSNSClient(credentials);
        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
        sns.setRegion(usEast1);

        String topicArn = null;

        try
        {
            String topicName = stripUrnUuidPrefix(notificationEndpoint.getAccount());

            LOG.info("Topic Name Assigned: " + topicName);

            CreateTopicRequest request = new CreateTopicRequest(topicName);
            CreateTopicResult result = sns.createTopic(request);

            topicArn = result.getTopicArn();

            //
            // Event
            //
            INotificationResultObject<IAccount> nro = new NotificationResultObject<>(EntityReferenceType.Account,
                    notificationEndpoint.getAccount(),
                    result.getTopicArn());
            IEventService eventService = context.getServiceFactory().getEventService(notificationEndpoint.getAccount());
            eventService.recordEvent(EventType.NotificationEnroll, notificationEndpoint.getAccount(), null, nro);

        } finally
        {
            sns.shutdown();
        }

        return topicArn;
    }

    @Override
    public String subscribe(INotificationEndpoint notificationEndpoint)
    {
        String subscriptionArn;

        Preconditions.checkArgument((notificationEndpoint != null),
                "Notification endpoint must not be null");

        Preconditions.checkArgument((notificationEndpoint.getTopicArn() != null),
                "Notification Topic ARN must not be null");

        AmazonSNS sns = new AmazonSNSClient(credentials);
        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
        sns.setRegion(usEast1);

        try
        {
            SubscribeRequest request = new SubscribeRequest(notificationEndpoint.getTopicArn(),
                    "https",
                    notificationEndpoint.getNotificationEndpointUrl());

            SubscribeResult result = sns.subscribe(request);

            subscriptionArn = result.getSubscriptionArn();

            //
            // Event
            //
            INotificationResultObject<IAccount> nro = new NotificationResultObject<>(EntityReferenceType.Account,
                    notificationEndpoint.getAccount(),
                    result.getSubscriptionArn());
            IEventService eventService = context.getServiceFactory().getEventService(notificationEndpoint.getAccount());
            eventService.recordEvent(EventType.NotificationSubscribe, notificationEndpoint.getAccount(), null, nro);

        } finally
        {
            sns.shutdown();
        }

        return subscriptionArn;
    }

    @Override
    public void deleteTopic(INotificationEndpoint notificationEndpoint)
    {
        Preconditions.checkArgument((notificationEndpoint != null),
                "Notification endpoint must not be null");

        Preconditions.checkArgument((notificationEndpoint.getTopicArn() != null),
                "Notification Topic ARN must not be null");

        AmazonSNS sns = new AmazonSNSClient(credentials);
        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
        sns.setRegion(usEast1);

        try
        {
            DeleteTopicRequest request = new DeleteTopicRequest(notificationEndpoint.getTopicArn());
            sns.deleteTopic(request);
        } finally
        {
            sns.shutdown();
        }

        //
        // Event
        //
        INotificationResultObject<IAccount> nro = new NotificationResultObject<>(EntityReferenceType.Account,
                notificationEndpoint.getAccount(),
                "");
        IEventService eventService = context.getServiceFactory().getEventService(notificationEndpoint.getAccount());
        eventService.recordEvent(EventType.NotificationWithdrawn, notificationEndpoint.getAccount(), null, nro);
    }

    @Override
    public void publish(INotificationEndpoint notificationEndpoint, String json)
    {
        Preconditions.checkArgument((notificationEndpoint != null),
                "Endpoint must not be null");

        Preconditions.checkArgument((notificationEndpoint.getTopicArn() != null),
                "Endpoint is missing a notification URL definition");

        Preconditions.checkArgument((!notificationEndpoint.isPendingConfirmation()),
                "Endpoint has not yet been confirmed");

        AmazonSNS sns = new AmazonSNSClient(credentials);
        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
        sns.setRegion(usEast1);

        try
        {
            String subject = "SMART COSMOS Objects Event Notification";

            JSONObject jsonObject = null;
            try
            {
                jsonObject = new JSONObject(json);
                if (jsonObject.has(EVENT_TYPE))
                {
                    String eventType = jsonObject.getString(EVENT_TYPE);
                    subject = "Objects Event: " + eventType;
                }
            } catch (JSONException e)
            {
                e.printStackTrace();
            }

            PublishRequest request = new PublishRequest(notificationEndpoint.getTopicArn(), json, subject);
            PublishResult result = sns.publish(request);

            //
            // Event
            //
            INotificationResultObject<IAccount> nro = new NotificationResultObject<>(EntityReferenceType.Account,
                    notificationEndpoint.getAccount(),
                    result.getMessageId());
            IEventService eventService = context.getServiceFactory().getEventService(notificationEndpoint.getAccount());
            eventService.recordEvent(EventType.NotificationBroadcast, notificationEndpoint.getAccount(), null, nro);

        } finally
        {
            sns.shutdown();
        }
    }

    @Override
    public void unsubscribe(INotificationEndpoint notificationEndpoint)
    {
        Preconditions.checkArgument((notificationEndpoint != null),
                "Notification endpoint must not be null");

        AmazonSNS sns = new AmazonSNSClient(credentials);
        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
        sns.setRegion(usEast1);

        try
        {

            UnsubscribeRequest request = new UnsubscribeRequest(notificationEndpoint.getSubscriptionArn());
            sns.unsubscribe(request);

            //
            // Event
            //
            INotificationResultObject<IAccount> nro = new NotificationResultObject<>(EntityReferenceType.Account,
                    notificationEndpoint.getAccount(),
                    "");
            IEventService eventService = context.getServiceFactory().getEventService(notificationEndpoint.getAccount());
            eventService.recordEvent(EventType.NotificationUnsubscribe, notificationEndpoint.getAccount(), null, nro);
        } finally
        {
            sns.shutdown();
        }
    }

    @Override
    public void confirmSubscription(INotificationEndpoint notificationEndpoint, String token)
    {
        Preconditions.checkArgument((notificationEndpoint != null),
                "Notification endpoint must not be null");

        Preconditions.checkArgument((notificationEndpoint.getTopicArn() != null),
                "Notification Topic ARN must not be null");

        AmazonSNS sns = new AmazonSNSClient(credentials);
        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
        sns.setRegion(usEast1);

        try
        {
            ConfirmSubscriptionRequest request
                    = new ConfirmSubscriptionRequest(notificationEndpoint.getTopicArn(), token);

            ConfirmSubscriptionResult result = sns.confirmSubscription(request);

            //
            // Event
            //
            INotificationResultObject<IAccount> nro = new NotificationResultObject<>(EntityReferenceType.Account,
                    notificationEndpoint.getAccount(),
                    result.getSubscriptionArn());
            IEventService eventService = context.getServiceFactory().getEventService(notificationEndpoint.getAccount());
            eventService.recordEvent(EventType.NotificationSubscriptionConfirmed,
                    notificationEndpoint.getAccount(),
                    null,
                    nro);
        } finally
        {
            sns.shutdown();
        }
    }

    @Override
    public boolean isHealthy()
    {
        try
        {
            AmazonSNS sns = new AmazonSNSClient(credentials);
            Region usEast1 = Region.getRegion(Regions.US_EAST_1);
            sns.setRegion(usEast1);
            sns.listTopics();
            return true;
        } catch (Exception e)
        {
            return false;
        }
    }

    @Override
    protected AWSCredentials createCloudCredentials(String accessKey, String secretAccessKey)
    {
        return new BasicAWSCredentials(accessKey, secretAccessKey);
    }
}
