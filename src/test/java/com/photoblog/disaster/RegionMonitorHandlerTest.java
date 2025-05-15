package com.photoblog.disaster;

import com.amazonaws.services.lambda.runtime.Context;
import com.photoblog.utils.SNSUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataResult;
import software.amazon.awssdk.services.health.HealthClient;
import software.amazon.awssdk.services.health.model.DescribeEventsRequest;
import software.amazon.awssdk.services.health.model.DescribeEventsResponse;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegionMonitorHandlerTest {

    @Mock
    private HealthClient healthClient;

    @Mock
    private CloudWatchClient cloudWatchClient;

    @Mock
    private SNSUtil snsUtil;

    @Mock
    private Context context;

    @InjectMocks
    private RegionMonitorHandler handler;

    private static final String PRIMARY_REGION = "eu-west-1";
    private static final String SYSTEM_ALERT_TOPIC = "arn:aws:sns:eu-west-1:123456789012:system-alerts";
    private static final String API_GATEWAY_ID = "abc123";
    private static final int FAILURE_THRESHOLD = 2;

    @BeforeEach
    void setUp() throws Exception {
        when(context.getLogger()).thenReturn(new MockLogger());

        new EnvironmentVariables()
                .set("PRIMARY_REGION", PRIMARY_REGION)
                .set("SYSTEM_ALERT_TOPIC", SYSTEM_ALERT_TOPIC)
                .set("API_GATEWAY_ID", API_GATEWAY_ID)
                .execute(() -> {});

        Field consecutiveFailuresField = RegionMonitorHandler.class.getDeclaredField("consecutiveFailures");
        consecutiveFailuresField.setAccessible(true);
        AtomicInteger consecutiveFailures = (AtomicInteger) consecutiveFailuresField.get(null);
        consecutiveFailures.set(0);

        MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        new EnvironmentVariables().execute(() -> {});
    }

    @Test
    void testPrimaryRegionHealthy() {
        DescribeEventsResponse eventsResponse = DescribeEventsResponse.builder()
                .events(Collections.emptyList())
                .build();
        when(healthClient.describeEvents(any(DescribeEventsRequest.class))).thenReturn(eventsResponse);

        GetMetricDataResponse metricDataResponse = GetMetricDataResponse.builder()
                .metricDataResults(MetricDataResult.builder().id("m1").values(Collections.singletonList(0.0)).build())
                .build();
        when(cloudWatchClient.getMetricData(any(GetMetricDataRequest.class))).thenReturn(metricDataResponse);

        doNothing().when(snsUtil).publishMessage(eq(SYSTEM_ALERT_TOPIC), any(Map.class), eq(context));

        Map<String, String> input = new HashMap<>();
        Map<String, String> response = handler.handleRequest(input, context);

        assertEquals("healthy", response.get("status"));
        assertEquals("Primary region is healthy", response.get("message"));

        ArgumentCaptor<Map<String, String>> messageCaptor = ArgumentCaptor.forClass(Map.class);
        verify(snsUtil).publishMessage(eq(SYSTEM_ALERT_TOPIC), messageCaptor.capture(), eq(context));
        Map<String, String> alertMessage = messageCaptor.getValue();
        assertEquals("region_monitor_check", alertMessage.get("event"));
        assertEquals(PRIMARY_REGION, alertMessage.get("primaryRegion"));
        assertEquals("true", alertMessage.get("healthy"));

        try {
            Field consecutiveFailuresField = RegionMonitorHandler.class.getDeclaredField("consecutiveFailures");
            consecutiveFailuresField.setAccessible(true);
            AtomicInteger consecutiveFailures = (AtomicInteger) consecutiveFailuresField.get(null);
            assertEquals(0, consecutiveFailures.get());
        } catch (Exception e) {
            fail("Failed to access consecutiveFailures field: " + e.getMessage());
        }
    }

    @Test
    void testPrimaryRegionUnhealthyBelowThreshold() {
        DescribeEventsResponse eventsResponse = DescribeEventsResponse.builder()
                .events(Collections.emptyList())
                .build();
        when(healthClient.describeEvents(any(DescribeEventsRequest.class))).thenReturn(eventsResponse);

        GetMetricDataResponse metricDataResponse = GetMetricDataResponse.builder()
                .metricDataResults(MetricDataResult.builder().id("m1").values(Collections.singletonList(15.0)).build())
                .build();
        when(cloudWatchClient.getMetricData(any(GetMetricDataRequest.class))).thenReturn(metricDataResponse);

        doNothing().when(snsUtil).publishMessage(eq(SYSTEM_ALERT_TOPIC), any(Map.class), eq(context));

        Map<String, String> input = new HashMap<>();
        Map<String, String> response = handler.handleRequest(input, context);

        assertEquals("warning", response.get("status"));
        assertEquals("Primary region unhealthy, waiting for threshold (1/" + FAILURE_THRESHOLD + ")", response.get("message"));

        ArgumentCaptor<Map<String, String>> messageCaptor = ArgumentCaptor.forClass(Map.class);
        verify(snsUtil).publishMessage(eq(SYSTEM_ALERT_TOPIC), messageCaptor.capture(), eq(context));
        Map<String, String> alertMessage = messageCaptor.getValue();
        assertEquals("region_monitor_check", alertMessage.get("event"));
        assertEquals(PRIMARY_REGION, alertMessage.get("primaryRegion"));
        assertEquals("false", alertMessage.get("healthy"));
        assertEquals("1", alertMessage.get("consecutiveFailures"));

        try {
            Field consecutiveFailuresField = RegionMonitorHandler.class.getDeclaredField("consecutiveFailures");
            consecutiveFailuresField.setAccessible(true);
            AtomicInteger consecutiveFailures = (AtomicInteger) consecutiveFailuresField.get(null);
            assertEquals(1, consecutiveFailures.get());
        } catch (Exception e) {
            fail("Failed to access consecutiveFailures field: " + e.getMessage());
        }
    }

    @Test
    void testPrimaryRegionUnhealthyAtThreshold() {
        try {
            Field consecutiveFailuresField = RegionMonitorHandler.class.getDeclaredField("consecutiveFailures");
            consecutiveFailuresField.setAccessible(true);
            AtomicInteger consecutiveFailures = (AtomicInteger) consecutiveFailuresField.get(null);
            consecutiveFailures.set(1);
        } catch (Exception e) {
            fail("Failed to set consecutiveFailures field: " + e.getMessage());
        }

        DescribeEventsResponse eventsResponse = DescribeEventsResponse.builder()
                .events(Collections.emptyList())
                .build();
        when(healthClient.describeEvents(any(DescribeEventsRequest.class))).thenReturn(eventsResponse);

        GetMetricDataResponse metricDataResponse = GetMetricDataResponse.builder()
                .metricDataResults(MetricDataResult.builder().id("m1").values(Collections.singletonList(15.0)).build())
                .build();
        when(cloudWatchClient.getMetricData(any(GetMetricDataRequest.class))).thenReturn(metricDataResponse);

        doNothing().when(snsUtil).publishMessage(eq(SYSTEM_ALERT_TOPIC), any(Map.class), eq(context));

        Map<String, String> input = new HashMap<>();
        Map<String, String> response = handler.handleRequest(input, context);

        assertEquals("triggered", response.get("status"));
        assertEquals("Disaster recovery should be triggered due to primary region outage", response.get("message"));

        ArgumentCaptor<Map<String, String>> messageCaptor = ArgumentCaptor.forClass(Map.class);
        verify(snsUtil, times(2)).publishMessage(eq(SYSTEM_ALERT_TOPIC), messageCaptor.capture(), eq(context));
        List<Map<String, String>> capturedMessages = messageCaptor.getAllValues();

        Map<String, String> checkMessage = capturedMessages.get(0);
        assertEquals("region_monitor_check", checkMessage.get("event"));
        assertEquals(PRIMARY_REGION, checkMessage.get("primaryRegion"));
        assertEquals("false", checkMessage.get("healthy"));
        assertEquals("2", checkMessage.get("consecutiveFailures"));

        Map<String, String> drMessage = capturedMessages.get(1);
        assertEquals("disaster_recovery_triggered", drMessage.get("event"));
        assertEquals("DR should be triggered due to 2 consecutive failures", drMessage.get("details"));

        try {
            Field consecutiveFailuresField = RegionMonitorHandler.class.getDeclaredField("consecutiveFailures");
            consecutiveFailuresField.setAccessible(true);
            AtomicInteger consecutiveFailures = (AtomicInteger) consecutiveFailuresField.get(null);
            assertEquals(0, consecutiveFailures.get());
        } catch (Exception e) {
            fail("Failed to access consecutiveFailures field: " + e.getMessage());
        }
    }

    @Test
    void testExceptionInHandler() {
        when(healthClient.describeEvents(any(DescribeEventsRequest.class)))
                .thenThrow(new RuntimeException("AWS Health API failure"));

        doNothing().when(snsUtil).publishMessage(eq(SYSTEM_ALERT_TOPIC), any(Map.class), eq(context));

        Map<String, String> input = new HashMap<>();
        Map<String, String> response = handler.handleRequest(input, context);

        assertEquals("error", response.get("status"));
        assertEquals("AWS Health API failure", response.get("errorMessage"));

        ArgumentCaptor<Map<String, String>> messageCaptor = ArgumentCaptor.forClass(Map.class);
        verify(snsUtil).publishMessage(eq(SYSTEM_ALERT_TOPIC), messageCaptor.capture(), eq(context));
        Map<String, String> errorMessage = messageCaptor.getValue();
        assertEquals("region_monitor_error", errorMessage.get("event"));
        assertEquals("AWS Health API failure", errorMessage.get("error"));
    }

    private static class MockLogger implements com.amazonaws.services.lambda.runtime.LambdaLogger {
        @Override
        public void log(String message) {
            System.out.println(message);
        }

        @Override
        public void log(byte[] message) {
            System.out.println(new String(message));
        }
    }
}