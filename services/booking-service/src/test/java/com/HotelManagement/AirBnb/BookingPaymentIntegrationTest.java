package com.HotelManagement.AirBnb;

import com.HotelManagement.AirBnb.booking.entity.Booking;
import com.HotelManagement.AirBnb.booking.service.HoldExpiryService;
import com.HotelManagement.AirBnb.payment.gateway.PaymentGateway.GatewayOrder;
import com.HotelManagement.AirBnb.payment.gateway.PaymentGateway.GatewayPayment;
import com.HotelManagement.AirBnb.payment.gateway.PaymentGateway.GatewayRefund;
import com.HotelManagement.AirBnb.payment.gateway.PaymentGatewayException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guest booking, Razorpay payment (gateway mocked, signatures real), My Bookings, cancellation/refund,
 * hold expiry and manager booking management, through the real HTTP + security + database stack.
 * Ported from the monolith: rooms are held in the (fake) catalog-service, see FakeCatalog.
 * Rooms come from newRoom(): 2500/night, capacity 2. Search reflecting bookings is tested in catalog-service.
 */
class BookingPaymentIntegrationTest extends ApiTestSupport {

    @Autowired
    HoldExpiryService holdExpiryService;

    static final LocalDate IN = TOMORROW.plusDays(1);
    static final LocalDate OUT = IN.plusDays(2);

    Account manager;
    Account guest;
    long hotel;
    long room;

    @BeforeEach
    void setUp() throws Exception {
        manager = signup("HOTEL_MANAGER");
        guest = signup("GUEST");
        hotel = newHotel(manager);
        room = newRoom(manager, hotel, 3);
        when(gateway.isEnabled()).thenReturn(true);
        when(gateway.keyId()).thenReturn("rzp_test_dummykey");
        when(gateway.createOrder(anyLong(), anyString(), anyString(), anyLong())).thenAnswer(inv ->
                new GatewayOrder("order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14),
                        inv.getArgument(0), inv.getArgument(1), "created"));
    }

    // ---------------------------------------------------------------- helpers

    long saveGuest(Account account, String name) throws Exception {
        String body = mvc.perform(auth(post("/api/v1/guests"), account).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"gender\":\"FEMALE\",\"age\":30}".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    ResultActions reserve(Account account, long hotelId, long roomId, LocalDate in, LocalDate out, int rooms,
                          List<Long> guestIds) throws Exception {
        String ids = guestIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return mvc.perform(auth(post("/api/v1/bookings"), account).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"hotelId":%d,"roomId":%d,"checkInDate":"%s","checkOutDate":"%s","roomsCount":%d,
                         "guestIds":[%s],"amount":1}""".formatted(hotelId, roomId, in, out, rooms, ids)));
    }

    /** RESERVED booking for {@code rooms} rooms over IN..OUT with one saved guest. */
    long reserved(Account account, int rooms) throws Exception {
        String body = reserve(account, hotel, room, IN, OUT, rooms, List.of(saveGuest(account, "Asha")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    String createOrder(Account account, long bookingId) throws Exception {
        return mvc.perform(auth(post("/api/v1/payments/" + bookingId), account))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    static String hmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    ResultActions verifyPayment(Account account, long bookingId, String orderId, String paymentId, String signature)
            throws Exception {
        return mvc.perform(auth(post("/api/v1/payments/verify"), account).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookingId":%d,"razorpayOrderId":"%s","razorpayPaymentId":"%s","razorpaySignature":"%s"}"""
                        .formatted(bookingId, orderId, paymentId, signature)));
    }

    /** Stubs Razorpay's view of a payment and verifies it with a correct checkout signature. */
    ResultActions pay(Account account, long bookingId, String orderId, long amount, String gatewayStatus) throws Exception {
        String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        when(gateway.fetchPayment(paymentId)).thenReturn(
                new GatewayPayment(paymentId, orderId, amount, "INR", gatewayStatus, null,
                        "failed".equals(gatewayStatus) ? "Card declined by bank" : null));
        return verifyPayment(account, bookingId, orderId, paymentId, hmac(orderId + "|" + paymentId, TEST_KEY_SECRET));
    }

