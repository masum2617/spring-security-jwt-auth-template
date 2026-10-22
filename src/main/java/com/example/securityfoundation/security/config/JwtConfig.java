package com.example.securityfoundation.security.config;

import com.example.securityfoundation.security.jwt.JwtClaimsValidator;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @Profile("!demo")
    RSAKey signingKey(JwtProperties properties) {
        // File-only loading is this template's production key-storage policy, not a JWT requirement.
        // Other trusted key providers can replace this loader. The demo bean below needs no files.
        requireLocalFile(properties.publicKey());
        requireLocalFile(properties.privateKey());
        try (InputStream publicInput = properties.publicKey().getInputStream();
                InputStream privateInput = properties.privateKey().getInputStream()) {
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicInput);
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privateInput);

            if (publicKey == null
                    || privateKey == null
                    || publicKey.getModulus().bitLength() < 2048
                    || !publicKey.getModulus().equals(privateKey.getModulus())) {
                throw new IllegalArgumentException("Invalid key pair");
            }

            return new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        } catch (Exception exception) {
            // Do not attach parser exceptions: they can expose key material or file contents.
            throw new IllegalStateException(
                    "JWT keys must be a matching RSA pair (2048+ bits), PKCS#8 private and X.509 public PEM");
        }
    }

    @Bean
    @Profile("demo")
    RSAKey demoSigningKey() throws java.security.NoSuchAlgorithmException {
        // Local learning only: generate once per application context, never save or log the keys.
        // Restarting creates a new pair and invalidates old tokens. Do not use this for production
        // or multiple instances, which need stable, consistently configured verification keys.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .build();
    }

    private void requireLocalFile(org.springframework.core.io.Resource resource) {
        try {
            if (!"file".equals(resource.getURI().getScheme())) {
                throw new IllegalArgumentException();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("JWT keys must use local file: locations");
        }
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey, JwtProperties properties, Clock clock) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(JwtClaimsValidator.CLOCK_SKEW);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamps,
                new JwtIssuerValidator(properties.issuer()),
                new JwtClaimsValidator(properties.audience(), clock)));
        return decoder;
    }
}
