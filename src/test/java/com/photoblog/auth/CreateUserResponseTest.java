package com.photoblog.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreateUserResponseTest {

    @Test
    void testNoArgsConstructor() {
        CreateUserResponse response = new CreateUserResponse();
        assertFalse(response.isSuccess(), "Success should be false by default.");
        assertNull(response.getMessage(), "Message should be null by default.");
    }

    @Test
    void testAllArgsConstructor() {
        boolean success = true;
        String message = "User created successfully.";
        CreateUserResponse response = new CreateUserResponse(success, message);

        assertEquals(success, response.isSuccess(), "Success should match constructor argument.");
        assertEquals(message, response.getMessage(), "Message should match constructor argument.");
    }

    @Test
    void testBuilder() {
        boolean success = true;
        String message = "User created via builder.";
        CreateUserResponse response = CreateUserResponse.builder()
                .success(success)
                .message(message)
                .build();

        assertEquals(success, response.isSuccess(), "Success should match builder value.");
        assertEquals(message, response.getMessage(), "Message should match builder value.");
    }

    @Test
    void testSettersAndGetters() {
        CreateUserResponse response = new CreateUserResponse();
        boolean success = true;
        String message = "Updated message.";

        response.setSuccess(success);
        response.setMessage(message);

        assertEquals(success, response.isSuccess(), "Success should match the set value.");
        assertEquals(message, response.getMessage(), "Message should match the set value.");
    }

    @Test
    void testBuilderDefaults() {
        String message = "Only message set.";
        CreateUserResponse response = CreateUserResponse.builder()
                .message(message)
                .build();

        assertFalse(response.isSuccess(), "Success should be default (false) when not set by builder.");
        assertEquals(message, response.getMessage(), "Message should match builder value.");

        CreateUserResponse response2 = CreateUserResponse.builder()
                .success(true)
                .build();
        assertTrue(response2.isSuccess(), "Success should match builder value.");
        assertNull(response2.getMessage(), "Message should be default (null) when not set by builder.");
    }
}
