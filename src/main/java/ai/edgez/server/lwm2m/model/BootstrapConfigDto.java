package ai.edgez.server.lwm2m.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.leshan.bsserver.BootstrapConfig;
import org.eclipse.leshan.bsserver.BootstrapConfig.ACLConfig;
import org.eclipse.leshan.bsserver.BootstrapConfig.OscoreObject;
import org.eclipse.leshan.bsserver.BootstrapConfig.ServerConfig;
import org.eclipse.leshan.bsserver.BootstrapConfig.ServerSecurity;
import org.eclipse.leshan.core.request.BootstrapDiscoverRequest;
import org.eclipse.leshan.core.request.ContentFormat;

public class BootstrapConfigDto {
    /**
     * If activated, bootstrap server will start the bootstrap session with a {@link BootstrapDiscoverRequest} to know
     * what is the Security(object 0) instance ID of bootstrap server on the device, then it will use this information
     * to automatically attribute IDs to {@link #security} instance. (meaning that ID defined in {@link #security} will
     * not be used)
     * <p>
     * This can be useful because bootstrap server can not be deleted and only 1 Bootstrap server can be present in
     * Security Object. <br>
     * See <a href=
     * "https://github.com/OpenMobileAlliance/OMA_LwM2M_for_Developers/issues/522">OMA_LwM2M_for_Developers#522</a>for
     * more details .
     */
    public boolean autoIdForSecurityObject = false;

    /**
     * Content format used to send requests.
     * <p>
     * if <code>null</code> content format used is the preferred one by the client (pct attribute from BootstrapRequest)
     */
    public ContentFormat contentFormat = null;

    /**
     * List of LWM2M path to delete.
     */
    public List<String> toDelete = new ArrayList<>();

    /**
     * Map indexed by Server Instance Id. Key is the Server Instance to write.
     */
    public Map<Integer, ServerConfigDto> servers = new HashMap<>();

    /**
     * Map indexed by Security Instance Id. Key is the Server Instance to write.
     */
    public Map<Integer, ServerSecurity> security = new HashMap<>();

    /**
     * Map indexed by ACL Instance Id. Key is the ACL Instance to write.
     */
    public Map<Integer, ACLConfig> acls = new HashMap<>();

    /**
     * Map indexed by OSCORE Object Instance Id. Key is the OSCORE Object Instance to write.
     */
    public Map<Integer, OscoreObject> oscore = new HashMap<>();

	public boolean isAutoIdForSecurityObject() {
		return autoIdForSecurityObject;
	}

	public void setAutoIdForSecurityObject(boolean autoIdForSecurityObject) {
		this.autoIdForSecurityObject = autoIdForSecurityObject;
	}

	public ContentFormat getContentFormat() {
		return contentFormat;
	}

	public void setContentFormat(ContentFormat contentFormat) {
		this.contentFormat = contentFormat;
	}

	public List<String> getToDelete() {
		return toDelete;
	}

	public void setToDelete(List<String> toDelete) {
		this.toDelete = toDelete;
	}

	public Map<Integer, ServerConfigDto> getServers() {
		return servers;
	}

	public void setServers(Map<Integer, ServerConfigDto> servers) {
		this.servers = servers;
	}

	public Map<Integer, ServerSecurity> getSecurity() {
		return security;
	}

	public void setSecurity(Map<Integer, ServerSecurity> security) {
		this.security = security;
	}

	public Map<Integer, ACLConfig> getAcls() {
		return acls;
	}

	public void setAcls(Map<Integer, ACLConfig> acls) {
		this.acls = acls;
	}

	public Map<Integer, OscoreObject> getOscore() {
		return oscore;
	}

	public void setOscore(Map<Integer, OscoreObject> oscore) {
		this.oscore = oscore;
	}

	public BootstrapConfig toConfig() {
		BootstrapConfig config = new BootstrapConfig();
		config.acls = this.acls;
		config.autoIdForSecurityObject = this.autoIdForSecurityObject;
		config.contentFormat = this.contentFormat;
		config.oscore = this.oscore;
		config.security = this.security;
		config.servers = parseServers(this.servers);
		config.toDelete = this.toDelete;
		return config;
	}

	private Map<Integer, ServerConfig> parseServers(Map<Integer, ServerConfigDto> servers2) {
		Map<Integer, ServerConfig> result = new HashMap<>();
		for(Map.Entry<Integer, ServerConfigDto> entry:servers.entrySet()) {
			result.put(entry.getKey(), parseServer(entry.getValue()));
		}
		return result;
	}

	private ServerConfig parseServer(ServerConfigDto value) {
		ServerConfig config = new ServerConfig();
		config.apnLink = value.apnLink;
		config.binding = value.binding;
		config.bootstrapOnRegistrationFailure = value.bootstrapOnRegistrationFailure;
		config.communicationRetryCount = value.communicationRetryCount;
		config.CommunicationRetryTimer = value.CommunicationRetryTimer;
		config.defaultMaxPeriod = value.defaultMaxPeriod;
		config.defaultMinPeriod = value.defaultMinPeriod;
		config.disableTimeout = value.disableTimeout;
		config.initialDelay = value.initialDelay;
		config.lifetime = value.lifetime;
		config.muteSend = value.muteSend;
		config.notifIfDisabled = value.notifIfDisabled;
		config.preferredTransport = value.preferredTransport;
		config.registrationFailure = value.registrationFailure;
		config.registrationPriority = value.registrationPriority;
		config.SequenceDelayTimer = value.SequenceDelayTimer;
		config.SequenceRetryCount = value.SequenceRetryCount;
		config.shortId = value.shortId;
		config.trigger = value.trigger;
		return config;
	}
    
    
    
}
