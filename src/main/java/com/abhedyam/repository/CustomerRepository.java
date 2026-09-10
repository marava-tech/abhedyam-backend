package com.abhedyam.repository;

import com.abhedyam.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findByOwnerId(UUID ownerId);
    
    Optional<Customer> findByPhoneNormalized(String phoneNormalized);
    Optional<Customer> findFirstByOwnerIdAndNameIgnoreCase(UUID ownerId, String name);

    @Query("SELECT c FROM Customer c WHERE c.ownerId = :ownerId " +
           "AND (:searchTerm IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Customer> searchCustomers(@Param("ownerId") UUID ownerId,
                                   @Param("searchTerm") String searchTerm,
                                   Pageable pageable);
    
    @Query("SELECT c FROM Customer c WHERE c.ownerId = :ownerId " +
           "AND LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Customer> findByNameContainingIgnoreCaseAndOwnerId(@Param("name") String name, 
                                                              @Param("ownerId") UUID ownerId);
    
    @Query("SELECT DISTINCT c FROM Customer c " +
           "LEFT JOIN LocationDetails ld ON ld.userId = c.id " +
           "WHERE c.ownerId = :ownerId " +
           "AND (:searchText IS NULL OR " +
           "     LOWER(c.name) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "     (ld.village IS NOT NULL AND LOWER(ld.village) LIKE LOWER(CONCAT('%', :searchText, '%'))) OR " +
           "     (:isNumeric = true AND c.phone LIKE CONCAT('%', :searchText, '%')))")
    List<Customer> filterCustomers(@Param("ownerId") UUID ownerId,
                                   @Param("searchText") String searchText,
                                   @Param("isNumeric") boolean isNumeric);

    @Query("SELECT DISTINCT c FROM Customer c " +
           "LEFT JOIN LocationDetails ld ON ld.userId = c.id " +
           "WHERE c.ownerId = :ownerId " +
           "AND (:searchText IS NULL OR " +
           "     LOWER(c.name) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "     (ld.village IS NOT NULL AND LOWER(ld.village) LIKE LOWER(CONCAT('%', :searchText, '%'))) OR " +
           "     (:isNumeric = true AND c.phone LIKE CONCAT('%', :searchText, '%')))")
    Page<Customer> searchCustomersWithVillage(@Param("ownerId") UUID ownerId,
                                              @Param("searchText") String searchText,
                                              @Param("isNumeric") boolean isNumeric,
                                              Pageable pageable);
    
    @Query("SELECT DISTINCT c FROM Customer c " +
           "LEFT JOIN LocationDetails ld ON ld.userId = c.id " +
           "WHERE c.ownerId = :ownerId " +
           "AND (:village IS NULL OR (ld.village IS NOT NULL AND LOWER(TRIM(ld.village)) = LOWER(TRIM(:village)))) " +
           "AND (:searchText IS NULL OR " +
           "     LOWER(c.name) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "     (:isNumeric = true AND c.phone LIKE CONCAT('%', :searchText, '%')))")
    Page<Customer> searchCustomersWithVillageFilter(@Param("ownerId") UUID ownerId,
                                                    @Param("village") String village,
                                                    @Param("searchText") String searchText,
                                                    @Param("isNumeric") boolean isNumeric,
                                                    Pageable pageable);

    /*
     * Village-ordered listing. Customers are grouped by village (case/whitespace
     * insensitive) and then by name; customers with no village sort last.
     * No DISTINCT here on purpose — location_details.user_id is unique, so the
     * LEFT JOIN is 1:0..1 and MySQL rejects ORDER BY on a joined column when
     * DISTINCT is present. The Pageable must be unsorted; ordering is fixed here.
     */
    @Query(value = "SELECT c FROM Customer c " +
           "LEFT JOIN LocationDetails ld ON ld.userId = c.id " +
           "WHERE c.ownerId = :ownerId " +
           "AND (:searchText IS NULL OR " +
           "     LOWER(c.name) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "     (ld.village IS NOT NULL AND LOWER(ld.village) LIKE LOWER(CONCAT('%', :searchText, '%'))) OR " +
           "     (:isNumeric = true AND c.phone LIKE CONCAT('%', :searchText, '%'))) " +
           "ORDER BY CASE WHEN ld.village IS NULL OR TRIM(ld.village) = '' THEN 1 ELSE 0 END, " +
           "LOWER(TRIM(ld.village)), LOWER(TRIM(c.name))",
           countQuery = "SELECT COUNT(c) FROM Customer c " +
           "LEFT JOIN LocationDetails ld ON ld.userId = c.id " +
           "WHERE c.ownerId = :ownerId " +
           "AND (:searchText IS NULL OR " +
           "     LOWER(c.name) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "     (ld.village IS NOT NULL AND LOWER(ld.village) LIKE LOWER(CONCAT('%', :searchText, '%'))) OR " +
           "     (:isNumeric = true AND c.phone LIKE CONCAT('%', :searchText, '%')))")
    Page<Customer> searchCustomersOrderByVillage(@Param("ownerId") UUID ownerId,
                                                 @Param("searchText") String searchText,
                                                 @Param("isNumeric") boolean isNumeric,
                                                 Pageable pageable);

    @Query(value = "SELECT c FROM Customer c " +
           "LEFT JOIN LocationDetails ld ON ld.userId = c.id " +
           "WHERE c.ownerId = :ownerId " +
           "AND (:village IS NULL OR (ld.village IS NOT NULL AND LOWER(TRIM(ld.village)) = LOWER(TRIM(:village)))) " +
           "AND (:searchText IS NULL OR " +
           "     LOWER(c.name) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "     (:isNumeric = true AND c.phone LIKE CONCAT('%', :searchText, '%'))) " +
           "ORDER BY CASE WHEN ld.village IS NULL OR TRIM(ld.village) = '' THEN 1 ELSE 0 END, " +
           "LOWER(TRIM(ld.village)), LOWER(TRIM(c.name))",
           countQuery = "SELECT COUNT(c) FROM Customer c " +
           "LEFT JOIN LocationDetails ld ON ld.userId = c.id " +
           "WHERE c.ownerId = :ownerId " +
           "AND (:village IS NULL OR (ld.village IS NOT NULL AND LOWER(TRIM(ld.village)) = LOWER(TRIM(:village)))) " +
           "AND (:searchText IS NULL OR " +
           "     LOWER(c.name) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "     (:isNumeric = true AND c.phone LIKE CONCAT('%', :searchText, '%')))")
    Page<Customer> searchCustomersWithVillageFilterOrderByVillage(@Param("ownerId") UUID ownerId,
                                                                 @Param("village") String village,
                                                                 @Param("searchText") String searchText,
                                                                 @Param("isNumeric") boolean isNumeric,
                                                                 Pageable pageable);

    Page<Customer> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);
    
    @Query("SELECT c FROM Customer c WHERE c.id IN :customerIds")
    List<Customer> findByIdIn(@Param("customerIds") List<UUID> customerIds);
    
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.ownerId = :ownerId")
    long countByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("SELECT DISTINCT c FROM Customer c " +
           "LEFT JOIN LocationDetails ld ON ld.userId = c.id " +
           "WHERE c.ownerId = :ownerId " +
           "AND LOWER(TRIM(c.name)) = LOWER(TRIM(:name)) " +
           "AND ld.village IS NOT NULL AND LOWER(TRIM(ld.village)) = LOWER(TRIM(:village))")
    Optional<Customer> findByOwnerIdAndNameAndVillageIgnoreCase(@Param("ownerId") UUID ownerId,
                                                                @Param("name") String name,
                                                                @Param("village") String village);
}
