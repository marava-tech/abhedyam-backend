package com.abhedyam.repository;

import com.abhedyam.model.Customer;
import com.abhedyam.model.LocationDetails;
import com.abhedyam.model.enums.UserType;
import com.abhedyam.util.SearchKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Names and villages saved in Telugu are found by English search and the other way round (specs/cross-script-search.md). */
@DataJpaTest
@AutoConfigureTestDatabase
@Import(com.abhedyam.config.JpaConfig.class)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"})
class CrossScriptSearchTest {
    @Autowired CustomerRepository customers;
    @Autowired LocationDetailsRepository locations;

    private final UUID owner = UUID.randomUUID();

    private Customer customer(String name, String village) {
        Customer c = new Customer();
        c.setName(name);
        c.setType(UserType.CUSTOMER);
        c.setOwnerId(owner);
        c = customers.saveAndFlush(c);
        if (village != null) {
            LocationDetails ld = new LocationDetails();
            ld.setUserId(c.getId());
            ld.setVillage(village);
            locations.saveAndFlush(ld);
        }
        return c;
    }

    private List<String> search(String text) {
        return customers.searchCustomersOrderByVillage(owner, text, false, SearchKey.forQuery(text), Pageable.unpaged())
                .map(Customer::getName).getContent();
    }

    @Test
    void englishSearch_findsTeluguName() {
        customer("భాస్కర్ నాయుడు", null);
        customer("Saritha K", null);
        assertThat(search("bhaskar")).containsExactly("భాస్కర్ నాయుడు");
        assertThat(search("naidu")).containsExactly("భాస్కర్ నాయుడు");
    }

    @Test
    void teluguSearch_findsEnglishName() {
        customer("Venkatesh Yadav", null);
        assertThat(search("వెంకటేష్")).containsExactly("Venkatesh Yadav");
    }

    @Test
    void englishSearch_findsCustomersInTeluguVillage() {
        customer("Ramesh", "వరంగల్");
        customer("Suresh", "Hanamkonda");
        assertThat(search("warangal")).containsExactly("Ramesh");
    }

    @Test
    void renamedCustomer_keyFollowsTheName() {
        Customer c = customer("Ravi", null);
        c.setName("సునీత");
        customers.saveAndFlush(c);
        assertThat(search("sunitha")).containsExactly("సునీత");
        assertThat(search("ravi")).isEmpty();
    }
}
