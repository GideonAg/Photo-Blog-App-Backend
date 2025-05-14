package com.photoblog.utils;

import java.util.Map;

public class HeadersUtil {

    public static Map<String, String> getHeaders() {
        return Map.of(
                "Content-Type", "application/json",
                "Access-Control-Allow-Origin", "*",
                "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS",
                "Access-Control-Allow-Headers", "'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token'"
        );
    }
}
