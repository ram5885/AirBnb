package com.HotelManagement.AirBnb;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Optional search filters (amenities, price range, minimum rating) on GET /api/v1/hotels/search, alone and
 * combined, on top of the date-wise availability rule. One fixture in its own city (Bargarh), searched for a
 * stay 10-12 days ahead:
 * <pre>
 *   Alpha   hotel [WIFI]                Standard 1500 [AIR_CONDITIONING], Deluxe 3500 [AIR_CONDITIONING, TV]  ratings 5, 4 (4.5)
 *   Bravo   hotel [WIFI, SWIMMING_POOL] Suite 6000 [TV]                                                       rating 3 (3.0)
 *   Charlie hotel []                    Basic 1000 [WIFI]                                                     no reviews
 *   Delta   hotel [WIFI, AIR_CONDITIONING] Standard 2000 [], its only room booked for one night of the stay   no reviews
 * </pre>
 * Ratings come from the hotel_rating read model that review-service keeps up to date.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchFilterIntegrationTest extends ApiTestSupport {

    static final String CITY = "Bargarh";
    static final LocalDate IN = LocalDate.now().plusDays(10);
    static final LocalDate OUT = IN.plusDays(2);

    Account manager;
    long alpha, bravo, charlie, delta;

    @BeforeAll
    void fixture() throws Exception {
        manager = signup("HOTEL_MANAGER");
        alpha = hotel("Alpha", "\"WIFI\"");
        room(alpha, "Standard", "1500", 5, "\"AIR_CONDITIONING\"");
        room(alpha, "Deluxe", "3500", 5, "\"AIR_CONDITIONING\",\"TV\"");
        bravo = hotel("Bravo", "\"WIFI\",\"SWIMMING_POOL\"");
        room(bravo, "Suite", "6000", 5, "\"TV\"");
        charlie = hotel("Charlie", "");
        room(charlie, "Basic", "1000", 5, "\"WIFI\"");
        delta = hotel("Delta", "\"WIFI\",\"AIR_CONDITIONING\"");
        long deltaRoom = room(delta, "Standard", "2000", 1, "");

        // Review-service pushes each hotel's rating (sum, count) after reviews of completed stays:
        // Alpha 5 + 4, Bravo 3.
        mvc.perform(internal(put("/internal/hotels/ratings")).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"hotelId\":%d,\"ratingSum\":9,\"ratingCount\":2},{\"hotelId\":%d,\"ratingSum\":3,\"ratingCount\":1}]"
                                .formatted(alpha, bravo)))
                .andExpect(status().isOk());
        // Delta's only room is taken for the second night of the searched stay.
        book(delta, deltaRoom, IN.plusDays(1), OUT, 1).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- helpers

    long hotel(String name, String amenities) throws Exception {
        String body = createHotel(manager, """
                {"name":"%s %s","state":"Odisha","city":"%s","address":"1 Main Road","contactPerson":"Asha Rao",
                 "phoneNumber":"9876543210","email":"desk@hotel.com","amenities":[%s],"active":true}"""
                .formatted(name, UUID.randomUUID(), CITY, amenities))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    long room(long hotelId, String type, String price, int total, String amenities) throws Exception {
        String body = mvc.perform(auth(multipart("/api/v1/admin/hotels/" + hotelId + "/rooms").file(data("""
                        {"type":"%s","basePrice":%s,"totalCount":%d,"capacity":2,"amenities":[%s]}"""
                        .formatted(type, price, total, amenities))), manager))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    ResultActions search(int rooms, String... filters) throws Exception {
        var req = get("/api/v1/hotels/search").param("city", CITY).param("state", "Odisha")
                .param("checkinDate", IN.toString()).param("checkoutDate", OUT.toString())
                .param("numberOfRooms", String.valueOf(rooms)).param("size", "50");
        for (int i = 0; i + 1 < filters.length; i += 2) req.param(filters[i], filters[i + 1]);
        return mvc.perform(req);
    }

    List<Map<String, Object>> results(String... filters) throws Exception {
        String body = search(1, filters).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.content");
    }

    List<Long> ids(String... filters) throws Exception {
        return results(filters).stream().map(h -> ((Number) h.get("id")).longValue()).toList();
    }

    static Map<String, Object> card(List<Map<String, Object>> list, long id) {
        return list.stream().filter(h -> ((Number) h.get("id")).longValue() == id).findFirst().orElseThrow();
    }

    static double num(Object v) {
        return ((Number) v).doubleValue();
    }

    // ---------------------------------------------------------------- no filters, availability

    @Test
    void noFiltersKeepsTheAvailabilityRule() throws Exception {
        List<Map<String, Object>> all = results();
        assertThat(all).extracting(h -> ((Number) h.get("id")).longValue()).containsExactly(alpha, bravo, charlie);
        // Delta matches every filter below but has no room on one night, so it never appears.
        assertThat(ids("amenities", "WIFI,AIR_CONDITIONING", "maxPrice", "2000")).containsExactly(alpha);

        Map<String, Object> a = card(all, alpha);
        assertThat(num(a.get("lowestNightlyPrice"))).isEqualTo(1500.0);
        assertThat(num(a.get("averageRating"))).isEqualTo(4.5);
        assertThat(num(a.get("reviewCount"))).isEqualTo(2);
        assertThat(card(all, charlie).get("averageRating")).isNull();
        assertThat(num(card(all, charlie).get("reviewCount"))).isZero();
        // Empty filter values are "not filtered".
        assertThat(ids("amenities", "", "minPrice", "", "maxPrice", "", "minRating", "")).containsExactly(alpha, bravo, charlie);
    }

    // ---------------------------------------------------------------- amenities

    @Test
    void amenitiesMustAllBeOfferedByOneBookableRoomOrItsHotel() throws Exception {
        assertThat(ids("amenities", "WIFI")).containsExactly(alpha, bravo, charlie); // hotel, hotel, room
        assertThat(ids("amenities", "AIR_CONDITIONING")).containsExactly(alpha);
        assertThat(ids("amenities", "WIFI,TV")).containsExactly(alpha, bravo);
        assertThat(ids("amenities", "WIFI", "amenities", "TV")).containsExactly(alpha, bravo); // repeated param form
        // Bravo has the pool but no AC, Alpha has AC but no pool: nobody offers both.
        assertThat(ids("amenities", "SWIMMING_POOL,AIR_CONDITIONING")).isEmpty();
        assertThat(ids("amenities", "KITCHEN")).isEmpty();

        // AC + TV only holds for Alpha's Deluxe, so the card is priced from it, not the cheaper Standard.
        Map<String, Object> a = card(results("amenities", "AIR_CONDITIONING,TV"), alpha);
        assertThat(num(a.get("lowestNightlyPrice"))).isEqualTo(3500.0);
        assertThat(num(a.get("availableRoomTypes"))).isEqualTo(1);
    }

    // ---------------------------------------------------------------- price

    @Test
    void priceBoundsApplyToOneBookableRoomTypesNightlyPrice() throws Exception {
        assertThat(ids("minPrice", "2000")).containsExactly(alpha, bravo);
        assertThat(num(card(results("minPrice", "2000"), alpha).get("lowestNightlyPrice"))).isEqualTo(3500.0);
        assertThat(ids("maxPrice", "1500")).containsExactly(alpha, charlie); // inclusive
        assertThat(ids("minPrice", "1200", "maxPrice", "4000")).containsExactly(alpha);
        Map<String, Object> a = card(results("minPrice", "1200", "maxPrice", "4000"), alpha);
        assertThat(num(a.get("lowestNightlyPrice"))).isEqualTo(1500.0);
        assertThat(num(a.get("availableRoomTypes"))).isEqualTo(2);
        assertThat(ids("minPrice", "1600", "maxPrice", "3400")).isEmpty(); // between Alpha's two room types
        assertThat(ids("minPrice", "0", "maxPrice", "1000000")).containsExactly(alpha, bravo, charlie);
    }

    // ---------------------------------------------------------------- rating

    @Test
    void minRatingUsesTheReviewAverageAndExcludesUnreviewedHotels() throws Exception {
        assertThat(ids("minRating", "4")).containsExactly(alpha);
        assertThat(ids("minRating", "4.5")).containsExactly(alpha);
        assertThat(ids("minRating", "4.6")).isEmpty();
        assertThat(ids("minRating", "3")).containsExactly(alpha, bravo); // Charlie has no reviews
        assertThat(ids("minRating", "1")).containsExactly(alpha, bravo);
    }

    // ---------------------------------------------------------------- combinations

    @Test
    void filtersCombineWithAnd() throws Exception {
        assertThat(ids("amenities", "TV", "maxPrice", "4000")).containsExactly(alpha);
        assertThat(ids("amenities", "WIFI", "minRating", "3")).containsExactly(alpha, bravo);
        assertThat(ids("amenities", "TV", "minRating", "4")).containsExactly(alpha);
        assertThat(ids("maxPrice", "2000", "minRating", "4")).containsExactly(alpha);
        assertThat(ids("maxPrice", "2000", "minRating", "3")).containsExactly(alpha); // Bravo is too expensive
        assertThat(ids("amenities", "WIFI", "minPrice", "1000", "maxPrice", "2000", "minRating", "4.5")).containsExactly(alpha);
        assertThat(ids("amenities", "WIFI", "minPrice", "1000", "maxPrice", "2000", "minRating", "4.6")).isEmpty();
        // Two rooms: every room type has 5, so the result set is the same.
        String body = search(2, "amenities", "TV").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(body, "$.totalElements")).intValue()).isEqualTo(2);
    }

    @Test
    void pagingAppliesAfterFiltering() throws Exception {
        var req = get("/api/v1/hotels/search").param("city", CITY).param("state", "Odisha")
                .param("checkinDate", IN.toString()).param("checkoutDate", OUT.toString()).param("numberOfRooms", "1")
                .param("amenities", "WIFI").param("size", "2");
        mvc.perform(req.param("page", "1"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(charlie));
    }

    // ---------------------------------------------------------------- validation

    @Test
    void rejectsInvalidFilters() throws Exception {
        search(1, "minPrice", "3000", "maxPrice", "1000").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Minimum price can't be more than the maximum price"));
        search(1, "minPrice", "-1").andExpect(status().isBadRequest());
        search(1, "maxPrice", "1000001").andExpect(status().isBadRequest());
        search(1, "maxPrice", "abc").andExpect(status().isBadRequest());
        search(1, "minRating", "0").andExpect(status().isBadRequest());
        search(1, "minRating", "5.5").andExpect(status().isBadRequest());
        search(1, "minRating", "high").andExpect(status().isBadRequest());
        search(1, "amenities", "WIFI,TELEPORTER").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown amenity: TELEPORTER"));
    }
}
