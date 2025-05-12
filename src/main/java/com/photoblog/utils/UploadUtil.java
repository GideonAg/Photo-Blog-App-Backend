package com.photoblog.utils;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

public class UploadUtil {
    private final String bucketName;
    private final S3Client s3Client;
    public UploadUtil(String bucketName, S3Client s3Client){

        this.bucketName = bucketName;
        this.s3Client = s3Client;
    }

    public PutObjectResponse uploadToS3(String fileName, byte[] content, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(contentType)
                .build();

        return s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));
    }
}
