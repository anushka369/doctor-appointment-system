package com.clinic.model;

import java.time.LocalDate;
import java.time.LocalTime;

/** Read model: appointment joined with doctor and patient names. */
public record Appointment(
        Long id,
        Long doctorId,
        String doctorName,
        String specialization,
        Long patientId,
        String patientName,
        LocalDate date,
        LocalTime time,
        String reason,
        String status) {
}
