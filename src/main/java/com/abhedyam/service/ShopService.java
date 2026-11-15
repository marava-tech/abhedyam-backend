package com.abhedyam.service;

import com.abhedyam.dto.AuthResponse;
import com.abhedyam.dto.ShopResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Owner;
import com.abhedyam.model.enums.UserType;
import com.abhedyam.repository.OwnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Business plan: more shops on one Gmail. Each extra shop is its own owner row pointing to the main shop, so every
 * owner-id check, helper, report and khata link works unchanged. Spec: specs/multiple-shops.md
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShopService {

    private final OwnerRepository ownerRepository;
    private final AuthService authService;

    /** Main shop first, then the extra shops oldest first. */
    @Transactional(readOnly = true)
    public List<ShopResponse> list(UUID currentShopId) {
        Owner main = ownerRepository.main(find(currentShopId));
        List<ShopResponse> shops = new ArrayList<>();
        shops.add(new ShopResponse(main.getId(), main.getBusinessName(), true, main.getId().equals(currentShopId), false));
        for (Owner s : ownerRepository.findByParentOwnerIdOrderByCreatedAtAsc(main.getId())) {
            if (!Boolean.TRUE.equals(s.getIsActive())) continue; // deleted shop
            shops.add(new ShopResponse(s.getId(), s.getBusinessName(), false, s.getId().equals(currentShopId), ownerRepository.paused(s)));
        }
        return shops;
    }

    /** Adds a shop (Business only) and signs this phone in to it; isNewUser=true so the app runs shop setup. */
    @Transactional
    public AuthResponse add(UUID currentShopId, UUID sessionId, String businessName, String deviceId, String deviceName) {
        String name = businessName == null ? "" : businessName.trim();
        if (name.isEmpty() || name.length() > 100) throw new BusinessException("VALIDATION_ERROR", "Enter the shop name");
        Owner main = ownerRepository.main(find(currentShopId));
        if (Plan.of(main) != Plan.BUSINESS) {
            throw new BusinessException("PLAN_LIMIT", "More shops come with the Business plan");
        }
        Owner shop = new Owner();
        shop.setName(main.getName());
        shop.setBusinessName(name);
        shop.setType(UserType.BUSINESS);
        shop.setIsVerified(false);
        shop.setParentOwnerId(main.getId());
        shop = ownerRepository.save(shop);
        log.info("Shop added: {} under main shop {}", shop.getId(), main.getId());
        return authService.shopAuth(shop, currentShopId, sessionId, deviceId, deviceName, true);
    }

    /** Signs this phone in to another of the owner's shops. */
    @Transactional
    public AuthResponse switchTo(UUID currentShopId, UUID sessionId, UUID targetShopId, String deviceId, String deviceName) {
        Owner main = ownerRepository.main(find(currentShopId));
        Owner target = find(targetShopId);
        boolean mine = target.getId().equals(main.getId()) || main.getId().equals(target.getParentOwnerId());
        if (!mine || !Boolean.TRUE.equals(target.getIsActive())) throw new ResourceNotFoundException("Shop not found");
        return authService.shopAuth(target, currentShopId, sessionId, deviceId, deviceName, false);
    }

    private Owner find(UUID id) {
        return ownerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
    }
}
