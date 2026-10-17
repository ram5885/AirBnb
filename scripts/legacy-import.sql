-- One-time import of the monolith's data (restored read-only copy in schema :"stage") into the microservice
-- schemas auth / catalog / booking / review. Run by scripts/migrate-legacy-data.sh as the database superuser.
--
--  * One transaction: everything is imported or nothing is (ON_ERROR_STOP + explicit checks).
--  * Refuses to run if any target business table already has rows (no merging into live data).
--  * Ids are preserved (bookings, reviews, photo URLs keep working); identity sequences are moved past them.
--  * Locations are matched by (city, state): catalog-service may already have seeded the same city list.
--  * New columns: booking snapshots (hotel/room/user names), booking.hotel_owner_id, the inventory hold of every
--    booking (hold_key derived from the booking id), review snapshots, catalog.hotel_rating.
--  * The legacy database itself is never modified.

\set ON_ERROR_STOP on
BEGIN;

-- ---------------------------------------------------------------- guards
DO $$
DECLARE
    busy text;
BEGIN
    SELECT string_agg(t, ', ') INTO busy FROM (
        SELECT 'auth.app_user' AS t WHERE EXISTS (SELECT 1 FROM auth.app_user)
        UNION ALL SELECT 'catalog.hotel' WHERE EXISTS (SELECT 1 FROM catalog.hotel)
        UNION ALL SELECT 'catalog.room' WHERE EXISTS (SELECT 1 FROM catalog.room)
        UNION ALL SELECT 'catalog.inventory' WHERE EXISTS (SELECT 1 FROM catalog.inventory)
        UNION ALL SELECT 'booking.guest' WHERE EXISTS (SELECT 1 FROM booking.guest)
        UNION ALL SELECT 'booking.payment' WHERE EXISTS (SELECT 1 FROM booking.payment)
        UNION ALL SELECT 'booking.booking' WHERE EXISTS (SELECT 1 FROM booking.booking)
        UNION ALL SELECT 'review.hotel_review' WHERE EXISTS (SELECT 1 FROM review.hotel_review)) x;
    IF busy IS NOT NULL THEN
        RAISE EXCEPTION 'Refusing to import: these tables already contain data: %. The import only runs into an empty stack.', busy;
    END IF;
END $$;

-- ---------------------------------------------------------------- auth
INSERT INTO auth.app_user (id, email, name, password)
SELECT id, email, name, password FROM :"stage".app_user;
INSERT INTO auth.user_roles (user_id, roles)
SELECT user_id, roles FROM :"stage".user_roles;

-- ---------------------------------------------------------------- catalog: locations (merge by city + state)
INSERT INTO catalog.location (city, state)
SELECT l.city, l.state FROM :"stage".location l
ON CONFLICT ON CONSTRAINT uk_location_city_state DO NOTHING;

CREATE TEMP TABLE location_map ON COMMIT DROP AS
SELECT l.id AS legacy_id, c.id AS new_id
FROM :"stage".location l JOIN catalog.location c ON c.city = l.city AND c.state = l.state;

INSERT INTO catalog.city_photo (location_id, file_name, content_type, size_bytes, data, source_page, image_page,
                                artist, license, created_at)
SELECT m.new_id, p.file_name, p.content_type, p.size_bytes, p.data, p.source_page, p.image_page, p.artist, p.license,
       p.created_at
FROM :"stage".city_photo p JOIN location_map m ON m.legacy_id = p.location_id
ON CONFLICT ON CONSTRAINT uk_city_photo_location DO NOTHING;

-- ---------------------------------------------------------------- catalog: hotels, rooms, photos, inventory
INSERT INTO catalog.hotel (hotel_id, name, city, location_id, owner_id, amenities, created_at, updated_at,
                           contact_person, address, phone_number, email, location, active, deleted_at)
SELECT h.hotel_id, h.name, h.city, m.new_id, h.owner_id, h.amenities, h.created_at, h.updated_at,
       h.contact_person, h.address, h.phone_number, h.email, h.location, h.active, h.deleted_at
FROM :"stage".hotel h JOIN location_map m ON m.legacy_id = h.location_id;

INSERT INTO catalog.hotel_photo (id, hotel_id, file_name, content_type, size_bytes, data, created_at)
SELECT id, hotel_id, file_name, content_type, size_bytes, data, created_at FROM :"stage".hotel_photo;

INSERT INTO catalog.room (id, hotel_id, type, base_price, amenities, total_count, capacity, created_at, updated_at,
                          deleted_at)
SELECT id, hotel_id, type, base_price, amenities, total_count, capacity, created_at, updated_at, deleted_at
FROM :"stage".room;

