-- Eliminar la restricción UNIQUE sobre usuario_id para permitir historial de suscripciones
ALTER TABLE suscripciones 
DROP INDEX usuario_id;