package com.jugu.propertylease.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmCipherTest {

    private static final String KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    void encryptDecrypt_roundtrip() {
        AesGcmCipher cipher = new AesGcmCipher(KEY);
        String enc = cipher.encrypt("12345678");
        assertThat(enc).isNotEqualTo("12345678");
        assertThat(cipher.decrypt(enc)).isEqualTo("12345678");
    }

    @Test
    void encrypt_usesRandomIv_samePlainDifferentCipher() {
        AesGcmCipher cipher = new AesGcmCipher(KEY);
        assertThat(cipher.encrypt("00000000")).isNotEqualTo(cipher.encrypt("00000000"));
    }

    @Test
    void constructor_rejectsNon32ByteKey() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new AesGcmCipher(shortKey))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decrypt_wrongKey_throws() {
        String enc = new AesGcmCipher(KEY).encrypt("12345678");
        String otherKey = Base64.getEncoder().encodeToString(
                "abcdef0123456789abcdef0123456789".getBytes());
        assertThatThrownBy(() -> new AesGcmCipher(otherKey).decrypt(enc))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_tamperedCipherText_throws() {
        AesGcmCipher cipher = new AesGcmCipher(KEY);
        String enc = cipher.encrypt("12345678");
        byte[] raw = Base64.getDecoder().decode(enc);
        raw[raw.length - 1] ^= 0x01; // 翻转最后一位，破坏 GCM tag
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }
}
