// Small presentational building blocks shared by the hotel manager screens.

export function Spinner({ label = 'Loading…' }) {
  return (
    <div className="m-spinner" role="status">
      <span className="m-spinner__circle" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function Alert({ type = 'error', children, onDismiss }) {
  if (!children) return null;
  return (
    <div className={`m-alert m-alert--${type}`} role={type === 'error' ? 'alert' : 'status'}>
      <span>{children}</span>
      {onDismiss && (
        <button type="button" className="m-alert__close" onClick={onDismiss} aria-label="Dismiss">
          ×
        </button>
      )}
    </div>
  );
}

export function EmptyState({ title, children, action }) {
  return (
    <div className="m-empty">
      <h3>{title}</h3>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

const STATUS_LABELS = {
  RESERVED: 'Reserved',
  ACCEPTED: 'Accepted',
  CONFIRMED: 'Confirmed',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Expired',
};

const PAYMENT_LABELS = {
  PENDING: 'Awaiting payment',
  CONFIRMED: 'Paid',
  CANCELLED: 'Not paid',
  FAILED: 'Payment failed',
  REFUND_PENDING: 'Refund in progress',
  REFUNDED: 'Refunded',
};

const PAY_AT_HOTEL_LABELS = {
  PENDING: 'Pay at hotel',
  CONFIRMED: 'Paid at hotel',
  CANCELLED: 'Not paid',
};

/** Payment state of a booking; null status means no payment has been started. */
export function PaymentBadge({ status, method }) {
  if (!status) return <span className="m-badge m-badge--inactive">Unpaid</span>;
  const atHotel = method === 'PAY_AT_HOTEL';
  const label = (atHotel && PAY_AT_HOTEL_LABELS[status]) || PAYMENT_LABELS[status] || status;
  const cls = atHotel && status === 'PENDING' ? 'pay-at-hotel' : `pay-${status.toLowerCase()}`;
  return <span className={`m-badge m-badge--${cls}`}>{label}</span>;
}

export function StatusBadge({ status }) {
  return <span className={`m-badge m-badge--${status.toLowerCase()}`}>{STATUS_LABELS[status] || status}</span>;
}

export function ActiveBadge({ active }) {
  return <span className={`m-badge ${active ? 'm-badge--active' : 'm-badge--inactive'}`}>{active ? 'Active' : 'Inactive'}</span>;
}

export function Field({ id, label, error, hint, required, children }) {
  return (
    <div className={`m-field ${error ? 'm-field--error' : ''}`}>
      <label htmlFor={id}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      {children}
      {hint && !error && (
        <p className="m-field__hint" id={`${id}-hint`}>
          {hint}
        </p>
      )}
      {error && (
        <p className="m-field__error" id={`${id}-error`}>
          {error}
        </p>
      )}
    </div>
  );
}

export function Pagination({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <nav className="m-pagination" aria-label="Pagination">
      <button type="button" className="m-btn m-btn--secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        Previous
      </button>
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        className="m-btn m-btn--secondary"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        Next
      </button>
    </nav>
  );
}

export function ToggleSwitch({ id, checked, onChange, disabled, label }) {
  return (
    <div className="m-toggle">
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={checked}
        className={`m-toggle__track ${checked ? 'is-on' : ''}`}
        onClick={() => onChange(!checked)}
        disabled={disabled}
      >
        <span className="m-toggle__thumb" />
      </button>
      <label htmlFor={id} className="m-toggle__label">
        {label ?? (checked ? 'Active' : 'Inactive')}
      </label>
    </div>
  );
}

export function formatDate(value) {
  if (!value) return '—';
  const d = new Date(value.length === 10 ? `${value}T00:00:00` : value);
  return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatDateTime(value) {
  if (!value) return '—';
  return new Date(value).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatPrice(value) {
  if (value === null || value === undefined) return '—';
  return Number(value).toLocaleString('en-IN', { style: 'currency', currency: 'INR' });
}

/** Local-date ISO string (yyyy-MM-dd), avoiding the UTC shift of toISOString(). */
export function isoDate(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function addDays(iso, days) {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + days);
  return isoDate(d);
}
