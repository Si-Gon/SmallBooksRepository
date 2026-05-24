-- Agregar campos para recuperación de contraseña
ALTER TABLE users 
ADD COLUMN reset_token VARCHAR(255) NULL,
ADD COLUMN reset_token_expiry DATETIME NULL;

-- Crear índice para búsqueda rápida por reset_token
CREATE INDEX idx_reset_token ON users(reset_token);