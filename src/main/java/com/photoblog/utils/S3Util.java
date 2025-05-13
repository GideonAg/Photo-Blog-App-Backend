package com.photoblog.utils;

import lombok.Getter;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;

public class S3Util {
    @Getter
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    @Getter
    private final String mainBucket;

    public S3Util() {
        String region = System.getenv("PRIMARY_REGION");
        String appName = System.getenv("APP_NAME");
        String stageName = System.getenv("STAGE");
        String accountId = System.getenv("AWS_ACCOUNT_ID");
        if (region == null || appName == null || stageName == null || accountId == null) {
            throw new IllegalStateException("Required environment variables are not set");
        }
        this.mainBucket = String.format("%s-%s-main-%s-%s", appName, stageName, region, accountId);
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .build();
        this.s3Presigner = S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }

    /**
     * Get an active image from the main bucket.
     * @param userId The user ID.
     * @param photoId The photo ID.
     * @return Presigned URL for the image (3-hour expiry).
     */
    public String getImage(String userId, String photoId) {
        String key = userId + "/" + photoId;
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(mainBucket)
                    .key(key)
                    .build();
            return generatePresignedUrl(getObjectRequest);
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to get image: " + e.getMessage(), e);
        }
    }

    /**
     * Get a deleted image by version ID from the main bucket.
     * @param userId The user ID.
     * @param photoId The photo ID.
     * @param versionId The version ID of the deleted image.
     * @return Presigned URL for the deleted image.
     */
    public String getDeletedImage(String userId, String photoId, String versionId) {
        String key = userId + "/" + photoId;
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(mainBucket)
                    .key(key)
                    .versionId(versionId)
                    .build();
            return generatePresignedUrl(getObjectRequest);
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to get deleted image: " + e.getMessage(), e);
        }
    }

    /**
     * Restore a deleted image by copying the version to the latest.
     * @param userId The user ID.
     * @param photoId The photo ID.
     * @param versionId The version ID to restore.
     */
    public void restoreImage(String userId, String photoId, String versionId) {
        String key = userId + "/" + photoId;
        try {
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(mainBucket)
                    .sourceKey(key)
                    .sourceVersionId(versionId)
                    .destinationBucket(mainBucket)
                    .destinationKey(key)
                    .build();
            s3Client.copyObject(copyRequest);
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to restore image: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a presigned URL for an S3 object (3-hour expiry).
     * @param getObjectRequest The S3 get object request.
     * @return Presigned URL.
     */
    private String generatePresignedUrl(GetObjectRequest getObjectRequest) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(3))
                .getObjectRequest(getObjectRequest)
                .build();
        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    /**
     * Apply bucket policy to restrict access to frontend domain.
     */
    public void applyBucketPolicy() {
        String domainName = System.getenv("DOMAIN_NAME");
        if (domainName == null) {
            throw new IllegalStateException("DOMAIN_NAME environment variable is not set");
        }
        String policy = """
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Principal": "*",
                            "Action": "s3:GetObject",
                            "Resource": "arn:aws:s3:::%s/*",
                            "Condition": {
                                "StringEquals": {
                                    "aws:Referer": "https://%s"
                                }
                            }
                        }
                    ]
                }
                """.formatted(mainBucket, domainName);

        try {
            PutBucketPolicyRequest policyRequest = PutBucketPolicyRequest.builder()
                    .bucket(mainBucket)
                    .policy(policy)
                    .build();
            s3Client.putBucketPolicy(policyRequest);
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to apply bucket policy: " + e.getMessage(), e);
        }
    }


}