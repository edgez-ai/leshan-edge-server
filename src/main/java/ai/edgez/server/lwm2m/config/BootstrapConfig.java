package ai.edgez.server.lwm2m.config;

import java.io.IOException;
import java.util.List;

import org.eclipse.leshan.core.link.Link;
import org.eclipse.leshan.core.model.InvalidDDFFileException;
import org.eclipse.leshan.core.model.InvalidModelException;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.node.LwM2mNode;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.server.model.LwM2mModelProvider;
import org.eclipse.leshan.server.model.VersionedModelProvider;
import org.eclipse.leshan.server.redis.CustomRedisRegistrationStore;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import ai.edgez.server.lwm2m.constant.LwM2mDemoConstant;
import ai.edgez.server.lwm2m.model.JacksonLinkSerializer;
import ai.edgez.server.lwm2m.model.JacksonLwM2mNodeDeserializer;
import ai.edgez.server.lwm2m.model.JacksonLwM2mNodeSerializer;
import ai.edgez.server.lwm2m.model.JacksonRegistrationSerializer;
import ai.edgez.server.lwm2m.model.JacksonResponseSerializer;
import redis.clients.jedis.JedisPool;

@Configuration
public class BootstrapConfig {
	
    @Autowired
    private JedisPool jedisPool;
    
    @Bean
    public RegistrationStore registrationStore() {
    	return new CustomRedisRegistrationStore.Builder(jedisPool).setPrefix("registration").build();
    }

    @Bean
    public ObjectMapper objectMapper(LeshanServerComponent server) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        SimpleModule module = new SimpleModule();
        module.addSerializer(Link.class, new JacksonLinkSerializer());
        module.addSerializer(Registration.class, new JacksonRegistrationSerializer(server.getServer().getPresenceService()));
        module.addSerializer(LwM2mResponse.class, new JacksonResponseSerializer());
        module.addSerializer(LwM2mNode.class, new JacksonLwM2mNodeSerializer());
        module.addDeserializer(LwM2mNode.class, new JacksonLwM2mNodeDeserializer());
        mapper.registerModule(module);

        return mapper;
    }

    @Bean
    public LwM2mModelProvider modelProvider() throws IOException, InvalidModelException, InvalidDDFFileException {
        // Load default LWM2M object models
        List<ObjectModel> models = ObjectLoader.loadAllDefault();
        models.addAll(ObjectLoader.loadDdfResources("/models/", LwM2mDemoConstant.modelPaths));
        // You can add custom models here if needed
        LwM2mModelProvider modelProvider = new VersionedModelProvider(models);
        return modelProvider;
    }

}
