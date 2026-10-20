-- SlotMate 스키마 — docs/04_DB_DESIGN_v0.2.md 5절 DDL 그대로
-- compose.yaml 의 MariaDB 최초 기동 때 실행됨

CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE programs (
    program_id BIGINT NOT NULL AUTO_INCREMENT,
    program_type VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    default_duration_minutes SMALLINT NOT NULL,
    default_capacity INT NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (program_id),
    KEY ix_programs_active_type (is_active, program_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE instructors (
    instructor_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    specialty VARCHAR(100) NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (instructor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE class_sessions (
    session_id BIGINT NOT NULL AUTO_INCREMENT,
    program_id BIGINT NOT NULL,
    instructor_id BIGINT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    capacity INT NOT NULL,
    location VARCHAR(150) NULL,
    description TEXT NULL,
    session_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (session_id),
    KEY ix_sessions_program_start (program_id, start_at),
    KEY ix_sessions_status_start (session_status, start_at),
    KEY ix_sessions_instructor_start (instructor_id, start_at),
    KEY ix_sessions_start (start_at),
    CONSTRAINT fk_sessions_program FOREIGN KEY (program_id)
        REFERENCES programs (program_id),
    CONSTRAINT fk_sessions_instructor FOREIGN KEY (instructor_id)
        REFERENCES instructors (instructor_id),
    CONSTRAINT fk_sessions_creator FOREIGN KEY (created_by_user_id)
        REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reservations (
    reservation_id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reservation_status VARCHAR(20) NOT NULL,
    attempt_no INT NOT NULL DEFAULT 1,
    requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    confirmed_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (reservation_id),
    UNIQUE KEY uq_reservations_session_user (session_id, user_id),
    KEY ix_reservations_session_queue
        (session_id, reservation_status, requested_at, reservation_id),
    KEY ix_reservations_user_status
        (user_id, reservation_status, requested_at),
    CONSTRAINT fk_reservations_session FOREIGN KEY (session_id)
        REFERENCES class_sessions (session_id),
    CONSTRAINT fk_reservations_user FOREIGN KEY (user_id)
        REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reservation_histories (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    actor_user_id BIGINT NULL,
    memo VARCHAR(500) NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (history_id),
    KEY ix_history_reservation_changed (reservation_id, changed_at),
    KEY ix_history_event_changed (event_type, changed_at),
    CONSTRAINT fk_history_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservations (reservation_id),
    CONSTRAINT fk_history_actor FOREIGN KEY (actor_user_id)
        REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notifications (
    notification_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    reservation_id BIGINT NULL,
    notification_type VARCHAR(30) NOT NULL,
    title VARCHAR(150) NOT NULL,
    message VARCHAR(500) NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at DATETIME(6) NULL,
    PRIMARY KEY (notification_id),
    KEY ix_notifications_user_read_created (user_id, is_read, created_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id)
        REFERENCES users (user_id),
    CONSTRAINT fk_notifications_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservations (reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
