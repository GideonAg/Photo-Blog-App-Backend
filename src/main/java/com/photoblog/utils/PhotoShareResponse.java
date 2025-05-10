package com.photoblog.utils;

public class PhotoShareResponse {
    public String message;
    public String value;

    public PhotoShareResponse(String message, String value) {
        this.message = message;
        this.value = value;
    }

    public PhotoShareResponse(String message) {
        this.message = message;
    }
}
