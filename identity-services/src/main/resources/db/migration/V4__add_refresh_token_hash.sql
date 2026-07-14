-- Agregar campo para hash de refresh token (rotación de tokens)
ALTER TABLE users
ADD COLUMN refresh_token_hash VARCHAR(64) NULL;

-- Crear índice para búsqueda rápida por refresh_token_hash
CREATE INDEX idx_refresh_token_hash ON users(refresh_token_hash);
