package ai.edgez.server.lwm2m.config;

import org.eclipse.paho.mqttv5.client.IMqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
public class MqttConfig {
	private static final Logger logger = LoggerFactory.getLogger(MqttConfig.class);
	private final MqttProperties mqttProperties;

	public MqttConfig(MqttProperties mqttProperties) {
		this.mqttProperties = mqttProperties;
	}

	@Bean
	public IMqttAsyncClient mqttClient()  {
		logger.info("create mqtt client");
		MqttAsyncClient client = null;
		try {
			client = new MqttAsyncClient(
					mqttProperties.getBroker(),
					mqttProperties.getClientId()
			);

			MqttConnectionOptions options = new MqttConnectionOptions();
			options.setUserName(mqttProperties.getUsername());
			options.setPassword(mqttProperties.getPassword() != null ? mqttProperties.getPassword().getBytes() : null);
			options.setCleanStart(mqttProperties.isCleanSession());
			options.setConnectionTimeout(mqttProperties.getConnectionTimeout());
			options.setKeepAliveInterval(mqttProperties.getKeepAliveInterval());
			options.setAutomaticReconnect(true);
			// Trust all certificates (for testing only!)
			if (mqttProperties.getBroker().startsWith("ssl") || mqttProperties.getBroker().startsWith("mqtts")) {
				try {
					TrustManager[] trustAllCerts = new TrustManager[]{
						new X509TrustManager() {
							public void checkClientTrusted(X509Certificate[] chain, String authType) {}
							public void checkServerTrusted(X509Certificate[] chain, String authType) {}
							public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
						}
					};
					SSLContext sc = SSLContext.getInstance("TLS");
					sc.init(null, trustAllCerts, new java.security.SecureRandom());
					SSLSocketFactory sslSocketFactory = sc.getSocketFactory();
					options.setSocketFactory(sslSocketFactory);
				} catch (Exception e) {
					logger.error("Failed to set trust-all SSL context", e);
				}
			}
			client.connect(options).waitForCompletion();
			logger.info("client connected");
		} catch (MqttException e) {
			e.printStackTrace();
		}
		return client;
	}
}
