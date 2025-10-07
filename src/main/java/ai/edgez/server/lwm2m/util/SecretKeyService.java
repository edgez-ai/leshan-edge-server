package ai.edgez.server.lwm2m.util;


import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;


@Service
public class SecretKeyService {

	@Value("${lwm2m.keys.public}")
	private String publicKeyBase64;

	@Value("${lwm2m.keys.private}")
	private String privateKeyBase64;
	
	@Value("${lwm2m.keys.server}")
	private String peerPublicKeyBase64;

	private PublicKey publicKey;
	private PrivateKey privateKey;
	private PublicKey pubKey;

	@PostConstruct
	public void init() {
		try {
			publicKey = loadECPublicKeyFromBase64(publicKeyBase64);
			privateKey = loadECPrivateKeyFromBase64(privateKeyBase64);
			pubKey = loadECPublicKeyFromBase64(peerPublicKeyBase64);

		} catch (Exception e) {
			throw new RuntimeException("Failed to parse base64 ECC keys", e);
		}
	}

	public String getPublicKeyBase64() {
		return publicKeyBase64;
	}

	public String getPrivateKeyBase64() {
		return privateKeyBase64;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	public byte[] getPSK(String input) {
		try {
			PrivateKey privKey = this.privateKey;

			KeyAgreement ka = KeyAgreement.getInstance("ECDH");
			ka.init(privKey);
			ka.doPhase(pubKey, true);
			byte[] sharedSecret = ka.generateSecret();

			byte[] aesKeyBytes = new byte[16];
			System.arraycopy(sharedSecret, 0, aesKeyBytes, 0, 16);
			SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, aesKey);
			byte[] encrypted = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
			byte[] psk = new byte[16];
			int pos = 0;
			for(int i = 0; i < encrypted.length; i++) {
				if (encrypted[i] != 0) {
					psk[pos++] = encrypted[i];
				}
				if(pos>=16) break;
			}
			return psk;
		} catch (Exception e) {
			throw new RuntimeException("Failed to generate PSK and encrypt input", e);
		}
	}

	// Helper to parse EC private key from PEM using BouncyCastle
	// Helper to parse EC private key from base64 string
	private PrivateKey loadECPrivateKeyFromBase64(String base64) throws Exception {
		byte[] encoded = Base64.getDecoder().decode(base64);
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
		KeyFactory kf = KeyFactory.getInstance("EC");
		return kf.generatePrivate(keySpec);
	}

	// Helper to parse EC public key from base64 string
	private PublicKey loadECPublicKeyFromBase64(String base64) throws Exception {
		byte[] encoded = Base64.getDecoder().decode(base64);
		X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
		KeyFactory kf = KeyFactory.getInstance("EC");
		return kf.generatePublic(keySpec);
	}
}
