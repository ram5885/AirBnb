#!/usr/bin/env python3
"""
Seeds the demo dataset (scripts/demo-data/*.csv) into a running stack through the public API,
replacing the monolith's DemoDataSeeder ("demo" profile). Same files, same rules:

  users.csv           email, name, role (HOTEL_MANAGER or GUEST)        -> signup (or login if it exists)
  guest_profiles.csv  user_email, name, gender, age                      -> saved guests of a guest account
  hotels.csv          manager_email, name, state, city, address, ...     -> hotels (keyed by manager + name)
  room_types.csv      manager_email, hotel_name, type, base_price, ...   -> room types (keyed by hotel + type)
  bookings.csv        manager_email, hotel_name, room_type, guest_email, check_in_after_days, nights, rooms,
                      guests, status (RESERVED, ACCEPTED or CANCELLED)   -> bookings, only for hotels without any yet

Idempotent and non-destructive: existing records are never modified; invalid rows are reported and skipped.
Every demo account gets DEMO_PASSWORD (default Demo@12345). Going through the API means all validation,
inventory generation and availability rules apply, exactly as for real users.

  python3 scripts/seed-demo-data.py [--api http://localhost:8080/api] [--data scripts/demo-data]
"""
import argparse
import csv
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import date, timedelta

API = "http://localhost:8080/api"


def call(method, path, token=None, body=None, raw=None, content_type=None):
    headers = {}
    data = None
    if raw is not None:
        data, headers["Content-Type"] = raw, content_type
    elif body is not None:
        data, headers["Content-Type"] = json.dumps(body).encode(), "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(API + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            text = r.read()
            return r.status, (json.loads(text) if text else None)
    except urllib.error.HTTPError as e:
        text = e.read()
        try:
            return e.code, json.loads(text)
        except ValueError:
            return e.code, {"error": text.decode(errors="replace")}


def multipart(data):
    boundary = "----seed" + uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\nContent-Type: application/json\r\n\r\n'
            .encode() + json.dumps(data).encode() + f"\r\n--{boundary}--\r\n".encode())
    return body, "multipart/form-data; boundary=" + boundary


def rows(folder, name, required=True):
    path = os.path.join(folder, name)
    if not os.path.exists(path):
        if required:
            sys.exit(f"Demo data file not found: {path}")
        return []
    with open(path, newline="", encoding="utf-8-sig") as f:
        return [({k.strip(): (v or "").strip() for k, v in r.items()}, i + 2) for i, r in enumerate(csv.DictReader(f))]


def all_pages(path, token):
    page, items = 0, []
    while True:
        sep = "&" if "?" in path else "?"
        status, body = call("GET", f"{path}{sep}page={page}&size=100", token)
        if status != 200:
            raise RuntimeError(f"GET {path}: HTTP {status} {body}")
        items += body["content"]
        if page + 1 >= body["totalPages"]:
            return items
        page += 1


