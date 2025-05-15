package com.photoblog.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreateUserRequestTest {

    @Test
    void testGettersAndSetters() {
        CreateUserRequest request = new CreateUserRequest();
        String email = "test@example.com";
        String password = "password123";
        String firstName = "John";
        String lastName = "Doe";

        request.setEmail(email);
        request.setPassword(password);
        request.setFirstName(firstName);
        request.setLastName(lastName);

        assertEquals(email, request.getEmail(), "Email should match the set value.");
        assertEquals(password, request.getPassword(), "Password should match the set value.");
        assertEquals(firstName, request.getFirstName(), "First name should match the set value.");
        assertEquals(lastName, request.getLastName(), "Last name should match the set value.");
    }

    @Test
    void testDefaultConstructor() {
        CreateUserRequest request = new CreateUserRequest();
        assertNull(request.getEmail(), "Email should be null by default.");
        assertNull(request.getPassword(), "Password should be null by default.");
        assertNull(request.getFirstName(), "First name should be null by default.");
        assertNull(request.getLastName(), "Last name should be null by default.");
    }
}
