package io.github.t0m4snm.control_panel_backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Errores HTTP de la API. Llegan al cliente como {@code application/problem+json}.
 */
public final class ApiErrors {

	private ApiErrors() {
	}

	/** El recurso no existe o el usuario no puede verlo; no se distingue para no revelar que existe. */
	public static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND);
	}

	/** El usuario ve el recurso, pero no puede hacer esta operación. */
	public static ResponseStatusException forbidden() {
		return new ResponseStatusException(HttpStatus.FORBIDDEN);
	}

	/** Los datos son válidos, pero chocan con el estado actual (p. ej. un reparto que supera el 100 %). */
	public static ResponseStatusException conflict(String detail) {
		return new ResponseStatusException(HttpStatus.CONFLICT, detail);
	}

	/** Los datos no son válidos por una regla que no cubre la validación del cuerpo. */
	public static ResponseStatusException badRequest(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}

}
