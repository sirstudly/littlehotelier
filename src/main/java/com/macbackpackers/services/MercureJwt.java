package com.macbackpackers.services;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Mints HS256 Mercure JWTs from a shared secret (no external JWT library).
 */
final class MercureJwt {

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private MercureJwt() {
    }

    /**
     * Publisher JWT allowing publish to a single topic (and optionally {@code *}).
     *
     * @param secret Mercure hub HMAC secret
     * @param topic e.g. {@code housekeeping/17363}
     * @param ttlSeconds token lifetime
     */
    static String mintPublisher( String secret, String topic, long ttlSeconds ) throws Exception {
        JsonObject mercure = new JsonObject();
        JsonArray publish = new JsonArray();
        publish.add( topic );
        mercure.add( "publish", publish );
        return mint( secret, mercure, ttlSeconds );
    }

    private static String mint( String secret, JsonObject mercureClaim, long ttlSeconds ) throws Exception {
        long now = TimeUnit.MILLISECONDS.toSeconds( System.currentTimeMillis() );
        JsonObject header = new JsonObject();
        header.addProperty( "alg", "HS256" );
        header.addProperty( "typ", "JWT" );

        JsonObject payload = new JsonObject();
        payload.addProperty( "iat", now );
        payload.addProperty( "exp", now + ttlSeconds );
        payload.add( "mercure", mercureClaim );

        String headerPart = B64URL.encodeToString( header.toString().getBytes( StandardCharsets.UTF_8 ) );
        String payloadPart = B64URL.encodeToString( payload.toString().getBytes( StandardCharsets.UTF_8 ) );
        String signingInput = headerPart + "." + payloadPart;

        Mac mac = Mac.getInstance( "HmacSHA256" );
        mac.init( new SecretKeySpec( secret.getBytes( StandardCharsets.UTF_8 ), "HmacSHA256" ) );
        String signature = B64URL.encodeToString( mac.doFinal( signingInput.getBytes( StandardCharsets.UTF_8 ) ) );
        return signingInput + "." + signature;
    }
}