INSERT INTO catalog.room_photo (id, room_id, file_name, content_type, size_bytes, data, created_at)
SELECT id, room_id, file_name, content_type, size_bytes, data, created_at FROM :"stage".room_photo;

INSERT INTO catalog.inventory (id, hotel_id, room_id, date, booked_count, total_count, surge_factor, price, city,
                               closed, created_at, updated_at)
SELECT id, hotel_id, room_id, date, booked_count, total_count, surge_factor, price, city, closed, created_at,
       updated_at
FROM :"stage".inventory;

-- ---------------------------------------------------------------- booking: guests, payments, bookings
INSERT INTO booking.guest (id, user_id, name, gender, age)
SELECT id, user_id, name, gender, age FROM :"stage".guest;

INSERT INTO booking.payment (id, transaction_id, razorpay_order_id, razorpay_payment_id, razorpay_refund_id, currency,
                             failure_reason, payment_status, amount, method, created_at, updated_at)
SELECT id, transaction_id, razorpay_order_id, razorpay_payment_id, razorpay_refund_id, currency, failure_reason,
       payment_status, amount, method, created_at, updated_at
FROM :"stage".payment;

INSERT INTO booking.payment_webhook_event (event_id, event_type, received_at)
SELECT event_id, event_type, received_at FROM :"stage".payment_webhook_event;

-- Every booking gets a deterministic hold key; inventory-holding bookings keep their rooms HELD.
INSERT INTO booking.booking (booking_id, hotel_id, room_id, user_id, hotel_owner_id, rooms_count, amount,
                             check_in_date, check_out_date, created_at, updated_at, payment_id, booking_status,
                             hold_key, hold_status, hotel_name, hotel_city, hotel_state, room_type, room_capacity,
                             user_name, user_email)
SELECT b.booking_id, b.hotel_id, b.room_id, b.user_id, h.owner_id, b.rooms_count, b.amount,
       b.check_in_date, b.check_out_date, b.created_at, b.updated_at, b.payment_id, b.booking_status,
       md5('legacy-booking-' || b.booking_id)::uuid,
       CASE WHEN b.booking_status IN ('RESERVED', 'ACCEPTED', 'CONFIRMED') THEN 'HELD' ELSE 'RELEASED' END,
       h.name, l.city, l.state, r.type, r.capacity, u.name, u.email
FROM :"stage".booking b
JOIN :"stage".hotel h ON h.hotel_id = b.hotel_id
JOIN :"stage".location l ON l.id = h.location_id
JOIN :"stage".room r ON r.id = b.room_id
JOIN :"stage".app_user u ON u.id = b.user_id;

INSERT INTO booking.booking_guest (booking_id, guest_id)
SELECT booking_id, guest_id FROM :"stage".booking_guest;

-- The inventory side of each booking (its rooms are already counted in the copied inventory.booked_count).
INSERT INTO catalog.inventory_hold (hold_key, booking_id, hotel_id, room_id, check_in_date, check_out_date,
                                    rooms_count, amount, status, created_at, updated_at)
SELECT md5('legacy-booking-' || b.booking_id)::uuid, b.booking_id, b.hotel_id, b.room_id, b.check_in_date,
       b.check_out_date, b.rooms_count, COALESCE(b.amount, 0),
       CASE WHEN b.booking_status IN ('RESERVED', 'ACCEPTED', 'CONFIRMED') THEN 'HELD' ELSE 'RELEASED' END,
       b.created_at, b.updated_at
FROM :"stage".booking b;

-- ---------------------------------------------------------------- review
INSERT INTO review.hotel_review (id, hotel_id, user_id, booking_id, rating, comment, created_at, reviewer_name,
                                 stayed_on, room_type)
SELECT v.id, v.hotel_id, v.user_id, v.booking_id, v.rating, v.comment, v.created_at, u.name, b.check_in_date, r.type
FROM :"stage".hotel_review v
JOIN :"stage".app_user u ON u.id = v.user_id
JOIN :"stage".booking b ON b.booking_id = v.booking_id
JOIN :"stage".room r ON r.id = b.room_id;

-- Search's rating read model (review-service also re-syncs it on startup).
INSERT INTO catalog.hotel_rating (hotel_id, rating_sum, rating_count, updated_at)
SELECT hotel_id, SUM(rating), COUNT(*), NOW() FROM review.hotel_review GROUP BY hotel_id
ON CONFLICT (hotel_id) DO UPDATE SET rating_sum = EXCLUDED.rating_sum, rating_count = EXCLUDED.rating_count,
                                     updated_at = NOW();

