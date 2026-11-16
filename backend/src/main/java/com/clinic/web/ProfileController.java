package com.clinic.web;

import com.clinic.auth.AuthInterceptor;
import com.clinic.auth.AuthService;
import com.clinic.auth.Roles;
import com.clinic.auth.Session;
import com.clinic.dao.DoctorDao;
import com.clinic.dao.PatientDao;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** The signed-in user's own profile. Doctors and patients can edit theirs; the admin account is read-only. */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    public record Profile(String role, long id, String name, String email, String phone,
                          String specialization, boolean editable, Instant sessionExpires) {
    }

    public record ProfileUpdate(String name, String specialization, String email, String phone) {
    }

    private final DoctorDao doctorDao;
    private final PatientDao patientDao;
    private final AuthService auth;

    public ProfileController(DoctorDao doctorDao, PatientDao patientDao, AuthService auth) {
        this.doctorDao = doctorDao;
        this.patientDao = patientDao;
        this.auth = auth;
    }

    @GetMapping
    public Profile get(@RequestAttribute(AuthInterceptor.ATTR) Session s) {
        return switch (s.role()) {
            case Roles.DOCTOR -> {
                Doctor d = doctorDao.findById(s.subjectId())
                        .orElseThrow(() -> ApiException.notFound("Your doctor record no longer exists"));
                yield new Profile(s.role(), d.id(), d.name(), d.email(), d.phone(), d.specialization(), true, s.expires());
            }
            case Roles.PATIENT -> {
                Patient p = patientDao.findById(s.subjectId())
                        .orElseThrow(() -> ApiException.notFound("Your patient record no longer exists"));
                yield new Profile(s.role(), p.id(), p.name(), p.email(), p.phone(), null, true, s.expires());
            }
            default -> new Profile(s.role(), 0, s.name(), s.email(), null, null, false, s.expires());
        };
    }

    @PutMapping
    public Profile update(@RequestAttribute(AuthInterceptor.ATTR) Session s, @RequestBody ProfileUpdate body) {
        String name = Validation.required(body.name(), "name");
        // The email is the login identity, so it cannot be blank.
        String email = Validation.optionalEmail(body.email());
        if (email == null) {
            throw ApiException.badRequest("email is required");
        }
        String phone = Validation.blankToNull(body.phone());

        switch (s.role()) {
            case Roles.DOCTOR -> {
                String spec = Validation.required(body.specialization(), "specialization");
                if (doctorDao.findByEmail(email).filter(o -> o.id() != s.subjectId()).isPresent()) {
                    throw ApiException.conflict("That email is already used by another doctor");
                }
                if (!doctorDao.update(new Doctor(s.subjectId(), name, spec, email, phone))) {
                    throw ApiException.notFound("Your doctor record no longer exists");
                }
            }
            case Roles.PATIENT -> {
                if (patientDao.findByEmail(email).filter(o -> o.id() != s.subjectId()).isPresent()) {
                    throw ApiException.conflict("That email is already used by another patient");
                }
                if (!patientDao.update(new Patient(s.subjectId(), name, email, phone))) {
                    throw ApiException.notFound("Your patient record no longer exists");
                }
            }
            default -> throw ApiException.forbidden("The admin account is configured on the server and can't be edited here");
        }
        Session refreshed = auth.refresh(s.token(), name, email);
        return get(refreshed != null ? refreshed : s);
    }
}
