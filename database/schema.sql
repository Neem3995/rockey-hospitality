-- Run against a NEW, explicitly selected database. This script never drops a database.
-- MySQL 8; exactly four normalized tables. Hibernate validates this schema.
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(120) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    role ENUM('USER','MANAGER','ADMIN') NOT NULL DEFAULT 'USER',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    refresh_token_hash VARCHAR(64) NULL,
    refresh_token_expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_refresh_hash UNIQUE (refresh_token_hash)
);
CREATE TABLE rooms (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_number VARCHAR(10) NOT NULL,
    floor SMALLINT NOT NULL,
    status ENUM('READY','DIRTY','CLEANING','INSPECTION','OUT_OF_SERVICE') NOT NULL DEFAULT 'READY',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_rooms_number UNIQUE (room_number),
    CONSTRAINT ck_rooms_floor CHECK (floor BETWEEN 1 AND 99)
);
CREATE TABLE tasks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    description VARCHAR(1000) NULL,
    status ENUM('ASSIGNED','IN_PROGRESS','COMPLETED','CANCELLED') NOT NULL DEFAULT 'ASSIGNED',
    priority ENUM('LOW','MEDIUM','HIGH','URGENT') NOT NULL,
    assigned_user_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    due_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_tasks_user FOREIGN KEY (assigned_user_id) REFERENCES users(id),
    CONSTRAINT fk_tasks_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    CONSTRAINT ck_tasks_completed CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)),
    INDEX idx_tasks_owner_status (assigned_user_id, status),
    INDEX idx_tasks_room_status (room_id, status)
);
CREATE TABLE inspections (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    inspected_by_user_id BIGINT NOT NULL,
    result ENUM('PASS','FAIL') NOT NULL,
    notes VARCHAR(1000) NULL,
    inspected_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_inspections_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    CONSTRAINT fk_inspections_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_inspections_user FOREIGN KEY (inspected_by_user_id) REFERENCES users(id),
    INDEX idx_inspections_room (room_id, inspected_at)
);
