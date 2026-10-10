package com.safayet.foodmobochain.service;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceWeeklyOrdersTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 5, 0, 0);
    private static final LocalDateTime END = START.plusDays(1);

    @Test
    void adminWeeklyCountUsesOneCreatedAtRange() {
        Query query = ReportService.weeklyOrderQuery(null, START, END);
        Document bson = assertDoesNotThrow(query::getQueryObject);
        assertEquals(1, bson.size());
        Document range = (Document) bson.get("createdAt");
        assertEquals(START, range.get("$gte"));
        assertEquals(END, range.get("$lt"));
    }

    @Test
    void sellerWeeklyCountAddsOwnerCartWithoutDuplicatingDateKey() {
        Query query = ReportService.weeklyOrderQuery("foodCarts:1", START, END);
        Document bson = assertDoesNotThrow(query::getQueryObject);
        assertEquals(2, bson.size());
        assertEquals("foodCarts:1", bson.getString("foodCartId"));
        Document range = (Document) bson.get("createdAt");
        assertEquals(START, range.get("$gte"));
        assertEquals(END, range.get("$lt"));
    }

    @Test
    void dateRangeUsesInclusiveStartAndExclusiveEnd() {
        Document range = (Document) ReportService.weeklyOrderQuery("foodCarts:2", START, END)
                .getQueryObject().get("createdAt");
        assertEquals(2, range.size());
        assertTrue(range.containsKey("$gte"));
        assertTrue(range.containsKey("$lt"));
    }
}
