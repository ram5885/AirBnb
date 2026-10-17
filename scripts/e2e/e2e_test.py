#!/usr/bin/env python3
"""
End-to-end test of the AirBnb microservices stack through the real entry point (nginx -> API gateway -> services
-> PostgreSQL). Started by scripts/e2e/run-e2e.sh against an isolated Compose project; payments go to the Razorpay
test double (compose.e2e.yaml), so the backend's real verification code runs (signature, server-side fetch,
amount/currency checks, capture, refunds, webhooks).

Covers the critical workflow: register/login -> search -> filters -> hotel page -> availability -> booking ->
Razorpay test payment -> backend verification -> consistent booking/payment status -> My Bookings -> review, plus
double-booking prevention, webhooks, cancellation with refund, manager actions, security rules and persistence
across container restarts.

Only Python's standard library. Exit code 0 = all checks passed.
"""
import hashlib
import hmac
import json
import os
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zlib
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta

API = os.environ.get("E2E_API", "http://localhost:3100/api")          # through nginx (frontend container)
GATEWAY = os.environ.get("E2E_GATEWAY", "http://localhost:8180/api")  # the gateway directly
MOCK = os.environ.get("E2E_RAZORPAY_MOCK", "http://localhost:9100")
PROJECT = os.environ.get("E2E_PROJECT", "airbnb-e2e")
COMPOSE = ["docker", "compose", "-p", PROJECT, "-f", "compose.yaml", "-f", "compose.e2e.yaml"]
KEY_SECRET = "e2e_mock_key_secret_not_a_real_secret"        # matches compose.e2e.yaml (test double only)
WEBHOOK_SECRET = "e2e_mock_webhook_secret_not_a_real_secret"

RUN = uuid.uuid4().hex[:8]
IN = date.today() + timedelta(days=20)
OUT = IN + timedelta(days=2)
passed, failed = [], []


# ---------------------------------------------------------------- tiny HTTP + assertion helpers

class Response:
    def __init__(self, status, body, headers):
        self.status, self.raw, self.headers = status, body, headers

    @property
    def json(self):
        return json.loads(self.raw) if self.raw else None


def call(method, path, body=None, token=None, base=None, headers=None, raw=None, content_type=None):
    url = (base or API) + path
    data, hdrs = None, dict(headers or {})
    if raw is not None:
        data = raw
        if content_type:
            hdrs["Content-Type"] = content_type
    elif body is not None:
        data = json.dumps(body).encode()
        hdrs["Content-Type"] = "application/json"
    if token:
        hdrs["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, method=method, headers=hdrs)
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            return Response(r.status, r.read(), dict(r.headers))
    except urllib.error.HTTPError as e:
        return Response(e.code, e.read(), dict(e.headers))


def check(name, condition, detail=""):
    (passed if condition else failed).append(name)
    print(("  PASS  " if condition else "  FAIL  ") + name + ("" if condition else f"  -> {detail}"), flush=True)
    return condition


def expect(name, response, status, extra=None):
    ok = response.status == status and (extra is None or extra(response.json))
    return check(name, ok, f"HTTP {response.status} {response.raw[:300]!r}")


def section(title):
    print(f"\n== {title}", flush=True)


def png():
    """A valid 4x3 RGB PNG (the services check magic bytes and parse the header)."""
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + b"\x80\x40\x20" * 4 for _ in range(3))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 4, 3, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def multipart(data, files=()):
    boundary = "----e2e" + uuid.uuid4().hex
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\nContent-Type: application/json\r\n\r\n'
             .encode() + json.dumps(data).encode() + b"\r\n"]
    for name, content in files:
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="photos"; filename="{name}"\r\n'
                     f"Content-Type: image/png\r\n\r\n".encode() + content + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), "multipart/form-data; boundary=" + boundary


def sign(message, secret):
    return hmac.new(secret.encode(), message.encode(), hashlib.sha256).hexdigest()


