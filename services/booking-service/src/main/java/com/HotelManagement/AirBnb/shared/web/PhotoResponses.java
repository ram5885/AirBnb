package com.HotelManagement.AirBnb.shared.web;

public final class PhotoResponses {

    private PhotoResponses() {
    }

    public static int pageSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
