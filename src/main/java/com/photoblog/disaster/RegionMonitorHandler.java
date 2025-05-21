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

public class RegionMonitorHandler implements RequestHandler<Map<String, Object>, Map<String, String>> {

    private static CloudWatchClient cloudWatchClient;
    private static SNSUtil snsUtil;

    private static final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static final int FAILURE_THRESHOLD = 2;

    private final String primaryRegion = System.getenv("PRIMARY_REGION");
    private final String backupAlertTopic = System.getenv("BACKUP_ALERT_TOPIC");
    private final String frontendAlarmName = System.getenv("FRONTEND_ALARM_NAME");
    private final String backendAlarmName = System.getenv("BACKEND_ALARM_NAME");

    private synchronized CloudWatchClient getCloudWatchClient() {
        if (cloudWatchClient == null) {
            cloudWatchClient = CloudWatchClient.builder().build();
        }
        return cloudWatchClient;
    }

    private synchronized SNSUtil getSNSUtil() {
        if (snsUtil == null) {
            snsUtil = new SNSUtil();
        }
        return snsUtil;
    }

    @Override
    public Map<String, String> handleRequest(Map<String, Object> input, Context context) {
        Map<String, String> response = new HashMap<>();

        try {
            Map<String, Boolean> healthStatus = checkPrimaryRegionHealth(context);
            boolean isFrontendHealthy = healthStatus.get("frontend");
            boolean isBackendHealthy = healthStatus.get("backend");
            boolean isPrimaryRegionHealthy = isFrontendHealthy && isBackendHealthy;

            Map<String, String> alertMessage = createBaseAlertMessage(isFrontendHealthy, isBackendHealthy, isPrimaryRegionHealthy);

            if (!isPrimaryRegionHealthy) {
                handleUnhealthyRegion(response, alertMessage, isFrontendHealthy, isBackendHealthy, context);
            } /* else {
                handleHealthyRegion(response, alertMessage, context);
            } */

            return response;

        } catch (Exception e) {
            context.getLogger().log("Error in region monitor: " + e.getMessage());
            return handleError(e, context);
        }
    }

    private Map<String, String> createBaseAlertMessage(boolean isFrontendHealthy,
                                                       boolean isBackendHealthy,
                                                       boolean isPrimaryRegionHealthy) {
        Map<String, String> alertMessage = new HashMap<>();
        alertMessage.put("event", "region_monitor_check");
        alertMessage.put("timestamp", Instant.now().toString());
        alertMessage.put("primaryRegion", primaryRegion);
        alertMessage.put("frontendHealthy", String.valueOf(isFrontendHealthy));
        alertMessage.put("backendHealthy", String.valueOf(isBackendHealthy));
        alertMessage.put("healthy", String.valueOf(isPrimaryRegionHealthy));
        return alertMessage;
    }

    private void handleUnhealthyRegion(Map<String, String> response,
                                       Map<String, String> alertMessage,
                                       boolean isFrontendHealthy,
                                       boolean isBackendHealthy,
                                       Context context) {
        int failures = consecutiveFailures.incrementAndGet();
        alertMessage.put("consecutiveFailures", String.valueOf(failures));

        context.getLogger().log(String.format(
                "Primary region unhealthy - Frontend: %s, Backend: %s, Failures: %d",
                isFrontendHealthy, isBackendHealthy, failures));

        publishMessage(alertMessage, context, "initial alert");

        if (failures >= FAILURE_THRESHOLD) {
            consecutiveFailures.set(0);
            response.put("status", "triggered");
            response.put("message", "Disaster recovery should be triggered due to primary region outage");

            Map<String, String> drAlertMessage = new HashMap<>(alertMessage);
            drAlertMessage.put("event", "disaster_recovery_triggered");
            drAlertMessage.put("details", String.format(
                    "DR triggered due to %d consecutive failures. Frontend: %s, Backend: %s",
                    failures, isFrontendHealthy, isBackendHealthy));

            publishMessage(drAlertMessage, context, "disaster recovery");
        } else {
            response.put("status", "warning");
            response.put("message", String.format(
                    "Primary region unhealthy, waiting for threshold (%d/%d)",
                    failures, FAILURE_THRESHOLD));
        }
    }

    private void handleHealthyRegion(Map<String, String> response,
                                     Map<String, String> alertMessage,
                                     Context context) {
        consecutiveFailures.set(0);
        response.put("status", "healthy");
        response.put("message", "Primary region is healthy");
        publishMessage(alertMessage, context, "healthy status");
    }

    private Map<String, String> handleError(Exception e, Context context) {
        Map<String, String> response = new HashMap<>();
        response.put("status", "error");
        response.put("errorMessage", e.getMessage());

        Map<String, String> errorMessage = new HashMap<>();
        errorMessage.put("event", "region_monitor_error");
        errorMessage.put("error", e.getMessage());
        errorMessage.put("timestamp", Instant.now().toString());

        publishMessage(errorMessage, context, "error");
        return response;
    }

    private void publishMessage(Map<String, String> message, Context context, String messageType) {
        try {
            getSNSUtil().publishMessage(backupAlertTopic, message, context);
        } catch (Exception e) {
            context.getLogger().log(String.format(
                    "Failed to publish %s message: %s", messageType, e.getMessage()));
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

            DescribeAlarmsResponse response = getCloudWatchClient().describeAlarms(request);

            for (MetricAlarm alarm : response.metricAlarms()) {
                String alarmName = alarm.alarmName();
                String state = alarm.stateValue().toString();

                if (alarmName.equals(frontendAlarmName)) {
                    isFrontendHealthy = !state.equals("ALARM");
                    context.getLogger().log(String.format(
                            "Frontend Alarm [%s] State: %s, Healthy: %s",
                            alarmName, state, isFrontendHealthy));
                } else if (alarmName.equals(backendAlarmName)) {
                    isBackendHealthy = !state.equals("ALARM");
                    context.getLogger().log(String.format(
                            "Backend Alarm [%s] State: %s, Healthy: %s",
                            alarmName, state, isBackendHealthy));
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