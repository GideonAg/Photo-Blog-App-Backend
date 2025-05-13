package com.photoblog.auth;

public record SignInRequest(
        String email,
        String password
) {

}
