package com.photoblog.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class QueueUtil {
    private final String bucketName;
    private final String sqsQueueUrl;
    private final String deadLetterQueueUrl;
    private final ObjectMapper objectMapper;
    private final SqsClient sqsClient = SqsClient.builder().build();

    public QueueUtil() {
        this.bucketName = System.getenv("STAGING_BUCKET");
        this.sqsQueueUrl = System.getenv("IMAGE_PROCESSING_QUEUE");
        this.deadLetterQueueUrl = System.getenv("DEAD_LETTER_QUEUE");
        this.objectMapper = new ObjectMapper();
    }

    public SendMessageResponse sendToQueue(
            String objectKey,
            String userId,
            String email,
            String firstName,
            String lastName,
            LocalDateTime uploadTimestamp
    ) throws Exception {
        Map<String, String> messageBody = new HashMap<>();
        messageBody.put("objectKey", objectKey);
        messageBody.put("uploadDate", String.valueOf(uploadTimestamp));
        messageBody.put("userId", userId);
        messageBody.put("email", email);
        messageBody.put("firstName", firstName);
        messageBody.put("lastName", lastName);
        messageBody.put("bucket", bucketName);

        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(messageBody);
        } catch (Exception e) {
            messageJson = String.format(
                    "{\"objectKey\":\"%s\",\"uploadDate\":\"%s\",\"bucket\":\"%s\"}",
                    objectKey, uploadTimestamp, bucketName
            );
        }
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(sqsQueueUrl)
                .messageBody(messageJson)
                .build();
        return sqsClient.sendMessage(sendMessageRequest);
 
    }

    
    
}
