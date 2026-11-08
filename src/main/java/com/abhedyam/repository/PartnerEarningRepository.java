package com.abhedyam.repository;

import com.abhedyam.model.PartnerEarning;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PartnerEarningRepository extends JpaRepository<PartnerEarning, UUID> {
    List<PartnerEarning> findByPartnerId(UUID partnerId);

    List<PartnerEarning> findByPartnerIdAndPaidAtIsNull(UUID partnerId);

    boolean existsByOwnerId(UUID ownerId);
}
