package ai.edgez.server.lwm2m.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {
    private String broker;
    private String clientId;
    private String username;
    private String password;
    private boolean cleanSession = true;
    private int connectionTimeout = 10;
    private int keepAliveInterval = 60;

    // Getters and setters
    public String getBroker() { return broker; }
    public void setBroker(String broker) { this.broker = broker; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isCleanSession() { return cleanSession; }
    public void setCleanSession(boolean cleanSession) { this.cleanSession = cleanSession; }
    public int getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    public int getKeepAliveInterval() { return keepAliveInterval; }
    public void setKeepAliveInterval(int keepAliveInterval) { this.keepAliveInterval = keepAliveInterval; }
}
