package com.photoblog.auth;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SignInResponse {
    private boolean success;
    private String message;
    private String idToken;

}
