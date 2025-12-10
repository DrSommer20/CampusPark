package de.campuspark.util;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {
    private static final Dotenv dotenv = Dotenv.load();

    public static final String MQTT_BROKER = dotenv.get("MQTT_BROKER");
    public static final String MQTT_USERNAME = dotenv.get("MQTT_USERNAME");
    public static final String MQTT_PASSWORD = dotenv.get("MQTT_PASSWORD");
    public static final String MQTT_CLIENT_ID = dotenv.get("MQTT_CLIENT_ID");

    public static final String TOPIC_REGISTRATION  = dotenv.get("TOPIC_REGISTRATION");
    public static final String TOPIC_LICENSE_PLATE = dotenv.get("TOPIC_LICENSE_PLATE");
    public static final String TOPIC_MOVE_REQUEST  = dotenv.get("TOPIC_MOVE_REQUEST");
    public static final String TOPIC_SPOT_STATE = dotenv.get("TOPIC_SPOT_STATE");

    public static final String TOPIC_ALLOCATION = dotenv.get("TOPIC_ALLOCATION");
    public static final String TOPIC_BARRIER    = dotenv.get("TOPIC_BARRIER");
    public static final String TOPIC_NOTIFICATION = dotenv.get("TOPIC_NOTIFICATION");
    public static final String TOPIC_SPOT = dotenv.get("TOPIC_SPOT_RAW");
}
