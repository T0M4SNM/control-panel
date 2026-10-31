package io.github.t0m4snm.control_panel_backend.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.sun.net.httpserver.HttpServer;

import io.github.t0m4snm.control_panel_backend.TestcontainersConfiguration;
import io.github.t0m4snm.control_panel_backend.notification.NotificationDraft;
import io.github.t0m4snm.control_panel_backend.notification.NotificationType;
import io.github.t0m4snm.control_panel_backend.notification.NotificationWriter;
import io.github.t0m4snm.control_panel_backend.notification.Severity;
import io.github.t0m4snm.control_panel_backend.user.AppUser;
import io.github.t0m4snm.control_panel_backend.user.AppUserRepository;
import io.github.t0m4snm.control_panel_backend.user.UserRole;
import io.github.t0m4snm.control_panel_backend.user.Workspace;
import io.github.t0m4snm.control_panel_backend.user.WorkspaceRepository;

/**
 * Suscripción y envío contra un «servicio de push» local que guarda lo que recibe. Para poder usar
 * {@code http://127.0.0.1}, este contexto permite http y ese host.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PushTests {

	private static final KeyPair VAPID = vapidKeys();

	@DynamicPropertySource
	static void pushProperties(DynamicPropertyRegistry registry) {
		registry.add("app.push.vapid-public-key",
				() -> WebPushCrypto.base64Url(WebPushCrypto.encodePublic((ECPublicKey) VAPID.getPublic())));
		registry.add("app.push.vapid-private-key",
				() -> WebPushCrypto.base64Url(WebPushCrypto.encodePrivate((ECPrivateKey) VAPID.getPrivate())));
		registry.add("app.push.subject", () -> "mailto:admin@example.com");
		registry.add("app.push.allow-http", () -> "true");
		registry.add("app.push.allowed-endpoint-hosts", () -> "127.0.0.1,fcm.googleapis.com");
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private WorkspaceRepository workspaces;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private PushSubscriptionRepository subscriptions;

	@Autowired
	private NotificationWriter writer;

	@Autowired
	private PushService push;

	@Autowired
	private UserDetailsService userDetailsService;

	private final List<Received> received = new CopyOnWriteArrayList<>();

	private final AtomicInteger pushServiceStatus = new AtomicInteger(201);

	private HttpServer pushServer;

	private AppUser admin;

	private AppUser member;

	private BrowserSide browser;

	@BeforeEach
	void setUp() throws Exception {
		pushServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		pushServer.createContext("/", exchange -> {
			received.add(new Received(exchange.getRequestURI().getPath(),
					Map.of("authorization", exchange.getRequestHeaders().getFirst("Authorization"),
							"content-encoding", exchange.getRequestHeaders().getFirst("Content-Encoding"),
							"ttl", exchange.getRequestHeaders().getFirst("TTL"),
							"urgency", exchange.getRequestHeaders().getFirst("Urgency")),
					exchange.getRequestBody().readAllBytes()));
			exchange.sendResponseHeaders(pushServiceStatus.get(), -1);
			exchange.close();
		});
		pushServer.start();

		Workspace workspace = workspaces.save(new Workspace("Principal"));
		admin = users.save(new AppUser(workspace, "admin@example.com", "{noop}x", "Admin", UserRole.ADMIN));
		member = users.save(new AppUser(workspace, "member@example.com", "{noop}x", "Member", UserRole.MEMBER));
		users.flush();
		browser = BrowserSide.create();
	}

	@AfterEach
	void stopPushServer() {
		pushServer.stop(0);
	}

	@Test
	void exposesThePublicKey() throws Exception {
		mvc.perform(get("/api/push/config").with(as(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.publicKey").value(
						WebPushCrypto.base64Url(WebPushCrypto.encodePublic((ECPublicKey) VAPID.getPublic()))))
				.andExpect(jsonPath("$.subscriptions").value(0));
	}

	@Test
	void rejectsUnknownPushServicesAndBadKeys() throws Exception {
		mvc.perform(subscribe("https://evil.example.com/push", browser.p256dh(), browser.auth(), member))
				.andExpect(status().isBadRequest());
		mvc.perform(subscribe("https://fcm.googleapis.com.evil.com/x", browser.p256dh(), browser.auth(), member))
				.andExpect(status().isBadRequest());
		mvc.perform(subscribe("https://fcm.googleapis.com/fcm/send/abc", "no-es-una-clave", browser.auth(), member))
				.andExpect(status().isBadRequest());
		mvc.perform(subscribe("https://fcm.googleapis.com/fcm/send/abc", browser.p256dh(), browser.auth(), member))
				.andExpect(status().isNoContent());
	}

	@Test
	void deliversEncryptedNotificationsToTheRecipientsDevices() throws Exception {
		mvc.perform(subscribe(endpoint("/movil"), browser.p256dh(), browser.auth(), member))
				.andExpect(status().isNoContent());
		BrowserSide adminBrowser = BrowserSide.create();
		mvc.perform(subscribe(endpoint("/admin"), adminBrowser.p256dh(), adminBrowser.auth(), admin))
				.andExpect(status().isNoContent());

		List<UUID> ids = writer.notify(List.of(member.getId()), new NotificationDraft(NotificationType.CHECK_DOWN,
				Severity.CRITICAL, "Caída: Web \"A\"", "No responde.\nÚltimo error: HTTP 503", null, null, "k1"));
		push.deliver(ids);

		assertThat(received).singleElement().satisfies(request -> {
			assertThat(request.path()).isEqualTo("/movil");
			assertThat(request.headers().get("content-encoding")).isEqualTo("aes128gcm");
			assertThat(request.headers().get("ttl")).isEqualTo("86400");
			assertThat(request.headers().get("urgency")).isEqualTo("high");
			assertThat(request.headers().get("authorization")).startsWith("vapid t=");
			String json = new String(browser.decrypt(request.body()), StandardCharsets.UTF_8);
			assertThat(json).contains("\"title\":\"Caída: Web \\\"A\\\"\"")
					.contains("\"body\":\"No responde.\\nÚltimo error: HTTP 503\"")
					.contains("\"severity\":\"CRITICAL\"")
					.contains("\"id\":\"" + ids.getFirst() + "\"");
		});
	}

	@Test
	void removesSubscriptionsThatThePushServiceRejects() throws Exception {
		mvc.perform(subscribe(endpoint("/caducada"), browser.p256dh(), browser.auth(), member))
				.andExpect(status().isNoContent());
		pushServiceStatus.set(410);

		push.deliver(writer.notify(List.of(member.getId()), new NotificationDraft(NotificationType.CHECK_RECOVERED,
				Severity.INFO, "Recuperada", "Vuelve a responder", null, null, "k2")));

		assertThat(subscriptions.findByEndpoint(endpoint("/caducada"))).isEmpty();
	}

	@Test
	void usersCanOnlyUnsubscribeTheirOwnDevices() throws Exception {
		mvc.perform(subscribe(endpoint("/movil"), browser.p256dh(), browser.auth(), member))
				.andExpect(status().isNoContent());

		mvc.perform(post("/api/push/subscriptions/unsubscribe").contentType(MediaType.APPLICATION_JSON)
				.content("{\"endpoint\":\"%s\"}".formatted(endpoint("/movil"))).with(as(admin)))
				.andExpect(status().isNoContent());
		assertThat(subscriptions.findByEndpoint(endpoint("/movil"))).isPresent();

		mvc.perform(post("/api/push/subscriptions/unsubscribe").contentType(MediaType.APPLICATION_JSON)
				.content("{\"endpoint\":\"%s\"}".formatted(endpoint("/movil"))).with(as(member)))
				.andExpect(status().isNoContent());
		assertThat(subscriptions.findByEndpoint(endpoint("/movil"))).isEmpty();
	}

	private String endpoint(String path) {
		return "http://127.0.0.1:" + pushServer.getAddress().getPort() + path;
	}

	private RequestBuilder subscribe(String endpoint, String p256dh, String auth,
			AppUser appUser) {
		return post("/api/push/subscriptions").contentType(MediaType.APPLICATION_JSON)
				.header("User-Agent", "Test")
				.content("{\"endpoint\":\"%s\",\"keys\":{\"p256dh\":\"%s\",\"auth\":\"%s\"}}".formatted(endpoint,
						p256dh, auth))
				.with(as(appUser));
	}

	private RequestPostProcessor as(AppUser appUser) {
		return request -> csrf().postProcessRequest(
				user(userDetailsService.loadUserByUsername(appUser.getEmail())).postProcessRequest(request));
	}

	private static KeyPair vapidKeys() {
		try {
			return WebPushCrypto.generateKeyPair();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	private record Received(String path, Map<String, String> headers, byte[] body) {
	}

}
