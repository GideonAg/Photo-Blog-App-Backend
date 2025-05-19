package com.photoblog.disaster;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.photoblog.utils.SNSUtil;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsRequest;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse;
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RegionMonitorHandler implements RequestHandler<Map<String, String>, Map<String, String>> {

    private final CloudWatchClient cloudWatchClient = CloudWatchClient.builder().build();
    private final SNSUtil snsUtil = new SNSUtil();
    private static final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static final int FAILURE_THRESHOLD = 2;

    private final String primaryRegion = System.getenv("PRIMARY_REGION");
    private final String backupAlertTopic = System.getenv("BACKUP_ALERT_TOPIC");
    private final String frontendAlarmName = System.getenv("FRONTEND_ALARM_NAME");
    private final String backendAlarmName = System.getenv("BACKEND_ALARM_NAME");

    @Override
    public Map<String, String> handleRequest(Map<String, String> input, Context context) {
        Map<String, String> response = new HashMap<>();
        try {
            Map<String, Boolean> healthStatus = checkPrimaryRegionHealth(context);
            boolean isFrontendHealthy = healthStatus.get("frontend");
            boolean isBackendHealthy = healthStatus.get("backend");
            boolean isPrimaryRegionHealthy = isFrontendHealthy && isBackendHealthy;

            Map<String, String> alertMessage = new HashMap<>();
            alertMessage.put("event", "region_monitor_check");
            alertMessage.put("timestamp", Instant.now().toString());
            alertMessage.put("primaryRegion", primaryRegion);
            alertMessage.put("frontendHealthy", String.valueOf(isFrontendHealthy));
            alertMessage.put("backendHealthy", String.valueOf(isBackendHealthy));
            alertMessage.put("healthy", String.valueOf(isPrimaryRegionHealthy));

            if (!isPrimaryRegionHealthy) {
                int failures = consecutiveFailures.incrementAndGet();
                alertMessage.put("consecutiveFailures", String.valueOf(failures));
                context.getLogger().log("Primary region unhealthy - Frontend: " + isFrontendHealthy + ", Backend: " + isBackendHealthy + ", Failures: " + failures);
                try {
                    snsUtil.publishMessage(backupAlertTopic, alertMessage, context);
                } catch (Exception e) {
                    context.getLogger().log("Failed to publish initial alert message: " + e.getMessage());
                }

                if (failures >= FAILURE_THRESHOLD) {
                    response.put("status", "triggered");
                    response.put("message", "Disaster recovery should be triggered due to primary region outage");
                    consecutiveFailures.set(0);
                    alertMessage.put("event", "disaster_recovery_triggered");
                    alertMessage.put("details", "DR should be triggered due to " + failures + " consecutive failures. Frontend Healthy: " + isFrontendHealthy + ", Backend Healthy: " + isBackendHealthy);
                    try {
                        snsUtil.publishMessage(backupAlertTopic, alertMessage, context);
                    } catch (Exception e) {
                        context.getLogger().log("Failed to publish disaster recovery message: " + e.getMessage());
                    }
                } else {
                    response.put("status", "warning");
                    response.put("message", "Primary region unhealthy, waiting for threshold (" + failures + "/" + FAILURE_THRESHOLD + ")");
                }
            } else {
                consecutiveFailures.set(0);
                response.put("status", "healthy");
                response.put("message", "Primary region is healthy");
                try {
                    snsUtil.publishMessage(backupAlertTopic, alertMessage, context);
                } catch (Exception e) {
                    context.getLogger().log("Failed to publish healthy status message: " + e.getMessage());
                }
            }

            return response;

        } catch (Exception e) {
            context.getLogger().log("Error in region monitor: " + e.getMessage());
            response.put("status", "error");
            response.put("errorMessage", e.getMessage());
            Map<String, String> errorMessage = new HashMap<>();
            errorMessage.put("event", "region_monitor_error");
            errorMessage.put("error", e.getMessage());
            errorMessage.put("timestamp", Instant.now().toString());
            try {
                snsUtil.publishMessage(backupAlertTopic, errorMessage, context);
            } catch (Exception e2) {
                context.getLogger().log("Failed to publish error message: " + e2.getMessage());
            }
            return response;
        } finally {
            cloudWatchClient.close();
            try {
                Thread.sleep(5000);
                snsUtil.close();
            } catch (InterruptedException e) {
                context.getLogger().log("Interrupted while waiting to close SNSUtil: " + e.getMessage());
            }
        }
    }

    private Map<String, Boolean> checkPrimaryRegionHealth(Context context) {
        Map<String, Boolean> healthStatus = new HashMap<>();
        boolean isFrontendHealthy = true;
        boolean isBackendHealthy = true;

        try {
            DescribeAlarmsRequest request = DescribeAlarmsRequest.builder()
                    .alarmNames(frontendAlarmName, backendAlarmName)
                    .build();
            DescribeAlarmsResponse response = cloudWatchClient.describeAlarms(request);

            /*
             * Just to simulate backend not reachable
             */
            double simulated5xxErrors = 15.0;
            context.getLogger().log("Simulated 5XX errors detected: " + simulated5xxErrors);
            if (simulated5xxErrors > 10)
                isBackendHealthy = false;

            for (MetricAlarm alarm : response.metricAlarms()) {
                String alarmName = alarm.alarmName();
                String state = alarm.stateValue().toString();

                if (alarmName.equals(frontendAlarmName)) {
                    isFrontendHealthy = !state.equals("ALARM");
                    context.getLogger().log("Frontend Alarm State: " + state + ", Healthy: " + isFrontendHealthy);
                } else if (alarmName.equals(backendAlarmName)) {
                    isBackendHealthy = !state.equals("ALARM");
                    context.getLogger().log("Backend Alarm State: " + state + ", Healthy: " + isBackendHealthy);
                }
            }
        } catch (Exception e) {
            context.getLogger().log("Error checking CloudWatch Alarms: " + e.getMessage());
            isFrontendHealthy = false;
            isBackendHealthy = false;
        }

        healthStatus.put("frontend", isFrontendHealthy);
        healthStatus.put("backend", isBackendHealthy);
        return healthStatus;
    }
}