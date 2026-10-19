package com.codeconheiner.tienda.common.ratelimit;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ventana deslizante en memoria, sin dependencias externas: guarda la marca de tiempo
 * de cada solicitud aceptada y descarta las que ya quedaron fuera de la ventana.
 *
 * <p>Alcance: una sola instancia de la API. En un despliegue multi-instancia habría que
 * respaldarlo con un almacenamiento compartido (p. ej. Redis) reemplazando esta clase
 * por otra implementación de {@link RateLimiter} (abiertos al cambio, cerrados a la
 * modificación).</p>
 */
public class VentanaDeslizanteRateLimiter implements RateLimiter {

    private final int maximo;
    private final long ventanaMillis;
    private final Clock clock;
    private final Map<String, Deque<Long>> ventanas = new ConcurrentHashMap<>();

    public VentanaDeslizanteRateLimiter(int maximo, long ventanaSegundos) {
        this(maximo, ventanaSegundos, Clock.systemUTC());
    }

    public VentanaDeslizanteRateLimiter(int maximo, long ventanaSegundos, Clock clock) {
        this.maximo = maximo;
        this.ventanaMillis = ventanaSegundos * 1000L;
        this.clock = clock;
    }

    @Override
    public boolean permitir(String clave) {
        long ahora = clock.millis();
        Deque<Long> marcas = ventanas.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && marcas.peekFirst() <= ahora - ventanaMillis) {
                marcas.pollFirst();
            }
            if (marcas.size() >= maximo) {
                return false;
            }
            marcas.addLast(ahora);
            return true;
        }
    }
}
