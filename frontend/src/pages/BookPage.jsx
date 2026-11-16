import { useEffect, useState } from 'react'
import { api, todayStr } from '../api.js'
import SlotPicker from '../components/SlotPicker.jsx'

export default function BookPage({ doctors, patients, fixedPatient, onBooked }) {
  const [doctorId, setDoctorId] = useState('')
  const [patientId, setPatientId] = useState(fixedPatient ? String(fixedPatient.id) : '')
  const [date, setDate] = useState(todayStr())
  const [time, setTime] = useState('')
  const [reason, setReason] = useState('')
  const [slots, setSlots] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const loadSlots = () => {
    if (!doctorId || !date) {
      setSlots([])
      return Promise.resolve()
    }
    return api
      .slots(doctorId, date)
      .then(setSlots)
      .catch((e) => setError(e.message))
  }

  // Reload free slots whenever the doctor or date changes; drop a stale selection.
  useEffect(() => {
    setTime('')
    setError('')
    loadSlots()
  }, [doctorId, date])

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await api.book({
        doctorId: Number(doctorId),
        patientId: Number(patientId),
        date,
        time,
        reason,
      })
      onBooked()
    } catch (err) {
      setError(err.message)
      setTime('')
      loadSlots() // someone may have taken the slot; refresh
    } finally {
      setBusy(false)
    }
  }

  const ready = doctorId && patientId && date && time

  return (
    <section>
      <form className="card" onSubmit={submit}>
        <h2>Book an appointment</h2>

        <label>
          Doctor
          <select value={doctorId} onChange={(e) => setDoctorId(e.target.value)} required>
            <option value="">Select a doctor</option>
            {doctors.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name} ({d.specialization})
              </option>
            ))}
          </select>
        </label>

        {fixedPatient ? (
          <p className="hint spaced">
            Booking as <strong>{fixedPatient.name}</strong>
          </p>
        ) : (
          <label>
            Patient
            <select value={patientId} onChange={(e) => setPatientId(e.target.value)} required>
              <option value="">Select a patient</option>
              {patients.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
        )}

        <label>
          Date
          <input type="date" value={date} min={todayStr()} onChange={(e) => setDate(e.target.value)} required />
        </label>

        <div className="field">
          <span>Time slot</span>
          <SlotPicker
            slots={slots}
            value={time}
            onChange={setTime}
            emptyHint={doctorId ? 'No free slots on this day.' : 'Pick a doctor to see free slots.'}
          />
        </div>

        <label>
          Reason (optional)
          <input
            type="text"
            value={reason}
            maxLength={255}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Checkup, follow-up, ..."
          />
        </label>

        {error && <p className="msg error">{error}</p>}

        <button type="submit" className="primary" disabled={!ready || busy}>
          {busy ? 'Booking...' : 'Book appointment'}
        </button>
      </form>
    </section>
  )
}
