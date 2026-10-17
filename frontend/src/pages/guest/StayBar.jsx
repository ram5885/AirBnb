import { useEffect, useState } from 'react';
import StayFields from '../../components/search/StayFields.jsx';
import { defaultDates, validateDates } from '../../components/search/stay.js';

/** Dates + rooms editor used on the hotel and room pages; applying updates the URL. */
export default function StayBar({ stay, onApply, busy }) {
  const [value, setValue] = useState(() => ({
    checkinDate: stay.checkinDate || defaultDates().checkinDate,
    checkoutDate: stay.checkoutDate || defaultDates().checkoutDate,
    numberOfRooms: stay.numberOfRooms || 1,
  }));
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (stay.checkinDate && stay.checkoutDate) {
      setValue({ checkinDate: stay.checkinDate, checkoutDate: stay.checkoutDate, numberOfRooms: stay.numberOfRooms });
    }
  }, [stay.checkinDate, stay.checkoutDate, stay.numberOfRooms]);

  function submit(e) {
    e.preventDefault();
    const errs = validateDates(value);
    setErrors(errs);
    if (Object.keys(errs).length === 0) onApply(value);
  }

  return (
    <form className="s-staybar" onSubmit={submit} noValidate aria-label="Change dates and rooms">
      <StayFields
        idPrefix="stay"
        value={value}
        errors={errors}
        onChange={(v) => {
          setValue(v);
          setErrors({});
        }}
      />
      <button type="submit" className="m-btn m-btn--primary" disabled={busy}>
        {stay.checkinDate ? 'Update' : 'Check availability'}
      </button>
    </form>
  );
}
