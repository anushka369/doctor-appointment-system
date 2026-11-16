package com.clinic.web;

import com.clinic.dao.DoctorDao;
import com.clinic.model.Doctor;
import com.clinic.service.AppointmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorDao doctorDao;
    private final AppointmentService appointmentService;

    public DoctorController(DoctorDao doctorDao, AppointmentService appointmentService) {
        this.doctorDao = doctorDao;
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public List<Doctor> list() {
        return doctorDao.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Doctor create(@RequestBody Doctor body) {
        String name = Validation.required(body.name(), "name");
        String spec = Validation.required(body.specialization(), "specialization");
        return doctorDao.insert(new Doctor(null, name, spec,
                Validation.optionalEmail(body.email()), Validation.blankToNull(body.phone())));
    }

    @PutMapping("/{id}")
    public Doctor update(@PathVariable long id, @RequestBody Doctor body) {
        Doctor d = new Doctor(id,
                Validation.required(body.name(), "name"),
                Validation.required(body.specialization(), "specialization"),
                Validation.optionalEmail(body.email()),
                Validation.blankToNull(body.phone()));
        if (!doctorDao.update(d)) {
            throw ApiException.notFound("Doctor " + id + " not found");
        }
        return d;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        if (!doctorDao.deleteById(id)) {
            throw ApiException.notFound("Doctor " + id + " not found");
        }
    }

    /** Free 30 minute slots for a doctor on a given day. */
    @GetMapping("/{id}/slots")
    public List<LocalTime> slots(@PathVariable long id,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointmentService.availableSlots(id, date);
    }
}
