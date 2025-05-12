package com.photoblog.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class QueueUtil {
    private final String bucketName = "";
    private final String sqsQueueUrl;
    private final ObjectMapper objectMapper;
    private final SqsClient sqsClient = SqsClient.builder().build();

    public QueueUtil() {
        this.bucketName = bucketName;
        this.sqsQueueUrl = sqsQueueUrl;
        this.objectMapper = new ObjectMapper();
    }

    public SendMessageResponse sendToQueue(String objectKey, String userEmail, LocalDateTime uploadTimestamp) throws Exception {
        Map<String, String> messageBody = new HashMap<>();
        messageBody.put("objectKey", objectKey);
        messageBody.put("uploadDate", String.valueOf(uploadTimestamp));
        messageBody.put("uploadedBy", userEmail);
        messageBody.put("bucket", bucketName);

        String messageJson;
        try {
            messageJson = objectMapper.writeValueAsString(messageBody);
        } catch (Exception e) {
            // Fallback in case serialization fails
            messageJson = String.format(
                    "{\"objectKey\":\"%s\",\"uploadDate\":\"%s\",\"uploadedBy\":\"%s\",\"bucket\":\"%s\"}",
                    objectKey, uploadTimestamp, userEmail != null ? userEmail : "unknown", bucketName
            );
        }
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(sqsQueueUrl)
                .messageBody(messageJson)
                .build();
        return sqsClient.sendMessage(sendMessageRequest);
    }
}
