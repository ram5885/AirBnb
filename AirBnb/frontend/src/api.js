// Thin wrapper around the Spring Boot auth API (see AuthController).
// In dev, Vite proxies /api/* to http://localhost:8080 (see vite.config.js).
const BASE_URL = '/api/auth';

async function handleResponse(res) {
  const raw = await res.text();
  let data = {};
  if (raw) {
    try {
      data = JSON.parse(raw);
    } catch {
      data = { message: raw };
    }
  }

  if (!res.ok) {
    // GlobalExceptionHandler returns { error: "..." }; anything unhandled
    // (validation errors, 500s) falls back to Spring's default { message } shape.
    const message = data.error || data.message || `Request failed (${res.status})`;
    throw new Error(message);
  }

  return data;
}

function post(path, body) {
  return fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(handleResponse);
}

export function login({ email, password }) {
  return post('/login', { email, password });
}

export function signup({ name, email, password, role }) {
  return post('/signup', { name, email, password, role });
}
