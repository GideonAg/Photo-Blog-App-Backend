package com.photoblog.processing;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoblog.dto.ImageProcessingRequest;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class ImageProcessingHandler implements RequestHandler<SQSEvent, String> {

    private final Region region = Region.of(System.getenv("PRIMARY_REGION"));
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TMP_DIR = "/tmp";
    private final String STAGING_BUCKET = System.getenv("STAGING_BUCKET");
    private final String MAIN_BUCKET = System.getenv("MAIN_BUCKET");
    private final String DESTINATION_PREFIX = "images/";
    private final S3Client s3Client = createS3Client();
    private final String WATERMARK_TEXT = "TRIAL WATERMARKING";


    @Override
    public String handleRequest(SQSEvent event, Context context) {
        try {
            String sourceKey = extractSourceKey(event, context);
            validateBucket(event, context);
            File unprocessedFile = downloadFileFromS3(sourceKey, context);
            File processedFile = processImage(unprocessedFile, WATERMARK_TEXT, context);
            uploadProcessedFile(sourceKey, processedFile, context);
            return successMessage(sourceKey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        finally {
            // TODO: Save to dynamoDB and delete the unprocessed file
        }
    }

    private File processImage(File unprocessedFile, String watermarkText, Context context) throws IOException {
        File processedFile = new File(TMP_DIR + "/processedFile.jpg");
        ImageProcessor processor = new ImageProcessor();
        ImageProcessingRequest request = ImageProcessingRequest.builder()
                .inputFile(unprocessedFile)
                .watermarkText(watermarkText)
                .exportPath(processedFile.getAbsolutePath())
                .build();
        processor.addWatermark(request);
        context.getLogger().log("Image proceesed successfully: "+processedFile.getAbsolutePath());
        return processedFile;
    }

    private String successMessage(String sourceKey) {

    }


    // Create the s3 Client
    private S3Client createS3Client() {
        return S3Client.builder()
                .region(region)
                .build();
    }

    // Extract the key from the source
    private String extractSourceKey(SQSEvent sqsEvent, Context context) throws Exception {
        JsonNode s3 = getS3Record(sqsEvent);
        JsonNode object = s3.get("object");
        String sourceKey = object.get("key").asText();
        return URLDecoder.decode(sourceKey, StandardCharsets.UTF_8);
    }

    /**
     * Validate the bucket from the event
     */
    private void validateBucket(SQSEvent sqsEvent, Context context) throws Exception {
        JsonNode s3 = getS3Record(sqsEvent);
        JsonNode bucket = s3.get("bucket");
        String eventBucket = bucket.get("name").asText();
        if (!eventBucket.equals(STAGING_BUCKET)) {
            logWarning(context, "Unexpected bucket event: " + eventBucket);
        }
    }

    /**
     * Download file from S3 Bucket
     */
    private File downloadFileFromS3(String sourceKey, Context context) throws Exception {
        verifyFileExistsInS3(sourceKey, context);
        ResponseBytes<GetObjectResponse> objectBytes = getS3Object(sourceKey, context);
        return saveBytesToFile(objectBytes.asByteArray(), TMP_DIR, context);
    }

    private void verifyFileExistsInS3(String sourceKey, Context context) {
        HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(STAGING_BUCKET)
                .key(sourceKey)
                .build();
        s3Client.headObject(headRequest);
    }

    private ResponseBytes<GetObjectResponse> getS3Object(String sourceKey, Context context) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(STAGING_BUCKET)
                .key(sourceKey)
                .build();
        return s3Client.getObjectAsBytes(getObjectRequest);
    }

    /**
     * Get the s3 object from the sqsEvent
     */
    private JsonNode getS3Record(SQSEvent sqsEvent) throws Exception {
        String messageBody = sqsEvent.getRecords().get(0).getBody();
        JsonNode root = parseS3Event(messageBody);
        JsonNode s3Record = root.get("Records").get(0);
        JsonNode s3Object = s3Record.get("s3");
        return s3Object;
    }

    /**
     * Parse the S3 event from the message body
     */
    private JsonNode parseS3Event(String messageBody) throws Exception {
        return objectMapper.readTree(messageBody);
    }

    private static void logWarning(Context context,String message){
        context.getLogger().log(message);
    }

    private File saveBytesToFile(byte[] data, String filePath, Context context) throws Exception {
        ensureDirectoryExists(TMP_DIR);
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(data);
        }
        context.getLogger().log("File saved: " + filePath);
        return new File(filePath);
    }

    private void ensureDirectoryExists(String directoryPath) throws Exception {
        Path dirPath = Paths.get(directoryPath);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
    }

    private void uploadProcessedFile(String sourceKey, File processedFile, Context context) throws Exception {
        String destinationKey = buildDestinationKey(sourceKey);
        byte[] fileBytes = readFileToBytes(processedFile);
        uploadFileToS3(destinationKey, fileBytes, context);
    }

    private String buildDestinationKey(String sourceKey) {
        String originalFilename = sourceKey.startsWith("images/") ? sourceKey.substring("images/".length()) : sourceKey;
        return DESTINATION_PREFIX + originalFilename;
    }

    private byte[] readFileToBytes(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        }
    }

    private void uploadFileToS3(String destinationKey, byte[] fileData, Context context) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(MAIN_BUCKET)
                .key(destinationKey)
                .build();
        s3Client.putObject(putRequest, RequestBody.fromBytes(fileData));
        context.getLogger().log("File uploaded: " + MAIN_BUCKET + "/" + destinationKey);
    }

    private String failureMessage(String errorMessage) {
        return "Failed to process file. " + errorMessage;
    }

    private void logError(Context context, String message) {
        context.getLogger().log("Error: " + message);
    }
}