def sql(statement):
    """Test-only fixture changes (e.g. move a stay into the past, which the API rightly refuses)."""
    env_user = os.environ.get("POSTGRES_USER", "airbnb_admin")
    subprocess.run(COMPOSE + ["exec", "-T", "postgres", "psql", "-v", "ON_ERROR_STOP=1", "-U", env_user,
                              "-d", "airbnb", "-c", statement], check=True, capture_output=True)


def signup(role, name):
    email = f"{role.lower()}-{RUN}-{uuid.uuid4().hex[:6]}@e2e.test"
    r = call("POST", "/auth/signup", {"name": name, "email": email, "password": "E2e-secret-123", "role": role})
    assert r.status == 201, r.raw
    return {"id": r.json["id"], "token": r.json["token"], "email": email}


def ids(page):
    return [h["id"] for h in page["content"]]


def search(**params):
    q = {"city": "Wai", "state": "Maharashtra", "checkinDate": IN.isoformat(), "checkoutDate": OUT.isoformat(),
         "numberOfRooms": 1, "size": 50}
    q.update(params)
    return call("GET", "/v1/hotels/search?" + urllib.parse.urlencode(q))


def pay(guest, booking_id, status="captured", amount=None):
    """Opens an order, plays the customer paying in Checkout, and returns (order, payment_id, signature)."""
    order = call("POST", f"/v1/payments/{booking_id}", token=guest["token"]).json
    payment_id = "pay_" + uuid.uuid4().hex[:14]
    urllib.request.urlopen(urllib.request.Request(MOCK + "/__test/payments", method="POST",
                                                  headers={"Content-Type": "application/json"},
                                                  data=json.dumps({"id": payment_id, "order_id": order["orderId"],
                                                                   "amount": amount or order["amount"],
                                                                   "currency": "INR", "status": status}).encode()))
    return order, payment_id, sign(order["orderId"] + "|" + payment_id, KEY_SECRET)


def book(guest, hotel_id, room_id, rooms=1, guest_ids=(), check_in=IN, check_out=OUT):
    return call("POST", "/v1/bookings", {"hotelId": hotel_id, "roomId": room_id, "checkInDate": check_in.isoformat(),
                                         "checkOutDate": check_out.isoformat(), "roomsCount": rooms,
                                         "guestIds": list(guest_ids), "amount": 1}, token=guest["token"])


