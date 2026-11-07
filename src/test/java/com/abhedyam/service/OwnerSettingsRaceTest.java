package com.abhedyam.service;

import com.abhedyam.dto.OwnerSettingsResponse;
import com.abhedyam.repository.OwnerSettingsRepository;
import com.abhedyam.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A new owner's app asks for settings from several screens at once. Every first request must get the
 * settings (no 409) and only one row may be created. Runs the real native INSERT IGNORE (H2 in MySQL mode),
 * so it also checks that the insert's column names match the entity's table.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OwnerSettingsService.class, com.abhedyam.config.JpaConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:settingsrace;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"})
class OwnerSettingsRaceTest {

    @Autowired OwnerSettingsService service;
    @Autowired OwnerSettingsRepository repo;
    @MockBean RedisTemplate<String, String> redis;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void parallelFirstRequestsAllGetSettingsAndCreateOneRow() throws Exception {
        UUID owner = UUID.randomUUID();
        int n = 8;
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            Callable<OwnerSettingsResponse> call = () -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(new UserPrincipal(owner, null), null, List.of()));
                go.await();
                return service.getOwnerSettings(owner);
            };
            List<Future<OwnerSettingsResponse>> results = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) results.add(pool.submit(call));
            go.countDown();
            for (Future<OwnerSettingsResponse> f : results) {
                OwnerSettingsResponse r = f.get(20, TimeUnit.SECONDS); // throws if any request failed
                assertThat(r.getId()).isEqualTo(owner);
                assertThat(r.getDailyQuoteEnabled()).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(repo.findAll().stream().filter(s -> owner.equals(s.getOwnerId())).count()).isEqualTo(1);
    }
}
