package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.security.AccountTokens;
import com.safayet.foodmobochain.security.JwtCookieService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * An explicitly invited instructor can temporarily assume only the ten
 * existing demonstration identities. The session is stored server-side and
 * NEVER returns an unrestricted JWT or any demo account password.
 */
@Service
@RequiredArgsConstructor
public class InstructorAccessService {
    public static final String COOKIE = "FMC_INSTRUCTOR";
    public static final String REQUEST_ATTRIBUTE = "foodmobo.instructor.session";
    public static final String STAGING_HOST = "https://food-mobo-chain-staging.onrender.com";
    public static final String STAGING_DATABASE = "food_mobo_chain";
    private static final String INVITE_ID = "instructor-only";
    private static final Duration INVITE_LIFE = Duration.ofMinutes(15);
    private static final Duration SESSION_LIFE = Duration.ofMinutes(60);
    private static final Duration SEND_COOLDOWN = Duration.ofMinutes(5);

    public static final List<Store> STORES = List.of(
            new Store("dhaka-biryani-house", "Dhaka Biryani House"),
            new Store("street-bite", "Street Bite"),
            new Store("burger-station", "Burger Station"),
            new Store("pizza-corner", "Pasta & Bowl Corner"),
            new Store("deshi-kitchen", "Deshi Kitchen"),
            new Store("spice-route", "Spice Route"),
            new Store("sweet-and-sip", "Sweet Street"),
            new Store("campus-cravings", "Campus Cravings")
    );

    private final MongoTemplate mongo;
    private final AgentMailService mail;

    @Value("${app.instructor.access-enabled:false}")
    private boolean enabled;
    @Value("${app.instructor.email:}")
    private String configuredEmail;
    @Value("${app.public-base-url:}")
    private String publicUrl;
    @Value("${app.auth.secure-cookies:true}")
    private boolean secureCookie;

