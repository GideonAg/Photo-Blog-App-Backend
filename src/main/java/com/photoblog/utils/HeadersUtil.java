package com.photoblog.utils;

import java.util.Map;

public class HeadersUtil {

    public static Map<String, String> getHeaders() {
        return Map.of(
                "Content-Type", "application/json",
                "Access-Control-Allow-Origin", "https://mscv2group1.link",
                "Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS",
                "Access-Control-Allow-Headers", "Content-Type, Authorization"
        );
    }
}
