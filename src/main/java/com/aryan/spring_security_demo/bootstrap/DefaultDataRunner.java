package com.aryan.spring_security_demo.bootstrap;

import com.aryan.spring_security_demo.config.StartupProperties;
import com.aryan.spring_security_demo.model.Category;
import com.aryan.spring_security_demo.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the default set of product <b>categories</b> so a fresh database is
 * immediately usable by the catalog endpoints. Roles/users are seeded separately
 * by {@code DataInitializer} on {@code ApplicationReadyEvent}; this runner owns
 * the catalog reference data.
 *
 * <p>The operation is idempotent (skips categories that already exist) and can be
 * disabled per-run with {@code --skip-seed} or globally via
 * {@code app.startup.seed.enabled=false}.
 *
 * <p>{@code @Transactional} on {@link #run} applies because Spring invokes the
 * runner through its proxy, so all inserts share one transaction.
 */
@Component
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class DefaultDataRunner implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final StartupProperties startupProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        StartupProperties.Seed seed = startupProperties.getSeed();
        if (!seed.isEnabled() || args.containsOption("skip-seed")) {
            log.info("[seed] Category seeding skipped (enabled={}, --skip-seed={})",
                    seed.isEnabled(), args.containsOption("skip-seed"));
            return;
        }

        List<String> categories = seed.getCategories();
        int created = 0;
        for (String rawName : categories) {
            String name = rawName.trim();
            if (name.isEmpty() || categoryRepository.existsByName(name)) {
                continue;
            }
            categoryRepository.save(new Category(name));
            created++;
        }

        log.info("[seed] Default categories ready — {} created, {} already present",
                created, categories.size() - created);
    }
}
