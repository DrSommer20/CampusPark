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
    private final SpotAllocator spotAllocator = new SpotAllocator();

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
        System.out.println("[MQTT] Received on " + topic + ": " + payload);

        if (topic.equals(Config.TOPIC_REGISTRATION)) {
            handleRegistration(payload);

        } else if (topic.equals(Config.TOPIC_LICENSE_PLATE)) {
            handleAccess(payload);

        } else if (topic.equals(Config.TOPIC_MOVE_REQUEST)) {
            handleMoveRequest(payload);
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

        // 1. Unbekanntes Kennzeichen → Schranke verweigern
        if (user == null) {
            System.out.println("[ACCESS] Unknown plate: " + lp.getPlate());
            publishBarrier(lp, "DENY", "unknown_plate");
            return;
        }

        // 2. Parkplatz finden (z. B. freie Spots aus State-Service)
        String spotId = spotAllocator.findSpotFor(user);

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
            System.out.println("[MOVE] Move request for unknown plate: " + moveReq.getPlate());
            return;
        }

        NotificationEvent notif = NotificationEvent.fromMoveRequest(moveReq, user);
        String notifJson = mapper.writeValueAsString(notif);

        client.publish(
                Config.TOPIC_NOTIFICATION,
                new MqttMessage(notifJson.getBytes(StandardCharsets.UTF_8))
        );

        System.out.println("[MOVE] Notification sent for plate: " + moveReq.getPlate());
    }

    // -----------------------------
    // PUBLISHER-Methoden
    // -----------------------------

    private void publishBarrier(LicensePlateEvent lp, String action, String reason) throws Exception {
        BarrierCommand cmd = new BarrierCommand(
                lp.getGateId(),
                lp.getPlate(),
                action,
                reason
        );

        String json = mapper.writeValueAsString(cmd);

        client.publish(
                Config.TOPIC_BARRIER,
                new MqttMessage(json.getBytes(StandardCharsets.UTF_8))
        );
    }

    private void publishAllocation(LicensePlateEvent lp, UserProfile user, String spotId) throws Exception {
        AllocationEvent alloc = new AllocationEvent(
                lp.getPlate(),
                lp.getGateId(),
                spotId
        );

        String json = mapper.writeValueAsString(alloc);

        client.publish(
                Config.TOPIC_ALLOCATION,
                new MqttMessage(json.getBytes(StandardCharsets.UTF_8))
        );
    }
}
