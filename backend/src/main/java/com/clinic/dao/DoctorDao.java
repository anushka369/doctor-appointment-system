package com.clinic.dao;

import com.clinic.model.Doctor;
import com.clinic.web.ApiException;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class DoctorDao {

    private static final String SELECT_ALL =
            "SELECT id, name, specialization, email, phone FROM doctors ORDER BY name";
    private static final String SELECT_BY_ID =
            "SELECT id, name, specialization, email, phone FROM doctors WHERE id = ?";
    private static final String INSERT =
            "INSERT INTO doctors (name, specialization, email, phone) VALUES (?, ?, ?, ?)";
    private static final String UPDATE =
            "UPDATE doctors SET name = ?, specialization = ?, email = ?, phone = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM doctors WHERE id = ?";
    private static final String COUNT = "SELECT COUNT(*) FROM doctors";

    private final DataSource dataSource;

    public DoctorDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Doctor> findAll() {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            List<Doctor> out = new ArrayList<>();
            while (rs.next()) {
                out.add(map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new DaoException("Failed to list doctors", e);
        }
    }

    public Optional<Doctor> findById(long id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException("Failed to load doctor " + id, e);
        }
    }

    public Optional<Doctor> findByEmail(String email) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id, name, specialization, email, phone FROM doctors WHERE LOWER(email) = LOWER(?)")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException("Failed to look up doctor by email", e);
        }
    }

    public Doctor insert(Doctor d) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, d.name());
            ps.setString(2, d.specialization());
            ps.setString(3, d.email());
            ps.setString(4, d.phone());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Doctor(keys.getLong(1), d.name(), d.specialization(), d.email(), d.phone());
            }
        } catch (SQLException e) {
            throw new DaoException("Failed to insert doctor", e);
        }
    }

    /** @return true if a row was updated, false if the id does not exist. */
    public boolean update(Doctor d) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE)) {
            ps.setString(1, d.name());
            ps.setString(2, d.specialization());
            ps.setString(3, d.email());
            ps.setString(4, d.phone());
            ps.setLong(5, d.id());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DaoException("Failed to update doctor " + d.id(), e);
        }
    }

    /** @return true if a row was deleted, false if the id does not exist. */
    public boolean deleteById(long id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(DELETE)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLIntegrityConstraintViolationException e) {
            throw ApiException.conflict("Doctor has appointments and cannot be deleted");
        } catch (SQLException e) {
            throw new DaoException("Failed to delete doctor " + id, e);
        }
    }

    public long count() {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(COUNT);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new DaoException("Failed to count doctors", e);
        }
    }

    private static Doctor map(ResultSet rs) throws SQLException {
        return new Doctor(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("specialization"),
                rs.getString("email"),
                rs.getString("phone"));
    }
}
