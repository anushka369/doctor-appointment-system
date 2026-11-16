import { useCallback, useEffect, useState } from 'react'
import { api, getToken, initials, setToken, setUnauthorizedHandler } from './api.js'
import { Icon } from './components/Modal.jsx'
import AppointmentsPage from './pages/AppointmentsPage.jsx'
import BookPage from './pages/BookPage.jsx'
import DirectoryPage from './pages/DirectoryPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import ProfilePage from './pages/ProfilePage.jsx'

const ROLE_LABEL = { ADMIN: 'Admin', DOCTOR: 'Doctor', PATIENT: 'Patient' }

// Which tabs each role sees. The backend enforces the same rules; this just hides what would be refused.
const TABS = [
  { id: 'appointments', label: 'Appointments', icon: 'calendar', roles: ['ADMIN', 'DOCTOR', 'PATIENT'] },
  { id: 'book', label: 'Book', icon: 'plus', roles: ['ADMIN', 'PATIENT'] },
  { id: 'doctors', label: 'Doctors', icon: 'doctor', roles: ['ADMIN'] },
  { id: 'patients', label: 'Patients', icon: 'users', roles: ['ADMIN'] },
]

function useTheme() {
  const [theme, setTheme] = useState(() => document.documentElement.dataset.theme || 'light')
  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try {
      localStorage.setItem('theme', theme)
    } catch {}
  }, [theme])
  return [theme, () => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))]
}

export default function App() {
  const [theme, toggleTheme] = useTheme()
  const [user, setUser] = useState(null)
  const [checking, setChecking] = useState(() => !!getToken())
  const [tab, setTab] = useState('appointments')
  const [doctors, setDoctors] = useState([])
  const [patients, setPatients] = useState([])
  const [loadError, setLoadError] = useState('')

  const loadDoctors = useCallback(() => api.listDoctors().then(setDoctors), [])
  const loadPatients = useCallback(() => api.listPatients().then(setPatients), [])

  // Restore a saved session; any 401 later sends us back to the login screen.
  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null))
    if (!getToken()) return
    api
      .me()
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setChecking(false))
  }, [])

  const role = user?.role
  useEffect(() => {
    if (!role) return
    setLoadError('')
    const jobs = [loadDoctors()]
    if (role === 'ADMIN') jobs.push(loadPatients())
    Promise.all(jobs).catch((e) => setLoadError(`Could not load data: ${e.message}`))
  }, [role, loadDoctors, loadPatients])

  const loggedIn = (u) => {
    setTab('appointments')
    setUser(u)
  }

  const logout = () => {
    api.logout().catch(() => {})
    setToken(null)
    setUser(null)
    setDoctors([])
    setPatients([])
  }

  if (checking) return null
  if (!user) return <LoginPage onLoggedIn={loggedIn} theme={theme} toggleTheme={toggleTheme} />

  const tabs = TABS.filter((t) => t.roles.includes(user.role))

  return (
    <div className="app">
      <header>
        <h1>Doctor Appointments</h1>
        <nav>
          {tabs.map((t) => (
            <button
              key={t.id}
              className={tab === t.id ? 'tab active' : 'tab'}
              onClick={() => setTab(t.id)}
            >
              <Icon name={t.icon} /> {t.id === 'appointments' && user.role !== 'ADMIN' ? 'My appointments' : t.label}
            </button>
          ))}
          <button
            className="tab theme-toggle"
            onClick={toggleTheme}
            aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}
            title={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}
          >
            <Icon name={theme === 'dark' ? 'sun' : 'moon'} size={18} />
          </button>
        </nav>
        <div className="user-chip">
          <button
            className={tab === 'profile' ? 'profile-btn active' : 'profile-btn'}
            onClick={() => setTab('profile')}
            title="My profile"
          >
            <span className="avatar">{initials(user.name)}</span>
            <span className="user-meta">
              <span className="user-name">{user.name}</span>
              <span className={`role-pill ${user.role.toLowerCase()}`}>{ROLE_LABEL[user.role]}</span>
            </span>
          </button>
          <button className="ghost" onClick={logout} title="Sign out">
            Sign out
          </button>
        </div>
      </header>

      {loadError && <p className="msg error">{loadError}</p>}

      <main>
        {tab === 'profile' && (
          <ProfilePage
            role={user.role}
            counts={{ doctors: doctors.length, patients: patients.length }}
            onUpdated={(p) => {
              setUser((u) => ({ ...u, name: p.name, email: p.email }))
              if (user.role === 'DOCTOR') loadDoctors()
            }}
          />
        )}
        {tab === 'appointments' && <AppointmentsPage doctors={doctors} role={user.role} />}
        {tab === 'book' && (
          <BookPage
            doctors={doctors}
            patients={patients}
            fixedPatient={user.role === 'PATIENT' ? user : null}
            onBooked={() => setTab('appointments')}
          />
        )}
        {tab === 'doctors' && (
          <DirectoryPage
            title="Doctors"
            singular="doctor"
            items={doctors}
            fields={[
              { key: 'name', label: 'Name', required: true },
              { key: 'specialization', label: 'Specialization', required: true },
              { key: 'email', label: 'Email', type: 'email' },
              { key: 'phone', label: 'Phone' },
            ]}
            onCreate={(d) => api.createDoctor(d).then(loadDoctors)}
            onUpdate={(id, d) => api.updateDoctor(id, d).then(loadDoctors)}
            onDelete={(id) => api.deleteDoctor(id).then(loadDoctors)}
          />
        )}
        {tab === 'patients' && (
          <DirectoryPage
            title="Patients"
            singular="patient"
            items={patients}
            fields={[
              { key: 'name', label: 'Name', required: true },
              { key: 'email', label: 'Email', type: 'email' },
              { key: 'phone', label: 'Phone' },
            ]}
            onCreate={(p) => api.createPatient(p).then(loadPatients)}
            onUpdate={(id, p) => api.updatePatient(id, p).then(loadPatients)}
            onDelete={(id) => api.deletePatient(id).then(loadPatients)}
          />
        )}
      </main>
    </div>
  )
}
