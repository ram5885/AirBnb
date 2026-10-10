import { Routes, Route, Navigate } from 'react-router-dom';
import Home from './pages/Home.jsx';
import Auth from './pages/Auth.jsx';
import { isHotelManager, useAuth } from './context/AuthContext.jsx';
import ManagerLayout from './components/manager/ManagerLayout.jsx';
import Dashboard from './pages/manager/Dashboard.jsx';
import ManageHotels from './pages/manager/ManageHotels.jsx';
import HotelForm from './pages/manager/HotelForm.jsx';
import HotelDetails from './pages/manager/HotelDetails.jsx';
import RoomTypeForm from './pages/manager/RoomTypeForm.jsx';
import InventoryPage from './pages/manager/InventoryPage.jsx';
import ManageBookings from './pages/manager/ManageBookings.jsx';
import BookingDetails from './pages/manager/BookingDetails.jsx';
import HotelPage from './pages/guest/HotelPage.jsx';
import RoomPage from './pages/guest/RoomPage.jsx';
import BookPage from './pages/bookings/BookPage.jsx';
import PaymentPage from './pages/bookings/PaymentPage.jsx';
import MyBookings from './pages/bookings/MyBookings.jsx';
import MyBookingDetails from './pages/bookings/MyBookingDetails.jsx';

function RequireAuth({ children }) {
  const { user, ready } = useAuth();
  if (!ready) return null;
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

// Hotel managers land on their dashboard; guests and other users see the regular home page.
function HomeRoute() {
  const { user } = useAuth();
  if (isHotelManager(user)) return <Navigate to="/manager" replace />;
  return <Home />;
}

// Booking and My Bookings are for guest accounts (the API enforces the GUEST role too).
function RequireGuest({ children }) {
  const { user, ready } = useAuth();
  if (!ready) return null;
  if (!user) return <Navigate to="/login" replace />;
  if (isHotelManager(user)) return <Navigate to="/manager" replace />;
  return children;
}

// UI-level guard only; the backend enforces the HOTEL_MANAGER role on /api/v1/admin/**.
function RequireManager({ children }) {
  const { user, ready } = useAuth();
  if (!ready) return null;
  if (!user) return <Navigate to="/login" replace />;
  if (!isHotelManager(user)) return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <RequireAuth>
            <HomeRoute />
          </RequireAuth>
        }
      />
      <Route
        path="/hotels/:hotelId"
        element={
          <RequireAuth>
            <HotelPage />
          </RequireAuth>
        }
      />
      <Route
        path="/hotels/:hotelId/rooms/:roomId"
        element={
          <RequireAuth>
            <RoomPage />
          </RequireAuth>
        }
      />
      <Route path="/book/:hotelId/:roomId" element={<RequireGuest><BookPage /></RequireGuest>} />
      <Route path="/my-bookings" element={<RequireGuest><MyBookings /></RequireGuest>} />
      <Route path="/my-bookings/:bookingId" element={<RequireGuest><MyBookingDetails /></RequireGuest>} />
      <Route path="/my-bookings/:bookingId/pay" element={<RequireGuest><PaymentPage /></RequireGuest>} />
      <Route path="/login" element={<Auth mode="login" />} />
      <Route path="/signup" element={<Auth mode="signup" />} />
      <Route
        path="/manager"
        element={
          <RequireManager>
            <ManagerLayout />
          </RequireManager>
        }
      >
        <Route index element={<Dashboard />} />
        <Route path="hotels" element={<ManageHotels />} />
        <Route path="hotels/new" element={<HotelForm />} />
        <Route path="hotels/:hotelId" element={<HotelDetails />} />
        <Route path="hotels/:hotelId/edit" element={<HotelForm />} />
        <Route path="hotels/:hotelId/rooms/new" element={<RoomTypeForm />} />
        <Route path="hotels/:hotelId/rooms/:roomId/edit" element={<RoomTypeForm />} />
        <Route path="hotels/:hotelId/rooms/:roomId/inventory" element={<InventoryPage />} />
        <Route path="bookings" element={<ManageBookings />} />
        <Route path="bookings/:bookingId" element={<BookingDetails />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
