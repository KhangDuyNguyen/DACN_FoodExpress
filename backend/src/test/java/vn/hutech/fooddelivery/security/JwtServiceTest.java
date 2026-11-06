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
        // Doi 1 ky tu o GIUA phan chu ky (sau dau '.' cuoi cung).
        // Khong doi ky tu cuoi: chu ky HS256 dai 32 byte, ma hoa base64url thanh 43 ky tu,
        // ky tu cuoi chi mang 4 bit that + 2 bit thua. Doi 'A' <-> 'B' chi dong vao bit thua
        // nen co the giai ma ra dung chu ky cu -> test luc dung luc sai (khoang 1/16 so lan chay).
        int i = token.lastIndexOf('.') + 10;
        char c = token.charAt(i);
        String tampered = token.substring(0, i) + (c == 'A' ? 'B' : 'A') + token.substring(i + 1);

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
