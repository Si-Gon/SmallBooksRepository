-- Agrega columna version para optimistic locking JPA
-- Cada actualización incrementa version automáticamente
-- Si dos transacciones (ej. prestar/devolver concurrentes) modifican
-- la misma fila con distinto version, la segunda recibe ObjectOptimisticLockingFailureException
ALTER TABLE licencias ADD COLUMN version INT NOT NULL DEFAULT 0;
