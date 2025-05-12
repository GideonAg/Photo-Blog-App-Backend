package com.photoblog.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {
    private String email;
    private String password;
    private String firstName;
    private String lastName;

}
