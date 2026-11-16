import { useState } from 'react'
import { initials } from '../api.js'
import Modal, { Icon } from '../components/Modal.jsx'

/** Shared list + add/edit forms used for both doctors and patients. */
export default function DirectoryPage({ title, singular, items, fields, onCreate, onUpdate, onDelete }) {
  const empty = Object.fromEntries(fields.map((f) => [f.key, '']))
  const [form, setForm] = useState(empty)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [editing, setEditing] = useState(null) // item being edited, with its draft values
  const [editError, setEditError] = useState('')
  const [saving, setSaving] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    try {
      await onCreate(form)
      setForm(empty)
    } catch (err) {
      setError(err.message)
    }
  }

  const remove = async (item) => {
    if (!window.confirm(`Delete ${item.name}?`)) return
    setError('')
    try {
      await onDelete(item.id)
    } catch (err) {
      setError(err.message)
    }
  }

  const startEdit = (item) => {
    setEditError('')
    setEditing(Object.fromEntries([['id', item.id], ...fields.map((f) => [f.key, item[f.key] || ''])]))
  }

  const saveEdit = async (e) => {
    e.preventDefault()
    setEditError('')
    setSaving(true)
    try {
      const { id, ...body } = editing
      await onUpdate(id, body)
      setEditing(null)
    } catch (err) {
      setEditError(err.message)
    } finally {
      setSaving(false)
    }
  }

  const q = query.trim().toLowerCase()
  const visible = q
    ? items.filter((it) => fields.some((f) => String(it[f.key] || '').toLowerCase().includes(q)))
    : items

  return (
    <section className="two-col">
      <form className="card" onSubmit={submit}>
        <h2>Add {singular}</h2>
        {fields.map((f) => (
          <label key={f.key}>
            <span>
              {f.label}
              {f.required && <span className="req"> *</span>}
            </span>
            <input
              type={f.type || 'text'}
              value={form[f.key]}
              required={f.required}
              onChange={(e) => setForm({ ...form, [f.key]: e.target.value })}
            />
          </label>
        ))}
        {error && <p className="msg error">{error}</p>}
        <button type="submit" className="primary block">
          <Icon name="plus" /> Add {singular}
        </button>
      </form>

      <div className="card flush">
        <div className="card-head">
          <h2>
            {title} <span className="count">{items.length}</span>
          </h2>
          <div className="search">
            <Icon name="search" />
            <input
              type="search"
              placeholder={`Search ${title.toLowerCase()}`}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </div>
        </div>
        <div className="table-wrap">
          <table className="directory">
            <thead>
              <tr>
                {fields.map((f) => (
                  <th key={f.key}>{f.label}</th>
                ))}
                <th className="right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {visible.length === 0 && (
                <tr>
                  <td colSpan={fields.length + 1} className="empty">
                    {items.length === 0 ? 'Nothing here yet.' : 'No matches.'}
                  </td>
                </tr>
              )}
              {visible.map((item) => (
                <tr key={item.id}>
                  {fields.map((f, i) => (
                    <td key={f.key}>
                      {i === 0 ? (
                        <span className="person">
                          <span className="avatar">{initials(item.name)}</span>
                          {item[f.key]}
                        </span>
                      ) : (
                        f.key === 'specialization' && item[f.key] ? (
                          <span className="chip">
                            {item[f.key]}
                          </span>
                        ) : (
                          item[f.key] || <span className="muted">—</span>
                        )
                      )}
                    </td>
                  ))}
                  <td className="actions">
                    <button className="ghost" onClick={() => startEdit(item)}>
                      <Icon name="edit" /> Edit
                    </button>
                    <button className="ghost danger" onClick={() => remove(item)}>
                      <Icon name="trash" /> Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {editing && (
        <Modal title={`Edit ${singular}`} onClose={() => setEditing(null)}>
          <form onSubmit={saveEdit}>
            {fields.map((f) => (
              <label key={f.key}>
                <span>
                  {f.label}
                  {f.required && <span className="req"> *</span>}
                </span>
                <input
                  type={f.type || 'text'}
                  value={editing[f.key]}
                  required={f.required}
                  autoFocus={f === fields[0]}
                  onChange={(e) => setEditing({ ...editing, [f.key]: e.target.value })}
                />
              </label>
            ))}
            {editError && <p className="msg error">{editError}</p>}
            <div className="modal-actions">
              <button type="button" onClick={() => setEditing(null)}>
                Cancel
              </button>
              <button type="submit" className="primary" disabled={saving}>
                {saving ? 'Saving…' : 'Save changes'}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </section>
  )
}
