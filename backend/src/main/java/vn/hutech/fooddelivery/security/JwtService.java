package vn.hutech.fooddelivery.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Tao va doc JWT (HS256).
 * Claim: sub = userId, role = vai tro, exp = thoi diem het han.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = props.expirationMinutes();
    }

    public String generateToken(long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /**
     * Kiem tra chu ky va han dung, tra ve thong tin trong token.
     * Token sai chu ky, sai dinh dang hoac het han -> nem JwtException.
     */
    public AuthUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            return new AuthUser(Long.parseLong(claims.getSubject()), claims.get("role", String.class));
        } catch (NumberFormatException ex) {
            throw new JwtException("Token khong hop le");
        }
    }
}
