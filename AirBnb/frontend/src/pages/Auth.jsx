import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { homePathFor, useAuth } from '../context/AuthContext.jsx';
import '../styles/auth.css';

const ROLES = [
  { value: 'GUEST', title: 'Guest', text: 'Find and book stays', icon: 'M4 20v-1a6 6 0 0112 0v1M10 11a4 4 0 100-8 4 4 0 000 8z' },
  {
    value: 'HOTEL_MANAGER',
    title: 'Hotel manager',
    text: 'List and manage properties',
    icon: 'M3 21V9l9-6 9 6v12M9 21v-6h6v6M3 21h18',
  },
];

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function Auth({ mode: initialMode }) {
  const [mode, setMode] = useState(initialMode === 'signup' ? 'signup' : 'login');
  const [form, setForm] = useState({
    name: '',
    email: '',
    password: '',
    confirmPassword: '',
    role: 'GUEST',
  });
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const { login, signup } = useAuth();
  const navigate = useNavigate();

  const isSignup = mode === 'signup';

  function updateField(key, value) {
    setForm((f) => ({ ...f, [key]: value }));
    setFieldErrors((e) => ({ ...e, [key]: undefined }));
  }

  function validate() {
    const errors = {};
    if (isSignup && !form.name.trim()) errors.name = 'Please enter your name';
    if (!form.email.trim()) {
      errors.email = 'Please enter your email';
    } else if (!EMAIL_RE.test(form.email.trim())) {
      errors.email = 'Please enter a valid email address';
    }
    if (!form.password) {
      errors.password = 'Please enter a password';
    } else if (isSignup && form.password.length < 6) {
      errors.password = 'Password must be at least 6 characters';
    }
    if (isSignup && form.confirmPassword !== form.password) {
      errors.confirmPassword = 'Passwords do not match';
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setFormError('');
    if (!validate()) return;

    setSubmitting(true);
    try {
      let user;
      if (isSignup) {
        user = await signup({
          name: form.name.trim(),
          email: form.email.trim(),
          password: form.password,
          role: form.role,
        });
      } else {
        user = await login({ email: form.email.trim(), password: form.password });
      }
      // Role-based landing: hotel managers -> manager dashboard, other users -> home.
      navigate(homePathFor(user), { replace: true });
    } catch (err) {
      setFormError(err.message || 'Something went wrong. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  function switchMode(next) {
    setMode(next);
    setFormError('');
    setFieldErrors({});
    navigate(next === 'signup' ? '/signup' : '/login', { replace: true });
  }

  return (
    <div className="auth-page">
      <div className="auth-brand" aria-hidden="true">
        <svg viewBox="0 0 32 32" width="120" height="120" fill="currentColor">
          <path d="M16 1c-1.1 0-2 .5-2.6 1.4C10.7 6.6 4 17.2 4 22a12 12 0 0024 0c0-4.8-6.7-15.4-9.4-19.6C18 1.5 17.1 1 16 1zm0 4.4c2.9 4.6 8 13.2 8 16.6a8 8 0 01-16 0c0-3.4 5.1-12 8-16.6z" />
        </svg>
        <span>airbnb</span>
      </div>

      <div className="auth-card">
        <div className="auth-card__header">
          <h1>Log in or sign up</h1>
        </div>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <h2 className="auth-form__title">{isSignup ? 'Create your account' : 'Welcome to Airbnb'}</h2>

          <div className="auth-group">
            {isSignup && (
              <Field
                label="Full name"
                value={form.name}
                onChange={(v) => updateField('name', v)}
                error={fieldErrors.name}
                autoComplete="name"
              />
            )}
            <Field
              label="Email"
              type="email"
              value={form.email}
              onChange={(v) => updateField('email', v)}
              error={fieldErrors.email}
              autoComplete="email"
            />
            <Field
              label="Password"
              type="password"
              value={form.password}
              onChange={(v) => updateField('password', v)}
              error={fieldErrors.password}
              autoComplete={isSignup ? 'new-password' : 'current-password'}
            />
            {isSignup && (
              <>
                <Field
                  label="Confirm password"
                  type="password"
                  value={form.confirmPassword}
                  onChange={(v) => updateField('confirmPassword', v)}
                  error={fieldErrors.confirmPassword}
                  autoComplete="new-password"
                />
              </>
            )}
          </div>

          {isSignup && (
            <fieldset className="auth-roles">
              <legend>I'm signing up as</legend>
              {ROLES.map((r) => (
                <label key={r.value} className={`auth-role ${form.role === r.value ? 'is-on' : ''}`}>
                  <input
                    type="radio"
                    name="role"
                    value={r.value}
                    checked={form.role === r.value}
                    onChange={() => updateField('role', r.value)}
                  />
                  <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d={r.icon} />
                  </svg>
                  <span className="auth-role__title">{r.title}</span>
                  <span className="auth-role__text">{r.text}</span>
                </label>
              ))}
            </fieldset>
          )}

          <FieldErrors errors={fieldErrors} />
          <p className="auth-form__hint">
            {isSignup
              ? 'Use at least 6 characters for your password.'
              : 'Use the email and password you signed up with.'}
          </p>

          {formError && (
            <p className="auth-form__error" role="alert">
              {formError}
            </p>
          )}

          <button type="submit" className="auth-submit" disabled={submitting}>
            {submitting ? 'Please wait…' : 'Continue'}
          </button>
        </form>

        <p className="auth-switch">
          {isSignup ? (
            <>
              Already have an account?{' '}
              <button type="button" onClick={() => switchMode('login')}>
                Log in
              </button>
            </>
          ) : (
            <>
              Don&apos;t have an account?{' '}
              <button type="button" onClick={() => switchMode('signup')}>
                Sign up
              </button>
            </>
          )}
        </p>
      </div>
    </div>
  );
}

/**
 * One row of the joined field group with a floating label: centred in the row while empty, small above
 * the text once focused or filled. Password rows get an eye button to show/hide the text.
 */
function Field({ label, type = 'text', value, onChange, error, autoComplete }) {
  const [revealed, setRevealed] = useState(false);
  const isPassword = type === 'password';
  return (
    <label className={`auth-field ${error ? 'has-error' : ''}`}>
      <input
        type={isPassword && revealed ? 'text' : type}
        value={value}
        placeholder=" "
        autoComplete={autoComplete}
        aria-invalid={!!error}
        onChange={(e) => onChange(e.target.value)}
      />
      <span className="auth-field__label">{label}</span>
      {isPassword && (
        <button
          type="button"
          className="auth-field__toggle"
          onClick={() => setRevealed((v) => !v)}
          aria-label={revealed ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`}
        >
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z" />
            <circle cx="12" cy="12" r="3" />
            {revealed && <path d="M4 4l16 16" />}
          </svg>
        </button>
      )}
    </label>
  );
}

/** Field errors listed under the group (the joined rows leave no room under each input). */
function FieldErrors({ errors }) {
  const messages = Object.values(errors).filter(Boolean);
  if (!messages.length) return null;
  return (
    <ul className="auth-field-errors" role="alert">
      {messages.map((m) => (
        <li key={m}>{m}</li>
      ))}
    </ul>
  );
}
