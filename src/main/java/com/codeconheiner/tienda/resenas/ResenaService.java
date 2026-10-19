package com.codeconheiner.tienda.resenas;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.resenas.dto.ResenaRequest;
import com.codeconheiner.tienda.resenas.dto.ResenaResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResenaService {

    private final ResenaRepository repo;

    public ResenaService(ResenaRepository repo) {
        this.repo = repo;
    }

    public List<ResenaResponse> listarPorProducto(Long productoId) {
        return repo.listarPorProducto(productoId);
    }

    /** El trigger de V3 rechaza (422) reseñas de productos no comprados o no entregados. */
    @Transactional
    public ResenaResponse crear(Long usuarioId, ResenaRequest request) {
        Long id = repo.crear(usuarioId, request);
        return repo.buscarPropia(id, usuarioId).orElseThrow(ResenaService::noEncontrada);
    }

    @Transactional
    public ResenaResponse actualizar(Long usuarioId, Long id, ResenaRequest request) {
        if (repo.actualizar(id, usuarioId, request) == 0) {
            throw noEncontrada();
        }
        return repo.buscarPropia(id, usuarioId).orElseThrow(ResenaService::noEncontrada);
    }

    @Transactional
    public void eliminar(Long usuarioId, Long id) {
        if (repo.eliminar(id, usuarioId) == 0) {
            throw noEncontrada();
        }
    }

    private static ApiException noEncontrada() {
        return new ApiException(HttpStatus.NOT_FOUND, "Reseña no encontrada");
    }
}
