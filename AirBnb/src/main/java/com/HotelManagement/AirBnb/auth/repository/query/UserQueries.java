package com.HotelManagement.AirBnb.auth.repository.query;

/** Native SQL for UserRepository (table app_user). */
public final class UserQueries {

    private UserQueries() {
    }

    public static final String FIND_BY_ID = """
            SELECT u.* FROM app_user u WHERE u.id = :id""";

    public static final String FIND_BY_EMAIL = """
            SELECT u.* FROM app_user u WHERE u.email = :email""";

    public static final String EXISTS_BY_EMAIL = """
            SELECT EXISTS (SELECT 1 FROM app_user u WHERE u.email = :email)""";

    public static final String COUNT_ALL = """
            SELECT COUNT(*) FROM app_user""";
}
