package com.codeconheiner.tienda.facturas;

/**
 * Copia de la dirección de envío que el pedido guarda al momento de comprar (la factura
 * siempre refleja los datos con los que se hizo el pedido, aunque luego se edite).
 */
public record EnvioCopia(
        String destinatario,
        String telefono,
        String departamento,
        String ciudad,
        String direccion) {}
