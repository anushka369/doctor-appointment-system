package com.clinic.service;

import com.clinic.dao.AppointmentDao;
import com.clinic.dao.DoctorDao;
import com.clinic.model.Appointment;
import com.clinic.model.BookingRequest;
import com.clinic.web.ApiException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class AppointmentService {

    private static final LocalTime FIRST_SLOT = LocalTime.of(9, 0);
    private static final LocalTime LAST_SLOT = LocalTime.of(16, 30);
    private static final int SLOT_MINUTES = 30;

    private final AppointmentDao appointmentDao;
    private final DoctorDao doctorDao;

    public AppointmentService(AppointmentDao appointmentDao, DoctorDao doctorDao) {
        this.appointmentDao = appointmentDao;
        this.doctorDao = doctorDao;
    }

    public List<LocalTime> availableSlots(long doctorId, LocalDate date) {
        if (doctorDao.findById(doctorId).isEmpty()) {
            throw ApiException.notFound("Doctor " + doctorId + " not found");
        }
        Set<LocalTime> booked = appointmentDao.findBookedTimes(doctorId, date);
        LocalDateTime now = LocalDateTime.now();
        List<LocalTime> out = new ArrayList<>();
        for (LocalTime t = FIRST_SLOT; !t.isAfter(LAST_SLOT); t = t.plusMinutes(SLOT_MINUTES)) {
            boolean inPast = LocalDateTime.of(date, t).isBefore(now);
            if (!booked.contains(t) && !inPast) {
                out.add(t);
            }
        }
        return out;
    }

    public Appointment book(BookingRequest r) {
        if (r.doctorId() == null || r.patientId() == null || r.date() == null || r.time() == null) {
            throw ApiException.badRequest("doctorId, patientId, date and time are required");
        }
        validateSlot(r.date(), r.time());
        long id = appointmentDao.book(r);
        return appointmentDao.findById(id).orElseThrow();
    }

    public Appointment update(long id, LocalDate date, LocalTime time, String reason, String status) {
        if (date == null || time == null) {
            throw ApiException.badRequest("date and time are required");
        }
        if (status != null && !List.of("SCHEDULED", "COMPLETED", "CANCELLED").contains(status)) {
            throw ApiException.badRequest("status must be SCHEDULED, COMPLETED or CANCELLED");
        }
        Appointment existing = appointmentDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Appointment " + id + " not found"));
        boolean moved = !existing.date().equals(date) || !existing.time().equals(time);
        if (moved) {
            validateSlot(date, time);
        }
        appointmentDao.update(id, date, time, reason, status == null ? existing.status() : status);
        return appointmentDao.findById(id).orElseThrow();
    }

    private static void validateSlot(LocalDate date, LocalTime t) {
        boolean onGrid = t.getSecond() == 0 && t.getNano() == 0
                && t.getMinute() % SLOT_MINUTES == 0
                && !t.isBefore(FIRST_SLOT) && !t.isAfter(LAST_SLOT);
        if (!onGrid) {
            throw ApiException.badRequest("Time must be a 30 minute slot between 09:00 and 16:30");
        }
        if (LocalDateTime.of(date, t).isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("Cannot book a slot in the past");
        }
    }

    public Appointment changeStatus(long id, String status) {
        if (status == null || !(status.equals("COMPLETED") || status.equals("CANCELLED"))) {
            throw ApiException.badRequest("status must be COMPLETED or CANCELLED");
        }
        if (!appointmentDao.updateStatus(id, status)) {
            Appointment existing = appointmentDao.findById(id)
                    .orElseThrow(() -> ApiException.notFound("Appointment " + id + " not found"));
            throw ApiException.conflict("Appointment is already " + existing.status());
        }
        return appointmentDao.findById(id).orElseThrow();
    }
}
