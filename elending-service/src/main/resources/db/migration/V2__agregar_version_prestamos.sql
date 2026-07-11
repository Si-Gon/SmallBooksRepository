-- Agrega columna version para optimistic locking JPA
-- Cada actualización incrementa version automáticamente
-- Si dos transacciones intentan actualizar la misma fila con distinto version,
-- la segunda recibe ObjectOptimisticLockingFailureException
ALTER TABLE prestamos ADD COLUMN version INT NOT NULL DEFAULT 0;
