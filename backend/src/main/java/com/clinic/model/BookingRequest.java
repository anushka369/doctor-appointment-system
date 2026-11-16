package com.clinic.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record BookingRequest(Long doctorId, Long patientId, LocalDate date, LocalTime time, String reason) {
}
