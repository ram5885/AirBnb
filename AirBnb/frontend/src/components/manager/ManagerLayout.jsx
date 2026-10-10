import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import Navbar from '../Navbar.jsx';
import { Alert } from './ui.jsx';
import '../../styles/manager.css';

/** Shell for all /manager pages: site navbar, section tabs, and one-shot flash messages. */
export default function ManagerLayout() {
  return (
    <div className="manager">
      <Navbar />
      <nav className="manager__tabs" aria-label="Hotel manager">
        <div className="container manager__tabs-inner">
          <NavLink end to="/manager">
            Dashboard
          </NavLink>
          <NavLink to="/manager/hotels">Hotels</NavLink>
          <NavLink to="/manager/bookings">Bookings</NavLink>
        </div>
      </nav>
      <main className="container manager__main">
        <Outlet />
      </main>
    </div>
  );
}

/**
 * Shows a success message passed via navigate(path, { state: { flash } }) once,
 * then clears it so a refresh doesn't show it again.
 */
export function FlashMessage() {
  const location = useLocation();
  const navigate = useNavigate();
  const [message, setMessage] = useState(location.state?.flash || '');

  useEffect(() => {
    if (location.state?.flash) {
      setMessage(location.state.flash);
      navigate(location.pathname + location.search, { replace: true, state: null });
    }
  }, [location, navigate]);

  return (
    <Alert type="success" onDismiss={() => setMessage('')}>
      {message}
    </Alert>
  );
}
