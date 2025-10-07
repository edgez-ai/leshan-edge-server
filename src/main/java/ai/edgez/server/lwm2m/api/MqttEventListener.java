/*******************************************************************************
 * Copyright (c) 2013-2015 Sierra Wireless and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *     Michał Wadowski (Orange) - Add Observe-Composite feature.
 *     Orange - keep one JSON dependency
 *******************************************************************************/
package ai.edgez.server.lwm2m.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.leshan.core.LwM2m.Version;
import org.eclipse.leshan.core.link.Link;
import org.eclipse.leshan.core.node.LwM2mNode;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.TimestampedLwM2mNodes;
import org.eclipse.leshan.core.observation.CompositeObservation;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.core.observation.SingleObservation;
import org.eclipse.leshan.core.request.SendRequest;
import org.eclipse.leshan.core.response.ObserveCompositeResponse;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.demo.server.servlet.json.JacksonLinkSerializer;
import org.eclipse.leshan.demo.server.servlet.json.JacksonLwM2mNodeSerializer;
import org.eclipse.leshan.demo.server.servlet.json.JacksonRegistrationSerializer;
import org.eclipse.leshan.demo.server.servlet.json.JacksonRegistrationUpdateSerializer;
import org.eclipse.leshan.demo.server.servlet.json.JacksonVersionSerializer;
import org.eclipse.leshan.server.observation.ObservationListener;
import org.eclipse.leshan.server.queue.PresenceListener;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationListener;
import org.eclipse.leshan.server.registration.RegistrationUpdate;
import org.eclipse.leshan.server.send.SendListener;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import ai.edgez.server.lwm2m.config.LeshanServerComponent;
import ai.edgez.server.lwm2m.service.MqttService;

//@Component
public class MqttEventListener {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(MqttEventListener.class);

    private static final String EVENT_DEREGISTRATION = "DEREGISTRATION";
    private static final String EVENT_UPDATED = "UPDATED";
    private static final String EVENT_REGISTRATION = "REGISTRATION";
    private static final String EVENT_AWAKE = "AWAKE";
    private static final String EVENT_SLEEPING = "SLEEPING";
    private static final String EVENT_NOTIFICATION = "NOTIFICATION";
    private static final String EVENT_SEND = "SEND";
    private static final String EVENT_COAP_LOG = "COAPLOG";

    private static final String QUERY_PARAM_ENDPOINT = "ep";
    
    @Autowired
    private MqttService mqttService;

