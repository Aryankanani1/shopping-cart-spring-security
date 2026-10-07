package com.aryan.spring_security_demo;

import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.order.Order;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every entity takes its id from MySQL's AUTO_INCREMENT ({@code IDENTITY}). The
 * other strategies draw ids from a sequence, which MySQL only emulates with a
 * table, and Hibernate fetches each new block of ids from it on a second pooled
 * connection while the request's transaction still holds its first. Enough
 * simultaneous requests then held every connection while waiting on each other,
 * and the pool stalled until the 30-second connection timeout.
 */
class EntityIdGenerationTest {

    // Regression: every entity used GenerationType.SEQUENCE.
    @Test
    void everyEntityUsesAutoIncrementIds() {
        List<Class<?>> entities = entityClasses();
        assertThat(entities).as("entities found by the scan").contains(User.class, Order.class);

        assertThat(entities).allSatisfy(entity -> {
            GeneratedValue generated = idField(entity).getAnnotation(GeneratedValue.class);
            assertThat(generated).as(entity.getSimpleName() + ": generated id").isNotNull();
            assertThat(generated.strategy()).as(entity.getSimpleName() + ": id strategy")
                    .isEqualTo(GenerationType.IDENTITY);
        });
    }

    private static List<Class<?>> entityClasses() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        return scanner.findCandidateComponents("com.aryan.spring_security_demo").stream()
                .map(BeanDefinition::getBeanClassName)
                .<Class<?>>map(name -> ClassUtils.resolveClassName(name, EntityIdGenerationTest.class.getClassLoader()))
                .toList();
    }

    private static Field idField(Class<?> entity) {
        for (Class<?> type = entity; type != Object.class; type = type.getSuperclass()) {
            var id = Arrays.stream(type.getDeclaredFields()).filter(f -> f.isAnnotationPresent(Id.class)).findFirst();
            if (id.isPresent()) {
                return id.get();
            }
        }
        throw new AssertionError(entity.getSimpleName() + " has no @Id field");
    }
}
