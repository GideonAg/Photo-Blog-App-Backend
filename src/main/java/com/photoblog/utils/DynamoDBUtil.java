package com.photoblog.utils;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.photoblog.models.Photo;
import com.photoblog.models.Photo.Status;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The type Dynamo DB util.
 */
public class DynamoDBUtil {
    private static final String PHOTO_TABLE = System.getenv("PHOTOS_TABLE");
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
     * Gets photo by id.
     *
     * @param userId  the user id
     * @param photoId the photo id
     * @return the photo by id
     */
    public static Photo getPhotoById(String userId, String photoId) throws Exception {
        try {
            return dynamoDBMapper.load(Photo.class, userId, photoId);
        } catch (DynamoDBMappingException e) {
            throw new Exception("Error mapping photo data: " + e.getMessage(), e);
        }
    }

    /**
     * Save photo.
     *
     * @param photo the photo
     */
    public static void savePhoto(Photo photo) {
        dynamoDBMapper.save(photo);
    }

    /**
     * Delete photo.
     *
     * @param photo the photo
     */
    public static void deletePhoto(Photo photo) {
        dynamoDBMapper.delete(photo);
    }

    /**
     * Update photo status photo.
     *
     * @param userId  the user id
     * @param photoId the photo id
     * @param status  the status
     * @return the photo
     * @throws Exception
     */
    public static Photo updatePhotoStatus(String userId, String photoId, Status status) throws Exception {
        Photo photo = getPhotoById(userId, photoId);
        if (photo != null) {
            photo.setStatus(status);
            savePhoto(photo);
        }
        return photo;
    }

    /**
     * Gets photos by user id and status.
     *
     * @param userId the user id
     * @param status the status
     * @return the photos by user id and status
     */
    public static List<Photo> getPhotosByUserIdAndStatus(String userId, Status status) {
        try {
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":userId", new AttributeValue().withS(userId));
            expressionAttributeValues.put(":status", new AttributeValue().withS(status.name()));

            Map<String, String> expressionAttributeNames = new HashMap<>();
            expressionAttributeNames.put("#status", "status");

            DynamoDBQueryExpression<Photo> queryExpression = new DynamoDBQueryExpression<Photo>()
                    .withKeyConditionExpression("userId = :userId")
                    .withFilterExpression("#status = :status")
                    .withExpressionAttributeValues(expressionAttributeValues)
                    .withExpressionAttributeNames(expressionAttributeNames)
                    .withIndexName("UserIdIndex")
                    .withConsistentRead(false);

            return dynamoDBMapper.query(Photo.class, queryExpression);
        } catch (DynamoDBMappingException e) {
            System.err.println("Error querying photos for userId " + userId + " with status " + status + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Gets active photos by user id.
     *
     * @param userId the user id
     * @return the active photos by user id
     */
    public static List<Photo> getActivePhotosByUserId(String userId) {
        return getPhotosByUserIdAndStatus(userId, Status.ACTIVE);
    }

    /**
     * Gets deleted photos by user id.
     *
     * @param userId the user id
     * @return the deleted photos by user id
     */
    public static List<Photo> getDeletedPhotosByUserId(String userId) {
        return getPhotosByUserIdAndStatus(userId, Status.DELETED);
    }

    /**
     * Update photo version id photo.
     *
     * @param userId    the user id
     * @param photoId   the photo id
     * @param versionId the version id
     * @return the photo
     * @throws Exception
     */
    public static Photo updatePhotoVersionId(String userId, String photoId, String versionId) throws Exception {
        Photo photo = getPhotoById(userId, photoId);
        if (photo != null) {
            photo.setVersionId(versionId);
            savePhoto(photo);
        }
        return photo;
    }
}