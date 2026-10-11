package com.azizul.foodmobochain.config;

import com.azizul.foodmobochain.model.User;
import com.azizul.foodmobochain.model.CustomerOrder;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import static org.assertj.core.api.Assertions.assertThat;

class MongoLegacyTypeHintCompatibilityTest {

    @Test
    void legacyClassNamesAreIgnoredForConcreteDocumentTypes() throws Exception {
        MongoMappingContext context = new MongoMappingContext();
        context.afterPropertiesSet();
        MappingMongoConverter converter =
                new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        MongoConfig.legacyMongoTypeHintCompatibility()
                .postProcessBeforeInitialization(converter, "mappingMongoConverter");
        converter.afterPropertiesSet();

        // Simulate a document written by a now-unavailable previous application package.
        Document oldUser = new Document("_id", "users:old-test")
                .append("_class", "com.archived.application.model.User")
                .append("email", "legacy@example.invalid")
                .append("emailNormalized", "legacy@example.invalid")
                .append("enabled", true);
        User user = converter.read(User.class, oldUser);
        assertThat(user.getId()).isEqualTo("users:old-test");
        assertThat(user.getEmailNormalized()).isEqualTo("legacy@example.invalid");
        Document savedUser = new Document();
        converter.write(user, savedUser);
        assertThat(savedUser).doesNotContainKey("_class");

        Document oldOrder = new Document("_id", "orders:old-test")
                .append("_class", "com.archived.application.model.CustomerOrder")
                .append("orderNumber", "FMC-LEGACY");
        CustomerOrder order = converter.read(CustomerOrder.class, oldOrder);
        assertThat(order.getOrderNumber()).isEqualTo("FMC-LEGACY");
        Document savedOrder = new Document();
        converter.write(order, savedOrder);
        assertThat(savedOrder).doesNotContainKey("_class");
    }
}
