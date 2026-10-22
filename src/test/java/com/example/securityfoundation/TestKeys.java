package com.example.securityfoundation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.Map;

/** Ephemeral test keys; no private signing key is stored in source control. */
public final class TestKeys {
    public static final KeyPair PAIR = createPair(2048);
    public static final Path DIRECTORY = createFiles(PAIR);

    private TestKeys() {}

    public static KeyPair createPair(int size) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(size);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static Path createFiles(KeyPair pair) {
        try {
            Path directory = Files.createTempDirectory("security-foundation-test-");
            Files.writeString(
                    directory.resolve("private.pem"),
                    pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
            Files.writeString(
                    directory.resolve("public.pem"),
                    pem("PUBLIC KEY", pair.getPublic().getEncoded()));
            directory.toFile().deleteOnExit();
            directory.resolve("private.pem").toFile().deleteOnExit();
            directory.resolve("public.pem").toFile().deleteOnExit();
            return directory;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String sign(Map<String, Object> claims) throws Exception {
        return sign(claims, PAIR, JWSAlgorithm.RS256);
    }

    public static String sign(Map<String, Object> claims, KeyPair pair, JWSAlgorithm algorithm) throws Exception {
        JWSObject token =
                new JWSObject(new JWSHeader(algorithm), new Payload(new ObjectMapper().writeValueAsString(claims)));
        token.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));
        return token.serialize();
    }

    private static String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(bytes)
                + "\n-----END " + type + "-----\n";
    }
}
