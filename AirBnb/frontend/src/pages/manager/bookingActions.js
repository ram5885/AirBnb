// Mirrors the backend transitions in ManagerBookingService.
export function canAccept(booking, today) {
  return booking.status === 'RESERVED' && booking.checkInDate >= today && !booking.hotelDeleted;
}

export function collectedAtHotel(booking) {
  return booking.paymentMethod === 'PAY_AT_HOTEL' && booking.paymentStatus === 'CONFIRMED';
}

// Money taken at the front desk is settled at the hotel, not cancelled/refunded online.
export function canCancel(booking, today) {
  return ['RESERVED', 'ACCEPTED', 'CONFIRMED'].includes(booking.status) && booking.checkOutDate > today && !collectedAtHotel(booking);
}

export function canMarkCollected(booking) {
  return booking.status === 'CONFIRMED' && booking.paymentMethod === 'PAY_AT_HOTEL' && booking.paymentStatus === 'PENDING';
}
