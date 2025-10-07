package ai.edgez.server.lwm2m.service;

import org.eclipse.paho.mqttv5.client.IMqttAsyncClient;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.stereotype.Service;

//@Service
public class MqttService {
    private final IMqttAsyncClient mqttClient;

    public MqttService(IMqttAsyncClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    public void publish(String topic, String payload) throws MqttException {
        MqttMessage message = new MqttMessage(payload.getBytes());
        if(!mqttClient.isConnected()) {
        	mqttClient.connect().waitForCompletion();
        }
        mqttClient.publish(topic, message);
    }

    // Add subscribe and other methods as needed
}
