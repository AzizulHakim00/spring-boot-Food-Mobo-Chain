package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.FoodCart;
import com.safayet.foodmobochain.model.enums.*;
import com.safayet.foodmobochain.repository.*;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final UserRepository userRepository;
    private final FoodCartRepository foodCartRepository;
    private final FoodItemRepository foodItemRepository;
    private final OrderRepository orderRepository;
    private final MongoTemplate mongoTemplate;

    public AdminDashboard adminDashboard() {
        return new AdminDashboard(userRepository.count(), foodCartRepository.count(), orderRepository.count(),
                sumAmount(Criteria.where("payment.status").is(PaymentStatus.PAID.name()), "payment.amount"),
                foodCartRepository.countByApprovedFalse(), mongoTemplate.count(pendingOnlinePaymentQuery(), "orders"),
                weeklyOrders(null), orderStatusCounts(), topFoods());
    }

    public SellerDashboard sellerDashboard(FoodCart cart) {
        return new SellerDashboard(orderRepository.countByFoodCartId(cart.getId()),
                sumAmount(new Criteria().andOperator(Criteria.where("foodCartId").is(cart.getId()),
                        Criteria.where("status").is(OrderStatus.DELIVERED.name())), "total"),
                foodItemRepository.countByFoodCartIdAndArchivedFalse(cart.getId()),
                orderRepository.countByFoodCartIdAndCreatedAtGreaterThanEqual(cart.getId(),
                        lastSevenDaysStart(LocalDate.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE))),
                weeklyOrders(cart));
    }

    public Map<OrderStatus, Long> orderStatusCounts() {
        Map<OrderStatus, Long> values = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) values.put(status, orderRepository.countByStatus(status));
        return values;
    }

    public List<ChartPoint> weeklyOrders(FoodCart cart) {
        List<ChartPoint> points = new ArrayList<>();
        DateTimeFormatter format = DateTimeFormatter.ofPattern("EEE");
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE).minusDays(i);
            LocalDateTime start = day.atStartOfDay(), end = day.plusDays(1).atStartOfDay();
            long count = mongoTemplate.count(
                    weeklyOrderQuery(cart == null ? null : cart.getId(), start, end),
                    "orders");
            points.add(new ChartPoint(day.format(format), count));
        }
        return points;
    }

    /** Seven calendar days including today, aligned with the daily activity chart. */
    static LocalDateTime lastSevenDaysStart(LocalDate today) {
        return today.minusDays(6).atStartOfDay();
    }

    /** Only payments awaiting online checkout count as "pending"; ordinary COD is excluded. */
    static Query pendingOnlinePaymentQuery() {
        return Query.query(Criteria.where("status").is(OrderStatus.PENDING_PAYMENT.name())
                .and("payment.method").is(PaymentMethod.SSLCOMMERZ.name())
                .and("payment.status").is(PaymentStatus.PENDING.name()));
    }

    /**
     * Construct a single MongoDB date range instead of two separate createdAt criteria.
     * Duplicate createdAt predicates in a derived count query trigger
     * InvalidMongoDbApiUsageException in Spring Data MongoDB 5.
     */
    static Query weeklyOrderQuery(String foodCartId, LocalDateTime start, LocalDateTime end) {
        Criteria criterion = Criteria.where("createdAt").gte(start).lt(end);
        if (foodCartId != null) {
            criterion.and("foodCartId").is(foodCartId);
        }
        return Query.query(criterion);
    }

    public List<TopFood> topFoods() {
        Aggregation aggregation = newAggregation(match(Criteria.where("status").is(OrderStatus.DELIVERED.name())),
                unwind("items"), group("items.foodItemIdSnapshot", "items.foodName").sum("items.quantity").as("quantity"),
                sort(org.springframework.data.domain.Sort.Direction.DESC, "quantity"), limit(8));
        return mongoTemplate.aggregate(aggregation, "orders", Document.class).getMappedResults().stream()
                .map(d -> {
                    Object key = d.get("_id");
                    String displayName = key instanceof Document id ? id.getString("foodName") : String.valueOf(key);
                    return new TopFood(displayName, ((Number) d.get("quantity")).longValue());
                }).toList();
    }

    private BigDecimal sumAmount(Criteria criterion, String amountField) {
        Aggregation aggregation = newAggregation(match(criterion), group().sum(amountField).as("total"));
        Document result = mongoTemplate.aggregate(aggregation, "orders", Document.class).getUniqueMappedResult();
        if (result == null || result.get("total") == null) return BigDecimal.ZERO;
        Object amount = result.get("total");
        if (amount instanceof Decimal128 decimal) return decimal.bigDecimalValue();
        return new BigDecimal(amount.toString());
    }

    public long buyerCount() { return userRepository.countByRole(Role.BUYER); }
    public long sellerCount() { return userRepository.countByRole(Role.SELLER); }

    public record ChartPoint(String label, long value) {}
    public record TopFood(String name, long quantity) {}
    public record AdminDashboard(long users, long foodCarts, long orders, BigDecimal revenue,
                                 long pendingCarts, long pendingPayments,
                                 List<ChartPoint> weeklyOrders, Map<OrderStatus, Long> orderStatuses,
                                 List<TopFood> topFoods) {}
    public record SellerDashboard(long orders, BigDecimal revenue, long menuItems,
                                  long ordersThisWeek, List<ChartPoint> weeklyOrders) {}
}
