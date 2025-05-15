package com.photoblog.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignInResponseTest {

    @Test
    void testBuilder() {
        boolean success = true;
        String message = "Login successful.";
        String idToken = "sample.jwt.token";

        SignInResponse response = SignInResponse.builder()
                .success(success)
                .message(message)
                .idToken(idToken)
                .build();

        assertEquals(success, response.isSuccess(), "Success should match builder value.");
        assertEquals(message, response.getMessage(), "Message should match builder value.");
        assertEquals(idToken, response.getIdToken(), "ID token should match builder value.");
    }

    @Test
    void testSettersAndGetters() {
        SignInResponse response = SignInResponse.builder().build();

        boolean success = false;
        String message = "Login failed.";
        String idToken = null;

        response.setSuccess(success);
        response.setMessage(message);
        response.setIdToken(idToken);

        assertEquals(success, response.isSuccess(), "Success should match the set value.");
        assertEquals(message, response.getMessage(), "Message should match the set value.");
        assertNull(response.getIdToken(), "ID token should match the set value (null).");

        String newIdToken = "new.sample.token";
        response.setIdToken(newIdToken);
        assertEquals(newIdToken, response.getIdToken(), "ID token should be updatable via setter.");
    }

    @Test
    void testBuilderDefaults() {
        String message = "Partial build.";
        SignInResponse response1 = SignInResponse.builder()
                .message(message)
                .build();

        assertFalse(response1.isSuccess(), "Success should be default (false) when not set by builder.");
        assertEquals(message, response1.getMessage(), "Message should match builder value.");
        assertNull(response1.getIdToken(), "ID token should be default (null) when not set by builder.");

        SignInResponse response2 = SignInResponse.builder()
                .success(true)
                .idToken("tokenOnly")
                .build();

        assertTrue(response2.isSuccess(), "Success should match builder value.");
        assertNull(response2.getMessage(), "Message should be default (null) when not set by builder.");
        assertEquals("tokenOnly", response2.getIdToken(), "ID token should match builder value.");
    }
}
