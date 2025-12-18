package de.campuspark.logic;

import de.campuspark.model.RegistrationEvent;
import de.campuspark.model.UserProfile;
import de.campuspark.util.DBConnection;

public class UserRegistry {

    private static DBConnection dbconnection = new DBConnection();

    public static void register(RegistrationEvent reg) {
        dbconnection.insertUser(reg.getUserId(), reg.getPlate(), reg.getPhoneNumber(), reg.getRole(), reg.getCourse());
    }

    public static UserProfile findByPlate(String plate) {
        return dbconnection.findUserByPlate(plate);
    }
}
