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
                foodCartRepository.countByApprovedFalse(), orderRepository.countByPaymentStatus(PaymentStatus.PENDING),
                weeklyOrders(null), orderStatusCounts(), topFoods());
    }

    public SellerDashboard sellerDashboard(FoodCart cart) {
        return new SellerDashboard(orderRepository.countByFoodCartId(cart.getId()),
                sumAmount(new Criteria().andOperator(Criteria.where("foodCartId").is(cart.getId()),
                        Criteria.where("status").is(OrderStatus.DELIVERED.name())), "total"),
                foodItemRepository.countByFoodCartIdAndArchivedFalse(cart.getId()),
                orderRepository.countByFoodCartIdAndCreatedAtGreaterThanEqual(cart.getId(), LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE).minusDays(7)),
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
            long count = cart == null
                    ? orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end)
                    : orderRepository.countByFoodCartIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(cart.getId(), start, end);
            points.add(new ChartPoint(day.format(format), count));
        }
        return points;
    }

    public List<TopFood> topFoods() {
        Aggregation aggregation = newAggregation(match(Criteria.where("status").is(OrderStatus.DELIVERED.name())),
                unwind("items"), group("items.foodName").sum("items.quantity").as("quantity"),
                sort(org.springframework.data.domain.Sort.Direction.DESC, "quantity"), limit(8));
        return mongoTemplate.aggregate(aggregation, "orders", Document.class).getMappedResults().stream()
                .map(d -> new TopFood(d.getString("_id"), ((Number) d.get("quantity")).longValue())).toList();
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
