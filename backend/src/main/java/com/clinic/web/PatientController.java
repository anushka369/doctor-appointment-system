package com.clinic.web;

import com.clinic.dao.PatientDao;
import com.clinic.model.Patient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientDao patientDao;

    public PatientController(PatientDao patientDao) {
        this.patientDao = patientDao;
    }

    @GetMapping
    public List<Patient> list() {
        return patientDao.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Patient create(@RequestBody Patient body) {
        String name = Validation.required(body.name(), "name");
        return patientDao.insert(new Patient(null, name,
                Validation.optionalEmail(body.email()), Validation.blankToNull(body.phone())));
    }

    @PutMapping("/{id}")
    public Patient update(@PathVariable long id, @RequestBody Patient body) {
        Patient p = new Patient(id,
                Validation.required(body.name(), "name"),
                Validation.optionalEmail(body.email()),
                Validation.blankToNull(body.phone()));
        if (!patientDao.update(p)) {
            throw ApiException.notFound("Patient " + id + " not found");
        }
        return p;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        if (!patientDao.deleteById(id)) {
            throw ApiException.notFound("Patient " + id + " not found");
        }
    }
}
