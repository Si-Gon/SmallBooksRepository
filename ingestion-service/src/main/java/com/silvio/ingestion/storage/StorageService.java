package com.silvio.ingestion.storage;

import org.springframework.web.multipart.MultipartFile;

// Interfaz que abstrae el mecanismo de almacenamiento
// Implementación actual: LocalStorageService (guarda en disco)
// Implementación futura: DatabaseStorageService (guarda en MySQL como BLOB)
//
// Para migrar: solo cambiar @Primary de una implementación a la otra

public interface StorageService {

    // Guarda el archivo y devuelve la ruta o clave donde quedó guardado
    String guardar(MultipartFile archivo, Long libroId);

    // Recupera el archivo como array de bytes para enviarlo al cliente
    byte[] obtener(String rutaOClave);

    // Elimina el archivo del almacenamiento
    void eliminar(String rutaOClave);
}