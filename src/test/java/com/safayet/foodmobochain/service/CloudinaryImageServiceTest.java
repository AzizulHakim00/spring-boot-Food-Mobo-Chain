package com.safayet.foodmobochain.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class CloudinaryImageServiceTest {
    @Test void acceptsWebpMagicHeader() {
        assertEquals("webp", CloudinaryImageService.sniffImageExtension(
                "RIFF0000WEBP12345".getBytes(StandardCharsets.US_ASCII)));
    }
    @Test void rejectsUntrustedFileContents() {
        assertThrows(IllegalArgumentException.class, () -> CloudinaryImageService.sniffImageExtension(
                "<svg onload=alert(1)>".getBytes(StandardCharsets.US_ASCII)));
    }
    @Test void signatureIsDeterministicAndNeverEqualsSecret() {
        String s = CloudinaryImageService.signature("food-mobo-chain/foods", "123", "secret");
        assertEquals("113a8821259aefc30e5ed987fefe181052518548", s);
        assertEquals(s, CloudinaryImageService.signature("food-mobo-chain/foods", "123", "secret"));
        assertNotEquals("secret", s);
    }

    @Test void signedMultipartUploadReturnsCloudinaryUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CloudinaryImageService service = new CloudinaryImageService(
                "testcloud", "test-key", "test-secret", builder.build());
        server.expect(requestTo("https://api.cloudinary.com/v1_1/testcloud/image/upload"))
                .andExpect(method(POST))
                .andExpect(request -> {
                    assertTrue(request.getHeaders().getContentType().isCompatibleWith(MediaType.MULTIPART_FORM_DATA));
                    String body = new String(((MockClientHttpRequest) request).getBodyAsBytes(), StandardCharsets.ISO_8859_1);
                    assertTrue(body.contains("name=\"folder\""));
                    assertTrue(body.contains("food-mobo-chain/foods"));
                    assertTrue(body.contains("name=\"api_key\""));
                    assertTrue(body.contains("test-key"));
                    assertTrue(body.contains("name=\"signature\""));
                    assertTrue(body.contains("name=\"file\""));
                })
                .andRespond(withSuccess(
                        "{\"secure_url\":\"https://res.cloudinary.com/testcloud/image/upload/v1/test.png\"}",
                        MediaType.APPLICATION_JSON));

        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        String url = service.upload(new MockMultipartFile("file", "test.png", "image/png", png), "foods");
        assertEquals("https://res.cloudinary.com/testcloud/image/upload/v1/test.png", url);
        server.verify();
    }

    @Test void rejectsAnUploadWhenCloudinaryIsNotConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CloudinaryImageService service = new CloudinaryImageService("", "", "", builder.build());
        assertThrows(IllegalStateException.class, () -> service.upload(
                new MockMultipartFile("file", "test.png", "image/png", new byte[] {(byte) 0x89}), "foods"));
        server.verify();
    }
}
