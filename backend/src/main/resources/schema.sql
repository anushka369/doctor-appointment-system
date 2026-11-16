CREATE TABLE IF NOT EXISTS doctors (
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    specialization VARCHAR(100) NOT NULL,
    email          VARCHAR(120),
    phone          VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS patients (
    id    BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    email VARCHAR(120),
    phone VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS appointments (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    doctor_id        BIGINT       NOT NULL,
    patient_id       BIGINT       NOT NULL,
    appointment_date DATE         NOT NULL,
    appointment_time TIME         NOT NULL,
    reason           VARCHAR(255),
    status           VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appt_doctor  FOREIGN KEY (doctor_id)  REFERENCES doctors(id),
    CONSTRAINT fk_appt_patient FOREIGN KEY (patient_id) REFERENCES patients(id)
);
