package de.campuspark.logic;

import java.util.List;
import java.util.ArrayList;

public class ParkingTopology {

    /**
     * Definiert, welche Lanes "Fahrwege/Stack-Lanes" sind.
     * Lane 2 und 3 sind die Fahrgassen, in denen in zweiter Reihe geparkt wird.
     */
    public static boolean isStackLane(int lane) {
        return lane == 2 || lane == 3;
    }

    /**
     * Definiert die Blockade-Logik: Welche Lane wird von der aktuellen Lane blockiert?
     * Rückgabe -1, wenn nichts blockiert wird.
     */
    public static int getBlockedLane(int currentLane) {
        if (currentLane == 2) return 1; 
        if (currentLane == 3) return 4; 
        return -1; 
    }

    /**
     * Berechnet dynamisch die IDs der Spots, die von (lane, pos) blockiert werden.
     */
    public static List<String> getBlockedSpotIds(int lane, int pos) {
        List<String> blockedIds = new ArrayList<>();
        
        int targetLane = getBlockedLane(lane);
        if (targetLane == -1) return blockedIds; // Kein Blockierer

        // Regel: Pos 0 blockiert pos 0 und 1, Pos 1 blockiert 2 und 3, ....
        blockedIds.add(createSpotId(targetLane, pos * 2));
        blockedIds.add(createSpotId(targetLane, (pos * 2)+ 1));

        return blockedIds;
    }

    // Hilfsmethode zum Bauen der ID 
    public static String createSpotId(int lane, int pos) {
        return "L" + lane + "-P" + pos;
    }
}