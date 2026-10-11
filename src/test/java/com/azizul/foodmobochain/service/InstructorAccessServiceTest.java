package com.azizul.foodmobochain.service;

import com.mongodb.client.MongoDatabase;
import com.azizul.foodmobochain.security.AccountTokens;
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
import java.util.List;
import java.util.stream.Collectors;
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
        ReflectionTestUtils.setField(access, "secureCookie", true);
    }

    @Test void invitesExactlyTheConfiguredInstructorAndStoresOnlyTokenHash() {
        access.sendInvitation("professor@university.edu", "admin@foodmobo.local");
        var token = ArgumentCaptor.forClass(String.class);
        verify(mail).sendInstructorInvitation(eq("professor@university.edu"), token.capture());
        assertThat(AccountTokens.validShape(token.getValue())).isTrue();
        var saved = ArgumentCaptor.forClass(Document.class);
        verify(mongo).insert(saved.capture(), eq("instructorInvitations"));
        assertThat(saved.getValue().getString("tokenHash")).isEqualTo(AccountTokens.hash(token.getValue()));
        assertThat(saved.getValue().getString("_id")).isEqualTo(AccountTokens.hash(token.getValue()));
        assertThat(saved.getValue().toJson()).doesNotContain(token.getValue());
        assertThat(saved.getValue().getDate("expiresAt").toInstant()).isAfter(Instant.now());
    }

    @Test void typedRecipientOverridesTheSuggestedAddressWithoutAnyRenderChanges() {
        access.sendInvitation(" Noor@Seu.Edu.Bd ", "admin@foodmobo.local");
        verify(mail).sendInstructorInvitation(eq("noor@seu.edu.bd"), anyString());
        var saved = ArgumentCaptor.forClass(Document.class);
        verify(mongo).insert(saved.capture(), eq("instructorInvitations"));
        assertThat(saved.getValue().getString("email")).isEqualTo("noor@seu.edu.bd");
        assertThat(saved.getValue().getString("issuedBy")).isEqualTo("admin@foodmobo.local");
    }

    @Test void rapidInvitationsToDifferentAndSameAddressesAreIndependentAndHaveNoCooldown() {
        access.sendInvitation("teacher.one@seu.edu.bd", "admin@foodmobo.local");
        access.sendInvitation("teacher.two@seu.edu.bd", "admin@foodmobo.local");
        access.sendInvitation("teacher.one@seu.edu.bd", "admin@foodmobo.local");

        var destinations = ArgumentCaptor.forClass(String.class);
        var tokens = ArgumentCaptor.forClass(String.class);
        verify(mail, times(3)).sendInstructorInvitation(destinations.capture(), tokens.capture());
        assertThat(destinations.getAllValues()).containsExactly(
                "teacher.one@seu.edu.bd", "teacher.two@seu.edu.bd", "teacher.one@seu.edu.bd");
        assertThat(tokens.getAllValues()).allMatch(AccountTokens::validShape);
        assertThat(tokens.getAllValues().stream().distinct().count()).isEqualTo(3);

        var saved = ArgumentCaptor.forClass(Document.class);
        verify(mongo, times(3)).insert(saved.capture(), eq("instructorInvitations"));
        List<Document> records = saved.getAllValues();
        assertThat(records).hasSize(3);
        assertThat(records.stream().map(d -> d.getString("_id")).collect(Collectors.toSet())).hasSize(3);
        for (int i = 0; i < records.size(); i++) {
            assertThat(records.get(i).getString("email")).isEqualTo(destinations.getAllValues().get(i));
            assertThat(records.get(i).getString("_id")).isEqualTo(AccountTokens.hash(tokens.getAllValues().get(i)));
            assertThat(records.get(i).getString("tokenHash")).isEqualTo(AccountTokens.hash(tokens.getAllValues().get(i)));
            assertThat(records.get(i).getDate("expiresAt").toInstant()).isAfter(Instant.now());
            assertThat(records.get(i).toJson()).doesNotContain(tokens.getAllValues().get(i));
        }
        verify(mongo, never()).save(any(Document.class), eq("instructorInvitations"));
        verify(mongo, never()).findById(anyString(), eq(Document.class), eq("instructorInvitations"));
    }

    @Test void acceptingOneInvitationNeverInvalidatesOtherUnusedLinks() {
        String firstToken = AccountTokens.create();
        String secondToken = AccountTokens.create();
        String firstHash = AccountTokens.hash(firstToken);
        String secondHash = AccountTokens.hash(secondToken);
        var first = new Document("email", "first@seu.edu.bd");
        var second = new Document("email", "second@seu.edu.bd");

        when(mongo.findOne(any(Query.class), eq(Document.class), eq("instructorInvitations")))
                .thenAnswer(call -> {
                    Query query = call.getArgument(0);
                    return firstHash.equals(query.getQueryObject().getString("tokenHash")) ? first
                            : secondHash.equals(query.getQueryObject().getString("tokenHash")) ? second : null;
                });
        when(mongo.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Document.class), eq("instructorInvitations")))
                .thenAnswer(call -> {
                    Query query = call.getArgument(0);
                    return firstHash.equals(query.getQueryObject().getString("tokenHash")) ? first
                            : secondHash.equals(query.getQueryObject().getString("tokenHash")) ? second : null;
                });

        assertThat(access.validInvitation(firstToken)).isTrue();
        assertThat(access.validInvitation(secondToken)).isTrue();
        assertThat(access.accept(firstToken)).isNotEmpty();
        assertThat(access.validInvitation(secondToken)).isTrue();
        assertThat(access.accept(secondToken)).isNotEmpty();
        var sessions = ArgumentCaptor.forClass(Document.class);
        verify(mongo, times(2)).insert(sessions.capture(), eq("instructorSessions"));
        assertThat(sessions.getAllValues()).extracting(doc -> doc.getString("teacherEmail"))
                .containsExactly("first@seu.edu.bd", "second@seu.edu.bd");
    }

    @Test void revokeAllCancelsEveryOutstandingInvitationAndEveryActiveSession() {
        access.revokeAll();
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).remove(query.capture(), eq("instructorInvitations"));
        assertThat(query.getValue().getQueryObject().isEmpty()).isTrue();
        verify(mongo).updateMulti(any(Query.class), any(Update.class), eq("instructorSessions"));
    }

    @Test void rejectsInvalidAddressesBeforeStoringOrSending() {
        for (String bad : new String[]{"", "not-an-email", "teacher@fake.local",
                "teacher@example.invalid", "teacher@example.test", "a b@example.com"}) {
            assertThatThrownBy(() -> access.sendInvitation(bad, "admin@foodmobo.local"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(mail);
        verify(mongo, never()).insert(any(Document.class), eq("instructorInvitations"));
    }

    @Test void invitedEmailNotTheSuggestedAddressOwnsTheSession() {
        String token = AccountTokens.create();
        when(mongo.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Document.class), eq("instructorInvitations")))
                .thenReturn(new Document("email", "recipient@seu.edu.bd"));
        String sessionToken = access.accept(token);
        assertThat(sessionToken).isNotBlank();
        var saved = ArgumentCaptor.forClass(Document.class);
        verify(mongo).insert(saved.capture(), eq("instructorSessions"));
        assertThat(saved.getValue().getString("teacherEmail")).isEqualTo("recipient@seu.edu.bd");
    }

    @Test void activeSessionAcceptsAValidPreviouslyInvitedRecipient() {
        var doc = new Document("teacherEmail", "recipient@seu.edu.bd")
                .append("role", "buyer").append("sellerSlot", 0)
                .append("expiresAt", Date.from(Instant.now().plusSeconds(1800)));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("instructorSessions"))).thenReturn(doc);
        assertThat(access.session(AccountTokens.create()).teacherEmail())
                .isEqualTo("recipient@seu.edu.bd");
    }

    @Test void disabledFeatureOrWrongDatabasePreventsAnInvitation() {
        ReflectionTestUtils.setField(access, "enabled", false);
        assertThatThrownBy(() -> access.sendInvitation("professor@university.edu", "admin@foodmobo.local"))
                .isInstanceOf(IllegalStateException.class);
        ReflectionTestUtils.setField(access, "enabled", true);
        MongoDatabase database = mongo.getDb();
        when(database.getName()).thenReturn("store_manager");
        assertThatThrownBy(() -> access.sendInvitation("professor@university.edu", "admin@foodmobo.local"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(mail);
    }

    @Test void consumedInvitationIssuesOneHourSessionAndIsNotReusable() {
        String rawToken = AccountTokens.create();
        var used = new Document("email", "professor@university.edu");
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
        var record = new Document("teacherEmail", "professor@university.edu")
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
                new InstructorAccessService.InstructorSession("professor@university.edu",
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
