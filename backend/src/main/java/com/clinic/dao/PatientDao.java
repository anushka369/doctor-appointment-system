package com.clinic.dao;

import com.clinic.model.Patient;
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
public class PatientDao {

    private static final String SELECT_ALL = "SELECT id, name, email, phone FROM patients ORDER BY name";
    private static final String SELECT_BY_ID = "SELECT id, name, email, phone FROM patients WHERE id = ?";
    private static final String INSERT = "INSERT INTO patients (name, email, phone) VALUES (?, ?, ?)";
    private static final String UPDATE = "UPDATE patients SET name = ?, email = ?, phone = ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM patients WHERE id = ?";

    private final DataSource dataSource;

    public PatientDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Patient> findAll() {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {
            List<Patient> out = new ArrayList<>();
            while (rs.next()) {
                out.add(map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new DaoException("Failed to list patients", e);
        }
    }

    public Optional<Patient> findById(long id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException("Failed to load patient " + id, e);
        }
    }

    public Optional<Patient> findByEmail(String email) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id, name, email, phone FROM patients WHERE LOWER(email) = LOWER(?)")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DaoException("Failed to look up patient by email", e);
        }
    }

    public Patient insert(Patient p) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.name());
            ps.setString(2, p.email());
            ps.setString(3, p.phone());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Patient(keys.getLong(1), p.name(), p.email(), p.phone());
            }
        } catch (SQLException e) {
            throw new DaoException("Failed to insert patient", e);
        }
    }

    /** @return true if a row was updated, false if the id does not exist. */
    public boolean update(Patient p) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE)) {
            ps.setString(1, p.name());
            ps.setString(2, p.email());
            ps.setString(3, p.phone());
            ps.setLong(4, p.id());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DaoException("Failed to update patient " + p.id(), e);
        }
    }

    public boolean deleteById(long id) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(DELETE)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLIntegrityConstraintViolationException e) {
            throw ApiException.conflict("Patient has appointments and cannot be deleted");
        } catch (SQLException e) {
            throw new DaoException("Failed to delete patient " + id, e);
        }
    }

    private static Patient map(ResultSet rs) throws SQLException {
        return new Patient(rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getString("phone"));
    }
}
