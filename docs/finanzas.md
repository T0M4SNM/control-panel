# Finanzas: movimientos y beneficio

Ingresos y gastos reales, y el beneficio mensual y por proyecto. **Solo `ADMIN`**: un miembro recibe 403 en `/api/movements/**` y `/api/finance/**`.

## Movimientos

Un movimiento es un ingreso o un gasto **fuera de contrato**: cobros a clientes, una compra puntual, una licencia… Los pagos de los contratos no se registran aquí: su coste se calcula (ver `docs/infraestructura-y-costes.md`).

| Petición | Respuesta |
|---|---|
| `GET /api/movements` (opcional `?projectId=`) | Movimientos, del más reciente al más antiguo |
| `GET /api/movements/{id}` | Uno |
| `POST /api/movements` | `201` |
| `PUT /api/movements/{id}` | Movimiento actualizado |
| `DELETE /api/movements/{id}` | `204` |

```json
{
  "type": "INCOME | EXPENSE", "amount": 50.00, "date": "2026-01-31",
  "description": "Cuota mensual cliente", "projectId": "…", "contractId": null,
  "recurrence": "ONE_TIME | MONTHLY | YEARLY", "endsOn": "2026-12-31"
}
```

- `amount` siempre es positivo; el signo lo da `type`.
- `recurrence` vale `ONE_TIME` si no llega. Un movimiento `MONTHLY` cuenta cada mes el mismo día que `date`; si el mes es más corto, cuenta el último día (un día 31 cuenta el 28 o 29 de febrero). Uno `YEARLY` cuenta cada año en el mes de `date`.
- `endsOn` es opcional y solo vale para movimientos que se repiten. No puede ser anterior a `date`.
- `projectId` y `contractId` son opcionales. Sin proyecto, el movimiento cuenta como «sin asignar».

## Resumen económico

`GET /api/finance/summary?from=2026-01&to=2026-12`. Sin parámetros devuelve el año en curso; el periodo máximo es de 36 meses.

```json
{
  "from": "2026-01", "to": "2026-12",
  "months": [
    { "month": "2026-01", "income": 50.00, "expenses": 0, "contractCosts": 11.00, "timeCosts": 0,
      "profit": 39.00, "realProfit": 39.00, "projected": false }
  ],
  "totals": { "income": 420.00, "expenses": 50.00, "contractCosts": 132.00, "timeCosts": 100.00,
              "profit": 238.00, "realProfit": 138.00 },
  "projects": [
    { "projectId": "…", "projectName": "A", "income": 300.00, "expenses": 20.00, "contractCosts": 120.00,
      "timeCosts": 100.00, "profit": 160.00, "realProfit": 60.00 }
  ],
  "unassigned": { "income": 0, "expenses": 30.00, "contractCosts": 12.00, "timeCosts": 0,
                  "profit": -42.00, "realProfit": -42.00 }
}
```

- En cada fila:
  - `profit = income − expenses − contractCosts` es el beneficio en dinero;
  - `realProfit = profit − timeCosts` descuenta también el tiempo dedicado, valorado con la tarifa de cada entrada (ver `docs/tiempo.md`).
- `contractCosts` es el coste mensual de los contratos activos, repartido por proyecto igual que en `/api/costs/summary`.
- La suma de los proyectos más `unassigned` da exactamente `totals`.
- `projected: true` marca los meses que aún no han llegado: sus cifras son una previsión.
- El tiempo cuenta en el mes en que empieza cada entrada (hora de Madrid); una entrada en marcha cuenta hasta ahora. Todo el tiempo es de algún proyecto, así que `unassigned.timeCosts` siempre es 0.

### Limitación conocida

Los contratos no guardan desde cuándo están activos, así que **todos los meses usan el coste de contratos actual**. Para meses pasados es una aproximación: si contrataste un servidor en marzo, aparecerá también en enero y febrero. Si se necesita precisión histórica, habría que añadir una fecha de inicio a `contract` con una migración nueva.
