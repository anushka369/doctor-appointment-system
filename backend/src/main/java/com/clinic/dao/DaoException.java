package com.clinic.dao;

/** Unchecked wrapper so callers are not forced to handle SQLException. */
public class DaoException extends RuntimeException {
    public DaoException(String message, Throwable cause) {
        super(message, cause);
    }
}
