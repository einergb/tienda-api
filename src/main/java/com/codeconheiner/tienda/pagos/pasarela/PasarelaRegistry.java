package com.codeconheiner.tienda.pagos.pasarela;

import com.codeconheiner.tienda.common.ApiException;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Catálogo de pasarelas disponibles; resuelve por nombre (WOMPI, EPAYCO...). */
@Component
public class PasarelaRegistry {

    private final Map<String, PasarelaPago> porNombre;

    public PasarelaRegistry(java.util.List<PasarelaPago> pasarelas) {
        this.porNombre = pasarelas.stream()
                .collect(Collectors.toMap(p -> normalizar(p.nombre()), Function.identity()));
    }

    public PasarelaPago obtener(String nombre) {
        PasarelaPago pasarela = porNombre.get(normalizar(nombre));
        if (pasarela == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Pasarela no soportada");
        }
        return pasarela;
    }

    private static String normalizar(String nombre) {
        return nombre == null ? "" : nombre.trim().toLowerCase(Locale.ROOT);
    }
}
