package vn.hutech.fooddelivery.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRET = "test_secret_toi_thieu_32_ky_tu_cho_hs256_abc";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, 60));

    @Test
    void generateThenParseReturnsSameUserAndRole() {
        String token = jwtService.generateToken(42, "CUSTOMER");

        AuthUser user = jwtService.parse(token);

        assertEquals(42, user.id());
        assertEquals("CUSTOMER", user.role());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken(42, "CUSTOMER");
        // Doi 1 ky tu o phan chu ky
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThrows(JwtException.class, () -> jwtService.parse(tampered));
    }

    @Test
    void tokenSignedWithOtherSecretIsRejected() {
        JwtService other = new JwtService(new JwtProperties("mot_secret_khac_cung_dai_hon_32_ky_tu_xyz", 60));
        String token = other.generateToken(42, "ADMIN");

        assertThrows(JwtException.class, () -> jwtService.parse(token));
    }

    @Test
    void expiredTokenIsRejected() {
        // Han dung am -> token het han ngay khi tao
        JwtService expired = new JwtService(new JwtProperties(SECRET, -1));
        String token = expired.generateToken(42, "CUSTOMER");

        assertThrows(JwtException.class, () -> jwtService.parse(token));
    }
}
