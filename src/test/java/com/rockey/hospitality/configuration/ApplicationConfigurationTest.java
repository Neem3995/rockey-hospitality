package com.rockey.hospitality.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {

    @Test
    void passwordEncoderUsesBcrypt() {
        PasswordEncoder passwordEncoder = new ApplicationConfiguration().passwordEncoder();

        String encoded = passwordEncoder.encode("valid-password");

        assertThat(encoded).startsWith("$2");
        assertThat(encoded).isNotEqualTo("valid-password");
        assertThat(passwordEncoder.matches("valid-password", encoded)).isTrue();
    }
}