def main():
    section("1. Stack reachable through nginx and the gateway")
    expect("GET /api/locations/states via nginx", call("GET", "/locations/states"), 200, lambda b: "Maharashtra" in b)
    expect("GET /api/v1/amenities via gateway", call("GET", "/v1/amenities", base=GATEWAY), 200, lambda b: len(b) > 5)

    section("2. Registration and login (auth-service)")
    manager = signup("HOTEL_MANAGER", "Meera Manager")
    guest = signup("GUEST", "Gita Guest")
    expect("login returns a token", call("POST", "/auth/login", {"email": guest["email"], "password": "E2e-secret-123"}),
           200, lambda b: b["token"] and b["roles"] == ["GUEST"])
    expect("wrong password is 401", call("POST", "/auth/login", {"email": guest["email"], "password": "nope"}), 401,
           lambda b: b["error"] == "Invalid email or password")
    expect("duplicate email is 409", call("POST", "/auth/signup", {"name": "x", "email": guest["email"],
                                                                    "password": "p", "role": "GUEST"}), 409)

    section("3. Manager creates a hotel, room types and sees inventory (catalog-service)")
    body, ctype = multipart({"name": f"E2E Lakeside {RUN}", "state": "Maharashtra", "city": "Wai",
                             "address": "1 Lake Road", "landmark": "Near the ghat", "contactPerson": "Meera",
                             "phoneNumber": "9876543210", "email": "desk@e2e.test",
                             "amenities": ["WIFI", "SWIMMING_POOL"], "active": True}, [("front.png", png())])
    r = call("POST", "/v1/admin/hotels", raw=body, content_type=ctype, token=manager["token"])
    expect("create hotel with photo (multipart through nginx + gateway)", r, 201, lambda b: len(b["photos"]) == 1)
    hotel = r.json["id"]
    rooms = {}
    for rtype, price, total, amenities in (("Deluxe", 2500, 2, ["AIR_CONDITIONING", "TV"]), ("Suite", 6000, 1, ["TV"])):
        body, ctype = multipart({"type": rtype, "basePrice": price, "totalCount": total, "capacity": 2,
                                 "amenities": amenities})
        r = call("POST", f"/v1/admin/hotels/{hotel}/rooms", raw=body, content_type=ctype, token=manager["token"])
        expect(f"create room type {rtype}", r, 201)
        rooms[rtype] = r.json["id"]
    expect("365 days of inventory generated", call("GET", f"/v1/admin/hotels/{hotel}/rooms/{rooms['Deluxe']}/inventory"
                                                   f"?startDate={date.today()}&endDate={date.today() + timedelta(days=364)}",
                                                   token=manager["token"]), 200, lambda b: len(b) == 365)
    expect("manager dashboard (booking-service + catalog counts)", call("GET", "/v1/admin/dashboard", token=manager["token"]),
           200, lambda b: b["totalHotels"] == 1 and "RESERVED" in b["bookingsByStatus"])

    section("4. Guest search and combinable filters (catalog-service)")
    expect("search by city/dates finds the hotel", search(), 200, lambda b: hotel in ids(b))
    expect("amenities TV + maxPrice 3000 -> Deluxe qualifies", search(amenities="TV,WIFI", maxPrice=3000), 200,
           lambda b: hotel in ids(b) and next(h for h in b["content"] if h["id"] == hotel)["lowestNightlyPrice"] == 2500)
    expect("minPrice 7000 excludes it", search(minPrice=7000), 200, lambda b: hotel not in ids(b))
    expect("unknown amenity is 400", search(amenities="TELEPORTER"), 400, lambda b: "Unknown amenity" in b["error"])
    expect("minRating 4 excludes unreviewed hotels", search(minRating=4), 200, lambda b: hotel not in ids(b))
    expect("home page sections include the city", call("GET", f"/v1/hotels/sections?checkinDate={IN}&checkoutDate={OUT}"
                                                       f"&numberOfRooms=1&maxCities=50"), 200,
           lambda b: any(s["city"] == "Wai" for s in b))
    expect("destinations include the city", call("GET", "/locations/destinations"), 200,
           lambda b: any(d["city"] == "Wai" for d in b))

    section("5. Hotel page, photo and room availability")
    r = call("GET", f"/v1/hotels/{hotel}?checkinDate={IN}&checkoutDate={OUT}&numberOfRooms=1")
    expect("public hotel page hides private contact data", r, 200,
           lambda b: b["address"] == "1 Lake Road" and "contactInfo" not in b and len(b["roomTypes"]) == 2)
    photo = call("GET", r.json["photoUrls"][0][len("/api"):])
    check("public hotel photo served without login", photo.status == 200 and photo.raw.startswith(b"\x89PNG"),
          photo.status)
    expect("room page shows nightly availability", call("GET", f"/v1/hotels/{hotel}/rooms/{rooms['Deluxe']}?checkinDate={IN}"
                                                        f"&checkoutDate={OUT}&numberOfRooms=1"), 200,
           lambda b: b["availability"]["available"] and b["availability"]["totalPrice"] == 5000 and len(b["nights"]) == 2)

    section("6. Booking with server-side price (booking-service -> catalog inventory hold)")
    g = call("POST", "/v1/guests", {"name": "Gita", "gender": "FEMALE", "age": 31}, token=guest["token"])
    expect("save travelling guest", g, 201)
    r = book(guest, hotel, rooms["Deluxe"], guest_ids=[g.json["id"]])
    expect("reserve: RESERVED at 2 nights x 2500, client amount ignored", r, 201,
           lambda b: b["status"] == "RESERVED" and b["amount"] == 5000 and b["canPay"] and b["hotelName"].startswith("E2E"))
    booking = r.json["id"]
    expect("inventory shows the held room", call("GET", f"/v1/admin/hotels/{hotel}/rooms/{rooms['Deluxe']}/inventory"
                                                  f"?startDate={IN}&endDate={IN}", token=manager["token"]), 200,
           lambda b: b[0]["bookedCount"] == 1)

    section("7. Razorpay test-mode payment and backend verification")
    order, payment_id, signature = pay(guest, booking)
    check("order uses the server amount in paise and a test key, never the secret",
          order["amount"] == 500000 and order["keyId"].startswith("rzp_test_") and KEY_SECRET not in json.dumps(order),
          order)
    expect("forged signature is rejected", call("POST", "/v1/payments/verify", {
        "bookingId": booking, "razorpayOrderId": order["orderId"], "razorpayPaymentId": payment_id,
        "razorpaySignature": sign(order["orderId"] + "|" + payment_id, "attacker-secret")}, token=guest["token"]), 400)
    expect("booking still RESERVED after forged verification", call("GET", f"/v1/bookings/{booking}", token=guest["token"]),
           200, lambda b: b["status"] == "RESERVED")
    r = call("POST", "/v1/payments/verify", {"bookingId": booking, "razorpayOrderId": order["orderId"],
                                              "razorpayPaymentId": payment_id, "razorpaySignature": signature},
             token=guest["token"])
    expect("verified payment confirms the booking", r, 200,
           lambda b: b["confirmed"] and b["booking"]["status"] == "CONFIRMED" and b["booking"]["paymentStatus"] == "CONFIRMED")
    expect("duplicate verification is idempotent", call("POST", "/v1/payments/verify", {
        "bookingId": booking, "razorpayOrderId": order["orderId"], "razorpayPaymentId": payment_id,
        "razorpaySignature": signature}, token=guest["token"]), 200, lambda b: "already" in b["message"])
    other_booking = book(guest, hotel, rooms["Deluxe"], guest_ids=[g.json["id"]]).json["id"]
    o2, p2, _ = pay(guest, other_booking, amount=100)
    expect("amount mismatch is refused (409) and never confirms", call("POST", "/v1/payments/verify", {
        "bookingId": other_booking, "razorpayOrderId": o2["orderId"], "razorpayPaymentId": p2,
        "razorpaySignature": sign(o2["orderId"] + "|" + p2, KEY_SECRET)}, token=guest["token"]), 409,
           lambda b: not b["confirmed"] and b["booking"]["status"] == "RESERVED")

    section("8. My Bookings")
    expect("upcoming shows the confirmed booking", call("GET", "/v1/bookings?scope=upcoming", token=guest["token"]), 200,
           lambda b: any(x["id"] == booking and x["status"] == "CONFIRMED" and x["paymentStatus"] == "CONFIRMED"
                         for x in b["content"]))
    expect("pending shows the unpaid one", call("GET", "/v1/bookings?scope=pending", token=guest["token"]), 200,
           lambda b: [x["id"] for x in b["content"]] == [other_booking])

    section("9. Double booking is impossible (concurrent requests for the last room)")
    racers = [signup("GUEST", f"Racer {i}") for i in range(6)]
    with ThreadPoolExecutor(max_workers=6) as pool:
        statuses = list(pool.map(lambda acc: book(acc, hotel, rooms["Suite"]).status, racers))
    check("exactly one of 6 concurrent bookings of a 1-room type succeeds",
          statuses.count(201) == 1 and statuses.count(409) == 5, statuses)
    expect("suite inventory booked exactly once", call("GET", f"/v1/admin/hotels/{hotel}/rooms/{rooms['Suite']}/inventory"
                                                       f"?startDate={IN}&endDate={IN}", token=manager["token"]), 200,
           lambda b: b[0]["bookedCount"] == 1 and b[0]["available"] == 0)

    section("10. Razorpay webhook (signed, idempotent) and cancellation with refund")
    order3, p3, _ = pay(guest, other_booking)  # same order is reused for the retry
    event = json.dumps({"event": "payment.captured", "payload": {"payment": {"entity": {
        "id": p3, "order_id": order3["orderId"], "amount": order3["amount"], "currency": "INR", "status": "captured"}}}})
    hdrs = {"X-Razorpay-Signature": sign(event, WEBHOOK_SECRET), "x-razorpay-event-id": "evt_" + uuid.uuid4().hex}
    expect("unsigned webhook rejected", call("POST", "/v1/payments/webhook", raw=event.encode(), content_type="application/json",
                                             headers={"X-Razorpay-Signature": "deadbeef",
                                                      "x-razorpay-event-id": "evt_" + uuid.uuid4().hex}), 400)
    expect("signed webhook processed", call("POST", "/v1/payments/webhook", raw=event.encode(),
                                            content_type="application/json", headers=hdrs), 200,
           lambda b: b["status"] == "processed")
    expect("re-delivered webhook is a duplicate", call("POST", "/v1/payments/webhook", raw=event.encode(),
                                                       content_type="application/json", headers=hdrs), 200,
           lambda b: b["status"] == "duplicate")
    expect("webhook confirmed the booking", call("GET", f"/v1/bookings/{other_booking}", token=guest["token"]), 200,
           lambda b: b["status"] == "CONFIRMED")
    expect("guest cancels a paid booking: refunded in full", call("PATCH", f"/v1/bookings/{other_booking}/cancel",
                                                                  token=guest["token"]), 200,
           lambda b: b["status"] == "CANCELLED" and b["paymentStatus"] == "REFUNDED")
    expect("cancellation released the room in catalog", call("GET", f"/v1/admin/hotels/{hotel}/rooms/{rooms['Deluxe']}"
                                                             f"/inventory?startDate={IN}&endDate={IN}", token=manager["token"]),
           200, lambda b: b[0]["bookedCount"] == 1)
    expect("second cancellation is refused", call("PATCH", f"/v1/bookings/{other_booking}/cancel", token=guest["token"]), 409)

    section("11. Manager booking management and pay at hotel")
    expect("manager lists bookings of their hotel", call("GET", "/v1/admin/bookings", token=manager["token"]), 200,
           lambda b: {booking, other_booking} <= {x["id"] for x in b["content"]})
    pah = book(guest, hotel, rooms["Deluxe"], guest_ids=[g.json["id"]]).json["id"]
    expect("manager accepts a reserved booking", call("PATCH", f"/v1/admin/bookings/{pah}/accept", token=manager["token"]),
           200, lambda b: b["status"] == "ACCEPTED")
    expect("guest confirms with pay at hotel", call("POST", f"/v1/payments/{pah}/pay-at-hotel", token=guest["token"]), 200,
           lambda b: b["status"] == "CONFIRMED" and b["paymentMethod"] == "PAY_AT_HOTEL")
    expect("manager marks payment collected", call("PATCH", f"/v1/admin/bookings/{pah}/payment-collected",
                                                   token=manager["token"]), 200, lambda b: b["paymentStatus"] == "CONFIRMED")

    section("12. Reviews of completed stays (review-service) and rating filter")
    expect("future stay can't be reviewed yet", call("POST", f"/v1/hotels/{hotel}/reviews", {
        "bookingId": booking, "rating": 5, "comment": "Reviewing before arriving."}, token=guest["token"]), 409)
    # Test fixture: the stay is over (the API refuses past dates, so the dates are moved directly).
    sql(f"UPDATE booking.booking SET check_in_date = current_date - 3, check_out_date = current_date - 1 "
        f"WHERE booking_id = {booking}")
    expect("eligibility says REVIEWABLE", call("GET", f"/v1/hotels/{hotel}/reviews/eligibility", token=guest["token"]), 200,
           lambda b: any(s["bookingId"] == booking and s["status"] == "REVIEWABLE" for s in b["bookings"]))
    expect("manager can't post a guest review", call("POST", f"/v1/hotels/{hotel}/reviews", {
        "bookingId": booking, "rating": 5, "comment": "Not a guest of this hotel."}, token=manager["token"]), 403)
    expect("guest reviews the completed stay", call("POST", f"/v1/hotels/{hotel}/reviews", {
        "bookingId": booking, "rating": 5, "comment": "Lovely lake view and very helpful staff."}, token=guest["token"]),
           201, lambda b: b["reviewerName"] == "Gita" and b["verifiedStay"])
    expect("second review of the same stay refused", call("POST", f"/v1/hotels/{hotel}/reviews", {
        "bookingId": booking, "rating": 1, "comment": "Trying to review twice."}, token=guest["token"]), 409)
    expect("summary shows the rating", call("GET", f"/v1/hotels/{hotel}/reviews/summary"), 200,
           lambda b: b["totalReviews"] == 1 and b["averageRating"] == 5.0)
    expect("public review list shows first name only", call("GET", f"/v1/hotels/{hotel}/reviews"), 200,
           lambda b: b["content"][0]["reviewerName"] == "Gita" and guest["email"] not in json.dumps(b))
    time.sleep(1)  # the rating is pushed to catalog right after the review commits
    later = {"checkinDate": (IN + timedelta(days=10)).isoformat(), "checkoutDate": (OUT + timedelta(days=10)).isoformat()}
    expect("search minRating 4 now includes the hotel (rating read model updated)", search(minRating=4, **later), 200,
           lambda b: hotel in ids(b) and next(h for h in b["content"] if h["id"] == hotel)["averageRating"] == 5.0)

    section("13. Security rules at the edge")
    expect("guest token on manager API is 403", call("GET", "/v1/admin/hotels", token=guest["token"]), 403)
    expect("no token on bookings is 401", call("GET", "/v1/bookings"), 401, lambda b: b["error"] == "Please log in to continue")
    expect("internal API is not reachable from outside", call("GET", "/internal/users/1", base=GATEWAY[:-len("/api")]), 401)
    r = call("GET", "/internal/users/1", base=API[:-len("/api")])
    check("internal API is not reachable through nginx either (only /api/ is proxied; the SPA answers)",
          "text/html" in r.headers.get("Content-Type", "") and b"password" not in r.raw, r.headers)
    expect("manager can't see another guest's booking via guest API", call("GET", f"/v1/bookings/{booking}",
                                                                            token=manager["token"]), 403)

    section("14. Data survives a full restart of the stack")
    subprocess.run(COMPOSE + ["restart"], check=True, capture_output=True)
    deadline = time.time() + 240
    while time.time() < deadline:
        r = call("GET", "/locations/states")
        if r.status == 200:
            gw = call("POST", "/auth/login", {"email": guest["email"], "password": "E2e-secret-123"})
            if gw.status == 200:
                break
        time.sleep(3)
    token = call("POST", "/auth/login", {"email": guest["email"], "password": "E2e-secret-123"})
    expect("login still works after restart", token, 200)
    if token.status == 200:
        expect("booking still CONFIRMED after restart", call("GET", f"/v1/bookings/{booking}", token=token.json["token"]),
               200, lambda b: b["status"] == "CONFIRMED")
    expect("review still there after restart", call("GET", f"/v1/hotels/{hotel}/reviews/summary"), 200,
           lambda b: b["totalReviews"] == 1)

    print(f"\n{len(passed)} passed, {len(failed)} failed")
    for name in failed:
        print("  FAILED: " + name)
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
