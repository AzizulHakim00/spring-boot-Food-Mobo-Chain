package com.safayet.foodmobochain.controller;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoAssetControllerTest {
    @Test void servesOnlyImagesFromTheDedicatedDemoDatabase() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoDatabase db = mock(MongoDatabase.class);
        when(mongo.getDb()).thenReturn(db);
        when(db.getName()).thenReturn("food_mobo_chain_demo");
        DemoAssetController controller = new DemoAssetController(mongo);
        ReflectionTestUtils.setField(controller, "enabled", true);
        ReflectionTestUtils.setField(controller, "baseUrl", FullDemoLoginController.DEMO_URL);
        String uuid = "141b1f67-0f66-4011-805a-ac1c997baef9";
        byte[] img = new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff};
        when(mongo.findById(uuid, Document.class, "demoImages")).thenReturn(
                new Document("_id",uuid).append("mime","image/jpeg").append("data",new Binary(img)));
        var response = controller.image(uuid);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(img, response.getBody());
        assertEquals("image/jpeg", response.getHeaders().getContentType().toString());

        when(db.getName()).thenReturn("food_mobo_chain");
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> controller.image(uuid)).getStatusCode());
    }
}