    public boolean enabledForStaging() {
        return enabled && STAGING_HOST.equals(publicUrl)
                && STAGING_DATABASE.equals(mongo.getDb().getName())
                && configuredEmail != null
                && configuredEmail.trim().toLowerCase(Locale.ROOT)
                        .matches("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    }

    private void requireEnabled() {
        if (!enabledForStaging()) throw new IllegalStateException("Instructor access is not enabled.");
    }

    /** Only an existing fully signed-in Admin controller calls this. */
    @Transactional
    public void sendInvitation() {
        requireEnabled();
        mail.requireConfigured();
        Instant now = Instant.now();
        Document last = mongo.findById(INVITE_ID, Document.class, "instructorInvitations");
        if (last != null && last.getDate("lastSentAt") != null
                && last.getDate("lastSentAt").toInstant().plus(SEND_COOLDOWN).isAfter(now)) {
            throw new IllegalStateException("An invitation was sent recently. Please wait five minutes.");
        }
        String token = AccountTokens.create();
        mongo.save(new Document("_id", INVITE_ID)
                .append("tokenHash", AccountTokens.hash(token))
                .append("email", configuredEmail.trim().toLowerCase(Locale.ROOT))
                .append("expiresAt", Date.from(now.plus(INVITE_LIFE)))
                .append("lastSentAt", Date.from(now))
                .append("usedAt", null), "instructorInvitations");
        // A provider error throws, rolling back the just-written token.
        mail.sendInstructorInvitation(configuredEmail.trim(), token);
    }

    public boolean validInvitation(String rawToken) {
        if (!enabledForStaging() || !AccountTokens.validShape(rawToken)) return false;
        Document doc = mongo.findOne(Query.query(Criteria.where("_id").is(INVITE_ID)
                .and("tokenHash").is(AccountTokens.hash(rawToken))
                .and("usedAt").is(null)
                .and("expiresAt").gt(new Date())), Document.class, "instructorInvitations");
        return doc != null && configuredEmail.trim().equalsIgnoreCase(doc.getString("email"));
    }

    /** POST only, protected by Spring CSRF; one-use atomic compare-and-modify. */
    @Transactional
    public String accept(String rawToken) {
        requireEnabled();
        if (!AccountTokens.validShape(rawToken)) throw new IllegalArgumentException("Invitation is invalid or expired.");
        Query query = Query.query(Criteria.where("_id").is(INVITE_ID)
                .and("tokenHash").is(AccountTokens.hash(rawToken))
                .and("usedAt").is(null)
                .and("expiresAt").gt(new Date()));
        Document invite = mongo.findAndModify(query, new Update().set("usedAt", new Date()),
                FindAndModifyOptions.options().returnNew(true), Document.class, "instructorInvitations");
        if (invite == null || !configuredEmail.trim().equalsIgnoreCase(invite.getString("email"))) {
            throw new IllegalArgumentException("Invitation is invalid or expired.");
        }
        String rawSession = AccountTokens.create();
        Instant now = Instant.now();
        mongo.insert(new Document("_id", AccountTokens.hash(rawSession))
                .append("teacherEmail", configuredEmail.trim().toLowerCase(Locale.ROOT))
                .append("createdAt", Date.from(now))
                .append("expiresAt", Date.from(now.plus(SESSION_LIFE)))
                .append("role", "buyer")
                .append("sellerSlot", 0)
                .append("revoked", false), "instructorSessions");
        audit("INVITATION_ACCEPTED", "buyer", "/instructor/accept", 200);
        return rawSession;
    }

    public InstructorSession session(String rawCookie) {
        if (!enabledForStaging() || !AccountTokens.validShape(rawCookie)) return null;
        Document doc = mongo.findOne(Query.query(Criteria.where("_id").is(AccountTokens.hash(rawCookie))
                .and("revoked").ne(true)
                .and("expiresAt").gt(new Date())), Document.class, "instructorSessions");
        if (doc == null || !configuredEmail.trim().equalsIgnoreCase(doc.getString("teacherEmail"))) return null;
        Integer slot = doc.getInteger("sellerSlot", 0);
        String role = doc.getString("role");
        if (!validRole(role, slot)) return null;
        return new InstructorSession(doc.getString("teacherEmail"), role, slot,
                doc.getDate("expiresAt").toInstant());
    }

    public InstructorSession switchRole(String rawCookie, String role, String storeSlug) {
        requireEnabled();
        InstructorSession current = session(rawCookie);
        if (current == null) throw new IllegalArgumentException("Your instructor session has expired.");
        int slot = 0;
        if ("seller".equals(role)) {
            slot = STORES.indexOf(new Store(storeSlug, storeName(storeSlug))) + 1;
            if (slot < 1 || slot > 8) throw new IllegalArgumentException("Unknown seller.");
        }
        if (!validRole(role, slot)) throw new IllegalArgumentException("Invalid role.");
        Document update = mongo.findAndModify(Query.query(Criteria.where("_id").is(AccountTokens.hash(rawCookie))
                        .and("revoked").ne(true).and("expiresAt").gt(new Date())),
                new Update().set("role", role).set("sellerSlot", slot),
                FindAndModifyOptions.options().returnNew(true),
                Document.class, "instructorSessions");
        if (update == null) throw new IllegalArgumentException("Your instructor session has expired.");
        audit("ROLE_SWITCH", role, "/instructor/switch", 200);
        return new InstructorSession(current.teacherEmail(), role, slot, current.expiresAt());
    }

    public void revoke(String rawCookie) {
        if (!enabledForStaging() || !AccountTokens.validShape(rawCookie)) return;
        mongo.updateFirst(Query.query(Criteria.where("_id").is(AccountTokens.hash(rawCookie))),
                new Update().set("revoked", true), "instructorSessions");
        audit("SESSION_EXIT", "instructor", "/instructor/exit", 200);
    }

    public void revokeAll() {
        requireEnabled();
        mongo.updateMulti(Query.query(Criteria.where("revoked").ne(true)),
                new Update().set("revoked", true), "instructorSessions");
        mongo.remove(Query.query(Criteria.where("_id").is(INVITE_ID)), "instructorInvitations");
        audit("REVOKE_ALL", "admin", "/admin/instructor-access/revoke", 200);
    }

    public void audit(String event, String role, String path, int status) {
        if (!enabledForStaging()) return;
        mongo.insert(new Document("event", event)
                .append("teacherEmail", configuredEmail.trim().toLowerCase(Locale.ROOT))
                .append("role", role)
                .append("path", path)
                .append("status", status)
                .append("at", new Date()), "instructorAudit");
    }

    private static boolean validRole(String role, int slot) {
        return ("admin".equals(role) || "buyer".equals(role)) && slot == 0
                || "seller".equals(role) && slot >= 1 && slot <= 8;
    }

    public static String accountEmail(InstructorSession session) {
        return switch (session.role()) {
            case "admin" -> "admin@foodmobo.local";
            case "buyer" -> "buyer@foodmobo.local";
            case "seller" -> "seller" + session.sellerSlot() + "@foodmobo.local";
            default -> throw new IllegalArgumentException("Unknown instructor role");
        };
    }

    private static String storeName(String slug) {
        for (Store store : STORES) if (store.slug().equals(slug)) return store.name();
        return null;
    }

    public void setCookie(HttpServletResponse response, String value) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(COOKIE, value).httpOnly(true).secure(secureCookie)
                        .sameSite("Lax").path("/").maxAge(SESSION_LIFE).build().toString());
    }

    public void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(COOKIE, "").httpOnly(true).secure(secureCookie)
                        .sameSite("Lax").path("/").maxAge(Duration.ZERO).build().toString());
    }

    public static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies)
            if (COOKIE.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    public record Store(String slug, String name) {}
    public record InstructorSession(String teacherEmail, String role, int sellerSlot, Instant expiresAt) {}
}
