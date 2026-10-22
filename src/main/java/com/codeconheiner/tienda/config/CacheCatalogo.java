package com.codeconheiner.tienda.config;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Invalidacion de las caches publicas del catalogo.
 *
 * <p>El stock que ve el catalogo publico es {@code stock - stock_reservado} y lo modifican los
 * triggers de la base de datos (V3) disparados desde el codigo Java. Cada vez que una de esas
 * operaciones se ejecuta desde la API hay que tirar la entrada del producto afectado y los
 * listados, porque ambas muestran el stock disponible.
 *
 * <p>Si hay una transaccion abierta la invalidacion se ejecuta despues del commit: antes podria
 * volver a meter en la cache el valor viejo si otra peticion repuebla la entrada mientras la
 * transaccion sigue sin confirmar.
 *
 * <p>Cuando la cache esta deshabilitada ({@code CACHE_TYPE=none}) no hay gestor de cache y todas
 * las invalidaciones son no-ops: el comportamiento es el de siempre.
 */
@Component
public class CacheCatalogo {

    /** Detalle publico de un producto. */
    public static final String PRODUCTO = "producto";
    /** Listados del catalogo (tienen el stock disponible y el nombre de la categoria). */
    public static final String LISTADO = "catalogo-listado";
    /** Categorias activas. */
    public static final String CATEGORIAS = "categorias";
    /** Datos de la tienda (nombre, moneda, IVA). */
    public static final String TIENDA = "tienda-info";

    private static final Logger log = LoggerFactory.getLogger(CacheCatalogo.class);

    private final Supplier<CacheManager> cacheManager;

    @Autowired
    public CacheCatalogo(ObjectProvider<CacheManager> cacheManagerProvider) {
        this.cacheManager = cacheManagerProvider::getIfAvailable;
    }

    public CacheCatalogo(CacheManager cacheManager) {
        this.cacheManager = () -> cacheManager;
    }

    /**
     * Invalida el detalle de un producto. Se usa cuando el producto cambia pero el stock no
     * (precio, nombre, imagen...).
     */
    public void invalidarProducto(Long productoId) {
        if (productoId == null) {
            return;
        }
        despuesDeCommit(() -> {
            Cache cache = cache(PRODUCTO);
            if (cache != null) {
                cache.evict(productoId);
            }
        });
    }

    /**
     * Invalida el detalle de los productos indicados y todos los listados: es lo que se usa
     * cuando el stock o la reserva de stock cambian.
     */
    public void invalidarProductos(Collection<Long> productoIds) {
        List<Long> ids = productoIds == null ? List.of()
                : productoIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        despuesDeCommit(() -> {
            Cache cache = cache(PRODUCTO);
            if (cache != null) {
                ids.forEach(cache::evict);
            }
            limpiar(LISTADO);
        });
    }

    /** Invalida todos los listados del catalogo. */
    public void invalidarListados() {
        despuesDeCommit(() -> limpiar(LISTADO));
    }

    /**
     * Invalida el detalle de todos los productos y todos los listados. Se usa cuando no se
     * conoce que productos cambiaron (por ejemplo, al expirar reservas en lote).
     */
    public void invalidarTodoElCatalogo() {
        despuesDeCommit(() -> {
            limpiar(PRODUCTO);
            limpiar(LISTADO);
        });
    }

    /**
     * Invalida las categorias y los listados: el listado publica el nombre de la categoria.
     */
    public void invalidarCategorias() {
        despuesDeCommit(() -> {
            limpiar(CATEGORIAS);
            limpiar(LISTADO);
        });
    }

    /** Invalida los datos de la tienda (nombre, moneda, IVA...). */
    public void invalidarTienda() {
        despuesDeCommit(() -> limpiar(TIENDA));
    }

    private void despuesDeCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ejecutar(accion);
                }
            });
            return;
        }
        ejecutar(accion);
    }

    private void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (Exception e) {
            // Invalidar la cache nunca debe tumbar la peticion.
            log.warn("No se pudo invalidar la cache: {}", e.toString());
        }
    }

    private Cache cache(String nombre) {
        CacheManager manager = cacheManager.get();
        return manager == null ? null : manager.getCache(nombre);
    }

    private void limpiar(String nombre) {
        Cache cache = cache(nombre);
        if (cache != null) {
            cache.clear();
        }
    }
}
