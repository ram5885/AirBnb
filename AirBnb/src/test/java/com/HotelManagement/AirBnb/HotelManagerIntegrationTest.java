package com.HotelManagement.AirBnb;

import com.HotelManagement.AirBnb.dto.booking.CreateBookingRequest;
import com.HotelManagement.AirBnb.entity.User;
import com.HotelManagement.AirBnb.exception.ConflictException;
import com.HotelManagement.AirBnb.repository.UserRepository;
import com.HotelManagement.AirBnb.service.BookingService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests of the hotel manager module through the real HTTP layer, security filter
 * chain and PostgreSQL database (same setup as AirBnbApplicationTests).
 */
class HotelManagerIntegrationTest extends ApiTestSupport {

    // ---------------------------------------------------------------- hotels

    @Nested
    class Hotels {

        @Test
        void createsHotelWithLocationContactAmenitiesPhotosAndTimestamps() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            String body = createHotel(manager, hotelJson("Sea Breeze", "kerala", " Alappuzha ", ""), photo("a.png"), photo("b.png"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Sea Breeze"))
                    .andExpect(jsonPath("$.city").value("Alappuzha"))
                    .andExpect(jsonPath("$.state").value("Kerala"))
                    .andExpect(jsonPath("$.contactInfo.contactPerson").value("Asha Rao"))
                    .andExpect(jsonPath("$.contactInfo.email").value("front.desk@hotel.com"))
                    .andExpect(jsonPath("$.amenities", hasSize(2)))
                    .andExpect(jsonPath("$.active").value(true))
                    .andExpect(jsonPath("$.photos", hasSize(2)))
                    .andExpect(jsonPath("$.photos[0].contentType").value("image/png"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();

            String photoUrl = JsonPath.read(body, "$.photos[1].url");
            mvc.perform(auth(get(photoUrl), manager))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("image/png"))
                    .andExpect(content().bytes(png()));
        }

        @Test
        void clientCannotSetOwnerIdOrTimestamps() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            createHotel(manager, hotelJson("Mass Assign", "Kerala", "Alappuzha",
                    ",\"owner\":{\"id\":999},\"createdAt\":\"2000-01-01T00:00:00\",\"hotelId\":42"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.createdAt", not(containsString("2000"))));
        }

        @Test
        void rejectsMissingAndInvalidFields() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            createHotel(manager, """
                    {"name":"","state":"Kerala","city":"Alappuzha","address":"","contactPerson":"X",
                     "phoneNumber":"12345","email":"not-an-email","active":true}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.name").exists())
                    .andExpect(jsonPath("$.fieldErrors.address").exists())
                    .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());
        }

        @Test
        void rejectsCityThatIsNotInTheSelectedState() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            createHotel(manager, hotelJson("Wrong State", "Punjab", "Alappuzha", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(containsString("not a valid city")));
            createHotel(manager, hotelJson("Unknown City", "Kerala", "Atlantis", ""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void sameCityNameInDifferentStatesResolvesToTheRightLocation() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            createHotel(manager, hotelJson("Bilaspur Inn", "Chhattisgarh", "Bilaspur", ""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.state").value("Chhattisgarh"));
        }

        @Test
        void rejectsInvalidUploads() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            MockMultipartFile fakeImage = new MockMultipartFile("photos", "evil.png", "image/png", "<script>".getBytes());
            createHotel(manager, hotelJson("Bad Upload", "Kerala", "Alappuzha", ""), fakeImage)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(containsString("not a JPEG, PNG or WebP")));

            MockMultipartFile empty = new MockMultipartFile("photos", "empty.png", "image/png", new byte[0]);
            createHotel(manager, hotelJson("Empty Upload", "Kerala", "Alappuzha", ""), empty)
                    .andExpect(status().isBadRequest());

            byte[] truncated = new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};
            createHotel(manager, hotelJson("Corrupt Upload", "Kerala", "Alappuzha", ""),
                    new MockMultipartFile("photos", "broken.png", "image/png", truncated))
                    .andExpect(status().isBadRequest());

            MockMultipartFile[] tooMany = new MockMultipartFile[11];
            for (int i = 0; i < tooMany.length; i++) {
                tooMany[i] = photo("p" + i + ".png");
            }
            createHotel(manager, hotelJson("Too Many", "Kerala", "Alappuzha", ""), tooMany)
                    .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsUnknownAmenities() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            createHotel(manager, hotelJson("Amenity", "Kerala", "Alappuzha", "").replace("\"WIFI\",", "\"JACUZZI_ON_MOON\","))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(containsString("Unknown amenity")));
        }

        @Test
        void togglesActiveStatus() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            long hotelId = newHotel(manager);
            mvc.perform(auth(patch("/api/v1/admin/hotels/" + hotelId + "/status"), manager)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));
            mvc.perform(auth(get("/api/v1/admin/hotels").param("active", "false"), manager))
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].active").value(false));
        }

        @Test
        void updatesHotelAndRefreshesUpdatedAtOnly() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            String created = createHotel(manager, hotelJson("Old Name", "Kerala", "Alappuzha", ""), photo("a.png"), photo("b.png"))
                    .andReturn().getResponse().getContentAsString();
            long hotelId = ((Number) JsonPath.read(created, "$.id")).longValue();
            long firstPhoto = ((Number) JsonPath.read(created, "$.photos[0].id")).longValue();
            String createdAt = JsonPath.read(created, "$.createdAt");
            String updatedAt = JsonPath.read(created, "$.updatedAt");
            Thread.sleep(20);

            String updated = mvc.perform(auth(multipart(HttpMethod.PUT, "/api/v1/admin/hotels/" + hotelId)
                            .file(data(hotelJson("New Name", "Telangana", "Hyderabad",
                                    ",\"removedPhotoIds\":[" + firstPhoto + "]")))
                            .file(photo("c.png")), manager))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("New Name"))
                    .andExpect(jsonPath("$.city").value("Hyderabad"))
                    .andExpect(jsonPath("$.photos", hasSize(2)))
                    .andReturn().getResponse().getContentAsString();
            assertThat((String) JsonPath.read(updated, "$.createdAt")).isEqualTo(createdAt);
            assertThat((String) JsonPath.read(updated, "$.updatedAt")).isGreaterThan(updatedAt);
        }

        @Test
        void managersCannotAccessOtherManagersHotels() throws Exception {
            Account owner = signup("HOTEL_MANAGER");
            Account other = signup("HOTEL_MANAGER");
            long hotelId = newHotel(owner);

            mvc.perform(auth(get("/api/v1/admin/hotels/" + hotelId), other)).andExpect(status().isNotFound());
            mvc.perform(auth(multipart(HttpMethod.PUT, "/api/v1/admin/hotels/" + hotelId)
                    .file(data(hotelJson("Hijack", "Kerala", "Alappuzha", ""))), other)).andExpect(status().isNotFound());
            mvc.perform(auth(delete("/api/v1/admin/hotels/" + hotelId), other)).andExpect(status().isNotFound());
            mvc.perform(auth(get("/api/v1/admin/hotels"), other)).andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        void managerEndpointsRequireTheManagerRole() throws Exception {
            Account guest = signup("GUEST");
            mvc.perform(get("/api/v1/admin/hotels")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/admin/hotels").header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(auth(get("/api/v1/admin/hotels"), guest)).andExpect(status().isForbidden());
            createHotel(guest, hotelJson("Guest Hotel", "Kerala", "Alappuzha", "")).andExpect(status().isForbidden());
        }

        @Test
        void deleteIsSoftAndKeepsBookingHistory() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 2);
            long bookingId = newBooking(guest, hotelId, roomId, 1);

            // Upcoming booking still holds inventory -> refuse.
            mvc.perform(auth(delete("/api/v1/admin/hotels/" + hotelId), manager))
                    .andExpect(status().isConflict());

            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/cancel"), manager)).andExpect(status().isOk());
            mvc.perform(auth(delete("/api/v1/admin/hotels/" + hotelId), manager)).andExpect(status().isNoContent());

            mvc.perform(auth(get("/api/v1/admin/hotels/" + hotelId), manager)).andExpect(status().isNotFound());
            mvc.perform(auth(get("/api/v1/admin/bookings/" + bookingId), manager))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hotelDeleted").value(true))
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
            book(guest, hotelId, roomId, TOMORROW, TOMORROW.plusDays(1), 1).andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------- room types

    @Nested
    class RoomTypes {

        @Test
        void createsRoomTypeUnderHotelWithInventory() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            long hotelId = newHotel(manager);
            mvc.perform(auth(multipart("/api/v1/admin/hotels/" + hotelId + "/rooms")
                            .file(data(roomJson("Deluxe King", "3499.50", 5, 3))).file(photo("room.png")), manager))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.hotelId").value(hotelId))
                    .andExpect(jsonPath("$.type").value("Deluxe King"))
                    .andExpect(jsonPath("$.basePrice").value(3499.50))
                    .andExpect(jsonPath("$.photos", hasSize(1)));

            String rooms = mvc.perform(auth(get("/api/v1/admin/hotels/" + hotelId + "/rooms"), manager))
                    .andExpect(jsonPath("$", hasSize(1))).andReturn().getResponse().getContentAsString();
            long roomId = ((Number) JsonPath.read(rooms, "$[0].id")).longValue();

            inventory(manager, hotelId, roomId, LocalDate.now(), LocalDate.now().plusDays(364))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(365)))
                    .andExpect(jsonPath("$[0].totalCount").value(5))
                    .andExpect(jsonPath("$[0].available").value(5))
                    .andExpect(jsonPath("$[0].price").value(3499.50));
        }

        @Test
        void rejectsInvalidRoomTypeValues() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            long hotelId = newHotel(manager);
            mvc.perform(auth(multipart("/api/v1/admin/hotels/" + hotelId + "/rooms")
                            .file(data(roomJson("", "0", 0, 0))), manager))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.type").exists())
                    .andExpect(jsonPath("$.fieldErrors.basePrice").exists())
                    .andExpect(jsonPath("$.fieldErrors.totalCount").exists())
                    .andExpect(jsonPath("$.fieldErrors.capacity").exists());

            newRoom(manager, hotelId, 1);
            mvc.perform(auth(multipart("/api/v1/admin/hotels/" + hotelId + "/rooms")
                    .file(data(roomJson("Suite", "100", 1, 1))), manager)).andExpect(status().isCreated());
            mvc.perform(auth(multipart("/api/v1/admin/hotels/" + hotelId + "/rooms")
                    .file(data(roomJson("suite", "100", 1, 1))), manager)).andExpect(status().isConflict());
        }

        @Test
        void roomTypesAreScopedToTheirHotelAndOwner() throws Exception {
            Account owner = signup("HOTEL_MANAGER");
            Account other = signup("HOTEL_MANAGER");
            long hotelA = newHotel(owner);
            long hotelB = newHotel(owner);
            long roomA = newRoom(owner, hotelA, 2);

            mvc.perform(auth(get("/api/v1/admin/hotels/" + hotelB + "/rooms/" + roomA), owner))
                    .andExpect(status().isNotFound());
            mvc.perform(auth(get("/api/v1/admin/hotels/" + hotelA + "/rooms/" + roomA), other))
                    .andExpect(status().isNotFound());
            mvc.perform(auth(multipart(HttpMethod.PUT, "/api/v1/admin/hotels/" + hotelA + "/rooms/" + roomA)
                    .file(data(roomJson("Hijacked", "1", 1, 1))), other)).andExpect(status().isNotFound());
            mvc.perform(auth(delete("/api/v1/admin/hotels/" + hotelA + "/rooms/" + roomA), other))
                    .andExpect(status().isNotFound());
            mvc.perform(auth(multipart("/api/v1/admin/hotels/" + hotelA + "/rooms")
                    .file(data(roomJson("Intruder", "1", 1, 1))), other)).andExpect(status().isNotFound());
        }

        @Test
        void updatesRoomTypeAndSyncsFutureInventory() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 3);
            newBooking(guest, hotelId, roomId, 2);
            String url = "/api/v1/admin/hotels/" + hotelId + "/rooms/" + roomId;

            // Can't drop below the 2 rooms already booked.
            mvc.perform(auth(multipart(HttpMethod.PUT, url).file(data(roomJson("Renamed", "4000", 1, 4))), manager))
                    .andExpect(status().isConflict());

            mvc.perform(auth(multipart(HttpMethod.PUT, url).file(data(roomJson("Renamed", "4000", 6, 4))), manager))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("Renamed"))
                    .andExpect(jsonPath("$.totalCount").value(6));
            inventory(manager, hotelId, roomId, TOMORROW, TOMORROW)
                    .andExpect(jsonPath("$[0].totalCount").value(6))
                    .andExpect(jsonPath("$[0].bookedCount").value(2))
                    .andExpect(jsonPath("$[0].price").value(4000.0));
        }

        @Test
        void deletesRoomTypeOnlyWithoutUpcomingBookings() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 2);
            long bookingId = newBooking(guest, hotelId, roomId, 1);
            String url = "/api/v1/admin/hotels/" + hotelId + "/rooms/" + roomId;

            mvc.perform(auth(delete(url), manager)).andExpect(status().isConflict());
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/cancel"), manager)).andExpect(status().isOk());
            mvc.perform(auth(delete(url), manager)).andExpect(status().isNoContent());
            mvc.perform(auth(get(url), manager)).andExpect(status().isNotFound());
            mvc.perform(auth(get("/api/v1/admin/bookings/" + bookingId), manager))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.roomId").value(roomId));
        }
    }

    // ---------------------------------------------------------------- bookings & inventory

    @Nested
    class Bookings {

        @Test
        void managerSeesOnlyBookingsOfTheirHotels() throws Exception {
            Account m1 = signup("HOTEL_MANAGER");
            Account m2 = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long h1 = newHotel(m1);
            long h2 = newHotel(m2);
            long b1 = newBooking(guest, h1, newRoom(m1, h1, 2), 1);
            newBooking(guest, h2, newRoom(m2, h2, 2), 1);

            mvc.perform(auth(get("/api/v1/admin/bookings"), m1))
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(b1))
                    .andExpect(jsonPath("$.content[0].status").value("RESERVED"));
            mvc.perform(auth(get("/api/v1/admin/bookings/" + b1), m2)).andExpect(status().isNotFound());
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + b1 + "/accept"), m2)).andExpect(status().isNotFound());
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + b1 + "/cancel"), m2)).andExpect(status().isNotFound());

            mvc.perform(auth(get("/api/v1/admin/bookings").param("status", "CANCELLED"), m1))
                    .andExpect(jsonPath("$.content", hasSize(0)));
            mvc.perform(auth(get("/api/v1/admin/bookings").param("startDate", TOMORROW.plusDays(10).toString()), m1))
                    .andExpect(jsonPath("$.content", hasSize(0)));
            mvc.perform(auth(get("/api/v1/admin/bookings").param("hotelId", String.valueOf(h1))
                            .param("startDate", TOMORROW.toString()).param("endDate", TOMORROW.toString()), m1))
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        void acceptsReservedBookingOnce() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long bookingId = newBooking(guest, hotelId, newRoom(manager, hotelId, 2), 1);

            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/accept"), manager))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/accept"), manager))
                    .andExpect(status().isConflict());
        }

        @Test
        void cancelReleasesInventoryExactlyOnce() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 3);
            long bookingId = newBooking(guest, hotelId, roomId, 2);
            newBooking(guest, hotelId, roomId, 1);
            inventory(manager, hotelId, roomId, TOMORROW, TOMORROW.plusDays(1))
                    .andExpect(jsonPath("$[0].bookedCount").value(3))
                    .andExpect(jsonPath("$[1].bookedCount").value(3));

            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/accept"), manager)).andExpect(status().isOk());
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/cancel"), manager))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
            inventory(manager, hotelId, roomId, TOMORROW, TOMORROW.plusDays(2))
                    .andExpect(jsonPath("$[0].bookedCount").value(1))
                    .andExpect(jsonPath("$[1].bookedCount").value(1))
                    .andExpect(jsonPath("$[2].bookedCount").value(0)); // check-out day was never booked

            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/cancel"), manager))
                    .andExpect(status().isConflict());
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/accept"), manager))
                    .andExpect(status().isConflict());
            inventory(manager, hotelId, roomId, TOMORROW, TOMORROW).andExpect(jsonPath("$[0].bookedCount").value(1));
        }

        @Test
        void preventsOverbooking() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 2);
            book(guest, hotelId, roomId, TOMORROW, TOMORROW.plusDays(3), 3).andExpect(status().isConflict());
            book(guest, hotelId, roomId, TOMORROW, TOMORROW.plusDays(3), 2).andExpect(status().isCreated());
            book(guest, hotelId, roomId, TOMORROW.plusDays(2), TOMORROW.plusDays(4), 1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value(containsString("Not enough rooms")));
            book(guest, hotelId, roomId, TOMORROW.plusDays(3), TOMORROW.plusDays(4), 2).andExpect(status().isCreated());
        }

        @Test
        void rejectsInvalidDates() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 2);
            book(guest, hotelId, roomId, TOMORROW, TOMORROW, 1).andExpect(status().isBadRequest());
            book(guest, hotelId, roomId, TOMORROW.plusDays(2), TOMORROW, 1).andExpect(status().isBadRequest());
            book(guest, hotelId, roomId, LocalDate.now().minusDays(1), TOMORROW, 1).andExpect(status().isBadRequest());
            book(guest, hotelId, roomId, TOMORROW, TOMORROW.plusDays(31), 1).andExpect(status().isBadRequest());
            book(guest, hotelId, roomId, TOMORROW.plusDays(400), TOMORROW.plusDays(401), 1).andExpect(status().isConflict());
        }

        @Test
        void inactiveHotelsAndClosedDatesCannotBeBooked() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            Account guest = signup("GUEST");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 2);

            mvc.perform(auth(patch("/api/v1/admin/hotels/" + hotelId + "/rooms/" + roomId + "/inventory"), manager)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"startDate\":\"%s\",\"endDate\":\"%s\",\"closed\":true,\"surgeFactor\":1.5}"
                                    .formatted(TOMORROW, TOMORROW)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.updatedDays").value(1));
            inventory(manager, hotelId, roomId, TOMORROW, TOMORROW)
                    .andExpect(jsonPath("$[0].closed").value(true))
                    .andExpect(jsonPath("$[0].price").value(3750.0));
            book(guest, hotelId, roomId, TOMORROW, TOMORROW.plusDays(1), 1).andExpect(status().isConflict());

            mvc.perform(auth(patch("/api/v1/admin/hotels/" + hotelId + "/status"), manager)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
            book(guest, hotelId, roomId, TOMORROW.plusDays(1), TOMORROW.plusDays(2), 1)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value(containsString("not accepting bookings")));
        }

        @Test
        void inventoryUpdatesRejectPastDatesAndBadSurge() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 2);
            String url = "/api/v1/admin/hotels/" + hotelId + "/rooms/" + roomId + "/inventory";
            mvc.perform(auth(patch(url), manager).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"startDate\":\"%s\",\"endDate\":\"%s\",\"closed\":true}"
                            .formatted(LocalDate.now().minusDays(1), TOMORROW))).andExpect(status().isBadRequest());
            mvc.perform(auth(patch(url), manager).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"startDate\":\"%s\",\"endDate\":\"%s\",\"surgeFactor\":9}"
                            .formatted(TOMORROW, TOMORROW))).andExpect(status().isBadRequest());
        }

        @Test
        void concurrentBookingsNeverOversell() throws Exception {
            Account manager = signup("HOTEL_MANAGER");
            long hotelId = newHotel(manager);
            long roomId = newRoom(manager, hotelId, 3);
            List<User> guests = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                guests.add(userRepository.findUserById(signup("GUEST").id()).orElseThrow());
            }

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(10);
            List<Future<Boolean>> results = new ArrayList<>();
            for (User g : guests) {
                Callable<Boolean> task = () -> {
                    start.await();
                    try {
                        bookingService.create(g, new CreateBookingRequest(hotelId, roomId, TOMORROW, TOMORROW.plusDays(2), 1, null));
                        return true;
                    } catch (ConflictException e) {
                        return false;
                    }
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            int succeeded = 0;
            for (Future<Boolean> f : results) {
                if (f.get()) {
                    succeeded++;
                }
            }
            pool.shutdown();

            assertThat(succeeded).isEqualTo(3);
            inventory(manager, hotelId, roomId, TOMORROW, TOMORROW.plusDays(1))
                    .andExpect(jsonPath("$[0].bookedCount").value(3))
                    .andExpect(jsonPath("$[1].bookedCount").value(3));
        }
    }

    // ---------------------------------------------------------------- regression

    @Nested
    class Regression {

        @Test
        void loginStillWorksAndNowReturnsAToken() throws Exception {
            Account account = signup("GUEST");
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"password\":\"secret123\"}".formatted(account.email())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(account.email()))
                    .andExpect(jsonPath("$.roles[0]").value("GUEST"))
                    .andExpect(jsonPath("$.message").value("Login successful"))
                    .andExpect(jsonPath("$.token").isNotEmpty());
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"password\":\"wrong\"}".formatted(account.email())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid email or password"));
            mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Dup\",\"email\":\"%s\",\"password\":\"secret123\",\"role\":\"GUEST\"}"
                                    .formatted(account.email())))
                    .andExpect(status().isConflict());
        }

        @Test
        void publicEndpointsStayPublic() throws Exception {
            mvc.perform(get("/api/locations/states")).andExpect(status().isOk());
            mvc.perform(get("/api/locations").param("state", "Kerala")).andExpect(status().isOk());
            mvc.perform(get("/api/v1/amenities")).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].amenities[0].code").value("WIFI"));
            mvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
