package com.HotelManagement.AirBnb.entity.enums;

/**
 * Amenities a hotel or room type can offer. Stored by name in the TEXT[]
 * amenities columns. Categories are inspired by the groupings commonly used
 * for Airbnb-style listings; this is not Airbnb's official list.
 */
public enum Amenity {
    WIFI("Wi-Fi", Category.INTERNET_AND_OFFICE),
    DEDICATED_WORKSPACE("Dedicated workspace", Category.INTERNET_AND_OFFICE),

    TV("TV", Category.ENTERTAINMENT),
    SOUND_SYSTEM("Sound system", Category.ENTERTAINMENT),
    BOOKS_AND_GAMES("Books and board games", Category.ENTERTAINMENT),

    AIR_CONDITIONING("Air conditioning", Category.HEATING_AND_COOLING),
    HEATING("Heating", Category.HEATING_AND_COOLING),
    CEILING_FAN("Ceiling fan", Category.HEATING_AND_COOLING),

    KITCHEN("Kitchen", Category.KITCHEN_AND_DINING),
    REFRIGERATOR("Refrigerator", Category.KITCHEN_AND_DINING),
    MICROWAVE("Microwave", Category.KITCHEN_AND_DINING),
    COOKING_BASICS("Cooking basics", Category.KITCHEN_AND_DINING),
    COFFEE_MAKER("Coffee maker", Category.KITCHEN_AND_DINING),

    WASHING_MACHINE("Washing machine", Category.BEDROOM_AND_LAUNDRY),
    DRYER("Dryer", Category.BEDROOM_AND_LAUNDRY),
    IRON("Iron", Category.BEDROOM_AND_LAUNDRY),
    BED_LINENS("Bed linens", Category.BEDROOM_AND_LAUNDRY),

    HAIR_DRYER("Hair dryer", Category.BATHROOM),
    HOT_WATER("Hot water", Category.BATHROOM),
    TOILETRIES("Shampoo and body soap", Category.BATHROOM),

    FREE_PARKING("Free parking", Category.PARKING_AND_FACILITIES),
    PAID_PARKING("Paid parking", Category.PARKING_AND_FACILITIES),
    EV_CHARGER("EV charger", Category.PARKING_AND_FACILITIES),
    ELEVATOR("Elevator", Category.PARKING_AND_FACILITIES),
    GYM("Gym", Category.PARKING_AND_FACILITIES),

    SWIMMING_POOL("Swimming pool", Category.OUTDOOR_AND_RECREATION),
    HOT_TUB("Hot tub", Category.OUTDOOR_AND_RECREATION),
    BALCONY("Balcony", Category.OUTDOOR_AND_RECREATION),
    GARDEN("Garden", Category.OUTDOOR_AND_RECREATION),
    BBQ_GRILL("BBQ grill", Category.OUTDOOR_AND_RECREATION),

    CRIB("Crib", Category.FAMILY),
    HIGH_CHAIR("High chair", Category.FAMILY),

    BREAKFAST("Breakfast", Category.SERVICES),
    SELF_CHECK_IN("Self check-in", Category.SERVICES),
    LUGGAGE_DROPOFF("Luggage drop-off", Category.SERVICES),

    SMOKE_ALARM("Smoke alarm", Category.HOME_SAFETY),
    CARBON_MONOXIDE_ALARM("Carbon monoxide alarm", Category.HOME_SAFETY),
    FIRE_EXTINGUISHER("Fire extinguisher", Category.HOME_SAFETY),
    FIRST_AID_KIT("First-aid kit", Category.HOME_SAFETY);

    public enum Category {
        INTERNET_AND_OFFICE("Internet and office"),
        ENTERTAINMENT("Entertainment"),
        HEATING_AND_COOLING("Heating and cooling"),
        KITCHEN_AND_DINING("Kitchen and dining"),
        BEDROOM_AND_LAUNDRY("Bedroom and laundry"),
        BATHROOM("Bathroom"),
        PARKING_AND_FACILITIES("Parking and facilities"),
        OUTDOOR_AND_RECREATION("Outdoor and recreation"),
        FAMILY("Family"),
        SERVICES("Services"),
        HOME_SAFETY("Home safety");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final String label;
    private final Category category;

    Amenity(String label, Category category) {
        this.label = label;
        this.category = category;
    }

    public String getLabel() {
        return label;
    }

    public Category getCategory() {
        return category;
    }
}
