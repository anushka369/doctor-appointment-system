import { hhmm } from '../api.js'

/** Grid of time-slot buttons. `slots` come from the API as "09:00:00"; `value` is "09:00". */
export default function SlotPicker({ slots, value, onChange, emptyHint }) {
  if (slots.length === 0) return <p className="hint">{emptyHint}</p>
  return (
    <div className="slots">
      {slots.map((s) => {
        const t = hhmm(s)
        return (
          <button
            type="button"
            key={t}
            className={value === t ? 'slot selected' : 'slot'}
            aria-pressed={value === t}
            onClick={() => onChange(t)}
          >
            {t}
          </button>
        )
      })}
    </div>
  )
}
