package de.campuspark;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import de.campuspark.service.ParkingAccessCallback;
import de.campuspark.util.Config;

public class ParkingAccessService
{
    public static void main(String[] args) throws InterruptedException
    {
        String brokerUrl = Config.MQTT_BROKER;
        String clientId  = Config.MQTT_CLIENT_ID;

        try {
            MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setMaxInflight(100);
            options.setConnectionTimeout(30);
            options.setKeepAliveInterval(60);
            options.setUserName(Config.MQTT_USERNAME);
            options.setPassword(Config.MQTT_PASSWORD.toCharArray());


            client.setCallback(new ParkingAccessCallback(client));

            client.connect(options);
            System.out.println("Connected to Broker!");

            client.subscribe(Config.TOPIC_REGISTRATION);
            client.subscribe(Config.TOPIC_LICENSE_PLATE);
            client.subscribe(Config.TOPIC_MOVE_REQUEST);
            client.subscribe(Config.TOPIC_SPOT); 

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}