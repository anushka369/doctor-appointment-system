package com.clinic.web;

final class Validation {

    private Validation() {
    }

    static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(field + " is required");
        }
        return value.trim();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String optionalEmail(String value) {
        String v = blankToNull(value);
        if (v != null && !v.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ApiException.badRequest("email is not valid");
        }
        return v;
    }
}
