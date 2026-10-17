package com.HotelManagement.AirBnb;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The service-to-service API booking-service and review-service depend on. */
class InternalCatalogApiIntegrationTest extends ApiTestSupport {

    @Test
    void internalApiRequiresTheInternalTokenAndIgnoresUserTokens() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        mvc.perform(get("/internal/owners/1/hotel-counts")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/internal/owners/1/hotel-counts"), manager)).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/owners/1/hotel-counts").header("X-Internal-Token", "wrong-wrong-wrong-wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(internal(get("/internal/owners/1/hotel-counts"))).andExpect(status().isOk());
    }

    @Test
    void holdIsIdempotentPerKeyAndPricedOnTheServer() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        long hotelId = newHotel(manager);
        long roomId = newRoom(manager, hotelId, 2);
        UUID key = UUID.randomUUID();

        hold(key, hotelId, roomId, TOMORROW, TOMORROW.plusDays(2), 1, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.amount").value(5000.0)) // 2 nights x 2500 x 1 room
                .andExpect(jsonPath("$.hotelOwnerId").value(manager.id()))
                .andExpect(jsonPath("$.roomCapacity").value(2))
                .andExpect(jsonPath("$.city").value("Alappuzha"));
        // A retry with the same key doesn't take more rooms.
        hold(key, hotelId, roomId, TOMORROW, TOMORROW.plusDays(2), 1, 2).andExpect(status().isOk());
        inventory(manager, hotelId, roomId, TOMORROW, TOMORROW).andExpect(jsonPath("$[0].bookedCount").value(1));
    }

    @Test
    void holdChecksPartySizeAgainstCapacity() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        long hotelId = newHotel(manager);
        long roomId = newRoom(manager, hotelId, 2); // capacity 2
        hold(UUID.randomUUID(), hotelId, roomId, TOMORROW, TOMORROW.plusDays(1), 1, 3)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Too many guests: 1 room(s) of this type hold at most 2 guests"));
        hold(UUID.randomUUID(), hotelId, roomId, TOMORROW, TOMORROW.plusDays(1), 2, 3).andExpect(status().isOk());
    }

    @Test
    void attachLinksOneBookingAndUnattachedHoldsAreListed() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        long hotelId = newHotel(manager);
        long roomId = newRoom(manager, hotelId, 2);
        UUID key = newBooking(hotelId, roomId, 1);

        mvc.perform(internal(get("/internal/inventory/holds/unattached")).param("olderThanMinutes", "1"))
                .andExpect(status().isOk());
        String attach = "/internal/inventory/holds/" + key + "/attach";
        mvc.perform(internal(post(attach)).contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":424242}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookingId").value(424242));
        mvc.perform(internal(post(attach)).contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":424242}"))
                .andExpect(status().isOk());
        mvc.perform(internal(post(attach)).contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(internal(post("/internal/inventory/holds/" + UUID.randomUUID() + "/attach"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookingId\":2}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reacquireRetakesReleasedRoomsOnlyWhileStillAvailable() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        long hotelId = newHotel(manager);
        long roomId = newRoom(manager, hotelId, 1);
        UUID late = newBooking(hotelId, roomId, 1);
        String reacquire = "/internal/inventory/holds/" + late + "/reacquire";

        mvc.perform(internal(post(reacquire))).andExpect(jsonPath("$.reacquired").value(true)); // still held
        release(late).andExpect(status().isOk());
        mvc.perform(internal(post(reacquire))).andExpect(jsonPath("$.reacquired").value(true));
        inventory(manager, hotelId, roomId, TOMORROW, TOMORROW).andExpect(jsonPath("$[0].bookedCount").value(1));

        // Released again and someone else took the room: can't re-take it.
        release(late).andExpect(status().isOk());
        newBooking(hotelId, roomId, 1);
        mvc.perform(internal(post(reacquire))).andExpect(jsonPath("$.reacquired").value(false));
        inventory(manager, hotelId, roomId, TOMORROW, TOMORROW).andExpect(jsonPath("$[0].bookedCount").value(1));
    }

    @Test
    void summariesPublicCheckAndOwnerCounts() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        String created = createHotel(manager, hotelJson("Summary Inn", "Kerala", "Alappuzha", ""), photo("a.png"))
                .andReturn().getResponse().getContentAsString();
        long hotelId = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();
        long roomId = newRoom(manager, hotelId, 2);
        long hidden = newHotel(manager);
        mvc.perform(auth(patch("/api/v1/admin/hotels/" + hidden + "/status"), manager)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
        long gone = newHotel(manager);
        mvc.perform(auth(delete("/api/v1/admin/hotels/" + gone), manager)).andExpect(status().isNoContent());

        mvc.perform(internal(post("/internal/hotels/summaries")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hotelIds\":[%d,%d,99999999],\"roomIds\":[%d]}".formatted(hotelId, hidden, roomId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotels", hasSize(2)))
                .andExpect(jsonPath("$.hotels[?(@.id == %d)].name".formatted(hotelId)).value("Summary Inn"))
                .andExpect(jsonPath("$.hotels[?(@.id == %d)].ownerId".formatted(hotelId)).value((int) manager.id()))
                .andExpect(jsonPath("$.hotels[?(@.id == %d)].coverPhotoUrl".formatted(hotelId)).isNotEmpty())
                .andExpect(jsonPath("$.hotels[?(@.id == %d)].active".formatted(hidden)).value(false))
                .andExpect(jsonPath("$.rooms[0].capacity").value(2));

        mvc.perform(internal(get("/internal/hotels/" + hotelId + "/public")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Summary Inn"));
        mvc.perform(internal(get("/internal/hotels/" + hidden + "/public"))).andExpect(status().isNotFound());
        mvc.perform(internal(get("/internal/hotels/" + gone + "/public"))).andExpect(status().isNotFound());

        mvc.perform(internal(get("/internal/owners/" + manager.id() + "/hotel-counts")))
                .andExpect(jsonPath("$.totalHotels").value(2))
                .andExpect(jsonPath("$.activeHotels").value(1));
    }

    @Test
    void ratingPushesAreAbsoluteAndShowUpInSearch() throws Exception {
        Account manager = signup("HOTEL_MANAGER");
        long hotelId = newHotel(manager, "Assam", "Diphu");
        newRoom(manager, hotelId, 1);
        String push = "[{\"hotelId\":%d,\"ratingSum\":%d,\"ratingCount\":%d}]";
        mvc.perform(internal(put("/internal/hotels/ratings")).contentType(MediaType.APPLICATION_JSON)
                .content(push.formatted(hotelId, 4, 1))).andExpect(status().isOk());
        mvc.perform(internal(put("/internal/hotels/ratings")).contentType(MediaType.APPLICATION_JSON)
                .content(push.formatted(hotelId, 9, 2))).andExpect(status().isOk());
        mvc.perform(internal(put("/internal/hotels/ratings")).contentType(MediaType.APPLICATION_JSON)
                .content(push.formatted(hotelId, 9, 2))).andExpect(status().isOk()); // re-sync: no change

        mvc.perform(get("/api/v1/hotels/search").param("city", "Diphu").param("checkinDate", TOMORROW.toString())
                        .param("checkoutDate", TOMORROW.plusDays(1).toString()).param("numberOfRooms", "1"))
                .andExpect(jsonPath("$.content[0].averageRating").value(4.5))
                .andExpect(jsonPath("$.content[0].reviewCount").value(2));
    }
}
