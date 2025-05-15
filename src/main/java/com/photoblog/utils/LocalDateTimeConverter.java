package com.photoblog.utils;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Converter for handling LocalDateTime types in DynamoDB
 */
public class LocalDateTimeConverter implements DynamoDBTypeConverter<String, LocalDateTime> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public String convert(LocalDateTime object) {
        return object != null ? object.format(FORMATTER) : null;
    }

    @Override
    public LocalDateTime unconvert(String object) {
        if (object == null || object.isEmpty()) {
            return null;
        }

        try {
            return LocalDateTime.parse(object, FORMATTER);
        } catch (DateTimeParseException e) {
            // Log the error and provide more context
            System.err.println("Failed to parse date: " + object + ". Error: " + e.getMessage());
            // Return null or throw a custom exception depending on your error handling strategy
            return null;
        }
    }
}