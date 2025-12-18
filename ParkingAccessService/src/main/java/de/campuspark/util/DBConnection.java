package de.campuspark.util;

import de.campuspark.model.UserProfile;

import java.sql.*;

public class DBConnection {

    // --- Configuration (Adapt these to your actual database settings) ---
    private static final String DB_URL = Config.DB_URL; // e.g., "jdbc:postgresql://localhost:5432/campus_park"
    private static final String DB_USER = Config.DB_USER;
    private static final String DB_PASSWORD = Config.DB_PASSWORD;

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public void insertUser(String plate, String phoneNumber, String role, String course) {
        String sql = "INSERT INTO users (plate, phone_number, role, course) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT (plate) DO UPDATE SET phone_number = EXCLUDED.phone_number, " +
                    "role = EXCLUDED.role, course = EXCLUDED.course";
        
        try (Connection conn = getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, plate);
            pstmt.setString(2, phoneNumber);
            pstmt.setString(3, role);
            pstmt.setString(4, course);
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB ERROR] " + e.getMessage());
        }
    }

    public UserProfile findUserByPlate(String plate) {
        String sql = "SELECT user_id, phone_number, role, course FROM users WHERE plate = ?";
        UserProfile user = null;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, plate);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String userId = rs.getString("user_id");
                    String phoneNumber = rs.getString("phone_number");
                    String role = rs.getString("role");
                    String course = rs.getString("course");
                    user = new UserProfile(plate, userId, role, phoneNumber, course);
                }
            }
            
        } catch (SQLException e) {
            System.err.println("[DB ERROR] Could not find user by plate: " + e.getMessage());
            e.printStackTrace();
        }
        
        return user;
    }

}