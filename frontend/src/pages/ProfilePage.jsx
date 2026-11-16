import { useEffect, useState } from 'react'
import { api, initials, todayStr } from '../api.js'

const ROLE_LABEL = { ADMIN: 'Administrator', DOCTOR: 'Doctor', PATIENT: 'Patient' }

const CAN = {
  ADMIN: ['Manage doctors and patients', 'See, book, edit and delete every appointment'],
  DOCTOR: ['See your own appointments', 'Complete, cancel and edit your appointments'],
  PATIENT: ['Book appointments for yourself', 'Edit or cancel your own scheduled appointments'],
}

function Stat({ label, value, tone = 'blue' }) {
  return (
    <div className={`stat ${tone}`}>
      <div>
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  )
}

/** Own profile for every role. Doctors and patients can edit theirs; the admin account is read-only. */
export default function ProfilePage({ role, counts, onUpdated }) {
  const [profile, setProfile] = useState(null)
  const [form, setForm] = useState(null)
  const [appts, setAppts] = useState([])
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  const [saving, setSaving] = useState(false)

  const apply = (p) => {
    setProfile(p)
    setForm({
      name: p.name || '',
      specialization: p.specialization || '',
      email: p.email || '',
      phone: p.phone || '',
    })
  }

  useEffect(() => {
    api.getProfile().then(apply).catch((e) => setError(e.message))
    api.listAppointments().then(setAppts).catch(() => {})
  }, [])

  if (!profile || !form) {
    return <section>{error ? <p className="msg error">{error}</p> : <p className="hint">Loading…</p>}</section>
  }

  const editable = profile.editable
  const dirty = ['name', 'specialization', 'email', 'phone'].some((k) => form[k] !== (profile[k] || ''))
  const set = (k) => (e) => {
    setSaved(false)
    setForm({ ...form, [k]: e.target.value })
  }

  const save = async (e) => {
    e.preventDefault()
    setError('')
    setSaved(false)
    setSaving(true)
    try {
      const p = await api.updateProfile(form)
      apply(p)
      setSaved(true)
      onUpdated(p)
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  const today = todayStr()
  const stat = (f) => appts.filter(f).length
  const expires = new Date(profile.sessionExpires).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })

  return (
    <section className="profile">
      <div className="card profile-head">
        <span className="avatar xl">{initials(profile.name)}</span>
        <div>
          <h2>{profile.name}</h2>
          <div className="sub">
            <span className={`role-pill ${role.toLowerCase()}`}>{ROLE_LABEL[role]}</span>
            {profile.specialization && <> · {profile.specialization}</>}
          </div>
          <div className="sub">{profile.email}</div>
        </div>
      </div>

      <div className="stats">
        {role === 'ADMIN' ? (
          <>
            <Stat label="Doctors" value={counts.doctors} />
            <Stat label="Patients" value={counts.patients} />
            <Stat label="Appointments" value={appts.length} />
            <Stat label="Scheduled" value={stat((a) => a.status === 'SCHEDULED')} />
          </>
        ) : (
          <>
            <Stat label="Upcoming" value={stat((a) => a.status === 'SCHEDULED' && a.date >= today)} />
            <Stat label="Completed" value={stat((a) => a.status === 'COMPLETED')} tone="green" />
            <Stat label="Cancelled" value={stat((a) => a.status === 'CANCELLED')} tone="red" />
            <Stat label="Total" value={appts.length} />
          </>
        )}
      </div>

      <div className="two-col">
        <form className="card" onSubmit={save}>
          <h2>{editable ? 'Edit profile' : 'Profile details'}</h2>
          {!editable && (
            <p className="hint spaced">The admin account is configured on the server, so it can't be edited here.</p>
          )}
          <label>
            <span>
              Name <span className="req">*</span>
            </span>
            <input value={form.name} onChange={set('name')} required disabled={!editable} />
          </label>
          {role === 'DOCTOR' && (
            <label>
              <span>
                Specialization <span className="req">*</span>
              </span>
              <input value={form.specialization} onChange={set('specialization')} required />
            </label>
          )}
          <label>
            <span>
              Email <span className="req">*</span>
            </span>
            <input type="email" value={form.email} onChange={set('email')} required disabled={!editable} />
            {editable && <span className="hint">This is the email you sign in with.</span>}
          </label>
          {role !== 'ADMIN' && (
            <label>
              <span>Phone</span>
              <input value={form.phone} onChange={set('phone')} />
            </label>
          )}
          {error && <p className="msg error">{error}</p>}
          {saved && <p className="msg success">Profile updated.</p>}
          {editable && (
            <div className="modal-actions">
              <button type="button" disabled={!dirty || saving} onClick={() => apply(profile)}>
                Reset
              </button>
              <button type="submit" className="primary" disabled={!dirty || saving}>
                {saving ? 'Saving…' : 'Save changes'}
              </button>
            </div>
          )}
        </form>

        <div className="card">
          <h2>Account</h2>
          <dl className="facts">
            <dt>Role</dt>
            <dd>{ROLE_LABEL[role]}</dd>
            <dt>Signed in until</dt>
            <dd>{expires}</dd>
          </dl>
          <h2 className="subhead">What you can do</h2>
          <ul className="can-list">
            {CAN[role].map((t) => (
              <li key={t}>{t}</li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  )
}
