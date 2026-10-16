package com.HotelManagement.AirBnb;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Home page "Stays in {city}" sections (public): only hotels that can take the whole stay, grouped by
 * city, with the same availability rule and cards as search. Uses its own cities (Odisha) so other
 * tests' hotels don't affect it; asks for many sections so they can't crowd these cities out.
 */
class HotelSectionsIntegrationTest extends ApiTestSupport {

    static final LocalDate IN = TOMORROW;
    static final LocalDate OUT = TOMORROW.plusDays(2);

    ResultActions sections(LocalDate in, LocalDate out, Object rooms, String... extra) throws Exception {
        var req = get("/api/v1/hotels/sections");
        if (!List.of(extra).contains("maxCities")) req.param("maxCities", "50");
        if (in != null) req.param("checkinDate", in.toString());
        if (out != null) req.param("checkoutDate", out.toString());
        if (rooms != null) req.param("numberOfRooms", String.valueOf(rooms));
        for (int i = 0; i + 1 < extra.length; i += 2) req.param(extra[i], extra[i + 1]);
        return mvc.perform(req); // no Authorization header: public
    }

    List<Map<String, Object>> sectionList(int rooms, String... extra) throws Exception {
        String body = sections(IN, OUT, rooms, extra).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }

    static Map<String, Object> section(List<Map<String, Object>> list, String city) {
        return list.stream().filter(s -> city.equals(s.get("city"))).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> hotels(Map<String, Object> section) {
        return (List<Map<String, Object>>) section.get("hotels");
    }

    long searchCount(String city, int rooms) throws Exception {
        String body = mvc.perform(get("/api/v1/hotels/search").param("city", city).param("state", "Odisha")
                        .param("checkinDate", IN.toString()).param("checkoutDate", OUT.toString())
                        .param("numberOfRooms", String.valueOf(rooms)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.totalElements")).longValue();
    }

    @Test
    void groupsAvailableHotelsByCityWithTheSameRuleAsSearch() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        Account guest = signup("GUEST");

        // Cuttack: three available hotels; the cut keeps the first two by name but reports all three.
        long c1 = newHotel(manager, "Odisha", "Cuttack");
        newRoom(manager, c1, 2);
        long c2 = newHotel(manager, "Odisha", "Cuttack");
        newRoom(manager, c2, 2);
        long c3 = newHotel(manager, "Odisha", "Cuttack");
        newRoom(manager, c3, 2);

        // Balasore: its only room is booked for the second night of the stay, so it can't take the stay.
        long b = newHotel(manager, "Odisha", "Balasore");
        long bRoom = newRoom(manager, b, 1);
        book(guest, b, bRoom, IN.plusDays(1), OUT, 1).andExpect(status().isCreated());

        // Bhadrak: one room in total, so it fits 1 room but not 2.
        long single = newHotel(manager, "Odisha", "Bhadrak");
        newRoom(manager, single, 1);

        // Dhenkanal: available inventory but the hotel is inactive (not public).
        long inactive = newHotel(manager, "Odisha", "Dhenkanal");
        newRoom(manager, inactive, 3);
        mvc.perform(auth(patch("/api/v1/admin/hotels/" + inactive + "/status"), manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());

        List<Map<String, Object>> list = sectionList(1, "perCity", "2");
        Map<String, Object> cuttack = section(list, "Cuttack");
        assertThat(cuttack.get("state")).isEqualTo("Odisha");
        assertThat(((Number) cuttack.get("availableHotels")).longValue()).isEqualTo(3).isEqualTo(searchCount("Cuttack", 1));
        List<Map<String, Object>> cards = hotels(cuttack);
        assertThat(cards).hasSize(2);
        assertThat(cards).allSatisfy(h -> {
            assertThat(h.get("city")).isEqualTo("Cuttack");
            assertThat(((Number) h.get("lowestNightlyPrice")).doubleValue()).isEqualTo(2500.0);
            assertThat(((Number) h.get("lowestTotalPrice")).doubleValue()).isEqualTo(5000.0); // 2 nights x 1 room
        });
        assertThat((String) cards.get(0).get("name")).isLessThan((String) cards.get(1).get("name"));

        assertThat(section(list, "Balasore")).isNull();
        assertThat(searchCount("Balasore", 1)).isZero();
        assertThat(hotels(section(list, "Bhadrak"))).extracting(h -> ((Number) h.get("id")).longValue()).containsExactly(single);
        assertThat(section(list, "Dhenkanal")).isNull();

        // Two rooms: Bhadrak's single room is not enough; Cuttack's hotels have two each.
        List<Map<String, Object>> twoRooms = sectionList(2);
        assertThat(section(twoRooms, "Bhadrak")).isNull();
        assertThat(hotels(section(twoRooms, "Cuttack"))).hasSize(3);
        assertThat(((Number) hotels(section(twoRooms, "Cuttack")).getFirst().get("lowestTotalPrice")).doubleValue())
                .isEqualTo(10000.0); // 2 nights x 2 rooms
    }

    @Test
    void cancelledBookingFreesTheRoomAgain() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        Account guest = signup("GUEST");
        long hotel = newHotel(manager, "Odisha", "Jharsuguda");
        long room = newRoom(manager, hotel, 1);
        long booking = newBooking(guest, hotel, room, 1);
        assertThat(section(sectionList(1), "Jharsuguda")).isNull();

        mvc.perform(auth(patch("/api/v1/bookings/" + booking + "/cancel"), guest)).andExpect(status().isOk());
        assertThat(hotels(section(sectionList(1), "Jharsuguda"))).hasSize(1);
    }

    @Test
    void rejectsInvalidStayAndLimits() throws Exception {
        sections(null, null, null).andExpect(status().isBadRequest());
        sections(IN, IN, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Check-out date must be after the check-in date"));
        sections(LocalDate.now().minusDays(1), OUT, 1).andExpect(status().isBadRequest());
        sections(IN, OUT, 0).andExpect(status().isBadRequest());
        sections(IN, OUT, 1, "perCity", "0").andExpect(status().isBadRequest());
        sections(IN, OUT, 1, "maxCities", "51").andExpect(status().isBadRequest());
    }
}
