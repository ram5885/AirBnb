package com.HotelManagement.AirBnb.config;

import com.HotelManagement.AirBnb.dto.booking.GuestBookingResponse;
import com.HotelManagement.AirBnb.dto.booking.CreateBookingRequest;
import com.HotelManagement.AirBnb.dto.hotel.HotelRequest;
import com.HotelManagement.AirBnb.dto.room.RoomRequest;
import com.HotelManagement.AirBnb.dto.room.RoomResponse;
import com.HotelManagement.AirBnb.entity.Guest;
import com.HotelManagement.AirBnb.entity.Hotel;
import com.HotelManagement.AirBnb.entity.User;
import com.HotelManagement.AirBnb.entity.enums.Amenity;
import com.HotelManagement.AirBnb.entity.enums.Gender;
import com.HotelManagement.AirBnb.entity.enums.Role;
import com.HotelManagement.AirBnb.repository.BookingRepository;
import com.HotelManagement.AirBnb.repository.GuestRepository;
import com.HotelManagement.AirBnb.repository.HotelRepository;
import com.HotelManagement.AirBnb.repository.LocationRepository;
import com.HotelManagement.AirBnb.repository.RoomRepository;
import com.HotelManagement.AirBnb.repository.UserRepository;
import com.HotelManagement.AirBnb.service.BookingService;
import com.HotelManagement.AirBnb.service.HotelService;
import com.HotelManagement.AirBnb.service.ManagerBookingService;
import com.HotelManagement.AirBnb.service.RoomService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seeds demo data from CSV files (default: classpath:data/demo/) for local development:
 * <ul>
 *   <li>users.csv          - email, name, role (HOTEL_MANAGER or GUEST)</li>
 *   <li>guest_profiles.csv - user_email, name, gender, age (travelling party of a guest account)</li>
 *   <li>hotels.csv         - manager_email, name, state, city, address, landmark, contact_person,
 *                            phone_number, email, amenities (pipe-separated), active</li>
 *   <li>room_types.csv     - manager_email, hotel_name, type, base_price, total_count, capacity, amenities</li>
 *   <li>bookings.csv       - manager_email, hotel_name, room_type, guest_email, check_in_after_days,
 *                            nights, rooms, guests, status (RESERVED, ACCEPTED or CANCELLED)</li>
 * </ul>
 * Only registered when app.demo-data.enabled=true (the "demo" profile) and refuses to run under a
 * prod/production profile. All demo accounts share app.demo-data.password (stored BCrypt-hashed).
 * <p>
 * Idempotent and non-destructive: users are keyed by email, hotels by manager + name, room types by
 * hotel + type, and a hotel's bookings are only added while it has none from the CSV's guest accounts.
 * Existing records are never modified. Invalid rows are logged with their file and line and skipped.
 * Records are created through the normal services, so validation, inventory generation, inventory
 * locking and availability rules all apply. A hotel with its room types is one transaction and its
 * bookings another, so a failed or interrupted run is retried cleanly on the next start.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** Statuses the seeder can produce; CONFIRMED needs a real payment, which isn't modelled yet. */
    private static final Set<String> SEEDABLE_STATUSES = Set.of("RESERVED", "ACCEPTED", "CANCELLED");

    record UserRow(String email, String name, Role role) {
    }

    record GuestProfileRow(String userEmail, String name, Gender gender, Integer age) {
    }

    record HotelRow(String where, String managerEmail, HotelRequest request) {
        String key() {
            return hotelKey(managerEmail, request.name());
        }
    }

    record RoomRow(String where, String hotelKey, RoomRequest request) {
    }

    record BookingRow(String where, String hotelKey, String roomType, String guestEmail, int checkInAfterDays,
                      int nights, int rooms, int guests, String status) {
    }

    /** Counts for one run. */
    public static final class Report {
        int managersCreated, managersExisting, guestsCreated, guestsExisting, guestProfilesCreated;
        int hotelsCreated, hotelsExisting, roomTypesCreated, roomTypesExisting, inventoryRowsCreated;
        int bookingsCreated, hotelsWithExistingBookings, invalidRows, failures;

        public int managersCreated() {
            return managersCreated;
        }

        public int guestsCreated() {
            return guestsCreated;
        }

        public int hotelsCreated() {
            return hotelsCreated;
        }

        public int roomTypesCreated() {
            return roomTypesCreated;
        }

        public int inventoryRowsCreated() {
            return inventoryRowsCreated;
        }

        public int bookingsCreated() {
            return bookingsCreated;
        }

        public int invalidRows() {
            return invalidRows;
        }

        public int failures() {
            return failures;
        }
    }

    private final Environment environment;
    private final UserRepository userRepository;
    private final GuestRepository guestRepository;
    private final LocationRepository locationRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final HotelService hotelService;
    private final RoomService roomService;
    private final BookingService bookingService;
    private final ManagerBookingService managerBookingService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Validator validator;
    private final ResourceLoader resourceLoader;
    private final String dataLocation;
    private final String demoPassword;

    public DemoDataSeeder(Environment environment, UserRepository userRepository, GuestRepository guestRepository,
                          LocationRepository locationRepository, HotelRepository hotelRepository,
                          RoomRepository roomRepository, BookingRepository bookingRepository,
                          HotelService hotelService, RoomService roomService, BookingService bookingService,
                          ManagerBookingService managerBookingService, PasswordEncoder passwordEncoder,
                          JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Validator validator,
                          ResourceLoader resourceLoader,
                          @Value("${app.demo-data.location:classpath:data/demo/}") String dataLocation,
                          @Value("${app.demo-data.password:}") String demoPassword) {
        this.environment = environment;
        this.userRepository = userRepository;
        this.guestRepository = guestRepository;
        this.locationRepository = locationRepository;
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.hotelService = hotelService;
        this.roomService = roomService;
        this.bookingService = bookingService;
        this.managerBookingService = managerBookingService;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.validator = validator;
        this.resourceLoader = resourceLoader;
        this.dataLocation = dataLocation.endsWith("/") ? dataLocation : dataLocation + "/";
        this.demoPassword = demoPassword;
    }

    /** Runs after all ApplicationRunners, so LocationDataLoader has already imported locations. */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            log.error("Demo data seeding is enabled but a production profile is active; refusing to seed demo data.");
            return;
        }
        try {
            seed();
        } catch (IOException | RuntimeException e) {
            // Never take the application down because of demo data; a restart retries the missing parts.
            log.error("Demo data seeding failed; fix the problem and restart to retry: {}", e.getMessage(), e);
        }
    }

    public Report seed() throws IOException {
        if (demoPassword == null || demoPassword.length() < 6) {
            throw new IllegalStateException("app.demo-data.password must be set (at least 6 characters) to seed demo users");
        }
        Report report = new Report();
        long started = System.currentTimeMillis();

        // ---- read and validate every file first, so problems are reported with file and line
        List<UserRow> userRows = readUsers(report);
        Map<String, UserRow> usersByEmail = userRows.stream()
                .collect(Collectors.toMap(UserRow::email, u -> u, (a, b) -> a, LinkedHashMap::new));
        Map<String, List<GuestProfileRow>> profilesByEmail = readGuestProfiles(usersByEmail, report);
        List<HotelRow> hotelRows = readHotels(usersByEmail, report);
        Set<String> hotelKeys = hotelRows.stream().map(HotelRow::key).collect(Collectors.toSet());
        Map<String, List<RoomRow>> roomsByHotel = readRooms(hotelKeys, report);
        Map<String, List<BookingRow>> bookingsByHotel = readBookings(usersByEmail, profilesByEmail, roomsByHotel, report);

        // ---- users and guest profiles
        Map<String, User> users = new LinkedHashMap<>();
        for (UserRow row : userRows) {
            users.put(row.email(), ensureUser(row, report));
        }
        Map<String, List<Guest>> profiles = new LinkedHashMap<>();
        profilesByEmail.forEach((email, rows) -> profiles.put(email, ensureGuestProfiles(users.get(email), rows, report)));
        List<Long> csvGuestIds = userRows.stream().filter(u -> u.role() == Role.GUEST)
                .map(u -> users.get(u.email()).getId()).toList();

        // ---- hotels, room types (with inventory) and bookings
        long inventoryBefore = countInventory();
        if (!hotelRows.isEmpty() && locationRepository.countAll() == 0) {
            log.error("Demo data: the location table is empty, so no demo hotels were created. "
                    + "Check that data/locations.csv is present (LocationDataLoader imports it at startup).");
        } else {
            for (HotelRow hotelRow : hotelRows) {
                User manager = users.get(hotelRow.managerEmail());
                if (!manager.getRoles().contains(Role.HOTEL_MANAGER)) {
                    log.warn("Demo data: existing account {} is not a hotel manager; leaving it unchanged and skipping "
                            + "hotel '{}'", manager.getEmail(), hotelRow.request().name());
                    continue;
                }
                Long hotelId;
                try {
                    hotelId = tx.execute(s -> seedHotel(manager, hotelRow, roomsByHotel.getOrDefault(hotelRow.key(), List.of()), report));
                } catch (RuntimeException e) {
                    report.failures++;
                    log.error("Demo data: failed to seed hotel '{}' ({}); rolled back, will be retried on next start: {}",
                            hotelRow.request().name(), hotelRow.where(), e.getMessage());
                    continue;
                }
                List<BookingRow> bookings = bookingsByHotel.getOrDefault(hotelRow.key(), List.of());
                if (hotelId == null || bookings.isEmpty()) {
                    continue;
                }
                try {
                    tx.executeWithoutResult(s -> seedBookings(manager.getId(), hotelId, bookings, users, profiles, csvGuestIds, report));
                } catch (RuntimeException e) {
                    report.failures++;
                    log.error("Demo data: failed to seed bookings for hotel '{}'; rolled back, will be retried on next start: {}",
                            hotelRow.request().name(), e.getMessage());
                }
            }
        }
        report.inventoryRowsCreated = (int) (countInventory() - inventoryBefore);

        logSummary(report, started);
        verify(users.values().stream().filter(u -> u.getRoles().contains(Role.HOTEL_MANAGER)).map(User::getId).toList());
        return report;
    }

    // ------------------------------------------------------------ reading CSV files

    private List<CsvTable.Row> read(String file, boolean required, String... columns) throws IOException {
        Resource resource = resourceLoader.getResource(dataLocation + file);
        if (!resource.exists()) {
            if (required) {
                throw new IOException("Demo data file not found: " + dataLocation + file);
            }
            log.info("Demo data: {} not found; skipping", dataLocation + file);
            return List.of();
        }
        return CsvTable.read(resource, columns);
    }

    private List<UserRow> readUsers(Report report) throws IOException {
        List<UserRow> result = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (CsvTable.Row row : read("users.csv", true, "email", "name", "role")) {
            String email = row.get("email").toLowerCase(Locale.ROOT);
            Role role = parseEnum(Role.class, row.get("role"));
            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || row.get("name").isEmpty()
                    || (role != Role.HOTEL_MANAGER && role != Role.GUEST)) {
                invalid(report, row, "needs a valid email, a name and role HOTEL_MANAGER or GUEST");
            } else if (!seen.add(email)) {
                invalid(report, row, "duplicate email " + email);
            } else {
                result.add(new UserRow(email, row.get("name"), role));
            }
        }
        return result;
    }

    private Map<String, List<GuestProfileRow>> readGuestProfiles(Map<String, UserRow> users, Report report) throws IOException {
        Map<String, List<GuestProfileRow>> result = new LinkedHashMap<>();
        for (CsvTable.Row row : read("guest_profiles.csv", false, "user_email", "name", "gender", "age")) {
            String email = row.get("user_email").toLowerCase(Locale.ROOT);
            Gender gender = parseEnum(Gender.class, row.get("gender"));
            Integer age = parseInt(row.get("age"));
            if (!users.containsKey(email) || row.get("name").isEmpty() || gender == null || age == null || age < 0 || age > 120) {
                invalid(report, row, "needs a user_email from users.csv, a name, gender MALE/FEMALE/OTHER and age 0-120");
            } else {
                result.computeIfAbsent(email, e -> new ArrayList<>()).add(new GuestProfileRow(email, row.get("name"), gender, age));
            }
        }
        return result;
    }

    private List<HotelRow> readHotels(Map<String, UserRow> users, Report report) throws IOException {
        List<HotelRow> result = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        boolean checkLocations = locationRepository.countAll() > 0; // empty table is reported separately
        for (CsvTable.Row row : read("hotels.csv", true, "manager_email", "name", "state", "city")) {
            String manager = row.get("manager_email").toLowerCase(Locale.ROOT);
            UserRow user = users.get(manager);
            if (user == null || user.role() != Role.HOTEL_MANAGER) {
                invalid(report, row, "manager_email must be a HOTEL_MANAGER in users.csv");
                continue;
            }
            Boolean active = parseBoolean(row.get("active"));
            if (active == null) {
                invalid(report, row, "active must be true or false");
                continue;
            }
            HotelRequest request = new HotelRequest(row.get("name"), row.get("state"), row.get("city"), row.get("address"),
                    emptyToNull(row.get("landmark")), row.get("contact_person"), row.get("phone_number"), row.get("email"),
                    splitList(row.get("amenities")), active, null);
            String problems = violations(request);
            if (problems == null) {
                problems = unknownAmenities(request.amenities());
            }
            if (problems == null && checkLocations && locationRepository
                    .findByCityAndState(request.city(), request.state()).isEmpty()) {
                problems = "'" + request.city() + "' is not a city in '" + request.state() + "' in the location table";
            }
            if (problems != null) {
                invalid(report, row, problems);
            } else if (!seen.add(hotelKey(manager, request.name()))) {
                invalid(report, row, "duplicate hotel name for this manager");
            } else {
                result.add(new HotelRow(row.where(), manager, request));
            }
        }
        return result;
    }

    private Map<String, List<RoomRow>> readRooms(Set<String> hotelKeys, Report report) throws IOException {
        Map<String, List<RoomRow>> result = new LinkedHashMap<>();
        for (CsvTable.Row row : read("room_types.csv", true, "manager_email", "hotel_name", "type", "base_price",
                "total_count", "capacity")) {
            String key = hotelKey(row.get("manager_email"), row.get("hotel_name"));
            if (!hotelKeys.contains(key)) {
                invalid(report, row, "manager_email + hotel_name must match a valid row in hotels.csv");
                continue;
            }
            BigDecimal price;
            try {
                price = new BigDecimal(row.get("base_price"));
            } catch (NumberFormatException e) {
                invalid(report, row, "base_price must be a number");
                continue;
            }
            RoomRequest request = new RoomRequest(row.get("type"), price, parseInt(row.get("total_count")),
                    parseInt(row.get("capacity")), splitList(row.get("amenities")), null);
            String problems = violations(request);
            if (problems == null) {
                problems = unknownAmenities(request.amenities());
            }
            List<RoomRow> hotelRooms = result.computeIfAbsent(key, k -> new ArrayList<>());
            if (problems != null) {
                invalid(report, row, problems);
            } else if (hotelRooms.stream().anyMatch(r -> r.request().type().equalsIgnoreCase(request.type()))) {
                invalid(report, row, "duplicate room type for this hotel");
            } else {
                hotelRooms.add(new RoomRow(row.where(), key, request));
            }
        }
        return result;
    }

    private Map<String, List<BookingRow>> readBookings(Map<String, UserRow> users, Map<String, List<GuestProfileRow>> profiles,
                                                      Map<String, List<RoomRow>> roomsByHotel, Report report) throws IOException {
        Map<String, List<BookingRow>> result = new LinkedHashMap<>();
        for (CsvTable.Row row : read("bookings.csv", false, "manager_email", "hotel_name", "room_type", "guest_email",
                "check_in_after_days", "nights", "rooms", "status")) {
            String key = hotelKey(row.get("manager_email"), row.get("hotel_name"));
            Optional<RoomRow> room = roomsByHotel.getOrDefault(key, List.of()).stream()
                    .filter(r -> r.request().type().equalsIgnoreCase(row.get("room_type"))).findFirst();
            String guestEmail = row.get("guest_email").toLowerCase(Locale.ROOT);
            UserRow guest = users.get(guestEmail);
            Integer after = parseInt(row.get("check_in_after_days"));
            Integer nights = parseInt(row.get("nights"));
            Integer rooms = parseInt(row.get("rooms"));
            Integer guests = row.get("guests").isEmpty() ? Integer.valueOf(1) : parseInt(row.get("guests"));
            String status = row.get("status").toUpperCase(Locale.ROOT);

            String problem = null;
            if (room.isEmpty()) problem = "hotel and room_type must match valid rows in hotels.csv and room_types.csv";
            else if (guest == null || guest.role() != Role.GUEST) problem = "guest_email must be a GUEST in users.csv";
            else if (after == null || after < 0) problem = "check_in_after_days must be 0 or more (days from the seeding date)";
            else if (nights == null || nights < 1 || nights > 30) problem = "nights must be 1-30";
            else if (rooms == null || rooms < 1 || rooms > room.get().request().totalCount()) problem = "rooms must be 1 to the room type's total_count";
            else if (guests == null || guests < 0 || guests > rooms * room.get().request().capacity())
                problem = "guests must be 0 to rooms x capacity";
            else if (guests > profiles.getOrDefault(guestEmail, List.of()).size())
                problem = "guests is more than the guest profiles listed for " + guestEmail + " in guest_profiles.csv";
            else if (!SEEDABLE_STATUSES.contains(status)) problem = "status must be RESERVED, ACCEPTED or CANCELLED";
            if (problem != null) {
                invalid(report, row, problem);
                continue;
            }
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(new BookingRow(row.where(), key,
                    room.get().request().type(), guestEmail, after, nights, rooms, guests, status));
        }
        return result;
    }

    // ------------------------------------------------------------ writing

    private User ensureUser(UserRow row, Report report) {
        boolean manager = row.role() == Role.HOTEL_MANAGER;
        return tx.execute(status -> {
            Optional<User> existing = userRepository.findByEmail(row.email());
            if (existing.isPresent()) {
                if (manager) report.managersExisting++;
                else report.guestsExisting++;
                return existing.get();
            }
            User user = new User();
            user.setName(row.name());
            user.setEmail(row.email());
            user.setPassword(passwordEncoder.encode(demoPassword)); // BCrypt, same as signup
            user.setRoles(Set.of(row.role()));
            if (manager) report.managersCreated++;
            else report.guestsCreated++;
            return userRepository.save(user);
        });
    }

    private List<Guest> ensureGuestProfiles(User user, List<GuestProfileRow> rows, Report report) {
        return tx.execute(status -> {
            List<Guest> existing = guestRepository.findByUser(user.getId());
            if (!existing.isEmpty()) {
                return existing; // the account already has a travelling party; don't add to it
            }
            List<Guest> party = rows.stream().map(r -> {
                Guest g = new Guest();
                g.setUser(user);
                g.setName(r.name());
                g.setGender(r.gender());
                g.setAge(r.age());
                return g;
            }).toList();
            report.guestProfilesCreated += party.size();
            return guestRepository.saveAll(party);
        });
    }

    /** Creates the hotel and any missing room types; returns the hotel id, or null to skip its bookings. */
    private Long seedHotel(User manager, HotelRow row, List<RoomRow> rooms, Report report) {
        Optional<Hotel> existing = hotelRepository.findFirstByOwnerAndName(manager.getId(), row.request().name());
        Long hotelId;
        if (existing.isPresent()) {
            report.hotelsExisting++;
            if (existing.get().getDeletedAt() != null) {
                return null; // deleted by the manager: respect that
            }
            hotelId = existing.get().getHotelId();
        } else {
            hotelId = hotelService.create(manager, row.request(), List.of()).id();
            report.hotelsCreated++;
        }
        for (RoomRow room : rooms) {
            if (roomRepository.existsTypeInHotel(hotelId, room.request().type())) {
                report.roomTypesExisting++;
            } else {
                roomService.create(manager.getId(), hotelId, room.request(), List.of());
                report.roomTypesCreated++;
            }
        }
        return hotelId;
    }

    private void seedBookings(Long ownerId, Long hotelId, List<BookingRow> rows, Map<String, User> users,
                              Map<String, List<Guest>> profiles, List<Long> csvGuestIds, Report report) {
        Hotel hotel = hotelRepository.findHotelById(hotelId).orElseThrow();
        if (!Boolean.TRUE.equals(hotel.getActive())) {
            log.warn("Demo data: hotel '{}' is inactive, so its {} booking row(s) were skipped (inactive hotels can't be booked)",
                    hotel.getName(), rows.size());
            return;
        }
        if (bookingRepository.existsForHotelByUsers(hotelId, csvGuestIds)) {
            report.hotelsWithExistingBookings++;
            return;
        }
        Map<String, Long> roomIds = roomService.list(ownerId, hotelId).stream()
                .collect(Collectors.toMap(r -> r.type().toLowerCase(Locale.ROOT), RoomResponse::id, (a, b) -> a));
        LocalDate today = LocalDate.now();
        for (BookingRow row : rows) {
            Long roomId = roomIds.get(row.roomType().toLowerCase(Locale.ROOT));
            if (roomId == null) {
                log.warn("Demo data: {}: room type '{}' no longer exists for this hotel; booking skipped", row.where(), row.roomType());
                continue;
            }
            LocalDate checkIn = today.plusDays(row.checkInAfterDays());
            List<Guest> party = profiles.getOrDefault(row.guestEmail(), List.of());
            List<Long> guestIds = party.subList(0, Math.min(row.guests(), party.size())).stream().map(Guest::getId).toList();
            GuestBookingResponse created = bookingService.create(users.get(row.guestEmail()),
                    new CreateBookingRequest(hotelId, roomId, checkIn, checkIn.plusDays(row.nights()), row.rooms(), guestIds));
            switch (row.status()) {
                case "ACCEPTED" -> managerBookingService.accept(ownerId, created.id());
                case "CANCELLED" -> managerBookingService.cancel(ownerId, created.id());
                default -> {
                }
            }
            report.bookingsCreated++;
        }
    }

    // ------------------------------------------------------------ helpers

    static String hotelKey(String managerEmail, String hotelName) {
        return managerEmail.strip().toLowerCase(Locale.ROOT) + "|" + hotelName.strip().toLowerCase(Locale.ROOT);
    }

    private String violations(Object request) {
        Set<ConstraintViolation<Object>> v = validator.validate(request);
        return v.isEmpty() ? null : v.stream().map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining("; "));
    }

    private static String unknownAmenities(List<String> codes) {
        List<String> unknown = codes.stream().filter(c -> Arrays.stream(Amenity.values()).noneMatch(a -> a.name().equals(c))).toList();
        return unknown.isEmpty() ? null : "unknown amenities " + unknown + " (see the Amenity enum)";
    }

    private static void invalid(Report report, CsvTable.Row row, String reason) {
        report.invalidRows++;
        log.warn("Demo data: skipping {}: {}", row.where(), reason);
    }

    private static List<String> splitList(String value) {
        return value.isEmpty() ? List.of()
                : Arrays.stream(value.split("\\|")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean parseBoolean(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "t", "yes", "y", "1" -> true;
            case "false", "f", "no", "n", "0" -> false;
            default -> null;
        };
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------ reporting

    private long countInventory() {
        Long n = jdbc.queryForObject("select count(*) from inventory", Long.class);
        return n == null ? 0 : n;
    }

    private void logSummary(Report r, long started) {
        log.info("""
                        Demo data seeding from {} finished in {} ms:
                          managers:      {} created, {} already existed
                          guest users:   {} created, {} already existed ({} guest profiles created)
                          hotels:        {} created, {} already existed (skipped)
                          room types:    {} created, {} already existed (skipped)
                          inventory:     {} date rows created
                          bookings:      {} created; {} hotel(s) already had demo bookings (skipped)
                          invalid rows:  {}
                          failures:      {}{}""",
                dataLocation, System.currentTimeMillis() - started,
                r.managersCreated, r.managersExisting, r.guestsCreated, r.guestsExisting, r.guestProfilesCreated,
                r.hotelsCreated, r.hotelsExisting, r.roomTypesCreated, r.roomTypesExisting, r.inventoryRowsCreated,
                r.bookingsCreated, r.hotelsWithExistingBookings, r.invalidRows, r.failures,
                r.failures > 0 ? " (restart to retry the failed hotels)" : "");
    }

    /** Post-seed consistency checks over the demo managers' data; problems are logged, not fixed. */
    void verify(List<Long> managerIds) {
        if (managerIds.isEmpty()) {
            return;
        }
        String in = managerIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        List<String> perLocation = jdbc.query("""
                select u.email, l.city, l.state, count(*) as hotels
                from hotel h join app_user u on u.id = h.owner_id join location l on l.id = h.location_id
                where h.owner_id in (%s) and h.deleted_at is null
                group by u.email, l.city, l.state order by u.email, l.city""".formatted(in),
                (rs, i) -> rs.getString(1) + " | " + rs.getString(2) + ", " + rs.getString(3) + " | " + rs.getInt(4));
        log.info("Demo data: hotels per manager and location:\n  {}", String.join("\n  ", perLocation));

        Integer inventoryMismatches = jdbc.queryForObject("""
                select count(*) from inventory i join hotel h on h.hotel_id = i.hotel_id
                where h.owner_id in (%s)
                  and i.booked_count <> coalesce((select sum(b.rooms_count) from booking b
                        where b.room_id = i.room_id and b.booking_status in ('RESERVED','ACCEPTED','CONFIRMED')
                          and i.date >= b.check_in_date and i.date < b.check_out_date), 0)""".formatted(in), Integer.class);
        Integer badDates = jdbc.queryForObject("""
                select count(*) from booking b join hotel h on h.hotel_id = b.hotel_id
                where h.owner_id in (%s) and b.check_out_date <= b.check_in_date""".formatted(in), Integer.class);
        Integer overCapacity = jdbc.queryForObject("""
                select count(*) from booking b join hotel h on h.hotel_id = b.hotel_id join room r on r.id = b.room_id
                where h.owner_id in (%s)
                  and (select count(*) from booking_guest bg where bg.booking_id = b.booking_id) > r.capacity * b.rooms_count"""
                .formatted(in), Integer.class);

        if (inventoryMismatches == 0 && badDates == 0 && overCapacity == 0) {
            log.info("Demo data verification passed: inventory matches active bookings, dates and guest counts are valid");
        } else {
            log.warn("Demo data verification found problems: {} inventory mismatches, {} invalid date ranges, "
                    + "{} bookings over room capacity", inventoryMismatches, badDates, overCapacity);
        }
    }
}
