package com.HotelManagement.AirBnb;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Explore stays by city" (public): cities with listed hotels, whatever their availability, and the
 * city-photo / hotel-photo / no-photo fallback. Uses its own cities so other tests' hotels don't affect it.
 */
class DestinationsIntegrationTest extends ApiTestSupport {

    List<Map<String, Object>> destinations() throws Exception {
        String body = mvc.perform(get("/api/locations/destinations")) // no Authorization header: public
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }

    static Map<String, Object> find(List<Map<String, Object>> list, String city) {
        return list.stream().filter(d -> city.equals(d.get("city"))).findFirst().orElse(null);
    }

    void setActive(Account manager, long hotelId, boolean active) throws Exception {
        mvc.perform(auth(patch("/api/v1/admin/hotels/" + hotelId + "/status"), manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":" + active + "}")).andExpect(status().isOk());
    }

    @Test
    void listsCitiesWithListedHotelsEvenWithoutAvailability() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        newRoom(manager, newHotel(manager, "Rajasthan", "Jodhpur"), 2);
        newRoom(manager, newHotel(manager, "Rajasthan", "Jodhpur"), 1);

        // Listed but fully closed for the coming month: still a destination (availability is per stay).
        long closed = newHotel(manager, "Rajasthan", "Bikaner");
        long closedRoom = newRoom(manager, closed, 2);
        mvc.perform(auth(patch("/api/v1/admin/hotels/" + closed + "/rooms/" + closedRoom + "/inventory"), manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"%s","endDate":"%s","closed":true}"""
                                .formatted(LocalDate.now(), LocalDate.now().plusDays(30))))
                .andExpect(status().isOk());

        newHotel(manager, "Rajasthan", "Barmer"); // no room types: nothing to book, so not listed
        long inactive = newHotel(manager, "Rajasthan", "Bharatpur");
        newRoom(manager, inactive, 2);
        setActive(manager, inactive, false); // not public

        List<Map<String, Object>> list = destinations();
        Map<String, Object> jodhpur = find(list, "Jodhpur");
        assertThat(jodhpur).isNotNull();
        assertThat(jodhpur.get("state")).isEqualTo("Rajasthan");
        assertThat(((Number) jodhpur.get("hotelCount")).longValue()).isEqualTo(2);
        assertThat(find(list, "Bikaner")).isNotNull();
        assertThat(((Number) find(list, "Bikaner").get("hotelCount")).longValue()).isEqualTo(1);
        assertThat(find(list, "Barmer")).isNull();
        assertThat(find(list, "Bharatpur")).isNull();

        // Jodhpur's photo is bundled, so it was stored on startup and is served without a login.
        String photoUrl = (String) jodhpur.get("photoUrl");
        assertThat(photoUrl).isEqualTo("/api/locations/" + jodhpur.get("locationId") + "/photo");
        assertThat((String) jodhpur.get("photoCredit")).contains("Wikimedia Commons");
        mvc.perform(get(photoUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentTypeCompatibleWith("image/*"));
    }

    @Test
    void fallsBackToAHotelPhotoThenToNoPhoto() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        // Neither city has a bundled city photo.
        String body = createHotel(manager, hotelJson("Hotel " + UUID.randomUUID(), "West Bengal", "Bankura", ""),
                photo("front.png")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long withPhoto = ((Number) JsonPath.read(body, "$.id")).longValue();
        newRoom(manager, withPhoto, 1);
        newRoom(manager, newHotel(manager, "Maharashtra", "Akot"), 1);

        List<Map<String, Object>> list = destinations();
        Map<String, Object> bankura = find(list, "Bankura");
        assertThat((String) bankura.get("photoUrl")).startsWith("/api/v1/hotels/" + withPhoto + "/photos/");
        assertThat(bankura.get("photoCredit")).isNull();
        mvc.perform(get((String) bankura.get("photoUrl"))).andExpect(status().isOk());

        Map<String, Object> akot = find(list, "Akot");
        assertThat(akot.get("photoUrl")).isNull();
        assertThat(akot.get("photoCredit")).isNull();
    }

    @Test
    void unknownCityPhotoIsNotFound() throws Exception {
        mvc.perform(get("/api/locations/999999999/photo")).andExpect(status().isNotFound());
    }
}
