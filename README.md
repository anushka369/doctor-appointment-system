# Doctor Appointment Management System

- **Backend:** Spring Boot 3 (Java 17+), plain JDBC (`DataSource`, `PreparedStatement`, manual transactions). No ORM, no JdbcTemplate.
- **Frontend:** React 18 + Vite.
- **Database:** H2 file DB by default (zero setup), MySQL via a profile.

## Run it

Backend (port 8080):

```bash
cd backend
mvn spring-boot:run
```

Frontend (port 5173, proxies `/api` to the backend):

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Sample doctors and patients are seeded on first run.

### Use MySQL instead of H2

```sql
CREATE DATABASE clinic;
```

Edit `backend/src/main/resources/application-mysql.properties` (user, password), then:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Tables are created automatically from `schema.sql`.

## Login (mock OTP)

Everything under `/api` needs a Bearer token. Sign in at http://localhost:5173 by choosing a role, entering that account's email, and entering the 6-digit code.

| Role | Email | Can do |
|---|---|---|
| Admin | `admin@clinic.test` (`clinic.auth.admin-email`) | everything |
| Doctor | any doctor's email | see their own appointments, complete / cancel / edit them |
| Patient | any patient's email | see their own appointments, book for themselves, edit / cancel their own scheduled ones |

No email or SMS is sent. The code is printed in the backend log, and while `clinic.auth.demo-otp=true` (the default) it is also shown on the login screen. Set it to `false` for log-only. Codes expire after 5 minutes, allow 5 attempts and are single use. Sessions last 8 hours and are held in memory, so restarting the backend signs everyone out.

Endpoints: `POST /api/auth/request-otp {role, email}`, `POST /api/auth/verify {challengeId, otp}` (returns `{token, user}`), `GET /api/auth/me`, `POST /api/auth/logout`.

## Project layout

```
backend/src/main/java/com/clinic
  dao/        DoctorDao, PatientDao, AppointmentDao   <- all JDBC lives here
  service/    AppointmentService                      <- validation, slot generation
  web/        Controllers, ApiException, error handler
  model/      Doctor, Patient, Appointment, BookingRequest (records)
  config/     CORS, DataSeeder
backend/src/main/resources/schema.sql                 <- table definitions
frontend/src/pages  AppointmentsPage, BookPage, DirectoryPage
```

## API

| Method | Path | Notes |
|---|---|---|
| GET | `/api/doctors` | |
| POST | `/api/doctors` | `{name, specialization, email?, phone?}` |
| PUT | `/api/doctors/{id}` | same body as POST |
| DELETE | `/api/doctors/{id}` | 409 if the doctor has appointments |
| GET | `/api/doctors/{id}/slots?date=YYYY-MM-DD` | free 30 min slots, 09:00 to 16:30 |
| GET | `/api/patients` | |
| POST | `/api/patients` | `{name, email?, phone?}` |
| PUT | `/api/patients/{id}` | same body as POST |
| DELETE | `/api/patients/{id}` | 409 if the patient has appointments |
| GET | `/api/appointments` | optional `date`, `doctorId`, `status` filters |
| POST | `/api/appointments` | `{doctorId, patientId, date, time, reason?}` |
| PUT | `/api/appointments/{id}` | `{date, time, reason?, status?}` edit an appointment of any status |
| DELETE | `/api/appointments/{id}` | |
| PATCH | `/api/appointments/{id}/status` | `{status: "COMPLETED" \| "CANCELLED"}` |

Quick test:

```bash
curl localhost:8080/api/doctors
curl "localhost:8080/api/doctors/1/slots?date=2026-12-01"
curl -X POST localhost:8080/api/appointments -H 'Content-Type: application/json' \
  -d '{"doctorId":1,"patientId":1,"date":"2026-12-01","time":"10:00","reason":"Checkup"}'
```

## Booking rules

- Slots are 30 minutes, 09:00 to 16:30. Past slots are rejected.
- A doctor cannot be double booked. `AppointmentDao.book()` runs in one transaction and locks the doctor row (`SELECT ... FOR UPDATE`), so two concurrent bookings for the same slot cannot both pass the check.
- Cancelled appointments free their slot.
- Only `SCHEDULED` appointments can be completed or cancelled.
