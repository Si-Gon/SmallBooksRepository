package com.silvio.elending.exception;

public class LimitePrestamosExcedidoException extends RuntimeException {
    public LimitePrestamosExcedidoException(int maxPrestamos, String plan) {
        super("Has alcanzado el límite de " + maxPrestamos + " préstamos activos para tu plan " + plan);
    }
}
