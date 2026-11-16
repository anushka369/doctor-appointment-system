import { useCallback, useEffect, useState } from 'react'
import { api, hhmm, initials, todayStr } from '../api.js'
import Modal, { Icon } from '../components/Modal.jsx'
import SlotPicker from '../components/SlotPicker.jsx'

const NO_FILTERS = { date: '', doctorId: '', status: '' }

function Stat({ icon, label, value, tone }) {
  return (
    <div className={`stat ${tone}`}>
      <span className="stat-icon">
        <Icon name={icon} size={20} />
      </span>
      <div>
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  )
}

export default function AppointmentsPage({ doctors, role }) {
  const [all, setAll] = useState([])
  const [filters, setFilters] = useState(NO_FILTERS)
  const [rows, setRows] = useState([])
  const [error, setError] = useState('')
  const [edit, setEdit] = useState(null) // { id, doctorId, doctorName, date, time, reason, original }
  const [slots, setSlots] = useState([])
  const [editError, setEditError] = useState('')
  const [saving, setSaving] = useState(false)

  const load = useCallback(() => {
    setError('')
    api.listAppointments(filters).then(setRows).catch((e) => setError(e.message))
    api.listAppointments().then(setAll).catch(() => {})
  }, [filters])

  useEffect(load, [load])

  // Free slots for the appointment being edited. Its own current slot is still free to keep.
  useEffect(() => {
    if (!edit) return
    let stale = false
    api
      .slots(edit.doctorId, edit.date)
      .then((s) => {
        if (stale) return
        const keep = edit.date === edit.original.date ? edit.original.time : null
        const all = keep && !s.some((x) => hhmm(x) === keep) ? [...s, keep] : s
        setSlots([...all].sort())
      })
      .catch((e) => !stale && setEditError(e.message))
    return () => {
      stale = true
    }
  }, [edit?.doctorId, edit?.date])

  const setField = (k) => (e) => setFilters({ ...filters, [k]: e.target.value })
  const filtered = Object.values(filters).some(Boolean)

  const changeStatus = async (id, status) => {
    try {
      await api.setStatus(id, status)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  const remove = async (a) => {
    if (!window.confirm(`Delete ${a.patientName}'s appointment on ${a.date} at ${hhmm(a.time)}?`)) return
    try {
      await api.deleteAppointment(a.id)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  const startEdit = (a) => {
    setEditError('')
    setSlots([])
    const time = hhmm(a.time)
    setEdit({
      id: a.id,
      doctorId: a.doctorId,
      doctorName: a.doctorName,
      patientName: a.patientName,
      date: a.date,
      time,
      reason: a.reason || '',
      status: a.status,
      original: { date: a.date, time },
    })
  }

  const saveEdit = async (e) => {
    e.preventDefault()
    setEditError('')
    setSaving(true)
    try {
      await api.updateAppointment(edit.id, { date: edit.date, time: edit.time, reason: edit.reason, status: edit.status })
      setEdit(null)
      load()
    } catch (err) {
      setEditError(err.message)
    } finally {
      setSaving(false)
    }
  }

  // Mirrors the backend rules: patients may edit/cancel their own scheduled visits; doctors can also
  // complete and edit any status; only admins delete.
  const actionsFor = (a) => {
    const scheduled = a.status === 'SCHEDULED'
    const canComplete = scheduled && role !== 'PATIENT'
    const canCancel = scheduled
    const canEdit = role === 'PATIENT' ? scheduled : true
    const canDelete = role === 'ADMIN'
    if (!canComplete && !canCancel && !canEdit && !canDelete) {
      return <span className="no-actions">No actions available</span>
    }
    return (
      <>
        {canComplete && (
          <button className="ghost success" onClick={() => changeStatus(a.id, 'COMPLETED')}>
            <Icon name="check" /> Complete
          </button>
        )}
        {canCancel && (
          <button className="ghost" onClick={() => changeStatus(a.id, 'CANCELLED')}>
            <Icon name="x" /> Cancel
          </button>
        )}
        {canEdit && (
          <button className="ghost" onClick={() => startEdit(a)}>
            <Icon name="edit" /> Edit
          </button>
        )}
        {canDelete && (
          <button className="ghost danger" onClick={() => remove(a)}>
            <Icon name="trash" /> Delete
          </button>
        )}
      </>
    )
  }

  const count = (s) => all.filter((a) => a.status === s).length
  const todayCount = all.filter((a) => a.date === todayStr() && a.status !== 'CANCELLED').length

  return (
    <section>
      <div className="stats">
        <Stat icon="calendar" label="Today" value={todayCount} tone="blue" />
        <Stat icon="clock" label="Scheduled" value={count('SCHEDULED')} tone="blue" />
        <Stat icon="check" label="Completed" value={count('COMPLETED')} tone="green" />
        <Stat icon="x" label="Cancelled" value={count('CANCELLED')} tone="red" />
      </div>

      <div className="filters card">
        <label>
          <span>Date</span>
          <input type="date" value={filters.date} onChange={setField('date')} />
        </label>
        {role !== 'DOCTOR' && (
        <label>
          <span>Doctor</span>
          <select value={filters.doctorId} onChange={setField('doctorId')}>
            <option value="">All doctors</option>
            {doctors.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </select>
        </label>
        )}
        <label>
          <span>Status</span>
          <select value={filters.status} onChange={setField('status')}>
            <option value="">All statuses</option>
            <option value="SCHEDULED">Scheduled</option>
            <option value="COMPLETED">Completed</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </label>
        {filtered && (
          <button className="link" onClick={() => setFilters(NO_FILTERS)}>
            Clear filters
          </button>
        )}
      </div>

      {error && <p className="msg error">{error}</p>}

      <div className="card flush">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Time</th>
                <th>Doctor</th>
                <th>Patient</th>
                <th>Reason</th>
                <th>Status</th>
                <th className="right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && (
                <tr>
                  <td colSpan="7" className="empty">
                    No appointments found.
                  </td>
                </tr>
              )}
              {rows.map((a) => (
                <tr key={a.id} className={`row-${a.status.toLowerCase()}`}>
                  <td>{a.date}</td>
                  <td>{hhmm(a.time)}</td>
                  <td>
                    <span className="person">
                      <span className="avatar">
                        {initials(a.doctorName)}
                      </span>
                      <span>
                        {a.doctorName}
                        <div className="sub">{a.specialization}</div>
                      </span>
                    </span>
                  </td>
                  <td>
                    <span className="person">
                      <span className="avatar">
                        {initials(a.patientName)}
                      </span>
                      {a.patientName}
                    </span>
                  </td>
                  <td>{a.reason || <span className="muted">—</span>}</td>
                  <td>
                    <span className={`badge ${a.status.toLowerCase()}`}>{a.status}</span>
                  </td>
                  <td className="actions">
                    {actionsFor(a)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {edit && (
        <Modal title="Edit appointment" onClose={() => setEdit(null)}>
          <form onSubmit={saveEdit}>
            <p className="hint spaced">
              {edit.patientName} with {edit.doctorName}
            </p>
            <label>
              <span>Date</span>
              <input
                type="date"
                value={edit.date}
                required
                onChange={(e) => setEdit({ ...edit, date: e.target.value, time: '' })}
              />
            </label>
            <div className="field">
              <span>Time slot</span>
              <SlotPicker
                slots={slots}
                value={edit.time}
                onChange={(t) => setEdit({ ...edit, time: t })}
                emptyHint="No free slots on this day."
              />
            </div>
            {role !== 'PATIENT' && (
            <label>
              <span>Status</span>
              <select value={edit.status} onChange={(e) => setEdit({ ...edit, status: e.target.value })}>
                <option value="SCHEDULED">Scheduled</option>
                <option value="COMPLETED">Completed</option>
                <option value="CANCELLED">Cancelled</option>
              </select>
            </label>
            )}
            <label>
              <span>Reason</span>
              <input
                type="text"
                value={edit.reason}
                maxLength={255}
                onChange={(e) => setEdit({ ...edit, reason: e.target.value })}
              />
            </label>
            {editError && <p className="msg error">{editError}</p>}
            <div className="modal-actions">
              <button type="button" onClick={() => setEdit(null)}>
                Cancel
              </button>
              <button type="submit" className="primary" disabled={!edit.time || !edit.date || saving}>
                {saving ? 'Saving…' : 'Save changes'}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </section>
  )
}
