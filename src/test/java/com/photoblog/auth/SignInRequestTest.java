package com.photoblog.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignInRequestTest {

    @Test
    void testRecordConstructorAndAccessors() {
        String email = "user@example.com";
        String password = "securePassword";
        SignInRequest request = new SignInRequest(email, password);

        assertEquals(email, request.email(), "Email accessor should return constructor value.");
        assertEquals(password, request.password(), "Password accessor should return constructor value.");
    }

    @Test
    void testEqualsAndHashCode() {
        String email = "user@example.com";
        String password = "securePassword";

        SignInRequest request1 = new SignInRequest(email, password);
        SignInRequest request2 = new SignInRequest(email, password);
        SignInRequest request3 = new SignInRequest("other@example.com", password);
        SignInRequest request4 = new SignInRequest(email, "otherPassword");

        assertEquals(request1, request1, "An object must be equal to itself.");

        assertEquals(request1, request2, "request1 should be equal to request2.");
        assertEquals(request2, request1, "request2 should be equal to request1.");


        assertEquals(request1.hashCode(), request2.hashCode(), "Hashcodes of equal objects must be equal.");

        assertNotEquals(request1, request3, "Requests with different emails should not be equal.");
        assertNotEquals(request1, request4, "Requests with different passwords should not be equal.");
        assertNotEquals(request1, null, "Request should not be equal to null.");
        assertNotEquals(request1, new Object(), "Request should not be equal to an object of a different type.");

        assertNotEquals(request1.hashCode(), request3.hashCode(), "Hashcodes of unequal objects (different email) might be different.");
    }

    @Test
    void testToString() {
        SignInRequest request = new SignInRequest("test@example.com", "pass");
        String expectedString = "SignInRequest[email=test@example.com, password=pass]";
        assertEquals(expectedString, request.toString(), "toString output should be as expected for records.");
    }
}