def main():
    global API
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--api", default=os.environ.get("SEED_API", API))
    parser.add_argument("--data", default=os.path.join(os.path.dirname(__file__), "demo-data"))
    args = parser.parse_args()
    API = args.api.rstrip("/")
    password = os.environ.get("DEMO_PASSWORD", "Demo@12345")
    report = {k: 0 for k in ("users_created", "users_existing", "guest_profiles_created", "hotels_created",
                             "hotels_existing", "room_types_created", "room_types_existing", "bookings_created",
                             "hotels_with_existing_bookings", "invalid_rows", "failures")}

    def invalid(file, line, why):
        report["invalid_rows"] += 1
        print(f"  skipped {file}:{line}: {why}")

    # ---------------------------------------------------------------- users
    accounts = {}
    for r, line in rows(args.data, "users.csv"):
        email, role = r["email"].lower(), r["role"]
        if role not in ("HOTEL_MANAGER", "GUEST") or not r["name"] or "@" not in email:
            invalid("users.csv", line, "needs a valid email, a name and role HOTEL_MANAGER or GUEST")
            continue
        status, body = call("POST", "/auth/signup", body={"name": r["name"], "email": email, "password": password,
                                                          "role": role})
        if status == 201:
            report["users_created"] += 1
        elif status == 409:
            status, body = call("POST", "/auth/login", body={"email": email, "password": password})
            if status != 200:
                print(f"  {email} already exists with another password; left unchanged and skipped")
                continue
            report["users_existing"] += 1
        else:
            report["failures"] += 1
            print(f"  signup {email} failed: HTTP {status} {body}")
            continue
        if role not in body["roles"]:
            print(f"  existing account {email} is not a {role}; left unchanged and skipped")
            continue
        accounts[email] = {"token": body["token"], "role": role}

    # ---------------------------------------------------------------- guest profiles
    profiles = {}
    wanted = {}
    for r, line in rows(args.data, "guest_profiles.csv", required=False):
        email = r["user_email"].lower()
        if accounts.get(email, {}).get("role") != "GUEST" or r["gender"] not in ("MALE", "FEMALE", "OTHER") \
                or not r["age"].isdigit():
            invalid("guest_profiles.csv", line, "needs a GUEST user_email from users.csv, gender and age")
            continue
        wanted.setdefault(email, []).append(r)
    for email, people in wanted.items():
        token = accounts[email]["token"]
        existing = {g["name"]: g["id"] for g in call("GET", "/v1/guests", token)[1]}
        for p in people:
            if p["name"] not in existing:
                status, g = call("POST", "/v1/guests", token, {"name": p["name"], "gender": p["gender"], "age": int(p["age"])})
                if status == 201:
                    existing[p["name"]] = g["id"]
                    report["guest_profiles_created"] += 1
        profiles[email] = [existing[p["name"]] for p in people if p["name"] in existing]

    # ---------------------------------------------------------------- hotels and room types
    room_rows = {}
    for r, line in rows(args.data, "room_types.csv"):
        room_rows.setdefault((r["manager_email"].lower(), r["hotel_name"]), []).append((r, line))
    hotels = {}
    managers_hotels = {}
    for r, line in rows(args.data, "hotels.csv"):
        manager = r["manager_email"].lower()
        if accounts.get(manager, {}).get("role") != "HOTEL_MANAGER":
            invalid("hotels.csv", line, "manager_email must be a HOTEL_MANAGER in users.csv")
            continue
        token = accounts[manager]["token"]
        if manager not in managers_hotels:
            managers_hotels[manager] = {h["name"]: h["id"] for h in all_pages("/v1/admin/hotels", token)}
        hotel_id = managers_hotels[manager].get(r["name"])
        if hotel_id:
            report["hotels_existing"] += 1
        else:
            data = {"name": r["name"], "state": r["state"], "city": r["city"], "address": r["address"],
                    "landmark": r.get("landmark") or None, "contactPerson": r["contact_person"],
                    "phoneNumber": r["phone_number"], "email": r["email"],
                    "amenities": [a for a in r.get("amenities", "").split("|") if a],
                    "active": r.get("active", "true").lower() != "false"}
            body, ctype = multipart(data)
            status, created = call("POST", "/v1/admin/hotels", token, raw=body, content_type=ctype)
            if status != 201:
                invalid("hotels.csv", line, f"HTTP {status} {created.get('error')} {created.get('fieldErrors', '')}")
                continue
            hotel_id = created["id"]
            managers_hotels[manager][r["name"]] = hotel_id
            report["hotels_created"] += 1
        hotels[(manager, r["name"])] = hotel_id
        existing_types = {x["type"].lower(): x["id"] for x in call("GET", f"/v1/admin/hotels/{hotel_id}/rooms", token)[1]}
        for room, rline in room_rows.get((manager, r["name"]), []):
            if room["type"].lower() in existing_types:
                report["room_types_existing"] += 1
                continue
            body, ctype = multipart({"type": room["type"], "basePrice": room["base_price"],
                                     "totalCount": int(room["total_count"]), "capacity": int(room["capacity"]),
                                     "amenities": [a for a in room.get("amenities", "").split("|") if a]})
            status, created = call("POST", f"/v1/admin/hotels/{hotel_id}/rooms", token, raw=body, content_type=ctype)
            if status == 201:
                existing_types[room["type"].lower()] = created["id"]
                report["room_types_created"] += 1
            else:
                invalid("room_types.csv", rline, f"HTTP {status} {created.get('error')}")

    # ---------------------------------------------------------------- bookings
    bookings = {}
    for r, line in rows(args.data, "bookings.csv", required=False):
        key = (r["manager_email"].lower(), r["hotel_name"])
        if key not in hotels or accounts.get(r["guest_email"].lower(), {}).get("role") != "GUEST" \
                or r["status"] not in ("RESERVED", "ACCEPTED", "CANCELLED"):
            invalid("bookings.csv", line, "needs a seeded hotel, a GUEST guest_email and status RESERVED/ACCEPTED/CANCELLED")
            continue
        bookings.setdefault(key, []).append((r, line))
    for (manager, name), items in bookings.items():
        token = accounts[manager]["token"]
        hotel_id = hotels[(manager, name)]
        if all_pages(f"/v1/admin/bookings?hotelId={hotel_id}", token):
            report["hotels_with_existing_bookings"] += 1
            continue
        types = {x["type"].lower(): x["id"] for x in call("GET", f"/v1/admin/hotels/{hotel_id}/rooms", token)[1]}
        for r, line in items:
            room_id = types.get(r["room_type"].lower())
            if not room_id:
                invalid("bookings.csv", line, f"room type '{r['room_type']}' doesn't exist for this hotel")
                continue
            guest = r["guest_email"].lower()
            check_in = date.today() + timedelta(days=int(r["check_in_after_days"]))
            party = profiles.get(guest, [])[:int(r["guests"])]
            status, created = call("POST", "/v1/bookings", accounts[guest]["token"], {
                "hotelId": hotel_id, "roomId": room_id, "checkInDate": check_in.isoformat(),
                "checkOutDate": (check_in + timedelta(days=int(r["nights"]))).isoformat(),
                "roomsCount": int(r["rooms"]), "guestIds": party})
            if status != 201:
                report["failures"] += 1
                print(f"  bookings.csv:{line}: HTTP {status} {created.get('error')}")
                continue
            if r["status"] in ("ACCEPTED", "CANCELLED"):
                action = "accept" if r["status"] == "ACCEPTED" else "cancel"
                call("PATCH", f"/v1/admin/bookings/{created['id']}/{action}", token)
            report["bookings_created"] += 1

    print("Demo data: " + ", ".join(f"{k.replace('_', ' ')} {v}" for k, v in report.items()))
    return 1 if report["failures"] else 0


if __name__ == "__main__":
    sys.exit(main())
