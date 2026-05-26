package com.silvio.content.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ingestion-service")
public interface IngestionClient {

    // Obtener los bytes del archivo del libro
    @GetMapping("/api/ingestion/{libroId}/bytes")
    byte[] obtenerBytes(@PathVariable("libroId") Long libroId);
}