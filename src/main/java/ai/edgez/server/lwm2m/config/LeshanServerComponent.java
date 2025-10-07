package ai.edgez.server.lwm2m.config;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.scandium.config.DtlsConfig;
import org.eclipse.californium.scandium.dtls.InMemorySessionStore;
import org.eclipse.leshan.core.model.InvalidDDFFileException;
import org.eclipse.leshan.core.model.InvalidModelException;
import org.eclipse.leshan.core.observation.CompositeObservation;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.core.observation.SingleObservation;
import org.eclipse.leshan.core.response.ObserveCompositeResponse;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.server.LeshanServer;
import org.eclipse.leshan.server.LeshanServerBuilder;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.observation.ObservationListener;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationListener;
import org.eclipse.leshan.server.registration.RegistrationStore;
import org.eclipse.leshan.server.registration.RegistrationUpdate;
import org.eclipse.leshan.servers.security.SecurityStore;
import org.eclipse.leshan.transport.californium.server.endpoint.CaliforniumServerEndpointsProvider;
import org.eclipse.leshan.transport.californium.server.endpoint.coap.CoapServerProtocolProvider;
import org.eclipse.leshan.transport.californium.server.endpoint.coaps.CoapsServerProtocolProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class LeshanServerComponent {
    private LeshanServer server;
    
    @Autowired
    private SecurityStore securityStore;
    
    @Autowired
    private RegistrationStore registrationStore;

    @Autowired
    private LwM2mModelProvider modelProvider;


    @PostConstruct
    public void startServer() throws IOException, InvalidModelException, InvalidDDFFileException {

        LeshanServerBuilder builder = new LeshanServerBuilder();


        builder.setObjectModelProvider(modelProvider);
        
        

        builder.setSecurityStore(securityStore);
        builder.setRegistrationStore(registrationStore);

        // Define trust store
 //       List<Certificate> trustStore = cli.identity.getTrustStore();
 //       builder.setTrustedCertificates(trustStore.toArray(new Certificate[trustStore.size()]));

        // Configure Californium endpoints (CoAP and CoAPS)
        // TODO it doesn't take any effect, seems need to change hardcoded DTLSConnector
        CaliforniumServerEndpointsProvider.Builder endpointsBuilder = new CaliforniumServerEndpointsProvider.Builder(
                new CoapServerProtocolProvider(),
                new CoapsServerProtocolProvider(c -> {
                    // Add MDC for connection logs
                        c.setSessionStore(new InMemorySessionStore(1000,300));

                }));

        // Create default configuration
        Configuration serverCoapConfig = endpointsBuilder.createDefaultConfiguration();
        // 10 minutes idle allowed
        serverCoapConfig.set(DtlsConfig.DTLS_STALE_CONNECTION_THRESHOLD, 300,TimeUnit.SECONDS);
        serverCoapConfig.set(DtlsConfig.DTLS_AUTO_HANDSHAKE_TIMEOUT, 300*1000, TimeUnit.MILLISECONDS);

        int coapsPort = serverCoapConfig.get(CoapConfig.COAP_SECURE_PORT);
        String coapsUri = "coaps://0.0.0.0:" + coapsPort;
        endpointsBuilder.addEndpoint(coapsUri);

        builder.setEndpointsProviders(endpointsBuilder.build());
        // builder.setUpdateRegistrationOnNotification(true);
        // builder.setUpdateRegistrationOnSend(true);
        builder.setAuthorizer(new CustomAuthorizer(securityStore));
//		K8sManagementClient k8sClient = null;
//		K8sDiscoverClient k8sGroup = null;
//		int nodeId = -1;
//		
//		ProtocolScheduledExecutorService executor = ExecutorsUtil.newProtocolScheduledThreadPool(//
//				serverCoapConfig.get(CoapConfig.PROTOCOL_STAGE_THREAD_COUNT), //
//				new NamedThreadFactory("ExtCoapServer#")); //$NON-NLS-1$
//		
//		DtlsClusterConnectorConfig.Builder clusterConfigBuilder = DtlsClusterConnectorConfig.builder();
//		clusterConfigBuilder.setAddress(config.cluster.clusterType.k8sCluster.cluster);
//		clusterConfigBuilder.setClusterMac(config.cluster.dtlsClusterMac);
//		K8sDiscoverClient.setConfiguration(clusterConfigBuilder);
//		k8sClient = new K8sManagementJdkClient();
//		k8sGroup = new K8sDiscoverClient(k8sClient, config.cluster.clusterType.k8sCluster.externalPort);
//		nodeId = k8sGroup.getNodeID();
//		LOGGER.info("dynamic k8s-cluster!");
//		DtlsClusterConnectorConfig clusterConfig = clusterConfigBuilder.build();
//		server.addClusterEndpoint(executor.getBackgroundExecutor(), config.cluster.clusterType.k8sCluster.dtls,
//				k8sGroup.getNodeID(), clusterConfig, null, k8sGroup, config);

        server = builder.build();
        server.getRegistrationService().addListener(new RegistrationListener() {
            @Override
            public void registered(Registration reg, Registration previousReg, Collection<Observation> previousObsersations) {
                // Automatically observe resource /3/0/3 (Firmware Version)
                try {
                    org.eclipse.leshan.core.request.ObserveRequest observeRequest = new org.eclipse.leshan.core.request.ObserveRequest("/3442/0/120");
                    server.send(reg, observeRequest, 5000L); // 5 seconds timeout
                } catch (Exception e) {
                    System.err.println("Failed to send observe request: " + e.getMessage());
                }
            }

            @Override
            public void updated(RegistrationUpdate update, Registration updatedReg, Registration previousReg) {
                // Automatically observe resource /3/0/3 (Firmware Version)
                try {
                    org.eclipse.leshan.core.request.ObserveRequest observeRequest = new org.eclipse.leshan.core.request.ObserveRequest("/3442/0/120");
                    server.send(updatedReg, observeRequest, 5000L); // 5 seconds timeout
                } catch (Exception e) {
                    System.err.println("Failed to send observe request: " + e.getMessage());
                }
            }

            @Override
            public void unregistered(Registration registration, Collection<Observation> observations, boolean expired,
                    Registration newReg) {

            }
        });
        // Listen for notifications from observed resources
        server.getObservationService().addListener(new ObservationListener() {

            @Override
            public void newObservation(Observation observation, Registration registration) {
                System.out.println("New observation started for " + observation.getRegistrationId());
            }

            @Override
            public void cancelled(Observation observation) {
                System.out.println("Observation cancelled for " + observation.getRegistrationId());
            }

            @Override
            public void onResponse(SingleObservation observation, Registration registration, ObserveResponse response) {
                if (response.isSuccess()) {
                    System.out.println("Notification received for " + observation.getRegistrationId() + ": " + response.getContent());
                } else {
                    System.err.println("Failed notification for " + observation.getRegistrationId() + ": " + response.getCode());
                }
            }

            @Override
            public void onResponse(CompositeObservation observation, Registration registration,
                    ObserveCompositeResponse response) {
                System.out.println("Composite notification received for " + observation.getRegistrationId() + ": " + response);
            }

            @Override
            public void onError(Observation observation, Registration registration, Exception error) {
                System.err.println("Observation error for " + observation.getRegistrationId() + ": " + error.getMessage());
            }

        });
        server.start();
        System.out.println("Leshan server started.");
    }

    @PreDestroy
    public void stopServer() {
        if (server != null) {
            server.stop();
            System.out.println("Leshan server stopped.");
        }
    }

    public LeshanServer getServer() {
        return server;
    }
}
