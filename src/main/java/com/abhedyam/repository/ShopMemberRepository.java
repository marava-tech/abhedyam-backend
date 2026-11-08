package com.abhedyam.repository;

import com.abhedyam.model.ShopMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopMemberRepository extends JpaRepository<ShopMember, UUID> {
    Optional<ShopMember> findByInviteCode(String inviteCode);

    List<ShopMember> findByOwnerIdAndStatusNotOrderByCreatedAtAsc(UUID ownerId, ShopMember.Status status);

    Optional<ShopMember> findFirstByFirebaseUidAndStatus(String firebaseUid, ShopMember.Status status);

    Optional<ShopMember> findFirstByEmailAndStatus(String email, ShopMember.Status status);

    List<ShopMember> findByOwnerId(UUID ownerId);
}
