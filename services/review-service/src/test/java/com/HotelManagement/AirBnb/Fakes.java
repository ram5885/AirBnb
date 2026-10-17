package com.HotelManagement.AirBnb;

import com.HotelManagement.AirBnb.client.AuthClient;
import com.HotelManagement.AirBnb.client.BookingClient;
import com.HotelManagement.AirBnb.client.CatalogClient;
import com.HotelManagement.AirBnb.shared.exception.ResourceNotFoundException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory stand-ins for the services review-service calls (their own tests cover the real endpoints). */
@TestConfiguration
public class Fakes {

    public static class FakeCatalog extends CatalogClient {
        final Set<Long> publicHotels = ConcurrentHashMap.newKeySet();
        final Map<Long, RatingUpdate> pushed = new ConcurrentHashMap<>();

        FakeCatalog() {
            super(RestClient.create());
        }

        @Override
        public PublicHotel requirePublicHotel(Long hotelId) {
            if (!publicHotels.contains(hotelId)) throw new ResourceNotFoundException("Hotel not found");
            return new PublicHotel(hotelId, "Hotel " + hotelId);
        }

        @Override
        public void pushRatings(List<RatingUpdate> updates) {
            updates.forEach(u -> pushed.put(u.hotelId(), u));
        }
    }

    public static class FakeBookings extends BookingClient {
        final Map<Long, StayView> stays = new ConcurrentHashMap<>();

        FakeBookings() {
            super(RestClient.create());
        }

        @Override
        public StayView requireUsersBooking(Long bookingId, Long userId) {
            StayView s = stays.get(bookingId);
            if (s == null || !s.userId().equals(userId)) throw new ResourceNotFoundException("Booking " + bookingId + " not found");
            return s;
        }

        @Override
        public List<StayView> confirmedStays(Long userId, Long hotelId) {
            return stays.values().stream()
                    .filter(s -> s.userId().equals(userId) && s.hotelId().equals(hotelId) && "CONFIRMED".equals(s.status()))
                    .sorted(Comparator.comparing(StayView::checkOutDate).thenComparing(StayView::bookingId).reversed())
                    .toList();
        }
    }

    public static class FakeAuth extends AuthClient {
        final Map<Long, UserView> users = new ConcurrentHashMap<>();

        FakeAuth() {
            super(RestClient.create());
        }

        @Override
        public Optional<UserView> findUser(Long id) {
            return Optional.ofNullable(users.get(id));
        }
    }

    @Bean
    @Primary
    FakeCatalog fakeCatalog() {
        return new FakeCatalog();
    }

    @Bean
    @Primary
    FakeBookings fakeBookings() {
        return new FakeBookings();
    }

    @Bean
    @Primary
    FakeAuth fakeAuth() {
        return new FakeAuth();
    }
}
