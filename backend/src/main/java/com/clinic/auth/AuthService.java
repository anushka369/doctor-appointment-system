package com.clinic.auth;

import com.clinic.dao.DoctorDao;
import com.clinic.dao.PatientDao;
import com.clinic.model.Doctor;
import com.clinic.model.Patient;
import com.clinic.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock OTP login. No email/SMS is sent: the code is written to the server log and, while
 * clinic.auth.demo-otp=true, also returned in the API response so the UI can display it.
 * OTP challenges and sessions live in memory, so a backend restart signs everyone out.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration SESSION_TTL = Duration.ofHours(8);
    private static final int MAX_ATTEMPTS = 5;
    /** Patients offered as one-click logins on the demo screen, in this order. */
    private static final List<String> DEMO_PATIENTS = List.of("Anushka Banerjee", "Shashank Singh", "Swarnava Paul");

    public record OtpChallenge(String challengeId, long expiresInSeconds, String demoOtp) {
    }

    private record Account(String role, long id, String name, String email) {
    }

    private static final class Challenge {
        final Account account;
        final String otp;
        final Instant expires;
        int attempts;

        Challenge(Account account, String otp, Instant expires) {
            this.account = account;
            this.otp = otp;
            this.expires = expires;
        }
    }

    private final DoctorDao doctorDao;
    private final PatientDao patientDao;
    private final String adminEmail;
    private final boolean demoOtp;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AuthService(DoctorDao doctorDao, PatientDao patientDao,
                       @Value("${clinic.auth.admin-email}") String adminEmail,
                       @Value("${clinic.auth.demo-otp:true}") boolean demoOtp) {
        this.doctorDao = doctorDao;
        this.patientDao = patientDao;
        this.adminEmail = adminEmail;
        this.demoOtp = demoOtp;
    }

    public OtpChallenge requestOtp(String role, String email) {
        purge();
        if (role == null || email == null || email.isBlank()) {
            throw ApiException.badRequest("role and email are required");
        }
        Account account = lookup(role, email.trim());
        String otp = String.format("%06d", random.nextInt(1_000_000));
        String id = UUID.randomUUID().toString();
        challenges.put(id, new Challenge(account, otp, Instant.now().plus(OTP_TTL)));
        log.info("[MOCK OTP] {} {} -> {}", role, account.email(), otp);
        return new OtpChallenge(id, OTP_TTL.toSeconds(), demoOtp ? otp : null);
    }

    public Session verify(String challengeId, String otp) {
        purge();
        Challenge c = challengeId == null ? null : challenges.get(challengeId);
        if (c == null) {
            throw ApiException.badRequest("This code has expired. Request a new one.");
        }
        synchronized (c) {
            c.attempts++;
            boolean ok = otp != null && MessageDigest.isEqual(
                    otp.trim().getBytes(StandardCharsets.UTF_8), c.otp.getBytes(StandardCharsets.UTF_8));
            if (!ok) {
                if (c.attempts >= MAX_ATTEMPTS) {
                    challenges.remove(challengeId);
                    throw ApiException.badRequest("Too many wrong attempts. Request a new code.");
                }
                throw ApiException.badRequest(
                        "Incorrect code (" + (MAX_ATTEMPTS - c.attempts) + " attempts left)");
            }
            if (challenges.remove(challengeId) == null) { // already consumed by a concurrent request
                throw ApiException.badRequest("This code has expired. Request a new one.");
            }
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Account a = c.account;
        Session s = new Session(token, a.role(), a.id(), a.name(), a.email(), Instant.now().plus(SESSION_TTL));
        sessions.put(token, s);
        return s;
    }

    public Optional<Session> session(String token) {
        Session s = sessions.get(token);
        if (s == null) {
            return Optional.empty();
        }
        if (s.expires().isBefore(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(s);
    }

    /** Keeps the live session in step with a profile edit (name/email shown in the header). */
    public Session refresh(String token, String name, String email) {
        return sessions.computeIfPresent(token, (t, s) ->
                new Session(s.token(), s.role(), s.subjectId(), name, email, s.expires()));
    }

    public void logout(String token) {
        sessions.remove(token);
    }

    /** A few real emails per role so the demo login screen can offer one-click fill. Empty when demo is off. */
    public Map<String, List<Map<String, String>>> demoAccounts() {
        Map<String, List<Map<String, String>>> out = new LinkedHashMap<>();
        if (!demoOtp) {
            return out;
        }
        out.put(Roles.ADMIN, List.of(Map.of("name", "Administrator", "email", adminEmail)));
        List<Map<String, String>> docs = new ArrayList<>();
        for (Doctor d : doctorDao.findAll()) {
            if (d.email() != null && docs.size() < 4) {
                docs.add(Map.of("name", d.name(), "email", d.email()));
            }
        }
        out.put(Roles.DOCTOR, docs);
        List<Map<String, String>> pats = new ArrayList<>();
        List<Patient> all = patientDao.findAll();
        for (String name : DEMO_PATIENTS) {
            all.stream().filter(p -> p.name().equals(name) && p.email() != null).findFirst()
                    .ifPresent(p -> pats.add(Map.of("name", p.name(), "email", p.email())));
        }
        out.put(Roles.PATIENT, pats);
        return out;
    }

    private Account lookup(String role, String email) {
        switch (role) {
            case Roles.ADMIN -> {
                if (adminEmail.equalsIgnoreCase(email)) {
                    return new Account(Roles.ADMIN, 0, "Administrator", adminEmail);
                }
            }
            case Roles.DOCTOR -> {
                Optional<Doctor> d = doctorDao.findByEmail(email);
                if (d.isPresent()) {
                    return new Account(Roles.DOCTOR, d.get().id(), d.get().name(), d.get().email());
                }
            }
            case Roles.PATIENT -> {
                Optional<Patient> p = patientDao.findByEmail(email);
                if (p.isPresent()) {
                    return new Account(Roles.PATIENT, p.get().id(), p.get().name(), p.get().email());
                }
            }
            default -> throw ApiException.badRequest("Unknown role");
        }
        throw ApiException.notFound("No " + role.toLowerCase() + " account found for that email");
    }

    private void purge() {
        Instant now = Instant.now();
        challenges.values().removeIf(c -> c.expires.isBefore(now));
        sessions.values().removeIf(s -> s.expires().isBefore(now));
    }
}
