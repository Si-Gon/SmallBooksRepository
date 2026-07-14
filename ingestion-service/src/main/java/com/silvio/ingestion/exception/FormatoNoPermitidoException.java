package com.silvio.ingestion.exception;

public class FormatoNoPermitidoException extends RuntimeException {
    public FormatoNoPermitidoException(String contentType) {
        super("Formato no permitido: " + contentType + ". Solo se aceptan PDF y EPUB");
    }
}
