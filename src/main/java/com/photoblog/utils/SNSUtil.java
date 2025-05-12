package com.photoblog.utils;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

public class SNSUtil {

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    public SNSUtil() {
        String region = System.getenv("PRIMARY_REGION");
        if (region == null) {
            throw new IllegalStateException("PRIMARY_REGION environment variable is not set");
        }
        this.snsClient = SnsClient.builder()
                .region(Region.of(region))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void publishMessage(String topicArn, String message, Context context) {
        try {
            PublishRequest publishRequest = PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(message)
                    .build();
            snsClient.publish(publishRequest);
            context.getLogger().log("Published message to SNS topic: " + topicArn);
        } catch (SnsException e) {
            context.getLogger().log("Failed to publish to SNS topic: " + topicArn + ", error: " + e.getMessage());
            throw new RuntimeException("Failed to publish SNS message: " + e.getMessage(), e);
        }
    }

    public void publishMessage(String topicArn, Object message, Context context) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            publishMessage(topicArn, jsonMessage, context);
        } catch (Exception e) {
            context.getLogger().log("Failed to serialize message for SNS topic: " + topicArn + ", error: " + e.getMessage());
            throw new RuntimeException("Failed to serialize SNS message: " + e.getMessage(), e);
        }
    }

    public void close() {
        snsClient.close();
    }
}
