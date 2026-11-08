package com.abhedyam.repository;

import com.abhedyam.model.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {
    Optional<Partner> findByCode(String code);

    boolean existsByCode(String code);

    List<Partner> findAllByOrderByCreatedAtDesc();
}
