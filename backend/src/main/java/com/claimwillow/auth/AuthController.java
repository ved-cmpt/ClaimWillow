package com.claimwillow.auth;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.claimwillow.common.ApiException;
import com.claimwillow.user.AppUser;
import com.claimwillow.user.AppUserDetails;
import com.claimwillow.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Session sign-in, sign-out and current user")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final LoginAttemptService loginAttempts;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository,
            LoginAttemptService loginAttempts) {
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.loginAttempts = loginAttempts;
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required.") @Size(max = 40) String username,
            @NotBlank(message = "Password is required.") @Size(max = 200) String password) {
    }

    public record CurrentUserResponse(UUID id, String username, String email, String displayName, Role role) {

        static CurrentUserResponse of(AppUserDetails user) {
            return new CurrentUserResponse(user.id(), user.getUsername(), user.email(), user.displayName(),
                    user.role());
        }
    }

    public record CsrfResponse(String headerName, String cookieName) {
    }

    @GetMapping("/csrf")
    @Operation(summary = "Ensure the XSRF-TOKEN cookie is issued (SPA bootstrap)")
    public CsrfResponse csrf(CsrfToken token) {
        // Resolving the token parameter triggers issuing the cookie.
        return new CsrfResponse(token.getHeaderName(), "XSRF-TOKEN");
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with username and password; creates a server-side session")
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        String username = AppUser.normalizeUsername(body.username());
        String accountKey = "account:" + username;
        String clientKey = "client:" + request.getRemoteAddr();
        if (loginAttempts.isBlocked(accountKey) || loginAttempts.isBlocked(clientKey)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed sign-in attempts. Wait a few minutes and try again.");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, body.password()));
        } catch (AuthenticationException ex) {
            loginAttempts.recordFailure(accountKey);
            loginAttempts.recordFailure(clientKey);
            log.info("Failed sign-in attempt");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Username or password is incorrect.");
        }
        loginAttempts.recordSuccess(accountKey);

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        AppUserDetails user = (AppUserDetails) authentication.getPrincipal();
        log.info("User {} signed in with role {}", user.id(), user.role());
        return CurrentUserResponse.of(user);
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out and invalidate the session")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        contextHolder.clearContext();
        csrfTokenRepository.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Return the signed-in user")
    public CurrentUserResponse me(@AuthenticationPrincipal AppUserDetails user) {
        return CurrentUserResponse.of(user);
    }
}
