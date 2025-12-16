package de.campuspark.service;

import de.campuspark.logic.*;
import de.campuspark.model.*;
import de.campuspark.util.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;

import java.nio.charset.StandardCharsets;

public class ParkingAccessCallback implements MqttCallback {

    private final MqttClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final UserRegistry userRegistry = new UserRegistry();

    public ParkingAccessCallback(MqttClient client) {
        this.client = client;
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("[ERROR] MQTT connection lost: " + cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {

        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        // Debug Log etwas gekürzt, damit bei vielen Sensoren die Konsole nicht explodiert
        if(!topic.startsWith(Config.TOPIC_SPOT)) {
            System.out.println("[MQTT] Received on " + topic + ": " + payload);
        }

        if (topic.equals(Config.TOPIC_REGISTRATION)) {
            handleRegistration(payload);

        } else if (topic.equals(Config.TOPIC_LICENSE_PLATE)) {
            handleAccess(payload);

        } else if (topic.equals(Config.TOPIC_MOVE_REQUEST)) {
            handleMoveRequest(payload);

        } 
        // WICHTIG: Hier prüfen wir mit startsWith, weil das Topic z.B. "parking/raw/spot/A-01" ist
        else if (topic.startsWith(Config.TOPIC_SPOT)) {
            handleSpotUpdate(topic, payload);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // optional logging
    }

    // -----------------------------
    // HANDLER-BEREICH
    // -----------------------------

    private void handleRegistration(String json) throws Exception {
        RegistrationEvent reg = mapper.readValue(json, RegistrationEvent.class);
        userRegistry.register(reg);
        System.out.println("[ACCESS] Registered new plate: " + reg.getPlate());
    }

    private void handleAccess(String json) throws Exception {
        LicensePlateEvent lp = mapper.readValue(json, LicensePlateEvent.class);
        UserProfile user = userRegistry.findByPlate(lp.getPlate());

        // 1. Unbekanntes Kennzeichen
        if (user == null) {
            System.out.println("[ACCESS] Unknown plate: " + lp.getPlate());
            publishBarrier(lp, "DENY", "unknown_plate");
            return;
        }

        // 2. Parkplatz reservieren (Logic im Manager)
        // HINWEIS: Hier nutzen wir jetzt den ParkingStateManager statt SpotAllocator
        String spotId = SpotAllocator.reserveSpotForUser(user);

        if (spotId == null) {
            System.out.println("[ACCESS] No free spot available for " + lp.getPlate());
            publishBarrier(lp, "DENY", "no_free_spot");
            return;
        }

        // 3. Schranke öffnen
        publishBarrier(lp, "OPEN", "valid_user");

        // 4. Display-/Parking-Node informieren
        publishAllocation(lp, user, spotId);

        System.out.println("[ACCESS] Allocation OK: " + lp.getPlate() + " -> " + spotId);
    }

    private void handleMoveRequest(String json) throws Exception {
        MoveRequestEvent moveReq = mapper.readValue(json, MoveRequestEvent.class);
        UserProfile user = userRegistry.findByPlate(moveReq.getPlate());

        if (user == null) {
             System.out.println("[MOVE] Request for unknown plate: " + moveReq.getPlate());
             return;
        }

        NotificationEvent notif = NotificationEvent.fromMoveRequest(moveReq, user);
        String notifJson = mapper.writeValueAsString(notif);

        client.publish(
                Config.TOPIC_NOTIFICATION,
                new MqttMessage(notifJson.getBytes(StandardCharsets.UTF_8))
        );
    }

    private void handleSpotUpdate(String topic, String payload) throws Exception {
        SpotUpdateEvent spotUpd = SpotUpdateEvent.of(topic, payload);
        SpotAllocator.handleSensorUpdate(spotUpd.getSpotId(), spotUpd.isOccupied());
    }

    // -----------------------------
    // PUBLISHER-Methoden
    // -----------------------------
    private void publishBarrier(LicensePlateEvent lp, String action, String reason) throws Exception {
        BarrierCommand cmd = new BarrierCommand(lp.getGateId(), lp.getPlate(), action, reason);
        client.publish(Config.TOPIC_BARRIER, new MqttMessage(mapper.writeValueAsString(cmd).getBytes(StandardCharsets.UTF_8)));
    }

    private void publishAllocation(LicensePlateEvent lp, UserProfile user, String spotId) throws Exception {
        AllocationEvent alloc = new AllocationEvent(lp.getPlate(), lp.getGateId(), spotId);
        client.publish(Config.TOPIC_ALLOCATION, new MqttMessage(mapper.writeValueAsString(alloc).getBytes(StandardCharsets.UTF_8)));
    }
}