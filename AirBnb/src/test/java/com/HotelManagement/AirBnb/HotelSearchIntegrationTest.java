package com.HotelManagement.AirBnb;

import com.HotelManagement.AirBnb.entity.Location;
import com.HotelManagement.AirBnb.repository.LocationRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guest hotel search, hotel details and room details (public API). Every test uses its own city so
 * hotels created by other tests in the shared database can't affect the results.
 */
class HotelSearchIntegrationTest extends ApiTestSupport {

    @Autowired
    LocationRepository locationRepository;

    static final LocalDate IN = TOMORROW;
    static final LocalDate OUT = TOMORROW.plusDays(2);

    ResultActions search(String city, String state, LocalDate in, LocalDate out, Object rooms, String... extra) throws Exception {
        var req = get("/api/v1/hotels/search").param("city", city);
        if (state != null) req.param("state", state);
        if (in != null) req.param("checkinDate", in.toString());
        if (out != null) req.param("checkoutDate", out.toString());
        if (rooms != null) req.param("numberOfRooms", String.valueOf(rooms));
        for (int i = 0; i + 1 < extra.length; i += 2) req.param(extra[i], extra[i + 1]);
        return mvc.perform(req); // no Authorization header: search is public
    }

    long roomOf(Account manager, long hotelId, String type, String price, int total, int capacity) throws Exception {
        String body = mvc.perform(auth(multipart("/api/v1/admin/hotels/" + hotelId + "/rooms")
                        .file(data(roomJson(type, price, total, capacity))), manager))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    void setActive(Account manager, long hotelId, boolean active) throws Exception {
        mvc.perform(auth(patch("/api/v1/admin/hotels/" + hotelId + "/status"), manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":" + active + "}")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- search

    @Test
    void searchesByCityAndReturnsPublicSummaryWithPricing() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        long h1 = newHotel(manager, "Maharashtra", "Wai");
        roomOf(manager, h1, "Standard", "2000", 3, 2);
        roomOf(manager, h1, "Suite", "5000", 1, 4);
        long h2 = newHotel(manager, "Maharashtra", "Wai");
        roomOf(manager, h2, "Standard", "2500", 3, 2);
        long elsewhere = newHotel(manager, "Maharashtra", "Wardha");
        roomOf(manager, elsewhere, "Standard", "1000", 3, 2);

        String body = search("wai", null, IN, OUT, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].city").value("Wai"))
                .andExpect(jsonPath("$.content[0].state").value("Maharashtra"))
                .andExpect(jsonPath("$.content[0].amenities").isArray())
                .andExpect(jsonPath("$.content[0].contactInfo").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        List<Integer> ids = JsonPath.read(body, "$.content[*].id");
        assertThat(ids).extracting(Integer::longValue).containsExactlyInAnyOrder(h1, h2);
        // Hotel 1: Suite only has 1 room, so for 2 rooms only "Standard" qualifies: 2000/night, 2 nights x 2 rooms.
        int idx = ids.get(0).longValue() == h1 ? 0 : 1;
        assertThat(((Number) JsonPath.read(body, "$.content[" + idx + "].availableRoomTypes")).intValue()).isEqualTo(1);
        assertThat(((Number) JsonPath.read(body, "$.content[" + idx + "].lowestNightlyPrice")).doubleValue()).isEqualTo(2000.0);
        assertThat(((Number) JsonPath.read(body, "$.content[" + idx + "].lowestTotalPrice")).doubleValue()).isEqualTo(8000.0);
    }

    @Test
    void rejectsInvalidDates() throws Exception {
        search("Wai", null, IN, IN, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("after the check-in")));
        search("Wai", null, OUT, IN, 1).andExpect(status().isBadRequest());
        search("Wai", null, LocalDate.now().minusDays(1), IN, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("past")));
        search("Wai", null, IN, IN.plusDays(31), 1).andExpect(status().isBadRequest());
        search("Wai", null, null, OUT, 1).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/hotels/search").param("city", "Wai").param("checkinDate", "11/02/2026")
                .param("checkoutDate", OUT.toString()).param("numberOfRooms", "1")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidRoomCountsAndPaging() throws Exception {
        search("Wai", null, IN, OUT, 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Number of rooms")));
        search("Wai", null, IN, OUT, -1).andExpect(status().isBadRequest());
        search("Wai", null, IN, OUT, 11).andExpect(status().isBadRequest());
        search("Wai", null, IN, OUT, null).andExpect(status().isBadRequest());
        search("Wai", null, IN, OUT, "two").andExpect(status().isBadRequest());
        search("Wai", null, IN, OUT, 1, "size", "0").andExpect(status().isBadRequest());
        search("Wai", null, IN, OUT, 1, "size", "51").andExpect(status().isBadRequest());
        search("Wai", null, IN, OUT, 1, "page", "-1").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingUnknownAndAmbiguousLocations() throws Exception {
        mvc.perform(get("/api/v1/hotels/search").param("checkinDate", IN.toString())
                .param("checkoutDate", OUT.toString()).param("numberOfRooms", "1")).andExpect(status().isBadRequest());
        search(" ", null, IN, OUT, 1).andExpect(status().isBadRequest());
        search("Atlantis", null, IN, OUT, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("not a known city")));
        search("Yanam", "Kerala", IN, OUT, 1).andExpect(status().isBadRequest());

        String city = "Dupcity" + UUID.randomUUID().toString().substring(0, 6);
        locationRepository.save(new Location(city, "Gujarat"));
        locationRepository.save(new Location(city, "Bihar"));
        search(city, null, IN, OUT, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Bihar, Gujarat")));
        search(city, "Gujarat", IN, OUT, 1).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void returnsEmptyPageWhenNothingMatches() throws Exception {
        search("Zira", "Punjab", IN, OUT, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void excludesInactiveDeletedAndRoomlessHotels() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        long visible = newHotel(manager, "Puducherry", "Yanam");
        roomOf(manager, visible, "Standard", "1500", 2, 2);
        long inactive = newHotel(manager, "Puducherry", "Yanam");
        roomOf(manager, inactive, "Standard", "1500", 2, 2);
        setActive(manager, inactive, false);
        long deleted = newHotel(manager, "Puducherry", "Yanam");
        roomOf(manager, deleted, "Standard", "1500", 2, 2);
        mvc.perform(auth(delete("/api/v1/admin/hotels/" + deleted), manager)).andExpect(status().isNoContent());
        newHotel(manager, "Puducherry", "Yanam"); // no room types -> nothing to book

        search("Yanam", null, IN, OUT, 1)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(visible));
    }

    @Test
    void appliesInventoryBookingsCancellationsAndClosedDates() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        Account guest = signup("GUEST");
        long hotel = newHotel(manager, "Bihar", "Gaya");
        long room = roomOf(manager, hotel, "Standard", "1000", 2, 2);

        // Multiple rooms: 2 fit, 3 don't.
        search("Gaya", null, IN, OUT, 2).andExpect(jsonPath("$.content", hasSize(1)));
        search("Gaya", null, IN, OUT, 3).andExpect(jsonPath("$.content", hasSize(0)));

        // A reserved booking of both rooms on the 2nd night blocks overlapping stays only.
        String booked = book(guest, hotel, room, IN.plusDays(1), IN.plusDays(2), 2)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long bookingId = ((Number) JsonPath.read(booked, "$.id")).longValue();
        search("Gaya", null, IN, OUT, 1).andExpect(jsonPath("$.content", hasSize(0)));
        search("Gaya", null, IN, IN.plusDays(1), 1).andExpect(jsonPath("$.content", hasSize(1))); // check-out day is free
        search("Gaya", null, IN.plusDays(2), IN.plusDays(3), 2).andExpect(jsonPath("$.content", hasSize(1)));

        // Accepted bookings still hold inventory.
        mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/accept"), manager)).andExpect(status().isOk());
        search("Gaya", null, IN, OUT, 1).andExpect(jsonPath("$.content", hasSize(0)));

        // Cancelling releases it.
        mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/cancel"), manager)).andExpect(status().isOk());
        search("Gaya", null, IN, OUT, 2).andExpect(jsonPath("$.content", hasSize(1)));

        // A closed night makes the stay unavailable.
        mvc.perform(auth(patch("/api/v1/admin/hotels/" + hotel + "/rooms/" + room + "/inventory"), manager)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"%s\",\"endDate\":\"%s\",\"closed\":true}".formatted(IN, IN))).andExpect(status().isOk());
        search("Gaya", null, IN, OUT, 1).andExpect(jsonPath("$.content", hasSize(0)));
        search("Gaya", null, IN.plusDays(1), OUT, 1).andExpect(jsonPath("$.content", hasSize(1)));

        // Beyond the 365-day inventory horizon nothing is on sale.
        search("Gaya", null, LocalDate.now().plusDays(400), LocalDate.now().plusDays(401), 1)
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void paginatesResults() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        for (int i = 0; i < 3; i++) {
            roomOf(manager, newHotel(manager, "Haryana", "Jind"), "Standard", "1200", 2, 2);
        }
        String p0 = search("Jind", null, IN, OUT, 1, "size", "2", "page", "0")
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andReturn().getResponse().getContentAsString();
        String p1 = search("Jind", null, IN, OUT, 1, "size", "2", "page", "1")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        Set<Object> seen = new HashSet<>(JsonPath.<List<Object>>read(p0, "$.content[*].id"));
        seen.addAll(JsonPath.<List<Object>>read(p1, "$.content[*].id"));
        assertThat(seen).hasSize(3);
    }

    @Test
    void bookingRevalidatesAvailabilityAfterSearch() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        Account first = signup("GUEST");
        Account second = signup("GUEST");
        long hotel = newHotel(manager, "Bihar", "Buxar");
        long room = roomOf(manager, hotel, "Standard", "1000", 1, 2);

        search("Buxar", null, IN, OUT, 1).andExpect(jsonPath("$.content", hasSize(1)));
        book(first, hotel, room, IN, OUT, 1).andExpect(status().isCreated());
        // The second guest saw the room as available, but the booking re-checks and refuses.
        book(second, hotel, room, IN, OUT, 1).andExpect(status().isConflict());
        search("Buxar", null, IN, OUT, 1).andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ---------------------------------------------------------------- hotel & room details

    @Test
    void hotelDetailsShowPublicInfoAndRoomAvailability() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        Account guest = signup("GUEST");
        long hotel = newHotel(manager, "Haryana", "Hansi");
        long small = roomOf(manager, hotel, "Single", "900", 1, 1);
        long big = roomOf(manager, hotel, "Family", "3000", 4, 4);
        book(guest, hotel, small, IN, OUT, 1).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/hotels/" + hotel).param("checkinDate", IN.toString())
                        .param("checkoutDate", OUT.toString()).param("numberOfRooms", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Hansi"))
                .andExpect(jsonPath("$.address").value("12 MG Road"))
                .andExpect(jsonPath("$.contactInfo").doesNotExist())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.stay.nights").value(2))
                .andExpect(jsonPath("$.roomTypes", hasSize(2)))
                .andExpect(jsonPath("$.roomTypes[?(@.id == %d)].availability.available".formatted(small)).value(false))
                .andExpect(jsonPath("$.roomTypes[?(@.id == %d)].availability.availableRooms".formatted(small)).value(0))
                .andExpect(jsonPath("$.roomTypes[?(@.id == %d)].availability.available".formatted(big)).value(true))
                .andExpect(jsonPath("$.roomTypes[?(@.id == %d)].availability.totalPrice".formatted(big)).value(12000.0));

        mvc.perform(get("/api/v1/hotels/" + hotel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stay").doesNotExist())
                .andExpect(jsonPath("$.roomTypes[0].availability").doesNotExist());
    }

    @Test
    void hotelDetailsHideMissingInactiveAndDeletedHotels() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        long inactive = newHotel(manager, "Tamil Nadu", "Erode");
        setActive(manager, inactive, false);
        long deleted = newHotel(manager, "Tamil Nadu", "Erode");
        mvc.perform(auth(delete("/api/v1/admin/hotels/" + deleted), manager)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/hotels/" + inactive)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/hotels/" + deleted)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/hotels/99999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/hotels/not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/hotels/" + deleted).param("checkinDate", OUT.toString())
                .param("checkoutDate", IN.toString())).andExpect(status().isBadRequest());
    }

    @Test
    void roomDetailsShowNightlyAvailabilityAndStayInsideTheirHotel() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        long hotel = newHotel(manager, "Manipur", "Imphal");
        long room = roomOf(manager, hotel, "Deluxe", "2000", 3, 2);
        long otherHotel = newHotel(manager, "Manipur", "Imphal");
        long otherRoom = roomOf(manager, otherHotel, "Deluxe", "2000", 3, 2);
        mvc.perform(auth(patch("/api/v1/admin/hotels/" + hotel + "/rooms/" + room + "/inventory"), manager)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"%s\",\"endDate\":\"%s\",\"surgeFactor\":1.5}".formatted(IN, IN))).andExpect(status().isOk());

        mvc.perform(get("/api/v1/hotels/" + hotel + "/rooms/" + room).param("checkinDate", IN.toString())
                        .param("checkoutDate", OUT.toString()).param("numberOfRooms", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("Deluxe"))
                .andExpect(jsonPath("$.capacity").value(2))
                .andExpect(jsonPath("$.hotelId").value(hotel))
                .andExpect(jsonPath("$.nights", hasSize(2)))
                .andExpect(jsonPath("$.nights[0].price").value(3000.0))
                .andExpect(jsonPath("$.nights[1].price").value(2000.0))
                .andExpect(jsonPath("$.availability.available").value(true))
                .andExpect(jsonPath("$.availability.totalPrice").value(5000.0))
                .andExpect(jsonPath("$.availability.averageNightlyPrice").value(2500.0));

        mvc.perform(get("/api/v1/hotels/" + hotel + "/rooms/" + otherRoom)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/hotels/" + otherHotel + "/rooms/" + room)).andExpect(status().isNotFound());
    }

    @Test
    void photosArePublicOnlyForVisibleHotels() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        String created = createHotel(manager, hotelJson("Photo Inn " + UUID.randomUUID(), "Mizoram", "Aizawl", ""), photo("a.png"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long hotel = ((Number) JsonPath.read(created, "$.id")).longValue();
        long photoId = ((Number) JsonPath.read(created, "$.photos[0].id")).longValue();
        roomOf(manager, hotel, "Standard", "1000", 1, 2);

        String result = search("Aizawl", null, IN, OUT, 1).andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(result, "$.content[0].photoUrls[0]");
        assertThat(url).isEqualTo("/api/v1/hotels/" + hotel + "/photos/" + photoId);
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png()));

        setActive(manager, hotel, false);
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void managerApisStillRequireTheManagerRole() throws Exception {
        Account guest = signup("GUEST");
        mvc.perform(get("/api/v1/admin/hotels")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/v1/admin/hotels"), guest)).andExpect(status().isForbidden());
    }
}
