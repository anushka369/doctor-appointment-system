package com.clinic.dao;

import com.clinic.model.Appointment;
import com.clinic.model.BookingRequest;
import com.clinic.web.ApiException;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class AppointmentDao {

    private static final String SELECT_JOINED = """
            SELECT a.id, a.doctor_id, d.name AS doctor_name, d.specialization,
                   a.patient_id, p.name AS patient_name,
                   a.appointment_date, a.appointment_time, a.reason, a.status
            FROM appointments a
            JOIN doctors  d ON d.id = a.doctor_id
            JOIN patients p ON p.id = a.patient_id
            """;

    private static final String LOCK_DOCTOR = "SELECT id FROM doctors WHERE id = ? FOR UPDATE";
    private static final String PATIENT_EXISTS = "SELECT 1 FROM patients WHERE id = ?";
    private static final String SLOT_TAKEN = """
            SELECT COUNT(*) FROM appointments
            WHERE doctor_id = ? AND appointment_date = ? AND appointment_time = ? AND status <> 'CANCELLED'
            """;
    private static final String INSERT = """
            INSERT INTO appointments (doctor_id, patient_id, appointment_date, appointment_time, reason, status)
            VALUES (?, ?, ?, ?, ?, 'SCHEDULED')
            """;
    private static final String BOOKED_TIMES = """
            SELECT appointment_time FROM appointments
            WHERE doctor_id = ? AND appointment_date = ? AND status <> 'CANCELLED'
            """;
    private static final String LOAD_FOR_UPDATE =
            "SELECT doctor_id, status FROM appointments WHERE id = ?";
    private static final String SLOT_TAKEN_BY_OTHER = SLOT_TAKEN + " AND id <> ?";
    private static final String UPDATE_ALL =
            "UPDATE appointments SET appointment_date = ?, appointment_time = ?, reason = ?, status = ? WHERE id = ?";
    private static final String UPDATE_STATUS =
            "UPDATE appointments SET status = ? WHERE id = ? AND status = 'SCHEDULED'";

    private final DataSource dataSource;

    public AppointmentDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Any filter may be null. Filter fragments are fixed strings; values are always bound parameters. */
    public List<Appointment> findAll(LocalDate date, Long doctorId, Long patientId, String status) {
        StringBuilder sql = new StringBuilder(SELECT_JOINED).append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (date != null) {
            sql.append(" AND a.appointment_date = ?");
            params.add(Date.valueOf(date));
        }
        if (doctorId != null) {
            sql.append(" AND a.doctor_id = ?");
            params.add(doctorId);
        }
        if (patientId != null) {
            sql.append(" AND a.patient_id = ?");
            params.add(patientId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY a.appointment_date, a.appointment_time");

        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Appointment> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new DaoException("Failed to list appointments", e);
        }
    }

    public Optional<Appointment> findById(long id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_JOINED + " WHERE a.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException("Failed to load appointment " + id, e);
        }
    }

    public Set<LocalTime> findBookedTimes(long doctorId, LocalDate date) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(BOOKED_TIMES)) {
            ps.setLong(1, doctorId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                Set<LocalTime> out = new HashSet<>();
                while (rs.next()) {
                    out.add(rs.getTime(1).toLocalTime());
                }
                return out;
            }
        } catch (SQLException e) {
            throw new DaoException("Failed to load booked slots", e);
        }
    }

    /**
     * Books a slot inside one transaction.
     * Locking the doctor row serialises concurrent bookings for the same doctor, so the
     * "is the slot free?" check and the INSERT cannot interleave with another booking.
     *
     * @return the new appointment id
     */
    public long book(BookingRequest r) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(LOCK_DOCTOR)) {
                    ps.setLong(1, r.doctorId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw ApiException.notFound("Doctor " + r.doctorId() + " not found");
                        }
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(PATIENT_EXISTS)) {
                    ps.setLong(1, r.patientId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw ApiException.notFound("Patient " + r.patientId() + " not found");
                        }
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(SLOT_TAKEN)) {
                    ps.setLong(1, r.doctorId());
                    ps.setDate(2, Date.valueOf(r.date()));
                    ps.setTime(3, Time.valueOf(r.time()));
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) {
                            throw ApiException.conflict("That slot is already booked for this doctor");
                        }
                    }
                }
                long id;
                try (PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, r.doctorId());
                    ps.setLong(2, r.patientId());
                    ps.setDate(3, Date.valueOf(r.date()));
                    ps.setTime(4, Time.valueOf(r.time()));
                    ps.setString(5, r.reason());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getLong(1);
                    }
                }
                con.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DaoException("Failed to book appointment", e);
        }
    }

    /**
     * Updates date/time/reason/status of an appointment of any status, in one transaction.
     * Locks the doctor row like {@link #book}; the slot-conflict check only applies if the
     * resulting status is not CANCELLED (cancelled appointments hold no slot).
     */
    public void update(long id, LocalDate date, LocalTime time, String reason, String status) {
        try (Connection con = dataSource.getConnection()) {
            con.setAutoCommit(false);
            try {
                long doctorId;
                try (PreparedStatement ps = con.prepareStatement(LOAD_FOR_UPDATE)) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw ApiException.notFound("Appointment " + id + " not found");
                        }
                        doctorId = rs.getLong("doctor_id");
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(LOCK_DOCTOR)) {
                    ps.setLong(1, doctorId);
                    ps.executeQuery().close();
                }
                if (!"CANCELLED".equals(status)) {
                    try (PreparedStatement ps = con.prepareStatement(SLOT_TAKEN_BY_OTHER)) {
                        ps.setLong(1, doctorId);
                        ps.setDate(2, Date.valueOf(date));
                        ps.setTime(3, Time.valueOf(time));
                        ps.setLong(4, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            if (rs.getInt(1) > 0) {
                                throw ApiException.conflict("That slot is already booked for this doctor");
                            }
                        }
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(UPDATE_ALL)) {
                    ps.setDate(1, Date.valueOf(date));
                    ps.setTime(2, Time.valueOf(time));
                    ps.setString(3, reason);
                    ps.setString(4, status);
                    ps.setLong(5, id);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DaoException("Failed to update appointment " + id, e);
        }
    }

    /** @return true if a row was deleted, false if the id does not exist. */
    public boolean deleteById(long id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM appointments WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DaoException("Failed to delete appointment " + id, e);
        }
    }

    /** Only SCHEDULED appointments can change status. @return true if a row was updated. */
    public boolean updateStatus(long id, String newStatus) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, newStatus);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DaoException("Failed to update appointment " + id, e);
        }
    }

    private static Appointment map(ResultSet rs) throws SQLException {
        return new Appointment(
                rs.getLong("id"),
                rs.getLong("doctor_id"),
                rs.getString("doctor_name"),
                rs.getString("specialization"),
                rs.getLong("patient_id"),
                rs.getString("patient_name"),
                rs.getDate("appointment_date").toLocalDate(),
                rs.getTime("appointment_time").toLocalTime(),
                rs.getString("reason"),
                rs.getString("status"));
    }
}
