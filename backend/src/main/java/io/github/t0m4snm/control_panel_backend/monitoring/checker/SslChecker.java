package io.github.t0m4snm.control_panel_backend.monitoring.checker;

import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.springframework.stereotype.Component;

import io.github.t0m4snm.control_panel_backend.monitoring.CheckType;

/**
 * Hace el handshake TLS con el host (puerto 443 si no se indica). Éxito si Java acepta el
 * certificado: firmado por una autoridad de confianza, no caducado y válido para ese nombre. Guarda
 * cuándo caduca para los avisos.
 */
@Component
public class SslChecker implements Checker {

	private static final int DEFAULT_PORT = 443;

	private final SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();

	@Override
	public CheckType type() {
		return CheckType.SSL;
	}

	@Override
	public CheckOutcome check(CheckSpec spec) {
		long start = System.nanoTime();
		int timeout = (int) spec.timeout().toMillis();
		int port = spec.port() == null ? DEFAULT_PORT : spec.port();
		try (SSLSocket socket = (SSLSocket) factory.createSocket()) {
			socket.connect(new InetSocketAddress(spec.host(), port), timeout);
			socket.setSoTimeout(timeout);
			SSLParameters parameters = socket.getSSLParameters();
			// Comprueba que el certificado es de este nombre, como haría un navegador
			parameters.setEndpointIdentificationAlgorithm("HTTPS");
			if (!isIpAddress(spec.host())) {
				parameters.setServerNames(List.of(new SNIHostName(spec.host())));
			}
			socket.setSSLParameters(parameters);
			socket.startHandshake();
			X509Certificate certificate = (X509Certificate) socket.getSession().getPeerCertificates()[0];
			return new CheckOutcome(true, (int) CheckOutcome.elapsedMs(start), null, null,
					certificate.getNotAfter().toInstant());
		} catch (Exception e) {
			return CheckOutcome.failure(CheckOutcome.elapsedMs(start), CheckOutcome.describe(e));
		}
	}

	private static boolean isIpAddress(String host) {
		return host.contains(":") || host.matches("[0-9.]+");
	}

}
