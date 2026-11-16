import { useEffect, useState } from 'react'
import { api, setToken } from '../api.js'
import { Icon } from '../components/Modal.jsx'

const ROLES = [
  { id: 'PATIENT', label: 'Patient', icon: 'users', hint: 'Book and manage your own appointments' },
  { id: 'DOCTOR', label: 'Doctor', icon: 'doctor', hint: 'View and update your schedule' },
  { id: 'ADMIN', label: 'Admin', icon: 'calendar', hint: 'Manage doctors, patients and all appointments' },
]

export default function LoginPage({ onLoggedIn, theme, toggleTheme }) {
  const [role, setRole] = useState('PATIENT')
  const [email, setEmail] = useState('')
  const [challenge, setChallenge] = useState(null) // { challengeId, expiresInSeconds, demoOtp }
  const [otp, setOtp] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [demo, setDemo] = useState({})

  useEffect(() => {
    api.demoAccounts().catch(() => ({})).then((d) => setDemo(d || {}))
  }, [])

  const current = ROLES.find((r) => r.id === role)

  const sendOtp = async (e) => {
    e?.preventDefault()
    setError('')
    setBusy(true)
    try {
      setChallenge(await api.requestOtp(role, email))
      setOtp('')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const verify = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      const res = await api.verifyOtp(challenge.challengeId, otp)
      setToken(res.token)
      onLoggedIn(res.user)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const back = () => {
    setChallenge(null)
    setOtp('')
    setError('')
  }

  return (
    <div className="login-wrap">
      <button className="tab theme-toggle login-theme" onClick={toggleTheme} aria-label="Toggle theme">
        <Icon name={theme === 'dark' ? 'sun' : 'moon'} size={18} />
      </button>

      <div className="login-card">
        <div className="login-brand">
          <span className="logo">+</span>
          <div>
            <h1 className="login-title">Doctor Appointments</h1>
            <p className="hint">Sign in with a one-time code</p>
          </div>
        </div>

        {!challenge ? (
          <form onSubmit={sendOtp}>
            <div className="segmented" role="tablist">
              {ROLES.map((r) => (
                <button
                  type="button"
                  key={r.id}
                  role="tab"
                  aria-selected={role === r.id}
                  className={role === r.id ? 'seg-btn active' : 'seg-btn'}
                  onClick={() => {
                    setRole(r.id)
                    setError('')
                  }}
                >
                  <Icon name={r.icon} /> {r.label}
                </button>
              ))}
            </div>
            <p className="hint spaced">{current.hint}</p>

            <label>
              <span>{role === 'ADMIN' ? 'Admin email' : `${current.label} email`}</span>
              <input
                type="email"
                value={email}
                required
                autoFocus
                placeholder="name@example.com"
                onChange={(e) => setEmail(e.target.value)}
              />
            </label>

            {demo[role]?.length > 0 && (
              <div className="demo-accounts">
                <span className="hint">Demo accounts, click to fill:</span>
                <div className="chips">
                  {demo[role].map((a) => (
                    <button type="button" key={a.email} className="chip-btn" title={a.email} onClick={() => setEmail(a.email)}>
                      {a.name}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {error && <p className="msg error">{error}</p>}
            <button type="submit" className="primary block" disabled={busy || !email}>
              {busy ? 'Sending…' : 'Send one-time code'}
            </button>
          </form>
        ) : (
          <form onSubmit={verify}>
            <p className="hint spaced">
              We generated a 6-digit code for <strong>{email}</strong>. It expires in{' '}
              {Math.round(challenge.expiresInSeconds / 60)} minutes.
            </p>

            {challenge.demoOtp && (
              <div className="demo-banner">
                <div>
                  <div className="demo-label">Demo mode: no email or SMS is sent</div>
                  <div>
                    Your code is <strong className="demo-code">{challenge.demoOtp}</strong>
                  </div>
                </div>
                <button type="button" onClick={() => setOtp(challenge.demoOtp)}>
                  Fill code
                </button>
              </div>
            )}

            <label>
              <span>One-time code</span>
              <input
                className="otp-input"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                value={otp}
                autoFocus
                placeholder="••••••"
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
              />
            </label>

            {error && <p className="msg error">{error}</p>}
            <button type="submit" className="primary block" disabled={busy || otp.length !== 6}>
              {busy ? 'Verifying…' : 'Verify and sign in'}
            </button>
            <div className="login-links">
              <button type="button" className="link" onClick={back}>
                ← Change email
              </button>
              <button type="button" className="link" onClick={sendOtp} disabled={busy}>
                Resend code
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
