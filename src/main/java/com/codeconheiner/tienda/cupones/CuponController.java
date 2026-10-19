package com.codeconheiner.tienda.cupones;

import com.codeconheiner.tienda.cupones.dto.ValidacionCuponResponse;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de un cupón antes de finalizar la compra. */
@RestController
@RequestMapping("/api/cupones")
public class CuponController {

    private final CuponService service;

    public CuponController(CuponService service) {
        this.service = service;
    }

    @GetMapping("/validar")
    public ValidacionCuponResponse validar(@RequestParam String codigo,
            @RequestParam(defaultValue = "0") BigDecimal subtotal) {
        return service.validar(codigo, subtotal);
    }
}
