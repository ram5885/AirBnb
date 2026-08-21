import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import '../styles/auth.css';

const ROLES = [
  { value: 'GUEST', label: 'Guest — book stays' },
  { value: 'HOTEL_MANAGER', label: 'Hotel manager — list properties' },
  { value: 'ADMIN', label: 'Admin' },
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
      if (isSignup) {
        await signup({
          name: form.name.trim(),
          email: form.email.trim(),
          password: form.password,
          role: form.role,
        });
      } else {
        await login({ email: form.email.trim(), password: form.password });
      }
      navigate('/');
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
      <div className="auth-card">
        <div className="auth-card__header">
          <button
            type="button"
            className="auth-card__close"
            aria-label="Close"
            onClick={() => navigate('/')}
          >
            &times;
          </button>
          <h1>Log in or sign up</h1>
        </div>

        <div className="auth-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={!isSignup}
            className={`auth-tabs__tab ${!isSignup ? 'is-active' : ''}`}
            onClick={() => switchMode('login')}
          >
            Log in
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={isSignup}
            className={`auth-tabs__tab ${isSignup ? 'is-active' : ''}`}
            onClick={() => switchMode('signup')}
          >
            Sign up
          </button>
        </div>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
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

              <label className="auth-select">
                <span>I'm signing up as</span>
                <select value={form.role} onChange={(e) => updateField('role', e.target.value)}>
                  {ROLES.map((r) => (
                    <option key={r.value} value={r.value}>
                      {r.label}
                    </option>
                  ))}
                </select>
              </label>
            </>
          )}

          {formError && <p className="auth-form__error">{formError}</p>}

          <button type="submit" className="auth-submit" disabled={submitting}>
            {submitting ? 'Please wait…' : isSignup ? 'Sign up' : 'Log in'}
          </button>
        </form>

        <div className="auth-divider">
          <span>or</span>
        </div>

        <div className="auth-social">
          <SocialButton label="Continue with Google" icon="G" />
          <SocialButton label="Continue with Facebook" icon="f" />
          <SocialButton label="Continue with Apple" icon="" />
        </div>

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

function Field({ label, type = 'text', value, onChange, error, autoComplete }) {
  return (
    <label className={`auth-field ${error ? 'has-error' : ''}`}>
      <span>{label}</span>
      <input
        type={type}
        value={value}
        autoComplete={autoComplete}
        onChange={(e) => onChange(e.target.value)}
      />
      {error && <em className="auth-field__error">{error}</em>}
    </label>
  );
}

function SocialButton({ label, icon }) {
  return (
    <button
      type="button"
      className="auth-social__btn"
      onClick={() => alert(`${label} is not implemented in this demo — please use email instead.`)}
    >
      <span className="auth-social__icon">{icon}</span>
      {label}
    </button>
  );
}
