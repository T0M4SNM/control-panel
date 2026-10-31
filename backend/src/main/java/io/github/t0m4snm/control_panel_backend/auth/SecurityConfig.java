package io.github.t0m4snm.control_panel_backend.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

/**
 * Sesión con cookie: el frontend (SPA) y la API se sirven desde el mismo origen detrás de Nginx.
 *
 * <ul>
 * <li>Login: {@code POST /api/auth/login} con formulario {@code email} y {@code password}.
 * Responde 204 y deja la cookie de sesión; 401 si las credenciales no son válidas.</li>
 * <li>CSRF: el token viaja en la cookie {@code XSRF-TOKEN} y el frontend lo devuelve en la
 * cabecera {@code X-XSRF-TOKEN} en cada petición que modifica algo (incluido el login).</li>
 * <li>Sin sesión, la API responde 401 en lugar de redirigir a una página de login.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health", "/api/auth/csrf").permitAll()
						// Sin esto, un 403 (p. ej. por CSRF) se reenvía a /error y acaba como 401
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf.spa())
				.formLogin(login -> login
						.loginPage("/api/auth/login")
						.loginProcessingUrl("/api/auth/login")
						.usernameParameter("email")
						.passwordParameter("password")
						.successHandler((request, response, authentication) ->
								response.setStatus(HttpStatus.NO_CONTENT.value()))
						.failureHandler((request, response, exception) ->
								response.setStatus(HttpStatus.UNAUTHORIZED.value()))
						.permitAll())
				.logout(logout -> logout
						.logoutUrl("/api/auth/logout")
						.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.httpBasic(basic -> basic.disable());
		return http.build();
	}

	/** BCrypt por defecto, con prefijo {@code {bcrypt}} para poder migrar de algoritmo en el futuro. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
