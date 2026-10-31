package io.github.t0m4snm.control_panel_backend.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Redondeo de importes en euros. Los cálculos intermedios se hacen con 4 decimales y solo se
 * redondea a céntimos al devolver el resultado, para no acumular errores.
 */
public final class Money {

	public static final int CALCULATION_SCALE = 4;

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private Money() {
	}

	public static BigDecimal toCents(BigDecimal amount) {
		return amount.setScale(2, RoundingMode.HALF_UP);
	}

	/** {@code amount * percent / 100}, con decimales de cálculo. */
	public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
		return amount.multiply(percent).divide(HUNDRED, CALCULATION_SCALE, RoundingMode.HALF_UP);
	}

}
