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
     * Gibt zurück, welche Lane die aktuelle Lane blockiert.
     */
    private static int getBlockerLaneFor(int victimLane) {
        if (victimLane == 1) return 2;
        if (victimLane == 4) return 3;
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

    /**
     * Ermittelt alle Spot-IDs, die den übergebenen Platz (lane, pos) blockieren.
     * Das heißt: Wenn auf einem dieser zurückgegebenen Plätze ein Auto steht,
     * kann (lane, pos) nicht rausfahren.
     */
    public static List<String> getBlockingSpots(int lane, int pos) {
        List<String> blockers = new ArrayList<>();

        // INTERNE BLOCKADE (Stacking / Reihenfolge)
        if (pos > 0 && (lane == 2 || lane == 3)) {
            for(int i = pos - 1; i >= 0; i--){
                blockers.add(createSpotId(lane, i));
            }
        }

        // SEITLICHE BLOCKADE (Cross-Lane)
        int blockerLane = getBlockerLaneFor(lane);

        if (blockerLane != -1) {
            int blockerPos = pos / 2;
            blockers.add(createSpotId(blockerLane, blockerPos));
            for(int i = blockerPos - 1; i >= 0; i--){
                blockers.add(createSpotId(blockerLane, i));
            }
        }

        return blockers;
    }

    // Hilfsmethode zum Bauen der ID 
    public static String createSpotId(int lane, int pos) {
        return "L" + lane + "-P" + pos;
    }
}