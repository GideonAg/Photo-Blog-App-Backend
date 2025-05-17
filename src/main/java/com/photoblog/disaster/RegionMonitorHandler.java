package com.photoblog.disaster;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoblog.utils.SNSUtil;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.Metric;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataQuery;
import software.amazon.awssdk.services.cloudwatch.model.MetricStat;
import software.amazon.awssdk.services.health.HealthClient;
import software.amazon.awssdk.services.health.model.DescribeEventsRequest;
import software.amazon.awssdk.services.health.model.Event;
import software.amazon.awssdk.services.health.model.EventStatusCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RegionMonitorHandler implements RequestHandler<Map<String, String>, Map<String, String>> {

    private final HealthClient healthClient = HealthClient.builder().build();
    private final CloudWatchClient cloudWatchClient = CloudWatchClient.builder().build();
    private final SNSUtil snsUtil = new SNSUtil();
    private static final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static final int FAILURE_THRESHOLD = 2;

    private final String primaryRegion = System.getenv("PRIMARY_REGION");
    private final String backupAlertTopic = System.getenv("BACKUP_ALERT_TOPIC");
    private final String apiGatewayId = System.getenv("API_GATEWAY_ID");

    @Override
    public Map<String, String> handleRequest(Map<String, String> input, Context context) {
        Map<String, String> response = new HashMap<>();
        try {
            boolean isPrimaryRegionHealthy = checkPrimaryRegionHealth(context);
            Map<String, String> alertMessage = new HashMap<>();
            alertMessage.put("event", "region_monitor_check");
            alertMessage.put("timestamp", Instant.now().toString());
            alertMessage.put("primaryRegion", primaryRegion);
            alertMessage.put("healthy", String.valueOf(isPrimaryRegionHealthy));

            if (!isPrimaryRegionHealthy) {
                int failures = consecutiveFailures.incrementAndGet();
                alertMessage.put("consecutiveFailures", String.valueOf(failures));
                context.getLogger().log("Primary region unhealthy, failures: " + failures);
                snsUtil.publishMessage(backupAlertTopic, alertMessage, context);

                if (failures >= FAILURE_THRESHOLD) {
                    response.put("status", "triggered");
                    response.put("message", "Disaster recovery should be triggered due to primary region outage");
                    consecutiveFailures.set(0);
                    alertMessage.put("event", "disaster_recovery_triggered");
                    alertMessage.put("details", "DR should be triggered due to " + failures + " consecutive failures");
                    snsUtil.publishMessage(backupAlertTopic, alertMessage, context);
                } else {
                    response.put("status", "warning");
                    response.put("message", "Primary region unhealthy, waiting for threshold (" + failures + "/" + FAILURE_THRESHOLD + ")");
                }
            } else {
                consecutiveFailures.set(0);
                response.put("status", "healthy");
                response.put("message", "Primary region is healthy");
                snsUtil.publishMessage(backupAlertTopic, alertMessage, context);
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
            snsUtil.publishMessage(backupAlertTopic, errorMessage, context);
            return response;
        } finally {
            healthClient.close();
            cloudWatchClient.close();
            snsUtil.close();
        }
    }

    private boolean checkPrimaryRegionHealth(Context context) {
        try {
            DescribeEventsRequest eventsRequest = DescribeEventsRequest.builder()
                    .filter(f -> f.regions(Collections.singletonList(primaryRegion))
                            .eventStatusCodes(EventStatusCode.OPEN, EventStatusCode.UPCOMING))
                    .build();
            for (Event event : healthClient.describeEvents(eventsRequest).events()) {
                if (event.region().equals(primaryRegion) && event.service().equals("AWS")) {
                    context.getLogger().log("AWS Health event detected: " + event.eventTypeCode());
                    return false;
                }
            }
        } catch (Exception e) {
            context.getLogger().log("Error checking AWS Health API: " + e.getMessage());
            return false;
        }

        try {
            Instant endTime = Instant.now();
            Instant startTime = endTime.minus(5, ChronoUnit.MINUTES);
            Metric metric = Metric.builder()
                    .namespace("AWS/ApiGateway")
                    .metricName("5XXError")
                    .dimensions(d -> d.name("ApiId").value(apiGatewayId))
                    .build();
            MetricStat stat = MetricStat.builder()
                    .metric(metric)
                    .stat("Sum")
                    .period(300)
                    .build();
            MetricDataQuery query = MetricDataQuery.builder()
                    .id("m1")
                    .metricStat(stat)
                    .build();
            GetMetricDataRequest metricRequest = GetMetricDataRequest.builder()
                    .metricDataQueries(query)
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();
            double errorCount = cloudWatchClient.getMetricData(metricRequest)
                    .metricDataResults()
                    .stream()
                    .flatMap(r -> r.values().stream())
                    .mapToDouble(Double::doubleValue)
                    .sum();
            if (errorCount > 10) {
                context.getLogger().log("API Gateway 5XX errors detected: " + errorCount);
                return false;
            }
        } catch (Exception e) {
            context.getLogger().log("Error checking API Gateway metrics: " + e.getMessage());
            return false;
        }

        return true;
    }
}