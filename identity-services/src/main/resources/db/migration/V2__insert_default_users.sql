-- Insertar admin con contraseña: admin123 (hash BCrypt generado)
INSERT INTO users (username, password)
VALUES ('admin', '$2a$10$DgFIbPVsRWWNErfxSqkZCOZhlDuufsSTDvYAfjRGfgpBvGzoWPA32');

-- Insertar user1 con contraseña: user123 (hash BCrypt generado)
INSERT INTO users (username, password)
VALUES ('user1', '$2a$10$xj5DD7v5FDWj34of/08q7.UHCIro0IRSRDdkPyJbDakrtj/uVSVCi');

-- Roles para admin
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_ADMIN' FROM users WHERE username = 'admin';

INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_USER' FROM users WHERE username = 'admin';

-- Roles para user1
INSERT INTO user_roles (user_id, role)
SELECT id, 'ROLE_USER' FROM users WHERE username = 'user1';