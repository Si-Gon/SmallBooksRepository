package com.silvio.license.exception;

public class ReduccionCopiasInvalidaException extends RuntimeException {
    public ReduccionCopiasInvalidaException(int totalCopias, int copiasPrestadas) {
        super("No se puede reducir a " + totalCopias + " copias — hay " + copiasPrestadas + " copias actualmente prestadas");
    }
}
