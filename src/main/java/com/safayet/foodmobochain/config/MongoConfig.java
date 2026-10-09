package com.safayet.foodmobochain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/** Maintain the original Asia/Dhaka UI timestamps while storing BSON Date values in UTC. */
@Configuration
@EnableMongoAuditing(dateTimeProviderRef = "dhakaDateTimeProvider")
public class MongoConfig {
    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Dhaka");

    @Bean
    public DateTimeProvider dhakaDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(APP_ZONE));
    }

    /** Requires replica-set/Atlas support; do not point transactional writes at standalone mongod. */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new ToMongoDate(), new FromMongoDate()));
    }

    @WritingConverter
    static class ToMongoDate implements Converter<LocalDateTime, Date> {
        @Override public Date convert(LocalDateTime value) {
            return Date.from(value.atZone(APP_ZONE).toInstant());
        }
    }

    @ReadingConverter
    static class FromMongoDate implements Converter<Date, LocalDateTime> {
        @Override public LocalDateTime convert(Date value) {
            return LocalDateTime.ofInstant(value.toInstant(), APP_ZONE);
        }
    }
}
