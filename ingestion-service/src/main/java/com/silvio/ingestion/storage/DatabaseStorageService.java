package com.silvio.ingestion.storage;

import com.silvio.ingestion.model.ArchivoLibro;
import com.silvio.ingestion.repository.ArchivoLibroRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


@Service
@Primary
public class DatabaseStorageService implements StorageService {

    private final ArchivoLibroRepository repository;

    public DatabaseStorageService(ArchivoLibroRepository repository) {
        this.repository = repository;
    }

    @Override
public String guardar(MultipartFile archivo, Long libroId) {
    // No inserta nada — IngestionService maneja el registro completo
    // Solo devuelve la clave que identifica que está en BD
    return "db:" + libroId;
}

@Override
public byte[] obtener(String rutaOClave) {
    Long libroId = Long.valueOf(rutaOClave.replace("db:", ""));
    ArchivoLibro entidad = repository.findByLibroId(libroId)
            .orElseThrow(() -> new RuntimeException("Archivo no encontrado: " + rutaOClave));
    return entidad.getDatos();
}

@Override
public void eliminar(String rutaOClave) {
    // No hace nada — IngestionService maneja el delete completo
    // El registro se borra en IngestionService.eliminar()
}
}