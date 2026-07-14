package com.silvio.catalog.exception;

public class LibroDuplicadoException extends RuntimeException {
    public LibroDuplicadoException(String isbn) {
        super("Ya existe un libro con ISBN: " + isbn);
    }
}
