package com.clinic.auth;

import java.time.Instant;

/** An authenticated login. subjectId is the doctor/patient row id (0 for the admin). */
public record Session(String token, String role, long subjectId, String name, String email, Instant expires) {

    public UserView view() {
        return new UserView(role, subjectId, name, email);
    }

    public record UserView(String role, long id, String name, String email) {
    }
}
