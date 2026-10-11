package com.safayet.foodmobochain.service;

import com.mongodb.client.MongoDatabase;
import com.safayet.foodmobochain.security.AccountTokens;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InstructorAccessServiceTest {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final AgentMailService mail = mock(AgentMailService.class);
    private final InstructorAccessService access = new InstructorAccessService(mongo, mail);

    @BeforeEach void setup() {
        MongoDatabase database = mock(MongoDatabase.class);
        when(mongo.getDb()).thenReturn(database);
        when(database.getName()).thenReturn(InstructorAccessService.STAGING_DATABASE);
        ReflectionTestUtils.setField(access, "enabled", true);
        ReflectionTestUtils.setField(access, "publicUrl", InstructorAccessService.STAGING_HOST);
        ReflectionTestUtils.setField(access, "configuredEmail", "professor@university.example");
        ReflectionTestUtils.setField(access, "secureCookie", true);
    }

    @Test void invitesExactlyTheConfiguredInstructorAndStoresOnlyTokenHash() {
        access.sendInvitation();
        var token = ArgumentCaptor.forClass(String.class);
        verify(mail).sendInstructorInvitation(eq("professor@university.example"), token.capture());
        assertThat(AccountTokens.validShape(token.getValue())).isTrue();
        var saved = ArgumentCaptor.forClass(Document.class);
        verify(mongo).save(saved.capture(), eq("instructorInvitations"));
        assertThat(saved.getValue().getString("tokenHash")).isEqualTo(AccountTokens.hash(token.getValue()));
        assertThat(saved.getValue().toJson()).doesNotContain(token.getValue());
        assertThat(saved.getValue().getDate("expiresAt").toInstant()).isAfter(Instant.now());
    }

    @Test void disabledFeatureOrWrongDatabasePreventsAnInvitation() {
        ReflectionTestUtils.setField(access, "enabled", false);
        assertThatThrownBy(access::sendInvitation).isInstanceOf(IllegalStateException.class);
        ReflectionTestUtils.setField(access, "enabled", true);
        MongoDatabase database = mongo.getDb();
        when(database.getName()).thenReturn("store_manager");
        assertThatThrownBy(access::sendInvitation).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mail);
    }

    @Test void consumedInvitationIssuesOneHourSessionAndIsNotReusable() {
        String rawToken = AccountTokens.create();
        var used = new Document("email", "professor@university.example");
        when(mongo.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Document.class), eq("instructorInvitations")))
                .thenReturn(used, null);
        String rawSession = access.accept(rawToken);
        assertThat(AccountTokens.validShape(rawSession)).isTrue();
        var stored = ArgumentCaptor.forClass(Document.class);
        verify(mongo).insert(stored.capture(), eq("instructorSessions"));
        assertThat(stored.getValue().getString("_id")).isEqualTo(AccountTokens.hash(rawSession));
        assertThat(stored.getValue().toJson()).doesNotContain(rawSession);
        assertThat(stored.getValue().getString("role")).isEqualTo("buyer");
        assertThat(stored.getValue().getDate("expiresAt").toInstant()).isAfter(Instant.now());
        assertThatThrownBy(() -> access.accept(rawToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void onlyOneOfTenAllowlistedRolesCanBeSelected() {
        String raw = AccountTokens.create();
        var record = new Document("teacherEmail", "professor@university.example")
                .append("role", "buyer").append("sellerSlot", 0)
                .append("expiresAt", Date.from(Instant.now().plusSeconds(3600)));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("instructorSessions")))
                .thenReturn(record);
        var selected = new Document(record).append("role", "seller").append("sellerSlot", 6);
        when(mongo.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Document.class), eq("instructorSessions")))
                .thenReturn(selected);

        assertThat(access.switchRole(raw, "seller", "spice-route").sellerSlot()).isEqualTo(6);
        assertThat(InstructorAccessService.accountEmail(
                new InstructorAccessService.InstructorSession("professor@university.example",
                        "seller", 6, Instant.now().plusSeconds(30))))
                .isEqualTo("seller6@foodmobo.local");
        assertThatThrownBy(() -> access.switchRole(raw, "seller", "another-seller"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> access.switchRole(raw, "superadmin", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void expiredOrRevokedSessionCannotAuthenticate() {
        assertThat(access.session("invalid-token")).isNull();
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("instructorSessions")))
                .thenReturn(null);
        assertThat(access.session(AccountTokens.create())).isNull();
    }

    @Test void cookieHasHttpOnlySecureSameSiteAndExpirationAndCanBeRevoked() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        String value = AccountTokens.create();
        access.setCookie(response, value);
        var header = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), header.capture());
        assertThat(header.getValue()).contains("FMC_INSTRUCTOR=", "HttpOnly", "Secure", "SameSite=Lax", "Path=/");
        assertThat(header.getValue()).doesNotContain("FMC_ACCESS");
        access.revoke(value);
        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq("instructorSessions"));
        access.clearCookie(response);
        verify(response, times(2)).addHeader(eq("Set-Cookie"), header.capture());
        assertThat(header.getValue()).contains("Max-Age=0");
    }
}
