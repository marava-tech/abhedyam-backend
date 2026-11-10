package com.abhedyam.config;

import com.abhedyam.util.SearchKey;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Fills the search keys of names and villages saved before cross-script search (specs/cross-script-search.md).
 * Runs on every start but only touches rows without a key, so after the first start it finds nothing.
 * Bulk JPQL updates on purpose: saving the entities would change their updatedAt and look like an edit to sync.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchKeyBackfill {

    private final EntityManager em;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void fill() {
        List<Object[]> names = em.createQuery(
                "SELECT u.id, u.name FROM User u WHERE u.nameKey IS NULL AND u.name IS NOT NULL", Object[].class)
                .getResultList();
        int n = 0;
        for (Object[] row : names) {
            String key = SearchKey.of((String) row[1]);
            if (key == null) continue;
            n += em.createQuery("UPDATE User u SET u.nameKey = :k WHERE u.id = :id")
                    .setParameter("k", key).setParameter("id", row[0]).executeUpdate();
        }
        List<Object[]> villages = em.createQuery(
                "SELECT ld.id, ld.village FROM LocationDetails ld WHERE ld.villageKey IS NULL AND ld.village IS NOT NULL",
                Object[].class).getResultList();
        int v = 0;
        for (Object[] row : villages) {
            String key = SearchKey.of((String) row[1]);
            if (key == null) continue;
            v += em.createQuery("UPDATE LocationDetails ld SET ld.villageKey = :k WHERE ld.id = :id")
                    .setParameter("k", key).setParameter("id", row[0]).executeUpdate();
        }
        if (n + v > 0) log.info("Search keys filled: {} names, {} villages", n, v);
    }
}
