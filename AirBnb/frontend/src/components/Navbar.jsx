import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import '../styles/navbar.css';

function initials(name) {
  if (!name) return '?';
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
}

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    function onClickOutside(e) {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  function handleLogout() {
    logout();
    setMenuOpen(false);
    navigate('/');
  }

  return (
    <header className="navbar">
      <div className="container navbar__inner">
        <a className="navbar__logo" href="/">
          <svg viewBox="0 0 32 32" width="32" height="32" fill="var(--color-primary)" aria-hidden="true">
            <path d="M16 1c-1.1 0-2 .5-2.6 1.4C10.7 6.6 4 17.2 4 22a12 12 0 0024 0c0-4.8-6.7-15.4-9.4-19.6C18 1.5 17.1 1 16 1zm0 4.4c2.9 4.6 8 13.2 8 16.6a8 8 0 01-16 0c0-3.4 5.1-12 8-16.6z" />
          </svg>
          <span>airbnb</span>
        </a>

        <button
          type="button"
          className="navbar__search"
          onClick={() => alert('Search is not wired up yet — this is a demo landing page.')}
        >
          <span className="navbar__search-item">Anywhere</span>
          <span className="navbar__search-divider" />
          <span className="navbar__search-item">Any week</span>
          <span className="navbar__search-divider" />
          <span className="navbar__search-item navbar__search-item--muted">Add guests</span>
          <span className="navbar__search-icon" aria-hidden="true">
            <svg viewBox="0 0 32 32" width="14" height="14" fill="#fff">
              <path d="M13 24a11 11 0 118-18.7A11 11 0 0113 24zm8.6-3l6.7 6.7-1.6 1.6-6.7-6.7z" />
            </svg>
          </span>
        </button>

        <div className="navbar__right">
          <a className="navbar__host-link" href="#host">
            Airbnb your home
          </a>
          <button
            type="button"
            className="navbar__icon-btn"
            aria-label="Language / region"
            onClick={() => alert('This is a demo — language settings are not implemented.')}
          >
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              <path d="M12 2a10 10 0 100 20 10 10 0 000-20zm6.9 8h-3a15.6 15.6 0 00-1.3-5.3A8 8 0 0118.9 10zM12 4.1c.9 1.3 1.9 3.3 2.3 5.9H9.7c.4-2.6 1.4-4.6 2.3-5.9zM4 12c0-.7.1-1.4.2-2h3.4a17 17 0 000 4H4.2A8 8 0 014 12zm1.1 4h3a15.6 15.6 0 001.3 5.3A8 8 0 015.1 16zm0-8a8 8 0 015.3-5.3A15.6 15.6 0 009.1 8zM12 19.9c-.9-1.3-1.9-3.3-2.3-5.9h4.6c-.4 2.6-1.4 4.6-2.3 5.9zM14.9 14a17 17 0 000-4h-5.8a17 17 0 000 4zm.9-8.3A15.6 15.6 0 0117.1 8h3a8 8 0 00-4.3-5.3zM17.1 16a15.6 15.6 0 01-1.3 5.3A8 8 0 0018.9 16zM16.4 14h3.4a8 8 0 000-4h-3.4a17 17 0 010 4z" />
            </svg>
          </button>

          <div className="navbar__menu" ref={menuRef}>
            <button
              type="button"
              className="navbar__menu-btn"
              onClick={() => setMenuOpen((v) => !v)}
              aria-haspopup="true"
              aria-expanded={menuOpen}
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                <path d="M3 6h18v2H3zM3 11h18v2H3zM3 16h18v2H3z" />
              </svg>
              {user ? (
                <span className="navbar__avatar">{initials(user.name)}</span>
              ) : (
                <span className="navbar__avatar navbar__avatar--guest">
                  <svg viewBox="0 0 24 24" width="22" height="22" fill="#717171">
                    <path d="M12 12a5 5 0 100-10 5 5 0 000 10zm0 2c-4.4 0-9 2.2-9 6v2h18v-2c0-3.8-4.6-6-9-6z" />
                  </svg>
                </span>
              )}
            </button>

            {menuOpen && (
              <div className="navbar__dropdown">
                {user ? (
                  <>
                    <div className="navbar__dropdown-user">
                      <strong>{user.name}</strong>
                      <span>{user.email}</span>
                    </div>
                    <button type="button" onClick={handleLogout}>
                      Log out
                    </button>
                  </>
                ) : (
                  <>
                    <button type="button" onClick={() => navigate('/signup')}>
                      Sign up
                    </button>
                    <button type="button" className="navbar__dropdown-primary" onClick={() => navigate('/login')}>
                      Log in
                    </button>
                  </>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
