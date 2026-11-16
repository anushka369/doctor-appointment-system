package com.clinic.model;

import java.time.LocalDate;
import java.time.LocalTime;

/** Editable fields of an existing appointment. Doctor and patient are fixed. status may be null (keep). */
public record AppointmentUpdate(LocalDate date, LocalTime time, String reason, String status) {
}
