package com.bomberserver.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Service xử lý toàn bộ nghiệp vụ liên quan đến JWT.
 *
 * Bao gồm:
 * - tạo token
 * - đọc claims từ token
 * - lấy userId từ token
 * - kiểm tra token còn hạn và hợp lệ hay không
 */
@Service
public class JwtService {

    /**
     * Chuỗi secret key lấy từ application.properties.
     *
     * Dùng để ký và xác thực JWT.
     */
    @Value("${app.jwt.secret}")
    private String secret;

    /**
     * Thời gian sống của token, tính bằng mili giây.
     *
     * Ví dụ:
     * 86400000 = 1 ngày
     */
    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Tạo SecretKey từ chuỗi secret.
     *
     * JWT library cần SecretKey để sign/verify token.
     *
     * @return secret key dùng cho HMAC
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Tạo JWT token mới cho user.
     *
     * Token sẽ chứa:
     * - subject = userId
     * - claim email
     * - claim username
     * - issuedAt
     * - expiration
     *
     * @param userId id người dùng
     * @param email email người dùng
     * @param username username người dùng
     * @return chuỗi JWT token
     */
    public String generateToken(String userId, String email, String username) {
        Date now = new Date();
        Date expiredAt = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiredAt)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Đọc toàn bộ claims từ token.
     *
     * Nếu token không hợp lệ hoặc sai chữ ký thì sẽ ném exception.
     *
     * @param token JWT token
     * @return Claims chứa payload của token
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Lấy userId từ token.
     *
     * Vì khi tạo token, userId được lưu ở subject.
     *
     * @param token JWT token
     * @return userId
     */
    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Kiểm tra token có hợp lệ hay không.
     *
     * Điều kiện:
     * - parse được token
     * - chữ ký đúng
     * - có expiration
     * - expiration còn sau thời điểm hiện tại
     *
     * @param token JWT token
     * @return true nếu token hợp lệ và chưa hết hạn
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Date expiration = claims.getExpiration();
            return expiration != null && expiration.after(new Date());
        } catch (Exception ex) {
            // Nếu parse lỗi, sai chữ ký hoặc token hỏng thì trả false
            return false;
        }
    }
}