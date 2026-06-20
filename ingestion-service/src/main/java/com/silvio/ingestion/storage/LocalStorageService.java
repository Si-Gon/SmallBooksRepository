package com.silvio.ingestion.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// Implementación actual — guarda archivos en carpeta local del servidor
// @Primary indica a Spring que use esta implementación por defecto
// Para migrar a MySQL: quitar @Primary aquí y agregarlo en DatabaseStorageService

@Service
public class LocalStorageService implements StorageService {

    // Ruta base configurada en el yml del Config Server
    // Ejemplo: C:/smallbooks/archivos/ en Windows
    @Value("${storage.local.path}")
    private String rutaBase;

    @Override
    public String guardar(MultipartFile archivo, Long libroId) {
        try {
            // Crear la carpeta si no existe
            Path directorio = Paths.get(rutaBase);
            if (!Files.exists(directorio)) {
                Files.createDirectories(directorio);
            }

            // Nombre del archivo: libro_{id}_{nombreOriginal}
            // Ejemplo: libro_1_don_quijote.pdf
            String nombreArchivo = "libro_" + libroId + "_" +
                    archivo.getOriginalFilename().replaceAll("\\s+", "_");

            Path rutaArchivo = directorio.resolve(nombreArchivo);
            Files.write(rutaArchivo, archivo.getBytes());

            // Devolvemos la ruta completa como clave
            return rutaArchivo.toString();

        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el archivo: " + e.getMessage());
        }
    }

    @Override
    public byte[] obtener(String rutaOClave) {
        try {
            Path rutaArchivo = Paths.get(rutaOClave);
            if (!Files.exists(rutaArchivo)) {
                throw new RuntimeException("Archivo no encontrado en: " + rutaOClave);
            }
            return Files.readAllBytes(rutaArchivo);
        } catch (IOException e) {
            throw new RuntimeException("Error al leer el archivo: " + e.getMessage());
        }
    }

    @Override
    public void eliminar(String rutaOClave) {
        try {
            Path rutaArchivo = Paths.get(rutaOClave);
            Files.deleteIfExists(rutaArchivo);
        } catch (IOException e) {
            throw new RuntimeException("Error al eliminar el archivo: " + e.getMessage());
        }
    }
}