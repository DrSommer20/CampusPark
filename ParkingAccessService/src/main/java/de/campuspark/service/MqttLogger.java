package de.campuspark.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.campuspark.model.LogEvent;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import java.nio.charset.StandardCharsets;

public class MqttLogger {

    private static MqttClient client;
    private static final String TOPIC = "parking/logs";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void setMqttClient(MqttClient mqttClient) {
        client = mqttClient;
    }

    public static void info(String source, String message, String spotId) {
        publish("INFO", source, message, spotId);
    }

    public static void warn(String source, String message, String spotId) {
        publish("WARN", source, message, spotId);
    }
    
    public static void error(String source, String message) {
        publish("ERROR", source, message, null);
    }

    private static void publish(String level, String source, String msg, String spotId) {
        if (client == null || !client.isConnected()) {
            // Fallback, falls MQTT down ist
            System.out.println("[" + level + "] " + msg); 
            return;
        }

        try {
            LogEvent event = new LogEvent(
                level, 
                source, 
                msg, 
                spotId != null ? spotId : "N/A", 
                System.currentTimeMillis()
            );
            
            String json = mapper.writeValueAsString(event);
            MqttMessage mqttMessage = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
            mqttMessage.setQos(0);
            
            client.publish(TOPIC, mqttMessage);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}