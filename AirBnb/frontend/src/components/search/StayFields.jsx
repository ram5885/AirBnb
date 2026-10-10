import { addDays, isoDate } from '../manager/ui.jsx';
import { MAX_NIGHTS, MAX_ROOMS } from './stay.js';

/** Check-in, check-out and number-of-rooms inputs (controlled). */
export default function StayFields({ idPrefix, value, errors = {}, onChange, disabled }) {
  const today = isoDate(new Date());

  function set(key, v) {
    const next = { ...value, [key]: v };
    // Keep check-out after check-in when check-in moves past it.
    if (key === 'checkinDate' && v && next.checkoutDate && next.checkoutDate <= v) next.checkoutDate = addDays(v, 1);
    onChange(next);
  }

  return (
    <>
      <div className={`s-field ${errors.checkinDate ? 's-field--error' : ''}`}>
        <label htmlFor={`${idPrefix}-in`}>Check-in</label>
        <input
          id={`${idPrefix}-in`}
          type="date"
          min={today}
          value={value.checkinDate}
          onChange={(e) => set('checkinDate', e.target.value)}
          disabled={disabled}
          aria-invalid={!!errors.checkinDate}
          aria-describedby={errors.checkinDate ? `${idPrefix}-in-err` : undefined}
        />
        {errors.checkinDate && (
          <span className="s-field__error" id={`${idPrefix}-in-err`}>
            {errors.checkinDate}
          </span>
        )}
      </div>
      <div className={`s-field ${errors.checkoutDate ? 's-field--error' : ''}`}>
        <label htmlFor={`${idPrefix}-out`}>Check-out</label>
        <input
          id={`${idPrefix}-out`}
          type="date"
          min={value.checkinDate ? addDays(value.checkinDate, 1) : addDays(today, 1)}
          max={value.checkinDate ? addDays(value.checkinDate, MAX_NIGHTS) : undefined}
          value={value.checkoutDate}
          onChange={(e) => set('checkoutDate', e.target.value)}
          disabled={disabled}
          aria-invalid={!!errors.checkoutDate}
          aria-describedby={errors.checkoutDate ? `${idPrefix}-out-err` : undefined}
        />
        {errors.checkoutDate && (
          <span className="s-field__error" id={`${idPrefix}-out-err`}>
            {errors.checkoutDate}
          </span>
        )}
      </div>
      <div className={`s-field s-field--narrow ${errors.numberOfRooms ? 's-field--error' : ''}`}>
        <label htmlFor={`${idPrefix}-rooms`}>Rooms</label>
        <select
          id={`${idPrefix}-rooms`}
          value={value.numberOfRooms}
          onChange={(e) => set('numberOfRooms', Number(e.target.value))}
          disabled={disabled}
        >
          {Array.from({ length: MAX_ROOMS }, (_, i) => i + 1).map((n) => (
            <option key={n} value={n}>
              {n} {n === 1 ? 'room' : 'rooms'}
            </option>
          ))}
        </select>
        {errors.numberOfRooms && <span className="s-field__error">{errors.numberOfRooms}</span>}
      </div>
    </>
  );
}
