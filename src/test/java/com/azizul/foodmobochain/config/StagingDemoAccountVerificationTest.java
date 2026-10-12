package com.azizul.foodmobochain.config;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StagingDemoAccountVerificationTest {

    @Test void updatesOnlyAllowlistedTenDemoUsersOnApprovedStaging() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoDatabase db = mock(MongoDatabase.class);
        when(mongo.getDb()).thenReturn(db);
        when(db.getName()).thenReturn("food_mobo_chain");
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq("users")))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));

        StagingDemoAccountVerification reconciler = new StagingDemoAccountVerification(mongo);
        ReflectionTestUtils.setField(reconciler, "baseUrl",
                "https://food-mobo-chain-staging.onrender.com");
        reconciler.run(new DefaultApplicationArguments(new String[0]));

        var queryCaptor = org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(mongo, times(10)).updateFirst(queryCaptor.capture(), any(Update.class), eq("users"));
        String constraints = queryCaptor.getAllValues().stream()
                .map(q -> q.getQueryObject().toJson())
                .reduce("", (a, b) -> a + b);
        assertThat(constraints).contains("users:1", "admin@foodmobo.local", "ADMIN",
                "users:2", "buyer@foodmobo.local", "BUYER",
                "users:10", "seller8@foodmobo.local", "SELLER");
        verify(mongo).getDb();
        verifyNoMoreInteractions(mongo);
    }

    @Test void refusesAnyNonStagingDatabase() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        MongoDatabase db = mock(MongoDatabase.class);
        when(mongo.getDb()).thenReturn(db);
        when(db.getName()).thenReturn("another_database");
        StagingDemoAccountVerification reconciler = new StagingDemoAccountVerification(mongo);
        ReflectionTestUtils.setField(reconciler, "baseUrl",
                "https://food-mobo-chain-staging.onrender.com");
        assertThatThrownBy(() -> reconciler.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class);
        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), anyString());
    }
}
