package de.campuspark;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import de.campuspark.util.Config;

public class ParkingAccessService
{
    public static void main( String[] args )
    {
        String brokerUrl = Config.MQTT_BROKER;
        String clientId  = Config.MQTT_CLIENT_ID;

        MqttClient client;

        try {
            client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setUserName(Config.MQTT_USERNAME);
            options.setPassword(Config.MQTT_PASSWORD.toCharArray());

            //TODO:Subscribe to RAW Messages
            client.subscribe(Config.TOPIC_REGISTRATION);
            client.subscribe(Config.TOPIC_LICENSE_PLATE);
            client.subscribe(Config.TOPIC_MOVE_REQUEST);
        } catch (MqttException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
}
