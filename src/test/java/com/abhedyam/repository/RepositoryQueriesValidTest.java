package com.abhedyam.repository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Starting the JPA slice makes Spring Data parse every @Query against the entity
 * model (no tables needed: ddl-auto=none). A malformed JPQL fails context startup,
 * which is exactly what would take production down on deploy.
 */
@DataJpaTest
@AutoConfigureTestDatabase
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=never"
})
class RepositoryQueriesValidTest {
    @Test
    void contextStartsSoAllQueriesParse() {
    }
}