    long confirmedBooking(Account account, int rooms) throws Exception {
        long bookingId = reserved(account, rooms);
        String order = createOrder(account, bookingId);
        pay(account, bookingId, JsonPath.read(order, "$.orderId"), ((Number) JsonPath.read(order, "$.amount")).longValue(),
                "captured").andExpect(status().isOk());
        return bookingId;
    }

    ResultActions myBooking(Account account, long bookingId) throws Exception {
        return mvc.perform(auth(get("/api/v1/bookings/" + bookingId), account));
    }

    /** Rooms taken in catalog's inventory for the date (by every inventory-holding booking). */
    void bookedOn(LocalDate date, int expected) {
        assertThat(catalog.bookedOn(room, date)).as("booked rooms on " + date).isEqualTo(expected);
    }

    ResultActions webhook(String body, String eventId, String signature) throws Exception {
        return mvc.perform(post("/api/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Razorpay-Signature", signature).header("x-razorpay-event-id", eventId));
    }

    // ---------------------------------------------------------------- booking creation

    @Nested
    class Booking {

        @Test
        void reservesAtServerComputedPriceIgnoringClientAmount() throws Exception {
            long g1 = saveGuest(guest, "Asha");
            long g2 = saveGuest(guest, "Ravi");
            reserve(guest, hotel, room, IN, OUT, 2, List.of(g1, g2))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("RESERVED"))
                    .andExpect(jsonPath("$.amount").value(10000.0)) // 2500 x 2 nights x 2 rooms, not the client's "1"
                    .andExpect(jsonPath("$.currency").value("INR"))
                    .andExpect(jsonPath("$.hotelId").value(hotel))
                    .andExpect(jsonPath("$.roomId").value(room))
                    .andExpect(jsonPath("$.guests", hasSize(2)))
                    .andExpect(jsonPath("$.holdExpiresAt").isNotEmpty())
                    .andExpect(jsonPath("$.canPay").value(true))
                    .andExpect(jsonPath("$.reference").value(containsString("BK")));
            bookedOn(IN, 2);
        }

