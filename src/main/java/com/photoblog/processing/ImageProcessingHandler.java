package com.photoblog.processing;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoblog.dto.ImageProcessingRequest;
import com.photoblog.models.Photo;
import com.photoblog.utils.SESUtil;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Map;


public class ImageProcessingHandler implements RequestHandler<SQSEvent, String> {

    private final Region region = Region.of(System.getenv("PRIMARY_REGION"));
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TMP_DIR = "/tmp";
    private final String STAGING_BUCKET = System.getenv("STAGING_BUCKET");
    private final String MAIN_BUCKET = System.getenv("MAIN_BUCKET");
    private final String DESTINATION_PREFIX = "images/";
    private final S3Client s3Client = createS3Client();
    private String WATERMARK_TEXT = null;
    private SESUtil sesUtil = getSesUtil();

    @Override
    public String handleRequest(SQSEvent event, Context context) {


            String sourceKey= null;
            String userEmail = null;
        try {

            context.getLogger().log("Processing SQS event: " + event.getRecords().size() + " records");
            for (SQSEvent.SQSMessage message : event.getRecords()) {
                String messageBody = message.getBody();
                context.getLogger().log("Processing message: " + message.getBody());



                // Deserialize the message body to extract file details
                Map<String, Object> messageMap = objectMapper.readValue(messageBody, Map.class);
                sourceKey = (String) messageMap.get("objectKey");
                String firstName = (String) messageMap.get("firstName");
                String lastName = (String) messageMap.get("lastName");
                userEmail = (String) messageMap.get("email");
                String userId = (String) messageMap.get("userId");
                String bucketName = (String) messageMap.get("bucket");


                validateBucket(bucketName, context);
                context.getLogger().log("File to process: " + sourceKey + " for " + firstName + " " + lastName);
                WATERMARK_TEXT = firstName+lastName+String.valueOf(LocalDateTime.now());
            }

            context.getLogger().log("File to process: " + sourceKey);
            context.getLogger().log("Image processing started for: " + sourceKey);

            sesUtil.sendProcessingStartedEmail(userEmail,sourceKey);

            File unprocessedFile = downloadFileFromS3(sourceKey, context);
            File processedFile = processImage(unprocessedFile, WATERMARK_TEXT, context);
            uploadProcessedFile(sourceKey, processedFile, context);


            context.getLogger().log("Image processing completed for: " + sourceKey);
            sesUtil.sendProcessingCompletedEmail(userEmail, sourceKey);

//            savePhotoToDynamoDB("userId", sourceKey, versionId);

            context.getLogger().log("Deleting unprocessed file: " + sourceKey);
            deleteUnprocessedFile(sourceKey, context);
            return successMessage(sourceKey);
        } catch (Exception e) {
            sesUtil.sendProcessingFailedEmail(userEmail, sourceKey, e.getMessage());
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
        context.getLogger().log("Image processed successfully: "+processedFile.getAbsolutePath());
        return processedFile;
    }


    private String successMessage(String sourceKey) {
        return "File successfully processed and uploaded: "+MAIN_BUCKET + "/" + buildDestinationKey(sourceKey);
    }


    // Create the s3 Client
    private S3Client createS3Client() {
        return S3Client.builder()
                .region(region)
                .build();
    }


    private void validateBucket(String bucketName, Context context){
        if (bucketName.equals(STAGING_BUCKET)){
            context.getLogger().log("Unexpected bucket event: " + bucketName);
        }
    }


    /**
     * Download file from S3 Bucket
     */
    private File downloadFileFromS3(String sourceKey, Context context) throws Exception {
        verifyFileExistsInS3(sourceKey, context);
        ResponseBytes<GetObjectResponse> objectBytes = getS3Object(sourceKey, context);
        return saveBytesToFile(objectBytes.asByteArray(), TMP_DIR + "/unprocessedFile.jpg", context);
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

    private String uploadProcessedFile(String sourceKey, File processedFile, Context context) throws Exception {
        String destinationKey = buildDestinationKey(sourceKey);
        byte[] fileBytes = readFileToBytes(processedFile);
        return uploadFileToS3(destinationKey, fileBytes, context);
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

    private String uploadFileToS3(String destinationKey, byte[] fileData, Context context) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(MAIN_BUCKET)
                .key(destinationKey)
                .build();
        PutObjectResponse response = s3Client.putObject(putRequest, RequestBody.fromBytes(fileData));
        String versionId = response.versionId();
        String fileUrl = s3Client.utilities().getUrl(GetUrlRequest.builder()
                .bucket(MAIN_BUCKET)
                .key(destinationKey)
                .build()).toString();
        context.getLogger().log("File uploaded: " + MAIN_BUCKET + "/" + destinationKey);
        context.getLogger().log("File uploaded with version ID: " + versionId);
        return versionId;
    }

    private String failureMessage(String errorMessage) {
        return "Failed to process file. " + errorMessage;
    }

    private void logError(Context context, String message) {
        context.getLogger().log("Error: " + message);
    }

    private SESUtil getSesUtil() {
        return new SESUtil();
    }

    /**
     * Write a delete method so that it will delete the unprocessed image from the source bucket

     */
    private void deleteUnprocessedFile(String sourceKey, Context context) {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(STAGING_BUCKET)
                .key(sourceKey)
                .build();
        s3Client.deleteObject(deleteRequest);
        context.getLogger().log("Unprocessed file deleted: " + STAGING_BUCKET + "/" + sourceKey);
    }

    private Photo savePhotoToDynamoDB(String userId, String imageUrl,  String versionId) {
        Photo newImage = Photo.builder()
                .userId(userId)
                .imageUrl(imageUrl)
                .status(Photo.Status.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .versionId(versionId)
                .build();
        return newImage;
    }

}
