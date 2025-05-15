package com.photoblog.utils;

import com.amazonaws.services.lambda.runtime.Context;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;

@Getter
@Setter
public class MultipartFormData {
    private final String fileName;
    private final String contentType;
    private final byte[] fileContent;

    public MultipartFormData(String fileName, String contentType, byte[] fileContent) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileContent = fileContent;
    }


    public static MultipartFormData parseMultipartRequest(String body, String contentTypeHeader, Context context) {
        try {
            String boundary = extractBoundary(contentTypeHeader);
            if (boundary == null) {
                context.getLogger().log("No boundary found in Content-Type header");
                return null;
            }
            String[] parts = body.split("--" + boundary);
            String fileName = null;
            String fileContentType = null;
            byte[] fileContent = null;
            for (int i = 1; i < parts.length - 1; i++) {
                String part = parts[i];
                int headerEndIndex = part.indexOf("\r\n\r\n");
                if (headerEndIndex == -1) {
                    continue;
                }
                String headers = part.substring(0, headerEndIndex);
                String content = part.substring(headerEndIndex + 4); // Skip the \r\n\r\n

                if (headers.contains("Content-Disposition: form-data") &&
                        headers.contains("filename=")) {
                    fileName = extractHeaderValue(headers, "filename=");
                    if (fileName != null) {
                        fileName = fileName.replaceAll("\"", "");
                    }

                    fileContentType = extractHeaderValue(headers, "Content-Type:");
                    if (content.endsWith("\r\n")) {
                        content = content.substring(0, content.length() - 2);
                    }
                    fileContent = content.getBytes(StandardCharsets.ISO_8859_1);
                }
            }

            if (fileName != null && fileContentType != null && fileContent != null) {
                return new MultipartFormData(fileName, fileContentType, fileContent);
            }

            return null;
        } catch (Exception e) {
            context.getLogger().log("Error parsing multipart request: " + e.getMessage());
            return null;
        }
    }



    public static String extractBoundary(String contentTypeHeader) {
        if (contentTypeHeader == null) {
            return null;
        }

        int boundaryIndex = contentTypeHeader.indexOf("boundary=");
        if (boundaryIndex == -1) {
            return null;
        }
        String boundary = contentTypeHeader.substring(boundaryIndex + 9);
        if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
            boundary = boundary.substring(1, boundary.length() - 1);
        }
        return boundary;
    }

    public static String extractHeaderValue(String headers, String fieldName) {
        int fieldIndex = headers.indexOf(fieldName);
        if (fieldIndex == -1) {
            return null;
        }
        int fieldStart = fieldIndex + fieldName.length();
        int fieldEnd = headers.indexOf("\r\n", fieldStart);
        if (fieldEnd == -1) {
            fieldEnd = headers.length();
        }
        return headers.substring(fieldStart, fieldEnd).trim();
    }
}
