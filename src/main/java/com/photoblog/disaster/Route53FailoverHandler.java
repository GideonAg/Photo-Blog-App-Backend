package com.photoblog.disaster;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.Change;
import software.amazon.awssdk.services.route53.model.ChangeBatch;
import software.amazon.awssdk.services.route53.model.ChangeResourceRecordSetsRequest;
import software.amazon.awssdk.services.route53.model.ListHostedZonesRequest;
import software.amazon.awssdk.services.route53.model.ResourceRecord;
import software.amazon.awssdk.services.route53.model.ResourceRecordSet;
import java.util.HashMap;
import java.util.Map;

public class Route53FailoverHandler implements RequestHandler<Map<String, String>, Map<String, String>> {

    private final Route53Client route53Client = Route53Client.builder().build();
    private final String domainName = System.getenv("DOMAIN_NAME");
    private final String backupRegion = System.getenv("BACKUP_REGION");

    @Override
    public Map<String, String> handleRequest(Map<String, String> input, Context context) {
        Map<String, String> response = new HashMap<>();
        try {
            String action = input.getOrDefault("action", "");
            if (!"failover".equalsIgnoreCase(action)) {
                response.put("status", "error");
                response.put("errorMessage", "Invalid action: " + action);
                return response;
            }

            String hostedZoneId = null;
            ListHostedZonesRequest listZonesRequest = ListHostedZonesRequest.builder().build();
            for (var zone : route53Client.listHostedZones(listZonesRequest).hostedZones()) {
                if (domainName.equals(zone.name()) || domainName.endsWith("." + zone.name())) {
                    hostedZoneId = zone.id().split("/hostedzone/")[1];
                    break;
                }
            }
            if (hostedZoneId == null) {
                response.put("status", "error");
                response.put("errorMessage", "Hosted zone not found for domain: " + domainName);
                return response;
            }

            String backupApiUrl = input.getOrDefault("backupApiUrl",
                    String.format("https://%s.execute-api.%s.amazonaws.com/%s",
                            input.getOrDefault("apiId", "backup-api"),
                            backupRegion,
                            System.getenv("STAGE")));

            ResourceRecordSet recordSet = ResourceRecordSet.builder()
                    .name(domainName)
                    .type("CNAME")
                    .ttl(300L)
                    .resourceRecords(ResourceRecord.builder().value(backupApiUrl).build())
                    .build();
            Change change = Change.builder()
                    .action("UPSERT")
                    .resourceRecordSet(recordSet)
                    .build();
            ChangeResourceRecordSetsRequest changeRequest = ChangeResourceRecordSetsRequest.builder()
                    .hostedZoneId(hostedZoneId)
                    .changeBatch(ChangeBatch.builder().changes(change).build())
                    .build();
            route53Client.changeResourceRecordSets(changeRequest);

            response.put("status", "success");
            response.put("message", "Route53 updated to point to " + backupApiUrl);
            context.getLogger().log("Updated Route53 DNS to: " + backupApiUrl);
            return response;

        } catch (Exception e) {
            context.getLogger().log("Error in Route53 failover: " + e.getMessage());
            response.put("status", "error");
            response.put("errorMessage", e.getMessage());
            return response;
        } finally {
            route53Client.close();
        }
    }
}
