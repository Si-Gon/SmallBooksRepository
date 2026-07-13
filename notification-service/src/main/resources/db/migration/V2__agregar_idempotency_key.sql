-- Agrega columna idempotency_key con UNIQUE INDEX para evitar
-- notificaciones duplicadas cuando RabbitMQ reintenta entregar
-- el mismo mensaje (retry policy: 3 intentos con backoff).
--
-- El idempotency_key se genera como SHA-256(usuario_id + "|" + tipo + "|" + mensaje)
-- para que dos mensajes con idéntico contenido produzcan el mismo hash.
ALTER TABLE notificaciones
    ADD COLUMN idempotency_key VARCHAR(64) NOT NULL AFTER leida,
    ADD UNIQUE INDEX idx_idempotency_key (idempotency_key);
