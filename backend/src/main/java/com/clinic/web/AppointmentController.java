package com.clinic.web;

import com.clinic.auth.AuthInterceptor;
import com.clinic.auth.Roles;
import com.clinic.auth.Session;
import com.clinic.dao.AppointmentDao;
import com.clinic.model.Appointment;
import com.clinic.model.AppointmentUpdate;
import com.clinic.model.BookingRequest;
import com.clinic.service.AppointmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentDao appointmentDao;
    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentDao appointmentDao, AppointmentService appointmentService) {
        this.appointmentDao = appointmentDao;
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public List<Appointment> list(
            @RequestAttribute(AuthInterceptor.ATTR) Session session,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String status) {
        Long patientId = null;
        if (Roles.DOCTOR.equals(session.role())) {
            doctorId = session.subjectId();
        } else if (Roles.PATIENT.equals(session.role())) {
            patientId = session.subjectId();
        }
        return appointmentDao.findAll(date, doctorId, patientId, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Appointment book(@RequestAttribute(AuthInterceptor.ATTR) Session session,
                            @RequestBody BookingRequest body) {
        Long patientId = Roles.PATIENT.equals(session.role()) ? (Long) session.subjectId() : body.patientId();
        return appointmentService.book(new BookingRequest(
                body.doctorId(), patientId, body.date(), body.time(),
                Validation.blankToNull(body.reason())));
    }

    /** Edit an appointment of any status. Patients: own SCHEDULED ones only, and cannot change status. */
    @PutMapping("/{id}")
    public Appointment update(@RequestAttribute(AuthInterceptor.ATTR) Session session,
                              @PathVariable long id, @RequestBody AppointmentUpdate body) {
        Appointment existing = owned(id, session);
        String status = body.status();
        if (Roles.PATIENT.equals(session.role())) {
            if (!"SCHEDULED".equals(existing.status())) {
                throw ApiException.forbidden("Only scheduled appointments can be changed");
            }
            status = null;
        }
        return appointmentService.update(id, body.date(), body.time(),
                Validation.blankToNull(body.reason()), status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        if (!appointmentDao.deleteById(id)) {
            throw ApiException.notFound("Appointment " + id + " not found");
        }
    }

    /** Body: {"status": "COMPLETED"} or {"status": "CANCELLED"}. Patients may only cancel their own. */
    @PatchMapping("/{id}/status")
    public Appointment changeStatus(@RequestAttribute(AuthInterceptor.ATTR) Session session,
                                    @PathVariable long id, @RequestBody Map<String, String> body) {
        owned(id, session);
        if (Roles.PATIENT.equals(session.role()) && !"CANCELLED".equals(body.get("status"))) {
            throw ApiException.forbidden("Patients can only cancel appointments");
        }
        return appointmentService.changeStatus(id, body.get("status"));
    }

    /** Loads the appointment and checks the caller may touch it (admin: any; doctor/patient: their own). */
    private Appointment owned(long id, Session s) {
        Appointment a = appointmentDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("Appointment " + id + " not found"));
        boolean ok = Roles.ADMIN.equals(s.role())
                || (Roles.DOCTOR.equals(s.role()) && a.doctorId() == s.subjectId())
                || (Roles.PATIENT.equals(s.role()) && a.patientId() == s.subjectId());
        if (!ok) {
            throw ApiException.forbidden("That appointment does not belong to you");
        }
        return a;
    }
}
