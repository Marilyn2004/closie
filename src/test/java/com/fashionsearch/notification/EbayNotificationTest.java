package com.fashionsearch.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fashionsearch.client.EbayClient;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;

class EbayNotificationTest {
  private EbayClient client;
  private KeyPair pair;
  private EbayNotificationVerifier verifier;

  @BeforeEach
  void setup() throws Exception {
    client = mock(EbayClient.class);
    var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(256);
    pair = generator.generateKeyPair();
    when(client.notificationPublicKey("test-key"))
        .thenReturn(
            new EbayClient.NotificationPublicKey(
                "ECDSA",
                "SHA1",
                "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----"));
    verifier = new EbayNotificationVerifier(client);
  }

  private String sign(byte[] data) throws Exception {
    Signature signature = Signature.getInstance("SHA1withECDSA");
    signature.initSign(pair.getPrivate());
    signature.update(data);
    return Base64.getEncoder()
        .encodeToString(
            ("{\"kid\":\"test-key\",\"signature\":\""
                    + Base64.getEncoder().encodeToString(signature.sign())
                    + "\"}")
                .getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void acceptsRealEccSignatureAndCachesKey() throws Exception {
    byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
    String header = sign(body);
    assertThat(verifier.verify(body, body, header)).isTrue();
    assertThat(verifier.verify(body, body, header)).isTrue();
    verify(client, times(1)).notificationPublicKey("test-key");
  }

  @Test
  void rejectsTamperedPayload() throws Exception {
    byte[] original = "original".getBytes(), changed = "changed".getBytes();
    assertThat(verifier.verify(changed, changed, sign(original))).isFalse();
  }

  @Test
  void acceptsSdkCompactSerialization() throws Exception {
    byte[] raw = "{ \"x\": 1 }".getBytes(), compact = "{\"x\":1}".getBytes();
    assertThat(verifier.verify(raw, compact, sign(compact))).isTrue();
  }

  @Test
  void malformedHeadersNeverFetchKeys() {
    for (String header :
        List.of(
            "broken",
            "a".repeat(4097),
            Base64.getEncoder().encodeToString("{\"kid\":\"../bad\"}".getBytes())))
      assertThat(verifier.verify(new byte[0], new byte[0], header)).isFalse();
    assertThat(verifier.verify(new byte[0], new byte[0], null)).isFalse();
    verifyNoInteractions(client);
  }

  @Test
  void keyOutageDoesNotAcknowledgeAndLookupsAreBounded() throws Exception {
    when(client.notificationPublicKey("test-key"))
        .thenThrow(new IllegalStateException("private upstream response"));
    byte[] body = "{}".getBytes();
    String header = sign(body);
    for (int i = 0; i < 2; i++)
      assertThatThrownBy(() -> verifier.verify(body, body, header))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("503")
          .hasMessageNotContaining("private");
    verify(client, times(1)).notificationPublicKey("test-key");
  }

  @Test
  void validatesEndpointAndRedactsToken() {
    String token = "x".repeat(64),
        endpoint = "https://closie.example.com" + EbayNotificationConfig.PATH;
    assertThat(new EbayNotificationConfig(endpoint, token).toString()).doesNotContain(token);
    for (String invalid :
        List.of(
            "http://closie.example.com",
            "https://localhost",
            "https://127.0.0.1",
            "https://closie.example.com/wrong",
            "https://closie.example.com" + EbayNotificationConfig.PATH + "?x=1"))
      assertThatThrownBy(() -> new EbayNotificationConfig(invalid, token))
          .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new EbayNotificationConfig(endpoint, "short"))
        .isInstanceOf(IllegalStateException.class);
  }
}
