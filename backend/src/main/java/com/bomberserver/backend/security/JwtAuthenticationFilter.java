package com.bomberserver.backend.security;

import com.bomberserver.backend.document.UserAccountDocument;
import com.bomberserver.backend.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Filter dùng để xác thực JWT cho mỗi request HTTP.
 *
 * Class này kế thừa OncePerRequestFilter nghĩa là:
 * - mỗi request chỉ chạy filter này đúng 1 lần
 *
 * Nhiệm vụ chính:
 * - lấy token từ header Authorization
 * - kiểm tra token hợp lệ không
 * - lấy userId từ token
 * - load user từ DB
 * - nếu hợp lệ thì gắn Authentication vào SecurityContext
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Service xử lý tạo và kiểm tra JWT.
     */
    private final JwtService jwtService;

    /**
     * Repository dùng để lấy user từ database.
     */
    private final UserAccountRepository userAccountRepository;

    /**
     * Constructor inject dependencies.
     *
     * @param jwtService service xử lý JWT
     * @param userAccountRepository repository tài khoản
     */
    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserAccountRepository userAccountRepository
    ) {
        this.jwtService = jwtService;
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * Hàm chính chạy mỗi khi có request đi qua filter.
     *
     * Luồng xử lý:
     * 1. Lấy Authorization header
     * 2. Nếu không có Bearer token thì bỏ qua
     * 3. Kiểm tra token hợp lệ
     * 4. Lấy userId từ token
     * 5. Tìm user trong DB
     * 6. Nếu user tồn tại và đang active thì tạo Authentication
     * 7. Gắn Authentication vào SecurityContext
     *
     * @param request request hiện tại
     * @param response response hiện tại
     * @param filterChain chuỗi filter tiếp theo
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Lấy header Authorization từ request
        String authHeader = request.getHeader("Authorization");

        /**
         * Nếu không có header hoặc không bắt đầu bằng "Bearer "
         * thì bỏ qua filter này và cho request đi tiếp.
         */
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Cắt bỏ tiền tố "Bearer " để lấy phần token thật
        String token = authHeader.substring(7);

        try {
            /**
             * Nếu token không hợp lệ thì không xác thực,
             * cho request đi tiếp để Security xử lý sau.
             */
            if (!jwtService.isTokenValid(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            // Lấy userId từ token
            String userId = jwtService.extractUserId(token);

            /**
             * Nếu userId null thì coi như không tìm được user.
             * Ngược lại tìm user theo id trong DB.
             */
            Optional<UserAccountDocument> userOpt = userId == null
                    ? Optional.empty()
                    : userAccountRepository.findById(userId);

            // Nếu không có user trong DB thì bỏ qua xác thực
            if (userOpt.isEmpty()) {
                filterChain.doFilter(request, response);
                return;
            }

            UserAccountDocument user = userOpt.get();

            /**
             * Nếu tài khoản đang bị khóa / inactive
             * thì xóa context và không cho xác thực.
             */
            if (Boolean.FALSE.equals(user.getActive())) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            /**
             * Chuẩn hóa role.
             * Nếu role null/rỗng thì mặc định USER.
             * Ví dụ kết quả cuối cùng:
             * - USER
             * - ADMIN
             */
            String role = user.getRole() == null || user.getRole().isBlank()
                    ? "USER"
                    : user.getRole().trim().toUpperCase();

            /**
             * Tạo Authentication object.
             *
             * principal  = userId
             * credentials = null vì không dùng password ở đây
             * authorities = danh sách quyền, ví dụ ROLE_ADMIN hoặc ROLE_USER
             */
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );

            // Gắn thông tin xác thực vào SecurityContext để controller/service dùng được
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ex) {
            /**
             * Nếu có lỗi trong quá trình parse/check token
             * thì xóa context để tránh dữ liệu xác thực sai.
             */
            SecurityContextHolder.clearContext();
        }

        // Cho request đi tiếp qua các filter còn lại
        filterChain.doFilter(request, response);
    }
}