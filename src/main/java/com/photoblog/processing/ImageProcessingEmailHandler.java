package com.photoblog.processing;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.photoblog.utils.SESUtil;
import java.util.HashMap;
import java.util.Map;

public class ImageProcessingEmailHandler implements RequestHandler<Map<String, String>, Map<String, String>> {
    private final SESUtil sesUtil = new SESUtil();

    @Override
    public Map<String, String> handleRequest(Map<String, String> input, Context context) {
        Map<String, String> response = new HashMap<>();

        try {
            String recipientEmail = "provide recipient email";
            String subject = "Image Processing Notification";
            String body = "image is processing";

            // Send email using SESUtil
            sesUtil.sendEmail(recipientEmail, subject, body);
            context.getLogger().log("Test email sent to: " + recipientEmail);

            response.put("status", "success");
            response.put("message", "Test email sent successfully");
        } catch (Exception e) {
            context.getLogger().log("Error sending test email: " + e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to send test email: " + e.getMessage());
        }

        return response;
    }
}