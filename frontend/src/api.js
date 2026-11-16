const TOKEN_KEY = 'authToken'
export const getToken = () => {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}
export const setToken = (t) => {
  try {
    if (t) localStorage.setItem(TOKEN_KEY, t)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {}
}

let onUnauthorized = () => {}
export const setUnauthorizedHandler = (fn) => {
  onUnauthorized = fn
}

async function request(path, options = {}) {
  const token = getToken()
  const res = await fetch(`/api${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  })
  if (res.status === 401 && token) {
    setToken(null)
    onUnauthorized() // session expired: drop back to the login screen
  }
  if (res.status === 204) return null
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    throw new Error(data?.error || `Request failed (${res.status})`)
  }
  return data
}

const json = (method, body) => ({ method, body: JSON.stringify(body) })

export const api = {
  requestOtp: (role, email) => request('/auth/request-otp', json('POST', { role, email })),
  verifyOtp: (challengeId, otp) => request('/auth/verify', json('POST', { challengeId, otp })),
  getProfile: () => request('/profile'),
  updateProfile: (p) => request('/profile', json('PUT', p)),
  me: () => request('/auth/me'),
  logout: () => request('/auth/logout', { method: 'POST' }),
  demoAccounts: () => request('/auth/demo-accounts'),


  listDoctors: () => request('/doctors'),
  createDoctor: (d) => request('/doctors', json('POST', d)),
  updateDoctor: (id, d) => request(`/doctors/${id}`, json('PUT', d)),
  deleteDoctor: (id) => request(`/doctors/${id}`, { method: 'DELETE' }),
  slots: (doctorId, date) => request(`/doctors/${doctorId}/slots?date=${date}`),

  listPatients: () => request('/patients'),
  createPatient: (p) => request('/patients', json('POST', p)),
  updatePatient: (id, p) => request(`/patients/${id}`, json('PUT', p)),
  deletePatient: (id) => request(`/patients/${id}`, { method: 'DELETE' }),

  listAppointments: ({ date, doctorId, status } = {}) => {
    const q = new URLSearchParams()
    if (date) q.set('date', date)
    if (doctorId) q.set('doctorId', doctorId)
    if (status) q.set('status', status)
    const qs = q.toString()
    return request(`/appointments${qs ? `?${qs}` : ''}`)
  },
  book: (b) => request('/appointments', json('POST', b)),
  updateAppointment: (id, a) => request(`/appointments/${id}`, json('PUT', a)),
  deleteAppointment: (id) => request(`/appointments/${id}`, { method: 'DELETE' }),
  setStatus: (id, status) => request(`/appointments/${id}/status`, json('PATCH', { status })),
}

// Backend sends times as "09:00:00"; the UI only needs "09:00".
export const hhmm = (t) => (t ? t.slice(0, 5) : '')

// Local date as YYYY-MM-DD (toISOString would shift to UTC).
export const todayStr = () => new Date().toLocaleDateString('en-CA')

// "Dr. Jane Doe" -> "JD"
export const initials = (name = '') =>
  name
    .replace(/^dr\.?\s+/i, '')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join('')

// Stable hue (0-359) from a string, so each name/specialty keeps its own colour.
export const hue = (str = '') => {
  let h = 0
  for (const c of str) h = (h * 31 + c.charCodeAt(0)) % 360
  return h
}
