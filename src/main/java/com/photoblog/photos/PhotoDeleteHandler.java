package com.photoblog.photos;

import java.util.HashMap;

import com.amazonaws.event.DeliveryMode;
import com.photoblog.utils.AuthorizerClaims;

public class PhotoDeleteHandler {
    try {
        HashMap<String, String> claims = AuthorizerClaims.extractCognitoClaims((request))
        String userId = claims.get("email");
    } catch(err) {
        System.out.println(err);
    }
}
