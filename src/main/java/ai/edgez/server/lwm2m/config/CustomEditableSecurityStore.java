package ai.edgez.server.lwm2m.config;

import java.util.Arrays;

import org.eclipse.leshan.core.peer.OscoreIdentity;
import org.eclipse.leshan.servers.security.SecurityInfo;
import org.eclipse.leshan.servers.security.SecurityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ai.edgez.server.lwm2m.util.SecretKeyService;

@Component
public class CustomEditableSecurityStore implements SecurityStore{
	private static Logger logger = LoggerFactory.getLogger(CustomEditableSecurityStore.class);
	@Autowired
	private SecretKeyService secretKeyService;

	@Override
	public SecurityInfo getByEndpoint(String endpoint) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SecurityInfo getByIdentity(String pskIdentity) {
		SecurityInfo info = SecurityInfo.newPreSharedKeyInfo(getEndpointFromIdentity(pskIdentity), pskIdentity, secretKeyService.getPSK(pskIdentity));
		logger.info("PSK for device {} : {}", pskIdentity, Arrays.toString(info.getPreSharedKey()));
		return info;
	}

	private String getEndpointFromIdentity(String pskIdentity) {
		return pskIdentity.substring(0, pskIdentity.length() - 4);
	}

	@Override
	public SecurityInfo getByOscoreIdentity(OscoreIdentity oscoreIdentity) {
		// TODO Auto-generated method stub
		return null;
	}


}
