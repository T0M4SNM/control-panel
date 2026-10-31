package io.github.t0m4snm.control_panel_backend.monitoring.checker;

import io.github.t0m4snm.control_panel_backend.monitoring.CheckType;

/**
 * Ejecuta un tipo de comprobación. Hay una implementación por {@link CheckType}, y Spring las
 * inyecta todas en una lista: es el patrón estrategia.
 * <p>
 * Nunca lanza excepciones: cualquier error de red es un {@link CheckOutcome} fallido.
 */
public interface Checker {

	CheckType type();

	CheckOutcome check(CheckSpec spec);

}
