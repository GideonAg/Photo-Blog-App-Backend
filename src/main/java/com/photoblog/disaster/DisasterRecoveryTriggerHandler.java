package com.photoblog.disaster;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoblog.utils.SNSUtil;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class DisasterRecoveryTriggerHandler implements RequestHandler<Map<String, String>, Map<String, String>> {

    private final LambdaClient lambdaClient = LambdaClient.builder().build();
    private final SNSUtil snsUtil = new SNSUtil();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String restoreFunctionArn = System.getenv("RESTORE_FUNCTION_ARN");
    private final String systemAlertTopic = System.getenv("BACKUP_ALERT_TOPIC");

    @Override
    public Map<String, String> handleRequest(Map<String, String> input, Context context) {
        Map<String, String> response = new HashMap<>();
        try {
            Map<String, String> alertMessage = new HashMap<>();
            alertMessage.put("event", "disaster_recovery_triggered");
            alertMessage.put("timestamp", Instant.now().toString());
            alertMessage.put("triggerDetails", input.toString());
            snsUtil.publishMessage(systemAlertTopic, alertMessage, context);
            context.getLogger().log("Published DR trigger alert to: " + systemAlertTopic);

            Map<String, String> restoreInput = new HashMap<>();
            restoreInput.put("action", "restore");
            String payload = objectMapper.writeValueAsString(restoreInput);
            InvokeRequest invokeRequest = InvokeRequest.builder()
                    .functionName(restoreFunctionArn)
                    .payload(SdkBytes.fromUtf8String(payload))
                    .build();
            lambdaClient.invoke(invokeRequest);
            context.getLogger().log("Invoked RestoreHandlerFunction: " + restoreFunctionArn);

            response.put("status", "success");
            response.put("message", "Disaster recovery triggered, restoration invoked");
            return response;

        } catch (Exception e) {
            context.getLogger().log("Error in DR trigger: " + e.getMessage());
            response.put("status", "error");
            response.put("errorMessage", e.getMessage());
            Map<String, String> errorMessage = new HashMap<>();
            errorMessage.put("event", "dr_trigger_error");
            errorMessage.put("error", e.getMessage());
            snsUtil.publishMessage(systemAlertTopic, errorMessage, context);
            return response;
        } finally {
            lambdaClient.close();
        }
    }
}
