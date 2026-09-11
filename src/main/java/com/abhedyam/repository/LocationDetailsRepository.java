package com.abhedyam.repository;

import com.abhedyam.model.LocationDetails;
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
public interface LocationDetailsRepository extends JpaRepository<LocationDetails, UUID> {
       Optional<LocationDetails> findByUserId(UUID userId);

       @Query("SELECT DISTINCT ld.village FROM LocationDetails ld " +
                     "INNER JOIN Customer c ON c.id = ld.userId " +
                     "WHERE c.ownerId = :ownerId " +
                     "AND ld.village IS NOT NULL " +
                     "AND LOWER(ld.village) LIKE LOWER(CONCAT('%', :name, '%')) " +
                     "ORDER BY ld.village ASC")
       List<String> findDistinctVillagesByNameContainingIgnoreCaseAndOwnerId(@Param("name") String name,
                     @Param("ownerId") UUID ownerId);

       @Query("SELECT DISTINCT ld.village FROM LocationDetails ld " +
                     "INNER JOIN Customer c ON c.id = ld.userId " +
                     "WHERE c.ownerId = :ownerId " +
                     "AND ld.village IS NOT NULL " +
                     "ORDER BY ld.village ASC")
       List<String> findDistinctVillagesByOwnerId(@Param("ownerId") UUID ownerId);

       @Query("SELECT ld FROM LocationDetails ld " +
                     "INNER JOIN Customer c ON c.id = ld.userId " +
                     "WHERE c.ownerId = :ownerId " +
                     "AND ld.userId IN :customerIds " +
                     "AND ld.latitude IS NOT NULL " +
                     "AND ld.longitude IS NOT NULL")
       List<LocationDetails> findCustomerLocationsByCustomerIds(@Param("ownerId") UUID ownerId,
                     @Param("customerIds") List<UUID> customerIds);

       @Query("SELECT ld FROM LocationDetails ld " +
                     "INNER JOIN Customer c ON c.id = ld.userId " +
                     "WHERE c.ownerId = :ownerId " +
                     "AND ld.latitude IS NOT NULL " +
                     "AND ld.longitude IS NOT NULL")
       List<LocationDetails> findCustomerLocationsByOwnerId(@Param("ownerId") UUID ownerId);

       @Query("SELECT ld FROM LocationDetails ld " +
                     "INNER JOIN Customer c ON c.id = ld.userId " +
                     "WHERE c.ownerId = :ownerId " +
                     "AND LOWER(TRIM(ld.village)) = LOWER(TRIM(:village)) " +
                     "AND ld.latitude IS NOT NULL " +
                     "AND ld.longitude IS NOT NULL")
       List<LocationDetails> findCustomerLocationsByOwnerIdAndVillage(@Param("ownerId") UUID ownerId,
                     @Param("village") String village);

       @Query("SELECT ld FROM LocationDetails ld WHERE ld.userId IN :customerIds")
       List<LocationDetails> findByUserIdIn(@Param("customerIds") List<UUID> customerIds);

       @Query("SELECT ld.village, COUNT(c.id) as customerCount " +
                     "FROM LocationDetails ld " +
                     "INNER JOIN Customer c ON c.id = ld.userId " +
                     "WHERE c.ownerId = :ownerId " +
                     "AND ld.village IS NOT NULL " +
                     "AND c.isActive = true " +
                     "GROUP BY ld.village " +
                     "ORDER BY LOWER(TRIM(ld.village)) ASC")
       List<Object[]> findVillagesWithCustomerCountByOwnerId(@Param("ownerId") UUID ownerId);

       @Query(value = "SELECT ld.village, COUNT(c.id) as customerCount " +
                     "FROM LocationDetails ld " +
                     "INNER JOIN Customer c ON c.id = ld.userId " +
                     "WHERE c.ownerId = :ownerId " +
                     "AND ld.village IS NOT NULL " +
                     "AND c.isActive = true " +
                     "GROUP BY ld.village " +
                     "ORDER BY LOWER(TRIM(ld.village)) ASC", countQuery = "SELECT COUNT(DISTINCT ld.village) " +
                                   "FROM LocationDetails ld " +
                                   "INNER JOIN Customer c ON c.id = ld.userId " +
                                   "WHERE c.ownerId = :ownerId " +
                                   "AND ld.village IS NOT NULL " +
                                   "AND c.isActive = true")
       Page<Object[]> findVillagesWithCustomerCountByOwnerIdPageable(@Param("ownerId") UUID ownerId, Pageable pageable);

       @Query("SELECT ld.village, COUNT(c.id) as customerCount " +
                     "FROM LocationDetails ld " +
                     "INNER JOIN Customer c ON c.id = ld.userId " +
                     "WHERE c.ownerId = :ownerId " +
                     "AND ld.village IS NOT NULL " +
                     "AND LOWER(ld.village) LIKE LOWER(CONCAT('%', :name, '%')) " +
                     "AND c.isActive = true " +
                     "GROUP BY ld.village " +
                     "ORDER BY LOWER(TRIM(ld.village)) ASC")
       List<Object[]> findVillagesWithCustomerCountByNameContainingIgnoreCaseAndOwnerId(
                     @Param("name") String name,
                     @Param("ownerId") UUID ownerId);

       @Query(value = "SELECT ld.village, COUNT(c.id) as customerCount " +
                     "FROM LocationDetails ld " +
                     "INNER JOIN Customer c ON c.id = ld.userId " +
                     "WHERE c.ownerId = :ownerId " +
                     "AND ld.village IS NOT NULL " +
                     "AND LOWER(ld.village) LIKE LOWER(CONCAT('%', :name, '%')) " +
                     "AND c.isActive = true " +
                     "GROUP BY ld.village " +
                     "ORDER BY LOWER(TRIM(ld.village)) ASC", countQuery = "SELECT COUNT(DISTINCT ld.village) " +
                                   "FROM LocationDetails ld " +
                                   "INNER JOIN Customer c ON c.id = ld.userId " +
                                   "WHERE c.ownerId = :ownerId " +
                                   "AND ld.village IS NOT NULL " +
                                   "AND LOWER(ld.village) LIKE LOWER(CONCAT('%', :name, '%')) " +
                                   "AND c.isActive = true")
       Page<Object[]> findVillagesWithCustomerCountByNameContainingIgnoreCaseAndOwnerIdPageable(
                     @Param("name") String name,
                     @Param("ownerId") UUID ownerId,
                     Pageable pageable);

}
