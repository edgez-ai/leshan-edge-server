package ai.edgez.server.lwm2m.api;

import java.time.Duration;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.leshan.core.request.ContentFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.eclipse.leshan.core.request.DeleteRequest;
import org.eclipse.leshan.core.request.DownlinkDeviceManagementRequest;
import org.eclipse.leshan.core.request.ExecuteRequest;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.core.observation.SingleObservation;
import org.eclipse.leshan.core.observation.CompositeObservation;
import org.eclipse.leshan.core.request.ObserveRequest;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.request.WriteRequest;
import org.eclipse.leshan.core.request.exception.ClientSleepingException;
import org.eclipse.leshan.core.response.LwM2mResponse;
import org.eclipse.leshan.server.LeshanServer;
import org.eclipse.leshan.server.registration.Registration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ai.edgez.server.lwm2m.config.LeshanServerComponent;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

	private final LeshanServer server;
	private final ObjectMapper objectMapper = new ObjectMapper();

	private static final long DEFAULT_TIMEOUT_MS = Duration.ofSeconds(5).toMillis();

	@Autowired
	public ClientController(LeshanServerComponent server) {
		this.server = server.getServer();
	}

	@GetMapping
	public ResponseEntity<Collection<Registration>> getAllClients() {
		Iterator<Registration> iterator = server.getRegistrationService().getAllRegistrations();
		Collection<Registration> registrations = new java.util.ArrayList<>();
		iterator.forEachRemaining(registrations::add);
		return ResponseEntity.ok(registrations);
	}

	@GetMapping("/{endpoint}")
	public ResponseEntity<Registration> getClient(@PathVariable("endpoint") String endpoint) {
		Registration registration = server.getRegistrationService().getByEndpoint(endpoint);
		if (registration == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(registration);
	}

	@GetMapping("/{endpoint}/read")
	public ResponseEntity<LwM2mResponse> readClient(@PathVariable("endpoint") String endpoint,
			@RequestParam String path, @RequestParam(name = "timeout", required = false) Long timeout) {
		Registration registration = server.getRegistrationService().getByEndpoint(endpoint);
		if (registration == null) {
			return ResponseEntity.notFound().build();
		}
		ReadRequest request = new ReadRequest(path);
		LwM2mResponse response = sendRequestAndGetResponse(registration, request, timeout);
		return ResponseEntity.ok(response);
	}

	@PutMapping("/{endpoint}/write")
	public ResponseEntity<LwM2mResponse> writeClient(@PathVariable("endpoint") String endpoint,
			@RequestParam int objectId, @RequestParam int objectInstanceId, @RequestParam int resourceId,
			@RequestBody String value,
			@RequestParam(name = "format", required = false, defaultValue = "TEXT") String format,
			@RequestParam(name = "timeout", required = false) Long timeout) {
		Registration registration = server.getRegistrationService().getByEndpoint(endpoint);
		if (registration == null) {
			return ResponseEntity.notFound().build();
		}
		ContentFormat contentFormat = ContentFormat.fromName(format.toUpperCase());
		Object actualValue = extractValueMaybeJson(value, resourceId);
		WriteRequest request = buildWriteRequest(contentFormat, objectId, objectInstanceId, resourceId, actualValue);
		LwM2mResponse response = sendRequestAndGetResponse(registration, request, timeout);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/{endpoint}/execute")
	public ResponseEntity<LwM2mResponse> executeClient(@PathVariable("endpoint") String endpoint,
			@RequestParam String path, @RequestBody(required = false) String params,
			@RequestParam(name = "timeout", required = false) Long timeout) {
		Registration registration = server.getRegistrationService().getByEndpoint(endpoint);
		if (registration == null) {
			return ResponseEntity.notFound().build();
		}
		ExecuteRequest request = new ExecuteRequest(path, params);
		LwM2mResponse response = sendRequestAndGetResponse(registration, request, timeout);
		return ResponseEntity.ok(response);
	}

	@DeleteMapping("/{endpoint}/delete")
	public ResponseEntity<LwM2mResponse> deleteClient(@PathVariable("endpoint") String endpoint,
			@RequestParam String path, @RequestParam(name = "timeout", required = false) Long timeout) {
		Registration registration = server.getRegistrationService().getByEndpoint(endpoint);
		if (registration == null) {
			return ResponseEntity.notFound().build();
		}
		DeleteRequest request = new DeleteRequest(path);
		LwM2mResponse response = sendRequestAndGetResponse(registration, request, timeout);
		return ResponseEntity.ok(response);
	}

	// Helper methods
	private LwM2mResponse sendRequestAndGetResponse(Registration registration, DownlinkDeviceManagementRequest<?> request,
			Long timeoutParam) {
		long timeout = timeoutParam != null ? timeoutParam*1000 : DEFAULT_TIMEOUT_MS;
		try {
			return server.send(registration, request, timeout);
		} catch (ClientSleepingException e) {
			// Device in queue mode and currently sleeping
			throw new ResponseStatusException(HttpStatus.ACCEPTED,
					"Client is sleeping (Queue mode). Request not sent immediately: " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Interrupted while sending request");
		} catch (RuntimeException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
					"Error sending LwM2M request: " + e.getMessage(), e);
		}
	}

	private boolean isObserved(Registration registration, String path) {
		for (Observation obs : server.getObservationService().getObservations(registration)) {
			if (matchesPath(obs, path)) return true;
		}
		return false;
	}

	private boolean cancelObservation(Registration registration, String path) {
		for (Observation obs : server.getObservationService().getObservations(registration)) {
			if (matchesPath(obs, path)) {
				server.getObservationService().cancelObservation(obs);
				return true;
			}
		}
		return false;
	}

	private boolean matchesPath(Observation observation, String path) {
		if (observation instanceof SingleObservation) {
			return ((SingleObservation) observation).getPath().toString().equals(path);
		} else if (observation instanceof CompositeObservation) {
			return ((CompositeObservation) observation).getPaths().stream()
					.anyMatch(p -> p.toString().equals(path));
		}
		return false;
	}

	@PostMapping("/{clientId}/{objectId}/{instanceId}/{resourceId}/observe")
	public ResponseEntity<?> observeResource(
			@PathVariable("clientId") String clientId,
			@PathVariable("objectId") int objectId,
			@PathVariable("instanceId") int instanceId,
			@PathVariable("resourceId") int resourceId,
			@RequestParam(required = false, defaultValue = "TLV", name = "format") String format,
			@RequestParam(required = false, name = "timeout") Long timeout) {

		ContentFormat contentFormat = format != null ? ContentFormat.fromName(format.toUpperCase()) : null;
		Registration registration = server.getRegistrationService().getByEndpoint(clientId);
		if (registration == null) return ResponseEntity.notFound().build();
		ObserveRequest request = new ObserveRequest(contentFormat, objectId, instanceId, resourceId);
		LwM2mResponse response = sendRequestAndGetResponse(registration, request, timeout);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{clientId}/{objectId}/{instanceId}/{resourceId}/observe")
	public ResponseEntity<?> getObservationStatus(
			@PathVariable("clientId") String clientId,
			@PathVariable("objectId") int objectId,
			@PathVariable("instanceId") int instanceId,
			@PathVariable("resourceId") int resourceId) {
		Registration registration = server.getRegistrationService().getByEndpoint(clientId);
		if (registration == null) return ResponseEntity.notFound().build();
		String path = String.format("%d/%d/%d", objectId, instanceId, resourceId);
		return ResponseEntity.ok(Map.of("path", path, "observed", isObserved(registration, path)));
	}

	@DeleteMapping("/{clientId}/{objectId}/{instanceId}/{resourceId}/observe")
	public ResponseEntity<?> cancelObservation(
			@PathVariable("clientId") String clientId,
			@PathVariable("objectId") int objectId,
			@PathVariable("instanceId") int instanceId,
			@PathVariable("resourceId") int resourceId) {
		Registration registration = server.getRegistrationService().getByEndpoint(clientId);
		if (registration == null) return ResponseEntity.notFound().build();
		String path = String.format("%d/%d/%d", objectId, instanceId, resourceId);
		boolean cancelled = cancelObservation(registration, path);
		return ResponseEntity.ok(Map.of("path", path, "cancelled", cancelled));
	}
	
	
	@PostMapping("/{clientId}/{objectId}/{instanceId}/observe")
	public ResponseEntity<?> observeInstance(
			@PathVariable("clientId") String clientId,
			@PathVariable("objectId") int objectId,
			@PathVariable("instanceId") int instanceId,
			@RequestParam(required = false, defaultValue = "TLV", name = "format") String format,
			@RequestParam(required = false, name = "timeout") Long timeout) {
		ContentFormat contentFormat = format != null ? ContentFormat.fromName(format.toUpperCase()) : null;
		Registration registration = server.getRegistrationService().getByEndpoint(clientId);
		if (registration == null) return ResponseEntity.notFound().build();
		ObserveRequest request = new ObserveRequest(contentFormat, objectId, instanceId);
		LwM2mResponse response = sendRequestAndGetResponse(registration, request, timeout);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{clientId}/{objectId}/{instanceId}/observe")
	public ResponseEntity<?> getInstanceObservationStatus(
			@PathVariable("clientId") String clientId,
			@PathVariable("objectId") int objectId,
			@PathVariable("instanceId") int instanceId) {
		Registration registration = server.getRegistrationService().getByEndpoint(clientId);
		if (registration == null) return ResponseEntity.notFound().build();
		String path = String.format("%d/%d", objectId, instanceId);
		return ResponseEntity.ok(Map.of("path", path, "observed", isObserved(registration, path)));
	}

	@DeleteMapping("/{clientId}/{objectId}/{instanceId}/observe")
	public ResponseEntity<?> cancelInstanceObservation(
			@PathVariable("clientId") String clientId,
			@PathVariable("objectId") int objectId,
			@PathVariable("instanceId") int instanceId) {
		Registration registration = server.getRegistrationService().getByEndpoint(clientId);
		if (registration == null) return ResponseEntity.notFound().build();
		String path = String.format("%d/%d", objectId, instanceId);
		boolean cancelled = cancelObservation(registration, path);
		return ResponseEntity.ok(Map.of("path", path, "cancelled", cancelled));
	}

	
	@PostMapping("/{clientId}/{objectId}/observe")
	public ResponseEntity<?> observeObject(
			@PathVariable("clientId") String clientId,
			@PathVariable("objectId") int objectId,
			@RequestParam(required = false, defaultValue = "TLV", name = "format") String format,
			@RequestParam(required = false, name = "timeout") Long timeout) {
		ContentFormat contentFormat = format != null ? ContentFormat.fromName(format.toUpperCase()) : null;
		Registration registration = server.getRegistrationService().getByEndpoint(clientId);
		if (registration == null) return ResponseEntity.notFound().build();
		ObserveRequest request = new ObserveRequest(contentFormat, objectId);
		LwM2mResponse response = sendRequestAndGetResponse(registration, request, timeout);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{clientId}/{objectId}/observe")
	public ResponseEntity<?> getObjectObservationStatus(
			@PathVariable("clientId") String clientId,
			@PathVariable("objectId") int objectId) {
		Registration registration = server.getRegistrationService().getByEndpoint(clientId);
		if (registration == null) return ResponseEntity.notFound().build();
		String path = String.format("%d", objectId);
		return ResponseEntity.ok(Map.of("path", path, "observed", isObserved(registration, path)));
	}

	@DeleteMapping("/{clientId}/{objectId}/observe")
	public ResponseEntity<?> cancelObjectObservation(
			@PathVariable("clientId") String clientId,
			@PathVariable("objectId") int objectId) {
		Registration registration = server.getRegistrationService().getByEndpoint(clientId);
		if (registration == null) return ResponseEntity.notFound().build();
		String path = String.format("%d", objectId);
		boolean cancelled = cancelObservation(registration, path);
		return ResponseEntity.ok(Map.of("path", path, "cancelled", cancelled));
	}
	
	
	@PutMapping("/{clientId}/{objectId}/{instanceId}/{resourceId}")
	public ResponseEntity<?> writeResource(
			@PathVariable("clientId") String clientId,
			@PathVariable("objectId") int objectId,
			@PathVariable("instanceId") int instanceId,
			@PathVariable("resourceId") int resourceId,
			@RequestParam(required = false, defaultValue = "TEXT", name = "format") String format,
			@RequestParam(required = false, name = "timeout") Long timeout,
			@RequestBody String value) {
		Registration registration = server.getRegistrationService().getByEndpoint(clientId);
		if (registration == null) return ResponseEntity.notFound().build();
		ContentFormat contentFormat = ContentFormat.fromName(format.toUpperCase());
		Object actualValue = extractValueMaybeJson(value, resourceId);
		WriteRequest request = buildWriteRequest(contentFormat, objectId, instanceId, resourceId, actualValue);
		LwM2mResponse response = sendRequestAndGetResponse(registration, request, timeout);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{clientId}/{objectId}/{instanceId}/{resourceId}")
	public ResponseEntity<?> readResource(
			@PathVariable("clientId") String clientId,
			@PathVariable("objectId") int objectId,
			@PathVariable("instanceId") int instanceId,
			@PathVariable("resourceId") int resourceId,
			@RequestParam(required = false, name = "timeout") Long timeout) {
		Registration registration = server.getRegistrationService().getByEndpoint(clientId);
		if (registration == null) return ResponseEntity.notFound().build();
		ReadRequest request = new ReadRequest(objectId, instanceId, resourceId);
		LwM2mResponse response = sendRequestAndGetResponse(registration, request, timeout);
		return ResponseEntity.ok(response);
	}

	@DeleteMapping("/{clientId}/{objectId}/{instanceId}")
	public ResponseEntity<?> deleteObjectInstance(
			@PathVariable("clientId") String clientId,
			@PathVariable("objectId") int objectId,
			@PathVariable("instanceId") int instanceId,
			@RequestParam(required = false, name = "timeout") Long timeout) {
		Registration registration = server.getRegistrationService().getByEndpoint(clientId);
		if (registration == null) return ResponseEntity.notFound().build();
		DeleteRequest request = new DeleteRequest(objectId, instanceId);
		LwM2mResponse response = sendRequestAndGetResponse(registration, request, timeout);
		return ResponseEntity.ok(response);
	}

	// DTO for JSON write commands
	private static class WriteCommandBody {
		public Integer id; // resource id
		@SuppressWarnings("unused")
		public String kind; // e.g. singleResource (not currently used but kept for future logic)
		public Object value; // raw value (could be number, boolean, string)
		public String type; // integer, float, double, boolean, string, opaque(base64), etc.
	}

	/**
	 * Accepts either a raw value (plain text / number) or a JSON object like:
	 * {"id":120,"kind":"singleResource","value":"1234","type":"integer"}
	 * If JSON is provided, ensures id matches the expected resourceId and coerces
	 * the value to a canonical textual form based on 'type'.
	 */
	private Object extractValueMaybeJson(String body, int expectedResourceId) {
		if (body == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty body");
		String trimmed = body.trim();
		if (!trimmed.startsWith("{")) {
			// Try to interpret as scalar JSON (number/boolean) if possible
			if (trimmed.matches("^-?\\d+$")) {
				try { return Long.parseLong(trimmed); } catch (NumberFormatException e) { /* fallback below */ }
			}
			if (trimmed.matches("^-?\\d+\\.\\d+$")) {
				try { return Double.parseDouble(trimmed); } catch (NumberFormatException e) { /* fallback */ }
			}
			if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
				return Boolean.parseBoolean(trimmed.toLowerCase());
			}
			return trimmed; // treat as raw string
		}
		try {
			WriteCommandBody cmd = objectMapper.readValue(trimmed, WriteCommandBody.class);
			if (cmd.id != null && cmd.id != expectedResourceId) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Resource id in body (" + cmd.id + ") does not match path resourceId (" + expectedResourceId + ")");
			}
			if (cmd.value == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing 'value' field in JSON body");
			}
			String type = cmd.type != null ? cmd.type.toLowerCase() : inferType(cmd.value);
			Object raw = cmd.value;
			return coerceValue(raw, type);
		} catch (JsonProcessingException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed JSON body: " + e.getOriginalMessage());
		}
	}

	private String inferType(Object value) {
		if (value instanceof Integer || value instanceof Long) return "integer";
		if (value instanceof Float || value instanceof Double) return "float";
		if (value instanceof Boolean) return "boolean";
		return "string"; // default
	}

	private Object coerceValue(Object raw, String type) {
		try {
			switch (type) {
			case "integer":
				if (raw instanceof Number n) return n.longValue();
				return Long.parseLong(raw.toString());
			case "float":
			case "double":
				if (raw instanceof Number n2) return n2.doubleValue();
				return Double.parseDouble(raw.toString());
			case "boolean":
				if (raw instanceof Boolean b) return b;
				String s = raw.toString().toLowerCase();
				if (!s.equals("true") && !s.equals("false")) throw new NumberFormatException("Invalid boolean: " + raw);
				return Boolean.parseBoolean(s);
			case "opaque":
				// Expect base64 string
				return java.util.Base64.getDecoder().decode(raw.toString());
			case "string":
			default:
				return raw.toString();
			}
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid value for type '" + type + "': " + raw);
		}
	}


	private WriteRequest buildWriteRequest(ContentFormat format, int objectId, int instanceId, int resourceId, Object value) {
		if (value == null) {
			return new WriteRequest(format, objectId, instanceId, resourceId, "");
		}
		if (value instanceof byte[] b) {
			return new WriteRequest(format, objectId, instanceId, resourceId, b);
		}
		if (value instanceof Boolean bool) {
			return new WriteRequest(format, objectId, instanceId, resourceId, bool.booleanValue());
		}
		if (value instanceof Long l) {
			return new WriteRequest(format, objectId, instanceId, resourceId, l.longValue());
		}
		if (value instanceof Integer i) {
			return new WriteRequest(format, objectId, instanceId, resourceId, i.longValue());
		}
		if (value instanceof Double d) {
			return new WriteRequest(format, objectId, instanceId, resourceId, d.doubleValue());
		}
		if (value instanceof Float f) {
			return new WriteRequest(format, objectId, instanceId, resourceId, f.doubleValue());
		}
		// Fallback to string.
		return new WriteRequest(format, objectId, instanceId, resourceId, value.toString());
	}
}
