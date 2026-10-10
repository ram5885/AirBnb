package com.HotelManagement.AirBnb;

import com.HotelManagement.AirBnb.config.DemoDataSeeder;
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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import jakarta.validation.Validator;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DemoDataSeeder against the real database. Same context setup as HotelManagerIntegrationTest
 * (demo data disabled), so the seeder is constructed by hand to exercise it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoDataSeederTest {

    @Autowired ApplicationContext context;
    @Autowired Environment environment;
    @Autowired UserRepository userRepository;
    @Autowired GuestRepository guestRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired HotelRepository hotelRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired HotelService hotelService;
    @Autowired RoomService roomService;
    @Autowired BookingService bookingService;
    @Autowired ManagerBookingService managerBookingService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;
    @Autowired Validator validator;
    @Autowired ResourceLoader resourceLoader;

    DemoDataSeeder seeder(Environment env) {
        return seeder(env, "classpath:data/demo/");
    }

    DemoDataSeeder seeder(Environment env, String location) {
        return new DemoDataSeeder(env, userRepository, guestRepository, locationRepository, hotelRepository,
                roomRepository, bookingRepository, hotelService, roomService, bookingService, managerBookingService,
                passwordEncoder, jdbc, transactionManager, validator, resourceLoader, location, "Demo@12345");
    }

    @Test
    @Order(1)
    void notRegisteredWhenDisabled() {
        assertThat(environment.getProperty("app.demo-data.enabled")).isEqualTo("false");
        assertThat(context.getBeanProvider(DemoDataSeeder.class).getIfAvailable()).isNull();
    }

    @Test
    @Order(2)
    void refusesToRunUnderProductionProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        seeder(prod).onApplicationReady();
        assertThat(userRepository.findByEmail("manager1@example.com")).isEmpty();
    }

    @Test
    @Order(3)
    void seedsConsistentDataOnceAndOnlyFillsGapsAfterwards() throws Exception {
        DemoDataSeeder.Report first = seeder(environment).seed();
        assertThat(first.failures()).isZero();
        assertThat(first.invalidRows()).isZero();
        assertThat(first.managersCreated()).isEqualTo(4);
        assertThat(first.guestsCreated()).isEqualTo(6);
        assertThat(first.hotelsCreated()).isEqualTo(54);
        assertThat(first.roomTypesCreated()).isEqualTo(161);
        assertThat(first.inventoryRowsCreated()).isEqualTo(first.roomTypesCreated() * RoomService.INVENTORY_DAYS);
        assertThat(first.bookingsCreated()).isEqualTo(161);

        String demoOwners = "(select id from app_user where email like 'manager_@example.com')";
        // Every demo hotel has a valid location: 12 cities x 4 hotels, plus 6 extra in Bhubaneswar (10 there).
        assertThat(jdbc.queryForObject("""
                select count(*) from hotel h join location l on l.id = h.location_id where h.owner_id in %s"""
                .formatted(demoOwners), Integer.class)).isEqualTo(54);
        assertThat(jdbc.queryForObject("""
                select count(*) from hotel h join location l on l.id = h.location_id
                where h.owner_id in %s and l.city = 'Bhubaneswar' and l.state = 'Odisha'""".formatted(demoOwners),
                Integer.class)).isEqualTo(10);
        // Inventory booked counts equal the rooms held by active bookings on every date.
        assertThat(jdbc.queryForObject("""
                select count(*) from inventory i join hotel h on h.hotel_id = i.hotel_id where h.owner_id in %s
                  and i.booked_count <> coalesce((select sum(b.rooms_count) from booking b where b.room_id = i.room_id
                      and b.booking_status in ('RESERVED','ACCEPTED','CONFIRMED')
                      and i.date >= b.check_in_date and i.date < b.check_out_date), 0)""".formatted(demoOwners),
                Integer.class)).isZero();
        // Valid dates, party sizes within room capacity, and a mix of statuses.
        assertThat(jdbc.queryForObject("""
                select count(*) from booking b join room r on r.id = b.room_id join hotel h on h.hotel_id = b.hotel_id
                where h.owner_id in %s and (b.check_out_date <= b.check_in_date or b.check_in_date < current_date
                  or (select count(*) from booking_guest bg where bg.booking_id = b.booking_id) > r.capacity * b.rooms_count)"""
                .formatted(demoOwners), Integer.class)).isZero();
        assertThat(jdbc.queryForList("""
                select distinct booking_status from booking b join hotel h on h.hotel_id = b.hotel_id where h.owner_id in %s"""
                .formatted(demoOwners), String.class)).containsExactlyInAnyOrder("RESERVED", "ACCEPTED", "CANCELLED");
        assertThat(jdbc.queryForObject("""
                select count(*) from booking b join hotel h on h.hotel_id = b.hotel_id where h.owner_id in %s and not h.active"""
                .formatted(demoOwners), Integer.class)).isZero();

        long users = userRepository.countAll();
        long hotels = hotelRepository.countAll();
        long rooms = roomRepository.countAll();
        long bookings = bookingRepository.countAll();
        DemoDataSeeder.Report second = seeder(environment).seed();
        assertThat(second.managersCreated() + second.guestsCreated() + second.hotelsCreated() + second.roomTypesCreated()
                + second.inventoryRowsCreated() + second.bookingsCreated()).isZero();
        assertThat(userRepository.countAll()).isEqualTo(users);
        assertThat(hotelRepository.countAll()).isEqualTo(hotels);
        assertThat(roomRepository.countAll()).isEqualTo(rooms);
        assertThat(bookingRepository.countAll()).isEqualTo(bookings);

        // Demo credentials work through the normal login, with a hashed password at rest.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"manager2@example.com\",\"password\":\"Demo@12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("HOTEL_MANAGER"));
        assertThat(userRepository.findByEmail("manager2@example.com").orElseThrow().getPassword()).startsWith("$2");
    }

    @Test
    @Order(4)
    void skipsInvalidRowsAndSeedsTheRest(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("users.csv"), """
                email,name,role
                csvmgr@example.com,Csv Manager,HOTEL_MANAGER
                csvguest@example.com,Csv Guest,GUEST
                not-an-email,Broken,GUEST
                someone@example.com,Unknown Role,SUPERUSER
                """);
        Files.writeString(dir.resolve("guest_profiles.csv"), """
                user_email,name,gender,age
                csvguest@example.com,Csv Guest,FEMALE,30
                csvguest@example.com,Csv Friend,MALE,31
                unknown@example.com,Nobody,MALE,20
                """);
        Files.writeString(dir.resolve("hotels.csv"), """
                manager_email,name,state,city,address,landmark,contact_person,phone_number,email,amenities,active
                csvmgr@example.com,Csv Palace,Kerala,Alappuzha,"1, Beach Road, Alappuzha",,Asha,9876500001,a@example.com,WIFI|TV,true
                csvmgr@example.com,Wrong State Inn,Punjab,Alappuzha,1 Road,,Asha,9876500002,b@example.com,WIFI,true
                csvmgr@example.com,Bad Phone Inn,Kerala,Alappuzha,1 Road,,Asha,123,c@example.com,WIFI,true
                csvmgr@example.com,Bad Amenity Inn,Kerala,Alappuzha,1 Road,,Asha,9876500003,d@example.com,TELEPORTER,true
                """);
        Files.writeString(dir.resolve("room_types.csv"), """
                manager_email,hotel_name,type,base_price,total_count,capacity,amenities
                csvmgr@example.com,Csv Palace,Standard Room,2000,2,2,WIFI
                csvmgr@example.com,Csv Palace,Broken Room,abc,2,2,WIFI
                csvmgr@example.com,No Such Hotel,Standard Room,2000,2,2,WIFI
                """);
        Files.writeString(dir.resolve("bookings.csv"), """
                manager_email,hotel_name,room_type,guest_email,check_in_after_days,nights,rooms,guests,status
                csvmgr@example.com,Csv Palace,Standard Room,csvguest@example.com,3,2,1,2,ACCEPTED
                csvmgr@example.com,Csv Palace,Standard Room,csvguest@example.com,3,2,5,1,RESERVED
                csvmgr@example.com,Csv Palace,Standard Room,csvguest@example.com,3,2,1,1,CONFIRMED
                csvmgr@example.com,Csv Palace,Standard Room,csvguest@example.com,3,2,1,9,RESERVED
                """);

        DemoDataSeeder.Report report = seeder(environment, "file:" + dir + "/").seed();

        // not-an-email, unknown role, unknown profile user, wrong state, bad phone, unknown amenity,
        // bad price, unknown hotel, too many rooms, CONFIRMED status, too many guests
        assertThat(report.invalidRows()).isEqualTo(11);
        assertThat(report.failures()).isZero();
        assertThat(report.hotelsCreated()).isEqualTo(1);
        assertThat(report.roomTypesCreated()).isEqualTo(1);
        assertThat(report.bookingsCreated()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select b.booking_status from booking b join hotel h on h.hotel_id = b.hotel_id
                where h.name = 'Csv Palace'""", String.class)).isEqualTo("ACCEPTED");
    }
}
