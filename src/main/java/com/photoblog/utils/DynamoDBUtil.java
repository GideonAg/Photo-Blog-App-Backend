package com.photoblog.utils;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.photoblog.models.Photo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for DynamoDB operations
 */
public class DynamoDBUtil {
    private static final String PHOTO_TABLE = System.getenv("PHOTO_TABLE");
    private static final AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.standard().build();
    private static final DynamoDBMapper dynamoDBMapper;

    static {
        if (PHOTO_TABLE == null || PHOTO_TABLE.isEmpty()) {
            throw new IllegalStateException("PHOTO_TABLE environment variable is not set.");
        }
        DynamoDBMapperConfig mapperConfig = DynamoDBMapperConfig.builder()
                .withTableNameOverride(DynamoDBMapperConfig.TableNameOverride.withTableNameReplacement(PHOTO_TABLE))
                .build();
        dynamoDBMapper = new DynamoDBMapper(dynamoDBClient, mapperConfig);
    }

    /**
     * Get photo by user ID and photo ID (composite key)
     */
    public static Photo getPhotoById(String userId, String photoId) {
        return dynamoDBMapper.load(Photo.class, userId, photoId);
    }

    /**
     * Save a photo to DynamoDB
     */
    public static void savePhoto(Photo photo) {
        dynamoDBMapper.save(photo);
    }

    /**
     * Delete a photo from DynamoDB
     */
    public static void deletePhoto(Photo photo) {
        dynamoDBMapper.delete(photo);
    }

    /**
     * Update photo status
     */
    public static Photo updatePhotoStatus(String userId, String photoId, String status) {
        Photo photo = getPhotoById(userId, photoId);
        if (photo != null) {
            photo.setStatus(status);
            savePhoto(photo);
        }
        return photo;
    }

    /**
     * Get photos by user ID with specific status
     */
    public static List<Photo> getPhotosByUserIdAndStatus(String userId, String status) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":userId", new AttributeValue().withS(userId));
        expressionAttributeValues.put(":status", new AttributeValue().withS(status));

        DynamoDBQueryExpression<Photo> queryExpression = new DynamoDBQueryExpression<Photo>()
                .withKeyConditionExpression("userId = :userId")
                .withFilterExpression("status = :status")
                .withExpressionAttributeValues(expressionAttributeValues)
                .withIndexName("UserIdIndex")
                .withConsistentRead(false);

        return dynamoDBMapper.query(Photo.class, queryExpression);
    }

    /**
     * Get all active photos by user ID
     */
    public static List<Photo> getActivePhotosByUserId(String userId) {
        return getPhotosByUserIdAndStatus(userId, "active");
    }

    /**
     * Get all deleted photos by user ID
     */
    public static List<Photo> getDeletedPhotosByUserId(String userId) {
        return getPhotosByUserIdAndStatus(userId, "deleted");
    }

    /**
     * Update the photo's S3 version ID
     */
    public static Photo updatePhotoVersionId(String userId, String photoId, String versionId) {
        Photo photo = getPhotoById(userId, photoId);
        if (photo != null) {
            photo.setVersionId(versionId);
            savePhoto(photo);
        }
        return photo;
    }
}
