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

    public void insertUser(String userId, String plate, String phoneNumber, String role) {
        String sql = "INSERT INTO users (user_id, plate, phone_number, role) VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT (user_id) DO UPDATE SET plate = EXCLUDED.plate, phone_number = EXCLUDED.phone_number, role = EXCLUDED.role";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, userId);
            pstmt.setString(2, plate);
            pstmt.setString(3, phoneNumber);
            pstmt.setString(4, role);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("[DB] User " + userId + " registered/updated successfully.");
            }
            
        } catch (SQLException e) {
            System.err.println("[DB ERROR] Could not insert/update user: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public UserProfile findUserByPlate(String plate) {
        String sql = "SELECT user_id, phone_number, role FROM users WHERE plate = ?";
        UserProfile user = null;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, plate);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String userId = rs.getString("user_id");
                    String phoneNumber = rs.getString("phone_number");
                    String role = rs.getString("role");
                    user = new UserProfile(plate, userId, role, phoneNumber);
                }
            }
            
        } catch (SQLException e) {
            System.err.println("[DB ERROR] Could not find user by plate: " + e.getMessage());
            e.printStackTrace();
        }
        
        return user;
    }
}