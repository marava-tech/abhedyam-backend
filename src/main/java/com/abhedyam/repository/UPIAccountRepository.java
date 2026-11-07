package com.abhedyam.repository;

import com.abhedyam.model.UPIAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UPIAccountRepository extends JpaRepository<UPIAccount, UUID> {
    Optional<UPIAccount> findByVpa(String vpa);
    Optional<UPIAccount> findByOwnerId(UUID ownerId);
}

