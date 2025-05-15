package com.photoblog.auth;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignInResponse {
    private boolean success;
    private String message;
    private String idToken;

}
