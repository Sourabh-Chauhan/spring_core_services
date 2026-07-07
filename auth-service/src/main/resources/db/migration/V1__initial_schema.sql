-- 1. Permissions Table
CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL
);

-- 2. Roles Table
CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL
);

-- 3. Role Permissions Join Table
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);

-- 4. Users Table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    user_email VARCHAR(300) UNIQUE NOT NULL,
    user_name VARCHAR(500),
    password VARCHAR(255),
    image VARCHAR(255),
    enable BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    provider_id VARCHAR(255)
);

-- 5. User Roles Join Table
CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

-- 6. Verification Tokens Table
CREATE TABLE IF NOT EXISTS verification_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 7. Password Reset Tokens Table
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 8. Refresh Tokens Table
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY,
    jti VARCHAR(255) UNIQUE NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by_token VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    device_info VARCHAR(150),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS refresh_tokens_user_id_idx ON refresh_tokens (user_id);

-- 9. Audit Logs Table
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    details VARCHAR(2000),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 10. Seed Default Roles
INSERT INTO roles (id, name) VALUES 
('c53ea45b-d1e9-4e78-9e6e-213c9e6eb1de', 'ROLE_USER'),
('c53ea45b-d1e9-4e78-9e6e-213c9e6eb1df', 'ROLE_ADMIN'),
('c53ea45b-d1e9-4e78-9e6e-213c9e6eb1e0', 'ROLE_GUEST')
ON CONFLICT (name) DO NOTHING;

-- 11. Seed First System Admin User (Credentials: admin@company.com / Admin123!)
---* This is an example user Please change it      DO NOT USE THIS *---
INSERT INTO users (id, user_email, user_name, password, enable, email_verified, provider)
VALUES (
    'd43ea45b-d1e9-4e78-9e6e-213c9e6eb1fa',
    'admin@company.com',
    'System Admin',
    '$2a$10$e0MYzXy5B.xqy779o/bF1u01mCj9t76i80yvUeM.L09y20v8j6b5a',
    TRUE,
    TRUE,
    'LOCAL'
)
ON CONFLICT (user_email) DO NOTHING;

-- 12. Link Seeded Admin User to ROLE_ADMIN role
INSERT INTO user_roles (user_id, role_id)
VALUES (
    'd43ea45b-d1e9-4e78-9e6e-213c9e6eb1fa',
    'c53ea45b-d1e9-4e78-9e6e-213c9e6eb1df'
)
ON CONFLICT DO NOTHING;
