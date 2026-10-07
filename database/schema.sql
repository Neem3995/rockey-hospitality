-- Rockey Hospitality table definitions and constraints.
-- Run this script against an existing MySQL database selected for Rockey.

CREATE TABLE IF NOT EXISTS departments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_departments_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS inventory_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    sku VARCHAR(40) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    reorder_threshold INT NOT NULL DEFAULT 0,
    department_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_items_sku UNIQUE (sku),
    CONSTRAINT fk_inventory_items_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT chk_inventory_quantity CHECK (quantity >= 0),
    CONSTRAINT chk_inventory_threshold CHECK (reorder_threshold >= 0),
    INDEX idx_inventory_department_active (department_id, active),
    INDEX idx_inventory_quantity_threshold (quantity, reorder_threshold)
);

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    department_id BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    token_version INT NOT NULL DEFAULT 0,
    refresh_token_hash VARCHAR(64) NULL,
    refresh_token_expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_users_department
        FOREIGN KEY (department_id) REFERENCES departments (id)
);

CREATE TABLE IF NOT EXISTS employees (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(120) NOT NULL,
    department_id BIGINT NOT NULL,
    job_role VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_employees_user UNIQUE (user_id),
    CONSTRAINT uk_employees_email UNIQUE (email),
    CONSTRAINT fk_employees_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_employees_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    INDEX idx_employees_department_status (department_id, status)
);

CREATE TABLE IF NOT EXISTS rooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_number VARCHAR(10) NOT NULL,
    room_type VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    status VARCHAR(30) NOT NULL DEFAULT 'READY',
    floor SMALLINT NOT NULL,
    next_arrival_at DATETIME NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_rooms_room_number UNIQUE (room_number),
    INDEX idx_rooms_status_next_arrival (status, next_arrival_at)
);

CREATE TABLE IF NOT EXISTS events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    event_date_time DATETIME NOT NULL,
    location VARCHAR(120) NOT NULL,
    capacity INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_events_status_event_date_time (status, event_date_time),
    CONSTRAINT chk_events_capacity CHECK (capacity BETWEEN 1 AND 10000)
);

CREATE TABLE IF NOT EXISTS tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    department_id BIGINT NOT NULL,
    assigned_employee_id BIGINT NULL,
    room_id BIGINT NULL,
    event_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_at DATETIME NULL,
    completed_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_tasks_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_tasks_assigned_employee
        FOREIGN KEY (assigned_employee_id) REFERENCES employees (id),
    CONSTRAINT fk_tasks_room
        FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_tasks_event
        FOREIGN KEY (event_id) REFERENCES events (id),
    INDEX idx_tasks_department_status (department_id, status),
    INDEX idx_tasks_assigned_employee (assigned_employee_id),
    INDEX idx_tasks_room (room_id),
    INDEX idx_tasks_event (event_id),
    INDEX idx_tasks_due_at (due_at)
);

CREATE TABLE IF NOT EXISTS event_registrations (
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, event_id),
    CONSTRAINT uk_event_registrations_user_event UNIQUE (user_id, event_id),
    CONSTRAINT fk_event_registrations_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_event_registrations_event
        FOREIGN KEY (event_id) REFERENCES events (id),
    INDEX idx_event_registrations_event (event_id)
);

CREATE TABLE IF NOT EXISTS alerts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    message VARCHAR(500) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    employee_id BIGINT NOT NULL,
    task_id BIGINT NULL,
    source_key VARCHAR(120) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    resolved_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_alerts_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_alerts_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    INDEX idx_alerts_employee_status_created (employee_id, status, created_at),
    INDEX idx_alerts_employee_type_status_source (employee_id, type, status, source_key),
    INDEX idx_alerts_task (task_id)
);