    private final transient ObjectMapper mapper;
    private final transient RegistrationListener registrationListener = new RegistrationListener() {

        @Override
        public void registered(Registration registration, Registration previousReg,
                Collection<Observation> previousObservations) {
            String jReg = null;
            try {
                jReg = MqttEventListener.this.mapper.writeValueAsString(registration);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
            sendEvent(EVENT_REGISTRATION, jReg, registration.getEndpoint());
        }

        @Override
        public void updated(RegistrationUpdate update, Registration updatedRegistration,
                Registration previousRegistration) {
            RegUpdate regUpdate = new RegUpdate();
            regUpdate.registration = updatedRegistration;
            regUpdate.update = update;
            String jReg = null;
            try {
                jReg = MqttEventListener.this.mapper.writeValueAsString(regUpdate);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
            sendEvent(EVENT_UPDATED, jReg, updatedRegistration.getEndpoint());
        }

        @Override
        public void unregistered(Registration registration, Collection<Observation> observations, boolean expired,
                Registration newReg) {
            String jReg = null;
            try {
                jReg = MqttEventListener.this.mapper.writeValueAsString(registration);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
            sendEvent(EVENT_DEREGISTRATION, jReg, registration.getEndpoint());
        }

    };

    public final transient PresenceListener presenceListener = new PresenceListener() {

        @Override
        public void onSleeping(Registration registration) {
            String data = new StringBuilder("{\"ep\":\"").append(registration.getEndpoint()).append("\"}").toString();

            sendEvent(EVENT_SLEEPING, data, registration.getEndpoint());
        }

        @Override
        public void onAwake(Registration registration) {
            String data = new StringBuilder("{\"ep\":\"").append(registration.getEndpoint()).append("\"}").toString();
            sendEvent(EVENT_AWAKE, data, registration.getEndpoint());
        }
    };

    private final transient ObservationListener observationListener = new ObservationListener() {

        @Override
        public void cancelled(Observation observation) {
            // no event sent on cancelled observation for now
        }

        @Override
        public void onResponse(SingleObservation observation, Registration registration, ObserveResponse response) {

            if (LOG.isDebugEnabled()) {
                LOG.debug("Received notification from [{}] containing value [{}]", observation.getPath(),
                        response.getContent());
            }
            String jsonContent = null;
            try {
                jsonContent = mapper.writeValueAsString(response.getContent());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }

            if (registration != null) {
                String data = new StringBuilder("{\"ep\":\"") //
                        .append(registration.getEndpoint()) //
                        .append("\",\"kind\":\"single\"") //
                        .append(",\"res\":\"") //
                        .append(observation.getPath()).append("\",\"val\":") //
                        .append(jsonContent) //
                        .append("}") //
                        .toString();

                sendEvent(EVENT_NOTIFICATION, data, registration.getEndpoint());
            }
        }

        @Override
        public void onResponse(CompositeObservation observation, Registration registration,
                ObserveCompositeResponse response) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Received composite notificationfrom [{}] containing value [{}]", registration,
                        response.getContent());
            }
            String jsonContent = null;
            String jsonListOfPath = null;
            try {
                jsonContent = mapper.writeValueAsString(response.getContent());
                List<String> paths = new ArrayList<>();
                for (LwM2mPath path : response.getObservation().getPaths()) {
                    paths.add(path.toString());
                }
                jsonListOfPath = mapper.writeValueAsString(paths);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }

            if (registration != null) {
                String data = new StringBuilder("{\"ep\":\"") //
                        .append(registration.getEndpoint()) //
                        .append("\",\"kind\":\"composite\"") //
                        .append(",\"val\":") //
                        .append(jsonContent) //
                        .append(",\"paths\":") //
                        .append(jsonListOfPath) //
                        .append("}") //
                        .toString();

                sendEvent(EVENT_NOTIFICATION, data, registration.getEndpoint());
            }
        }

        @Override
        public void onError(Observation observation, Registration registration, Exception error) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(String.format("Unable to handle notification of [%s:%s]", observation.getRegistrationId(),
                        getObservationPaths(observation)), error);
            }
        }

        @Override
        public void newObservation(Observation observation, Registration registration) {
            // no event sent on new observation for now
        }

        private String getObservationPaths(final Observation observation) {
            String path = null;
            if (observation instanceof SingleObservation) {
                path = ((SingleObservation) observation).getPath().toString();
            } else if (observation instanceof CompositeObservation) {
                path = ((CompositeObservation) observation).getPaths().toString();
            }
            return path;
        }
    };

    private final transient SendListener sendListener = new SendListener() {

        @Override
        public void dataReceived(Registration registration, TimestampedLwM2mNodes data, SendRequest request) {

            if (LOG.isDebugEnabled()) {
                LOG.debug("Received Send request from [{}] containing value [{}]", registration, data);
            }

            if (registration != null) {
                try {
                    String jsonContent = MqttEventListener.this.mapper.writeValueAsString(data.getMostRecentNodes());

                    String eventData = new StringBuilder("{\"ep\":\"") //
                            .append(registration.getEndpoint()) //
                            .append("\",\"val\":") //
                            .append(jsonContent) //
                            .append("}") //
                            .toString(); //

                    sendEvent(EVENT_SEND, eventData, registration.getEndpoint());
                } catch (JsonProcessingException e) {
                    MqttEventListener.LOG.warn(String.format("Error while processing json [%s] : [%s]", data, e.getMessage()));
                }
            }
        }

        @Override
        public void onError(Registration registration, String errorMessage, Exception error) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(String.format("Unable to handle Send Request from [%s] : %s.", registration, errorMessage),
                        error);
            }
        }
    };

    @Autowired
    public MqttEventListener(LeshanServerComponent server) {
        server.getServer().getRegistrationService().addListener(this.registrationListener);
        server.getServer().getObservationService().addListener(this.observationListener);
        server.getServer().getPresenceService().addListener(this.presenceListener);
        server.getServer().getSendService().addListener(this.sendListener);


        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        SimpleModule module = new SimpleModule();
        module.addSerializer(Link.class, new JacksonLinkSerializer());
        module.addSerializer(Registration.class, new JacksonRegistrationSerializer(server.getServer().getPresenceService()));
        module.addSerializer(RegistrationUpdate.class, new JacksonRegistrationUpdateSerializer());
        module.addSerializer(LwM2mNode.class, new JacksonLwM2mNodeSerializer());
        module.addSerializer(Version.class, new JacksonVersionSerializer());
        objectMapper.registerModule(module);
        this.mapper = objectMapper;
    }

    public synchronized void sendEvent(String event, String data, String endpoint) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Dispatching {} event from endpoint {}", event, endpoint);
        }
        
        // send event to mqtt
        //TODO also need to do adaptation for the protocol
//        try {
//        	mqttService.publish("test",data);
//        } catch (MqttException e) {
//            e.printStackTrace();
//        }
        
    }

    @SuppressWarnings("unused")
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private class RegUpdate {
        private Registration registration;
        private RegistrationUpdate update;
    }
}
