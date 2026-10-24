package vn.hutech.fooddelivery.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.hutech.fooddelivery.user.UserRepository;
import vn.hutech.fooddelivery.user.UserRow;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Chay truoc moi request: doc header "Authorization: Bearer <token>".
 * Token hop le va user con hoat dong thi dat AuthUser vao SecurityContext.
 * Nguoc lai khong lam gi: API cong khai van chay, API can dang nhap se bi 401.
 * Moi request deu doc role/status tu database, nen khoa tai khoan co hieu luc ngay (khong doi token het han).
 * Khong dung @Component de Spring khong tu dang ky filter nay lan thu hai; SecurityConfig tu tao.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                AuthUser fromToken = jwtService.parse(token);
                Optional<UserRow> user = userRepository.findById(fromToken.id());
                if (user.isPresent() && !user.get().isBlocked()) {
                    // Lay role tu database (khong tin role trong token, phong khi admin vua doi role)
                    AuthUser authUser = new AuthUser(user.get().id(), user.get().role());
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + authUser.role()));
                    var authentication = new UsernamePasswordAuthenticationToken(authUser, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                // Token sai hoac het han: bo qua, coi nhu chua dang nhap
            }
        }
        chain.doFilter(request, response);
    }
}
