package app.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import app.enums.VerificationStatus;
import app.model.Account;
import app.model.Ngo;
import app.repository.AccountRepository;
import app.repository.NgoRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/ngos/*/media/photo", "/api/ngos/*/media/video",
                        "/api/ngos/*/media/video/upload", "/api/ngos/*/notifications", "/api/ngos/*/events",
                        "/api/ngos/*/food-slots").hasRole("NGO")
                .requestMatchers(HttpMethod.DELETE, "/api/ngos/*/media/*", "/api/ngos/*/events/*",
                        "/api/ngos/*/food-slots/*").hasRole("NGO")
                // An NGO's own list of needs includes closed ones, and closing / filling are writes.
                .requestMatchers(HttpMethod.GET, "/api/ngos/*/notifications").hasRole("NGO")
                .requestMatchers(HttpMethod.PATCH, "/api/ngos/*/notifications/*/close",
                        "/api/ngos/*/food-slots/*/fill", "/api/ngos/*/food-slots/*/reopen").hasRole("NGO")
                .requestMatchers(HttpMethod.PUT, "/api/ngos/*/food-settings").hasRole("NGO")
                .requestMatchers(HttpMethod.PATCH, "/api/events/enrollments/*/attend").hasRole("NGO")
                .requestMatchers(HttpMethod.GET, "/api/events/*/enrollments").hasRole("NGO")
                .requestMatchers(HttpMethod.POST, "/api/donations/**", "/api/events/*/enroll").hasRole("USER")
                .requestMatchers(HttpMethod.GET, "/api/events/my-enrollments", "/api/donations").hasRole("USER")
                // 80G receipts carry the donor's name, email and the amount they gave. The service
                // also checks the donation actually belongs to the caller — this rule only keeps
                // anonymous requests out. Stated explicitly because the pattern above matches the
                // exact path "/api/donations" and would leave "/api/donations/{id}/receipt" to fall
                // through to the permitAll default.
                .requestMatchers(HttpMethod.GET, "/api/donations/*/receipt").hasRole("USER")
                // Wallets belong to donor accounts only — an NGO or the admin login has no balance
                // to read or spend, so the whole subtree is locked to ROLE_USER in one rule.
                .requestMatchers("/api/wallet/**").hasRole("USER")
                // An NGO's own money: balance, ledger, payout details, withdrawal requests.
                // The per-record ownership check lives in NgoFinanceController — ROLE_NGO only
                // proves the caller is *some* approved NGO, not that it is this one.
                .requestMatchers("/api/ngos/*/finance/**").hasRole("NGO")
                // Razorpay's server-to-server payment callback. Necessarily unauthenticated —
                // Razorpay has no account here — so PaymentWebhookService verifies an HMAC
                // signature over the raw body instead, and refuses everything if the webhook
                // secret is unset. Stated explicitly rather than left to the permitAll default,
                // because an endpoint that credits wallets should not be public by accident.
                .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                // Profile pictures. Any logged-in account can change its OWN picture — the service
                // resolves the target from the authenticated identity (donors) or checks ownership
                // of the named NGO (organisations), so this rule only has to keep strangers out.
                // The same goes for a donor's profile gallery under /api/profile/media; viewing a
                // gallery (/api/users/*/media) is public, like an NGO's.
                .requestMatchers("/api/profile/**").authenticated()
                .requestMatchers("/api/chats/**").authenticated()
                .requestMatchers("/api/users/notifications/**").authenticated()
                .anyRequest().permitAll())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    /**
     * One rule for every endpoint, instead of a different hardcoded port list per controller.
     * Accepts any localhost port so it keeps working no matter which port the dev server picks,
     * plus the deployed frontend's address(es) from APP_FRONTEND_URL — comma-separated, so a
     * custom domain and the hosting provider's own URL can both be allowed.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrls) {
        List<String> origins = new java.util.ArrayList<>(List.of("http://localhost:*", "http://127.0.0.1:*"));
        for (String url : frontendUrls.split(",")) {
            // A trailing slash would never match: browsers send the Origin header without one.
            String origin = url.trim().replaceAll("/+$", "");
            if (!origin.isEmpty()) origins.add(origin);
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public UserDetailsService adminUserDetailsService(
            @Value("${ngo-admin.username:}") String username,
            @Value("${ngo-admin.password:}") String password,
            PasswordEncoder passwordEncoder,
            AccountRepository accountRepository,
            NgoRepository ngoRepository) {
        return email -> {
            if (!username.isBlank() && email.equals(username))
                return User.withUsername(username).password(passwordEncoder.encode(password)).roles("ADMIN").build();
            Account account = accountRepository.findByEmail(email);
            if (account != null) {
                String role = "ADMIN".equalsIgnoreCase(account.getRole()) ? "ADMIN" : "USER";
                return User.withUsername(account.getEmail()).password(account.getPassword())
                        .disabled(!account.isActive()).roles(role).build();
            }
            Ngo ngo = ngoRepository.findByEmail(email);
            if (ngo != null && ngo.getVerificationStatus() == VerificationStatus.APPROVED)
                return User.withUsername(ngo.getEmail()).password(ngo.getPassword())
                        .disabled(!ngo.isActive()).roles("NGO").build();
            throw new UsernameNotFoundException("Account not found: " + email);
        };
    }
}
