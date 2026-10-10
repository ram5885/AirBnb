import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api.js';
import { useAuth } from '../../context/AuthContext.jsx';
import { Alert, Spinner, StatusBadge } from '../../components/manager/ui.jsx';
import { FlashMessage } from '../../components/manager/ManagerLayout.jsx';

export default function Dashboard() {
  const { user } = useAuth();
  const [stats, setStats] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.getDashboard().then(setStats).catch((e) => setError(e.message));
  }, []);

  return (
    <>
      <FlashMessage />
      <div className="m-page-header">
        <div>
          <h1>Welcome, {user?.name?.split(' ')[0]}</h1>
          <p className="m-muted">Manage your hotels, room types and bookings.</p>
        </div>
        <Link to="/manager/hotels/new" className="m-btn m-btn--primary">
          Create hotel
        </Link>
      </div>

      <Alert>{error}</Alert>
      {!stats && !error && <Spinner />}
      {stats && (
        <>
          <div className="m-stats">
            <Link to="/manager/hotels" className="m-stat">
              <span className="m-stat__value">{stats.totalHotels}</span>
              <span className="m-stat__label">Hotels</span>
            </Link>
            <Link to="/manager/hotels?active=true" className="m-stat">
              <span className="m-stat__value">{stats.activeHotels}</span>
              <span className="m-stat__label">Active hotels</span>
            </Link>
            {Object.entries(stats.bookingsByStatus).map(([status, count]) => (
              <Link key={status} to={`/manager/bookings?status=${status}`} className="m-stat">
                <span className="m-stat__value">{count}</span>
                <span className="m-stat__label">
                  <StatusBadge status={status} /> bookings
                </span>
              </Link>
            ))}
          </div>

          <div className="m-card-grid m-card-grid--links">
            <Link to="/manager/hotels" className="m-card m-card--link">
              <h2>Manage hotels</h2>
              <p>Edit details, photos and amenities, activate or deactivate listings.</p>
            </Link>
            <Link to="/manager/bookings?status=RESERVED" className="m-card m-card--link">
              <h2>Review booking requests</h2>
              <p>Accept new reservations or cancel bookings.</p>
            </Link>
            <Link to="/manager/hotels/new" className="m-card m-card--link">
              <h2>List a new hotel</h2>
              <p>Add a hotel, then create its room types and manage availability.</p>
            </Link>
          </div>
        </>
      )}
    </>
  );
}
