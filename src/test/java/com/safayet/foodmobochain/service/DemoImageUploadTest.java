package com.safayet.foodmobochain.service;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoImageUploadTest {

    @Test void isolatedDemoStoresImageBytesInItsOwnMongoDatabase() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoDatabase db = mock(MongoDatabase.class);
        when(mongo.getDb()).thenReturn(db);
        when(db.getName()).thenReturn("food_mobo_chain_demo");
        CloudinaryImageService service = new CloudinaryImageService(
                "", "", "", RestClient.create(), true, mongo);

        byte[] png = new byte[] {(byte)0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        String imageUrl = service.uploadBytes(png, "foods");
        assertTrue(imageUrl.startsWith("/demo-assets/"));

        ArgumentCaptor<Document> doc = ArgumentCaptor.forClass(Document.class);
        verify(mongo).insert(doc.capture(), eq("demoImages"));
        assertEquals("image/png", doc.getValue().getString("mime"));
        assertArrayEquals(png, ((Binary)doc.getValue().get("data")).getData());
    }

    @Test void ordinaryStagingDoesNotUseMongoForCloudinaryUpload() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoDatabase db = mock(MongoDatabase.class);
        when(mongo.getDb()).thenReturn(db);
        when(db.getName()).thenReturn("food_mobo_chain");
        CloudinaryImageService service = new CloudinaryImageService(
                "", "", "", RestClient.create(), true, mongo);
        byte[] png = new byte[] {(byte)0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        assertThrows(IllegalStateException.class, () -> service.uploadBytes(png, "foods"));
        verify(mongo, never()).insert(any(Document.class), eq("demoImages"));
    }

    @Test void demoRejectsUntrustedImageContentsAndOversizedFiles() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoDatabase db = mock(MongoDatabase.class);
        when(mongo.getDb()).thenReturn(db);
        when(db.getName()).thenReturn("food_mobo_chain_demo");
        CloudinaryImageService service = new CloudinaryImageService(
                "", "", "", RestClient.create(), true, mongo);
        assertThrows(IllegalArgumentException.class, () ->
                service.uploadBytes("<svg onload=alert(1)>".getBytes(), "foods"));
        assertThrows(IllegalArgumentException.class, () ->
                service.uploadBytes(new byte[(int)CloudinaryImageService.MAX_SIZE+1], "foods"));
        verify(mongo, never()).insert(any(Document.class), anyString());
    }
}