-- ---------------------------------------------------------------- identity sequences past the imported ids
SELECT setval(pg_get_serial_sequence(t.tbl, t.col), COALESCE(t.max_id, 0) + 1, false)
FROM (VALUES
          ('auth.app_user', 'id', (SELECT MAX(id) FROM auth.app_user)),
          ('catalog.location', 'id', (SELECT MAX(id) FROM catalog.location)),
          ('catalog.city_photo', 'id', (SELECT MAX(id) FROM catalog.city_photo)),
          ('catalog.hotel', 'hotel_id', (SELECT MAX(hotel_id) FROM catalog.hotel)),
          ('catalog.hotel_photo', 'id', (SELECT MAX(id) FROM catalog.hotel_photo)),
          ('catalog.room', 'id', (SELECT MAX(id) FROM catalog.room)),
          ('catalog.room_photo', 'id', (SELECT MAX(id) FROM catalog.room_photo)),
          ('catalog.inventory', 'id', (SELECT MAX(id) FROM catalog.inventory)),
          ('catalog.inventory_hold', 'id', (SELECT MAX(id) FROM catalog.inventory_hold)),
          ('booking.guest', 'id', (SELECT MAX(id) FROM booking.guest)),
          ('booking.payment', 'id', (SELECT MAX(id) FROM booking.payment)),
          ('booking.booking', 'booking_id', (SELECT MAX(booking_id) FROM booking.booking)),
          ('review.hotel_review', 'id', (SELECT MAX(id) FROM review.hotel_review))) AS t(tbl, col, max_id);

-- ---------------------------------------------------------------- verification: every legacy row arrived
CREATE TEMP TABLE import_report ON COMMIT DROP AS
SELECT * FROM (VALUES
    ('app_user', (SELECT COUNT(*) FROM :"stage".app_user), (SELECT COUNT(*) FROM auth.app_user)),
    ('user_roles', (SELECT COUNT(*) FROM :"stage".user_roles), (SELECT COUNT(*) FROM auth.user_roles)),
    ('hotel', (SELECT COUNT(*) FROM :"stage".hotel), (SELECT COUNT(*) FROM catalog.hotel)),
    ('hotel_photo', (SELECT COUNT(*) FROM :"stage".hotel_photo), (SELECT COUNT(*) FROM catalog.hotel_photo)),
    ('room', (SELECT COUNT(*) FROM :"stage".room), (SELECT COUNT(*) FROM catalog.room)),
    ('room_photo', (SELECT COUNT(*) FROM :"stage".room_photo), (SELECT COUNT(*) FROM catalog.room_photo)),
    ('inventory', (SELECT COUNT(*) FROM :"stage".inventory), (SELECT COUNT(*) FROM catalog.inventory)),
    ('location (legacy cities present)', (SELECT COUNT(*) FROM :"stage".location), (SELECT COUNT(*) FROM location_map)),
    ('guest', (SELECT COUNT(*) FROM :"stage".guest), (SELECT COUNT(*) FROM booking.guest)),
    ('payment', (SELECT COUNT(*) FROM :"stage".payment), (SELECT COUNT(*) FROM booking.payment)),
    ('payment_webhook_event', (SELECT COUNT(*) FROM :"stage".payment_webhook_event), (SELECT COUNT(*) FROM booking.payment_webhook_event)),
    ('booking', (SELECT COUNT(*) FROM :"stage".booking), (SELECT COUNT(*) FROM booking.booking)),
    ('booking_guest', (SELECT COUNT(*) FROM :"stage".booking_guest), (SELECT COUNT(*) FROM booking.booking_guest)),
    ('inventory_hold (one per booking)', (SELECT COUNT(*) FROM :"stage".booking), (SELECT COUNT(*) FROM catalog.inventory_hold)),
    ('hotel_review', (SELECT COUNT(*) FROM :"stage".hotel_review), (SELECT COUNT(*) FROM review.hotel_review))
) AS r(table_name, legacy_rows, imported_rows);

SELECT table_name, legacy_rows, imported_rows,
       CASE WHEN legacy_rows = imported_rows THEN 'ok' ELSE 'MISMATCH' END AS check
FROM import_report;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM import_report WHERE legacy_rows <> imported_rows) THEN
        RAISE EXCEPTION 'Row counts differ (see the report above); nothing was imported.';
    END IF;
    -- Rooms held by imported bookings must match the copied inventory counters (sanity check of the holds).
    IF EXISTS (
        SELECT 1 FROM catalog.inventory i
        WHERE i.booked_count <> COALESCE((SELECT SUM(h.rooms_count) FROM catalog.inventory_hold h
                                          WHERE h.status = 'HELD' AND h.room_id = i.room_id
                                            AND i.date >= h.check_in_date AND i.date < h.check_out_date), 0)) THEN
        RAISE WARNING 'Some inventory booked_count values differ from the held bookings (legacy data drift); kept as-is.';
    END IF;
END $$;

COMMIT;