        @Test
        void rejectsInvalidRequests() throws Exception {
            long g = saveGuest(guest, "Asha");
            reserve(guest, hotel, room, OUT, IN, 1, List.of(g)).andExpect(status().isBadRequest());
            reserve(guest, hotel, room, IN, OUT, 0, List.of(g)).andExpect(status().isBadRequest());
            // 1 room of capacity 2 can't take 3 guests.
            reserve(guest, hotel, room, IN, OUT, 1, List.of(g, saveGuest(guest, "B"), saveGuest(guest, "C")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(containsString("Too many guests")));
            // Another user's saved guest.
            Account other = signup("GUEST");
            reserve(guest, hotel, room, IN, OUT, 1, List.of(saveGuest(other, "Not mine"))).andExpect(status().isBadRequest());
            // Insufficient inventory (3 rooms exist).
            reserve(guest, hotel, room, IN, OUT, 4, List.of(g)).andExpect(status().isConflict());
            // Room type of another hotel.
            long otherHotel = newHotel(manager);
            reserve(guest, otherHotel, room, IN, OUT, 1, List.of(g)).andExpect(status().isNotFound());
        }

        @Test
        void onlyAuthenticatedGuestsCanBook() throws Exception {
            mvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(auth(post("/api/v1/bookings"), manager).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(auth(get("/api/v1/guests"), manager)).andExpect(status().isForbidden());
        }

        @Test
        void guestDetailsAreValidatedAndPrivate() throws Exception {
            mvc.perform(auth(post("/api/v1/guests"), guest).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"\",\"age\":200}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.name").exists())
                    .andExpect(jsonPath("$.fieldErrors.gender").exists())
                    .andExpect(jsonPath("$.fieldErrors.age").exists());
            long mine = saveGuest(guest, "Asha");
            Account other = signup("GUEST");
            mvc.perform(auth(get("/api/v1/guests"), other)).andExpect(jsonPath("$", hasSize(0)));
            mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/guests/" + mine), other)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"gender\":\"MALE\",\"age\":5}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void guestsCanBeUpdatedOnAnActiveBooking() throws Exception {
            long bookingId = reserve(guest, hotel, room, IN, OUT, 1, List.of()).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString().transform(b -> ((Number) JsonPath.read(b, "$.id")).longValue());
            long g = saveGuest(guest, "Asha");
            mvc.perform(auth(patch("/api/v1/bookings/" + bookingId + "/guests"), guest).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"guestIds\":[" + g + "]}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.guests[0].name").value("Asha"));
        }
    }

    // ---------------------------------------------------------------- payments

    @Nested
    class Payments {

        @Test
        void createsOneOrderWithServerAmountAndNoSecrets() throws Exception {
            long bookingId = reserved(guest, 2);
            String order = createOrder(guest, bookingId);
            assertThat(((Number) JsonPath.read(order, "$.amount")).longValue()).isEqualTo(1_000_000L); // paise
            assertThat((String) JsonPath.read(order, "$.currency")).isEqualTo("INR");
            assertThat((String) JsonPath.read(order, "$.keyId")).isEqualTo("rzp_test_dummykey");
            assertThat(order).doesNotContain(TEST_KEY_SECRET);
            String again = createOrder(guest, bookingId); // retry / reopened checkout
            assertThat((String) JsonPath.read(again, "$.orderId")).isEqualTo(JsonPath.read(order, "$.orderId"));
            verify(gateway, times(1)).createOrder(eq(1_000_000L), eq("INR"), anyString(), eq(bookingId));
        }

        @Test
        void orderNeedsGuestsConfiguredPaymentsAndOwnership() throws Exception {
            String body = reserve(guest, hotel, room, IN, OUT, 1, List.of()).andReturn().getResponse().getContentAsString();
            long noGuests = ((Number) JsonPath.read(body, "$.id")).longValue();
            mvc.perform(auth(post("/api/v1/payments/" + noGuests), guest)).andExpect(status().isBadRequest());

            long bookingId = reserved(guest, 1);
            mvc.perform(auth(post("/api/v1/payments/" + bookingId), signup("GUEST"))).andExpect(status().isNotFound());
            when(gateway.isEnabled()).thenReturn(false);
            mvc.perform(auth(post("/api/v1/payments/" + bookingId), guest)).andExpect(status().isServiceUnavailable());
        }

        @Test
        void verifiedPaymentConfirmsBookingOnceAndKeepsInventory() throws Exception {
            long bookingId = reserved(guest, 2);
            String order = createOrder(guest, bookingId);
            String orderId = JsonPath.read(order, "$.orderId");
            String paymentId = "pay_success123";
            when(gateway.fetchPayment(paymentId)).thenReturn(new GatewayPayment(paymentId, orderId, 1_000_000L, "INR", "captured", null, null));
            String sig = hmac(orderId + "|" + paymentId, TEST_KEY_SECRET);

            verifyPayment(guest, bookingId, orderId, paymentId, sig)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.confirmed").value(true))
                    .andExpect(jsonPath("$.booking.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.booking.paymentStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.booking.canPay").value(false));
            // Duplicate callback: idempotent, no second gateway lookup, no inventory change.
            verifyPayment(guest, bookingId, orderId, paymentId, sig)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(containsString("already")));
            verify(gateway, times(1)).fetchPayment(paymentId);
            bookedOn(IN, 2);
            mvc.perform(auth(post("/api/v1/payments/" + bookingId), guest)).andExpect(status().isConflict());
        }

        @Test
        void authorizedPaymentsAreCaptured() throws Exception {
            long bookingId = reserved(guest, 1);
            String orderId = JsonPath.read(createOrder(guest, bookingId), "$.orderId");
            when(gateway.capture(anyString(), eq(500_000L), eq("INR"))).thenAnswer(inv ->
                    new GatewayPayment(inv.getArgument(0), orderId, 500_000L, "INR", "captured", null, null));
            pay(guest, bookingId, orderId, 500_000L, "authorized").andExpect(status().isOk())
                    .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
        }

        @Test
        void rejectsInvalidSignatureWithoutChangingAnything() throws Exception {
            long bookingId = reserved(guest, 1);
            String orderId = JsonPath.read(createOrder(guest, bookingId), "$.orderId");
            verifyPayment(guest, bookingId, orderId, "pay_forged", hmac(orderId + "|pay_forged", "wrong-secret"))
                    .andExpect(status().isBadRequest());
            verify(gateway, never()).fetchPayment(anyString());
            myBooking(guest, bookingId).andExpect(jsonPath("$.status").value("RESERVED"))
                    .andExpect(jsonPath("$.paymentStatus").value("PENDING"));
        }

        @Test
        void rejectsAmountMismatchAndForeignOrders() throws Exception {
            long bookingId = reserved(guest, 1);
            String orderId = JsonPath.read(createOrder(guest, bookingId), "$.orderId");
            pay(guest, bookingId, orderId, 100L, "captured").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.confirmed").value(false));
            myBooking(guest, bookingId).andExpect(jsonPath("$.status").value("RESERVED"))
                    .andExpect(jsonPath("$.paymentStatus").value("FAILED"));

            // An order from another booking can't be used to pay this one.
            long otherBooking = reserved(guest, 1);
            String otherOrder = JsonPath.read(createOrder(guest, otherBooking), "$.orderId");
            pay(guest, bookingId, otherOrder, 500_000L, "captured").andExpect(status().isBadRequest());
            // Another guest can't verify against this booking.
            pay(signup("GUEST"), bookingId, orderId, 500_000L, "captured").andExpect(status().isNotFound());
        }

        @Test
        void failedPaymentCanBeRetriedOnTheSameOrder() throws Exception {
            long bookingId = reserved(guest, 1);
            String orderId = JsonPath.read(createOrder(guest, bookingId), "$.orderId");
            pay(guest, bookingId, orderId, 500_000L, "failed")
                    .andExpect(status().isPaymentRequired())
                    .andExpect(jsonPath("$.message").value(containsString("Card declined")))
                    .andExpect(jsonPath("$.booking.status").value("RESERVED"))
                    .andExpect(jsonPath("$.booking.paymentStatus").value("FAILED"))
                    .andExpect(jsonPath("$.booking.canPay").value(true));

            // Checkout's payment.failed event (or a dismissed popup) never confirms anything.
            mvc.perform(auth(post("/api/v1/payments/" + bookingId + "/failure"), guest).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"razorpayOrderId\":\"%s\",\"description\":\"Payment cancelled\"}".formatted(orderId)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESERVED"));

            String retry = createOrder(guest, bookingId);
            assertThat((String) JsonPath.read(retry, "$.orderId")).isEqualTo(orderId);
            pay(guest, bookingId, orderId, 500_000L, "captured").andExpect(status().isOk())
                    .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
            verify(gateway, times(1)).createOrder(anyLong(), anyString(), anyString(), eq(bookingId));
            bookedOn(IN, 1);
        }
    }

    // ---------------------------------------------------------------- webhooks

    @Nested
    class Webhooks {

        String capturedEvent(String orderId, String paymentId, long amount) {
            return """
                    {"event":"payment.captured","payload":{"payment":{"entity":{"id":"%s","order_id":"%s",
                    "amount":%d,"currency":"INR","status":"captured"}}}}""".formatted(paymentId, orderId, amount);
        }

        @Test
        void signedCapturedEventConfirmsOnceEvenWhenRedelivered() throws Exception {
            long bookingId = reserved(guest, 1);
            String orderId = JsonPath.read(createOrder(guest, bookingId), "$.orderId");
            String body = capturedEvent(orderId, "pay_wh_1", 500_000L);
            String sig = hmac(body, TEST_WEBHOOK_SECRET);
            String eventId = "evt_" + UUID.randomUUID();

            webhook(body, eventId, sig).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("processed"));
            myBooking(guest, bookingId).andExpect(jsonPath("$.status").value("CONFIRMED"));
            webhook(body, eventId, sig).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("duplicate"));
            webhook(body, "evt_" + UUID.randomUUID(), sig).andExpect(status().isOk()); // same payment, new event id
            bookedOn(IN, 1);
            myBooking(guest, bookingId).andExpect(jsonPath("$.paymentStatus").value("CONFIRMED"));
        }

        @Test
        void rejectsUnsignedOrTamperedWebhooks() throws Exception {
            long bookingId = reserved(guest, 1);
            String orderId = JsonPath.read(createOrder(guest, bookingId), "$.orderId");
            String body = capturedEvent(orderId, "pay_wh_2", 500_000L);
            webhook(body, "evt_" + UUID.randomUUID(), "deadbeef").andExpect(status().isBadRequest());
            webhook(body.replace("500000", "100"), "evt_" + UUID.randomUUID(), hmac(body, TEST_WEBHOOK_SECRET))
                    .andExpect(status().isBadRequest());
            myBooking(guest, bookingId).andExpect(jsonPath("$.status").value("RESERVED"));
        }

        @Test
        void refundProcessedEventMarksPaymentRefunded() throws Exception {
            long bookingId = confirmedBooking(guest, 1);
            when(gateway.refund(anyString(), eq(500_000L), anyString())).thenReturn(new GatewayRefund("rfnd_1" + bookingId, "pay", 500_000L, "pending"));
            mvc.perform(auth(patch("/api/v1/bookings/" + bookingId + "/cancel"), guest))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("REFUND_PENDING"));
            String body = """
                    {"event":"refund.processed","payload":{"refund":{"entity":{"id":"rfnd_1%d","status":"processed"}}}}"""
                    .formatted(bookingId);
            webhook(body, "evt_" + UUID.randomUUID(), hmac(body, TEST_WEBHOOK_SECRET)).andExpect(status().isOk());
            myBooking(guest, bookingId).andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        }
    }

    // ---------------------------------------------------------------- cancellation, refunds, expiry

    @Nested
    class CancellationAndExpiry {

        @Test
        void guestCancellationRefundsInFullAndReleasesInventoryOnce() throws Exception {
            long bookingId = confirmedBooking(guest, 2);
            bookedOn(IN, 2);
            when(gateway.refund(anyString(), eq(1_000_000L), anyString())).thenReturn(new GatewayRefund("rfnd_ok", "pay", 1_000_000L, "processed"));

            mvc.perform(auth(patch("/api/v1/bookings/" + bookingId + "/cancel"), guest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
            bookedOn(IN, 0);
            mvc.perform(auth(patch("/api/v1/bookings/" + bookingId + "/cancel"), guest)).andExpect(status().isConflict());
            verify(gateway, times(1)).refund(anyString(), anyLong(), anyString());
            bookedOn(IN, 0);
        }

        @Test
        void refundFailureLeavesBookingConfirmed() throws Exception {
            long bookingId = confirmedBooking(guest, 1);
            when(gateway.refund(anyString(), anyLong(), anyString())).thenThrow(new PaymentGatewayException("down"));
            mvc.perform(auth(patch("/api/v1/bookings/" + bookingId + "/cancel"), guest))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error").value(not(containsString("down"))));
            myBooking(guest, bookingId).andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.paymentStatus").value("CONFIRMED"));
            bookedOn(IN, 1);
        }

        @Test
        void unpaidCancellationAndCheckInDayRules() throws Exception {
            long unpaid = reserved(guest, 1);
            mvc.perform(auth(patch("/api/v1/bookings/" + unpaid + "/cancel"), signup("GUEST"))).andExpect(status().isNotFound());
            mvc.perform(auth(patch("/api/v1/bookings/" + unpaid + "/cancel"), guest))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
            verify(gateway, never()).refund(anyString(), anyLong(), anyString());
            bookedOn(IN, 0);

            // Check-in today: too late for the guest to cancel.
            String body = reserve(guest, hotel, room, LocalDate.now(), LocalDate.now().plusDays(1), 1,
                    List.of(saveGuest(guest, "A"))).andReturn().getResponse().getContentAsString();
            long today = ((Number) JsonPath.read(body, "$.id")).longValue();
            myBooking(guest, today).andExpect(jsonPath("$.canCancel").value(false));
            mvc.perform(auth(patch("/api/v1/bookings/" + today + "/cancel"), guest)).andExpect(status().isConflict());
        }

        @Test
        void expiredHoldsReleaseInventoryExactlyOnce() throws Exception {
            long bookingId = reserved(guest, 2);
            bookedOn(IN, 2);
            assertThat(holdExpiryService.expireHoldsCreatedBefore(LocalDateTime.now().plusMinutes(1))).isPositive();
            myBooking(guest, bookingId).andExpect(jsonPath("$.status").value("EXPIRED")).andExpect(jsonPath("$.canPay").value(false));
            bookedOn(IN, 0);
            holdExpiryService.expireHoldsCreatedBefore(LocalDateTime.now().plusMinutes(1));
            bookedOn(IN, 0);
            mvc.perform(auth(post("/api/v1/payments/" + bookingId), guest)).andExpect(status().isConflict());
        }

        @Test
        void latePaymentAfterExpiryReconfirmsOrRefunds() throws Exception {
            // Rooms still free: the late payment re-takes them.
            long first = reserved(guest, 1);
            String order1 = JsonPath.read(createOrder(guest, first), "$.orderId");
            holdExpiryService.expireHoldsCreatedBefore(LocalDateTime.now().plusMinutes(1));
            pay(guest, first, order1, 500_000L, "captured").andExpect(status().isOk())
                    .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
            bookedOn(IN, 1);

            // Rooms gone meanwhile: the payment is refunded, never left as a paid booking without rooms.
            long second = reserved(guest, 2);
            String order2 = JsonPath.read(createOrder(guest, second), "$.orderId");
            holdExpiryService.expireHoldsCreatedBefore(LocalDateTime.now().plusMinutes(1));
            reserved(signup("GUEST"), 2); // takes the remaining 2 rooms
            when(gateway.refund(anyString(), eq(1_000_000L), anyString())).thenReturn(new GatewayRefund("rfnd_late", "p", 1_000_000L, "pending"));
            pay(guest, second, order2, 1_000_000L, "captured").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.confirmed").value(false))
                    .andExpect(jsonPath("$.booking.status").value("EXPIRED"))
                    .andExpect(jsonPath("$.booking.paymentStatus").value("REFUND_PENDING"));
            bookedOn(IN, 3);
        }
    }

    // ---------------------------------------------------------------- pay at hotel

    @Nested
    class PayAtHotel {

        ResultActions payAtHotel(Account account, long bookingId) throws Exception {
            return mvc.perform(auth(post("/api/v1/payments/" + bookingId + "/pay-at-hotel"), account));
        }

        ResultActions collected(Account account, long bookingId) throws Exception {
            return mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/payment-collected"), account));
        }

        @Test
        void confirmsWithoutOnlinePaymentAndHoldsRoomsWithoutExpiring() throws Exception {
            when(gateway.isEnabled()).thenReturn(false); // works even when online payments are off
            long bookingId = reserved(guest, 2);
            payAtHotel(guest, bookingId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.paymentMethod").value("PAY_AT_HOTEL"))
                    .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                    .andExpect(jsonPath("$.amount").value(10000.0))
                    .andExpect(jsonPath("$.canPay").value(false))
                    .andExpect(jsonPath("$.canCancel").value(true))
                    .andExpect(jsonPath("$.holdExpiresAt").doesNotExist());
            holdExpiryService.expireHoldsCreatedBefore(LocalDateTime.now().plusMinutes(1));
            myBooking(guest, bookingId).andExpect(jsonPath("$.status").value("CONFIRMED"));
            bookedOn(IN, 2);
            mvc.perform(auth(get("/api/v1/bookings").param("scope", "upcoming"), guest))
                    .andExpect(jsonPath("$.content[0].id").value(bookingId));

            // Can't be paid or confirmed again.
            payAtHotel(guest, bookingId).andExpect(status().isConflict());
            when(gateway.isEnabled()).thenReturn(true);
            mvc.perform(auth(post("/api/v1/payments/" + bookingId), guest)).andExpect(status().isConflict());
            verify(gateway, never()).createOrder(anyLong(), anyString(), anyString(), anyLong());
        }

        @Test
        void validatesGuestsOwnershipAndExpiry() throws Exception {
            String body = reserve(guest, hotel, room, IN, OUT, 1, List.of()).andReturn().getResponse().getContentAsString();
            long noGuests = ((Number) JsonPath.read(body, "$.id")).longValue();
            payAtHotel(guest, noGuests).andExpect(status().isBadRequest());

            long bookingId = reserved(guest, 1);
            payAtHotel(signup("GUEST"), bookingId).andExpect(status().isNotFound());
            payAtHotel(manager, bookingId).andExpect(status().isForbidden());

            holdExpiryService.expireHoldsCreatedBefore(LocalDateTime.now().plusMinutes(1));
            payAtHotel(guest, bookingId).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value(containsString("expired")));
        }

        @Test
        void unpaidPayAtHotelCancellationReleasesRoomsWithoutRefund() throws Exception {
            long bookingId = reserved(guest, 1);
            payAtHotel(guest, bookingId).andExpect(status().isOk());
            mvc.perform(auth(patch("/api/v1/bookings/" + bookingId + "/cancel"), guest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.paymentStatus").value("CANCELLED"));
            verify(gateway, never()).refund(anyString(), anyLong(), anyString());
            bookedOn(IN, 0);
        }

        @Test
        void managerMarksPaymentCollectedAfterWhichOnlineCancellationIsRefused() throws Exception {
            long bookingId = reserved(guest, 1);
            payAtHotel(guest, bookingId).andExpect(status().isOk());

            mvc.perform(auth(get("/api/v1/admin/bookings").param("reference", String.valueOf(bookingId)), manager))
                    .andExpect(jsonPath("$.content[0].paymentMethod").value("PAY_AT_HOTEL"))
                    .andExpect(jsonPath("$.content[0].paymentStatus").value("PENDING"));
            collected(signup("HOTEL_MANAGER"), bookingId).andExpect(status().isNotFound());
            collected(manager, bookingId).andExpect(status().isOk())
                    .andExpect(jsonPath("$.paymentStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.paymentMethod").value("PAY_AT_HOTEL"));
            collected(manager, bookingId).andExpect(status().isConflict());

            myBooking(guest, bookingId).andExpect(jsonPath("$.canCancel").value(false));
            mvc.perform(auth(patch("/api/v1/bookings/" + bookingId + "/cancel"), guest)).andExpect(status().isConflict());
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/cancel"), manager))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(containsString("at the hotel")));
            verify(gateway, never()).refund(anyString(), anyLong(), anyString());
            bookedOn(IN, 1);
        }

        @Test
        void onlyPayAtHotelBookingsCanBeMarkedCollected() throws Exception {
            long online = confirmedBooking(guest, 1);
            collected(manager, online).andExpect(status().isConflict());
            long unpaid = reserved(guest, 1);
            collected(manager, unpaid).andExpect(status().isConflict());
        }

        @Test
        void switchingAfterStartingOnlineCheckoutIsSafe() throws Exception {
            long bookingId = reserved(guest, 1);
            String orderId = JsonPath.read(createOrder(guest, bookingId), "$.orderId");
            payAtHotel(guest, bookingId).andExpect(status().isOk());

            // A late failure report from the abandoned online attempt doesn't override pay-at-hotel.
            mvc.perform(auth(post("/api/v1/payments/" + bookingId + "/failure"), guest).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"razorpayOrderId\":\"%s\",\"description\":\"cancelled\"}".formatted(orderId)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                    .andExpect(jsonPath("$.paymentMethod").value("PAY_AT_HOTEL"));

            // If that online payment still completes, the booking is recorded as paid online; no second booking.
            pay(guest, bookingId, orderId, 500_000L, "captured").andExpect(status().isOk())
                    .andExpect(jsonPath("$.booking.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.booking.paymentStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.booking.paymentMethod").value("ONLINE"));
            bookedOn(IN, 1);
        }
    }

    // ---------------------------------------------------------------- My Bookings & manager view

    @Nested
    class Views {

        @Test
        void myBookingsAreScopedFilteredAndPaginated() throws Exception {
            long confirmed = confirmedBooking(guest, 1);
            long pending = reserved(guest, 1);
            long cancelled = reserved(guest, 1);
            mvc.perform(auth(patch("/api/v1/bookings/" + cancelled + "/cancel"), guest)).andExpect(status().isOk());

            mvc.perform(auth(get("/api/v1/bookings").param("scope", "upcoming"), guest))
                    .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].id").value(confirmed))
                    .andExpect(jsonPath("$.content[0].amount").value(5000.0))
                    .andExpect(jsonPath("$.content[0].paymentStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.content[0].hotelName").isNotEmpty());
            mvc.perform(auth(get("/api/v1/bookings").param("scope", "pending"), guest))
                    .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].id").value(pending));
            mvc.perform(auth(get("/api/v1/bookings").param("scope", "cancelled"), guest))
                    .andExpect(jsonPath("$.content", hasSize(1))).andExpect(jsonPath("$.content[0].id").value(cancelled));
            mvc.perform(auth(get("/api/v1/bookings").param("scope", "past"), guest)).andExpect(jsonPath("$.content", hasSize(0)));
            mvc.perform(auth(get("/api/v1/bookings").param("size", "2"), guest))
                    .andExpect(jsonPath("$.content", hasSize(2))).andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2));
            mvc.perform(auth(get("/api/v1/bookings").param("scope", "bogus"), guest)).andExpect(status().isBadRequest());

            Account other = signup("GUEST");
            mvc.perform(auth(get("/api/v1/bookings"), other)).andExpect(jsonPath("$.totalElements").value(0));
            myBooking(other, confirmed).andExpect(status().isNotFound());
            myBooking(guest, confirmed).andExpect(status().isOk()).andExpect(jsonPath("$.guests[0].name").value("Asha"))
                    .andExpect(jsonPath("$.nights").value(2)).andExpect(jsonPath("$.roomsCount").value(1));
        }

        @Test
        void managerSeesPaymentInfoAndCancellingRefunds() throws Exception {
            long bookingId = confirmedBooking(guest, 1);
            mvc.perform(auth(get("/api/v1/admin/bookings").param("reference", "BK" + String.format("%06d", bookingId)), manager))
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.content[0].amount").value(5000.0))
                    .andExpect(jsonPath("$.content[0].paymentStatus").value("CONFIRMED"));
            mvc.perform(auth(get("/api/v1/admin/bookings").param("reference", "nonsense"), manager)).andExpect(status().isBadRequest());

            Account otherManager = signup("HOTEL_MANAGER");
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/cancel"), otherManager)).andExpect(status().isNotFound());
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/accept"), manager)).andExpect(status().isConflict());

            when(gateway.refund(anyString(), eq(500_000L), anyString())).thenReturn(new GatewayRefund("rfnd_m", "p", 500_000L, "processed"));
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/cancel"), manager))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
            bookedOn(IN, 0);
            mvc.perform(auth(patch("/api/v1/admin/bookings/" + bookingId + "/cancel"), manager)).andExpect(status().isConflict());
        }
    }
}
