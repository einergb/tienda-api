package com.codeconheiner.tienda.direcciones;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.direcciones.dto.DireccionRequest;
import com.codeconheiner.tienda.direcciones.dto.DireccionResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DireccionService {

    private final DireccionRepository repo;

    public DireccionService(DireccionRepository repo) {
        this.repo = repo;
    }

    public List<DireccionResponse> listar(Long usuarioId) {
        return repo.listarPorUsuario(usuarioId);
    }

    @Transactional
    public DireccionResponse crear(Long usuarioId, DireccionRequest request) {
        Long id = repo.crear(usuarioId, request);
        return repo.buscarPropia(id, usuarioId).orElseThrow(DireccionService::noEncontrada);
    }

    @Transactional
    public DireccionResponse actualizar(Long usuarioId, Long id, DireccionRequest request) {
        if (repo.actualizar(id, usuarioId, request) == 0) {
            throw noEncontrada();
        }
        return repo.buscarPropia(id, usuarioId).orElseThrow(DireccionService::noEncontrada);
    }

    @Transactional
    public void eliminar(Long usuarioId, Long id) {
        if (repo.eliminar(id, usuarioId) == 0) {
            throw noEncontrada();
        }
    }

    /** Uso interno del checkout: valida que la direccion exista y pertenezca al usuario. */
    public DireccionResponse obtenerParaPedido(Long usuarioId, Long direccionId) {
        return repo.buscarPropia(direccionId, usuarioId).orElseThrow(DireccionService::noEncontrada);
    }

    private static ApiException noEncontrada() {
        return new ApiException(HttpStatus.NOT_FOUND, "Dirección no encontrada");
    }
}
