package com.abhedyam.service;

import com.abhedyam.dto.AuthResponse;
import com.abhedyam.dto.ShopResponse;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.exception.ResourceNotFoundException;
import com.abhedyam.model.Owner;
import com.abhedyam.model.OwnerSettings;
import com.abhedyam.model.enums.UserType;
import com.abhedyam.repository.OwnerRepository;
import com.abhedyam.repository.OwnerSettingsRepository;
import com.abhedyam.service.interfaces.IOwnerService;
import com.abhedyam.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final OwnerSettingsRepository ownerSettingsRepository;
    private final IOwnerService ownerService;

    /** Main shop first, then the extra shops oldest first. */
    @Transactional(readOnly = true)
    public List<ShopResponse> list(UUID currentShopId) {
        Owner main = ownerRepository.main(find(currentShopId));
        List<ShopResponse> shops = new ArrayList<>();
        shops.add(response(main, true, currentShopId));
        for (Owner s : ownerRepository.findByParentOwnerIdOrderByCreatedAtAsc(main.getId())) {
            if (!Boolean.TRUE.equals(s.getIsActive())) continue; // deleted shop
            shops.add(response(s, false, currentShopId));
        }
        return shops;
    }

    private ShopResponse response(Owner s, boolean main, UUID currentShopId) {
        return new ShopResponse(s.getId(), s.getBusinessName(), main, s.getId().equals(currentShopId),
                !main && ownerRepository.paused(s), ownerService.toCollect(s.getId()), s.getShopColour());
    }

    /** Rename a shop and/or pick its colour (More → Your shops). Any of the owner's shops, paused ones too. */
    @Transactional
    public ShopResponse update(UUID currentShopId, UUID shopId, String businessName, Integer colour) {
        Owner main = ownerRepository.main(find(currentShopId));
        Owner shop = mine(main, shopId);
        if (businessName != null) {
            String name = businessName.trim();
            if (name.isEmpty() || name.length() > 100) throw new BusinessException("VALIDATION_ERROR", "Enter the shop name");
            shop.setBusinessName(name);
        }
        if (colour != null) {
            if (colour < 0 || colour > 7) throw new BusinessException("VALIDATION_ERROR", "Pick a colour");
            shop.setShopColour(colour);
        }
        return response(ownerRepository.save(shop), shop.getId().equals(main.getId()), currentShopId);
    }

    /** [shopId] if it is the main shop or one of its open extra shops, else 404. */
    private Owner mine(Owner main, UUID shopId) {
        Owner target = find(shopId);
        boolean mine = target.getId().equals(main.getId()) || main.getId().equals(target.getParentOwnerId());
        if (!mine || !Boolean.TRUE.equals(target.getIsActive())) throw new ResourceNotFoundException("Shop not found");
        return target;
    }

    /**
     * Adds a shop (Business only) and signs this phone in to it. The bill phone defaults to the main shop's, and the
     * main shop's settings (setup answers, villages, location tools, Home tools) are copied, so the app opens the new
     * shop straight on Home: no setup questions, no referral code (that is for new accounts).
     */
    @Transactional
    public AuthResponse add(UUID currentShopId, UUID sessionId, String businessName, String phone, String deviceId, String deviceName) {
        String name = businessName == null ? "" : businessName.trim();
        if (name.isEmpty() || name.length() > 100) throw new BusinessException("VALIDATION_ERROR", "Enter the shop name");
        String billPhone = null;
        if (phone != null && !phone.isBlank()) {
            if (!PhoneUtil.isValidPhone(phone)) throw new BusinessException("VALIDATION_ERROR", "Enter a 10-digit phone number");
            billPhone = PhoneUtil.extractPhoneWithoutCountryCode(PhoneUtil.normalizePhone(phone));
        }
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
        shop.setPhone(billPhone != null ? billPhone : main.getPhone()); // phone_normalized stays empty: see OwnerService
        shop = ownerRepository.save(shop);
        copySettings(main.getId(), shop.getId());
        log.info("Shop added: {} under main shop {}", shop.getId(), main.getId());
        return authService.shopAuth(shop, currentShopId, sessionId, deviceId, deviceName, false);
    }

    private void copySettings(UUID from, UUID to) {
        ownerSettingsRepository.findByOwnerId(from).ifPresent(m -> {
            OwnerSettings s = new OwnerSettings();
            s.setOwnerId(to);
            s.setIsDarkModeEnabled(m.getIsDarkModeEnabled());
            s.setVillageModeEnabled(m.getVillageModeEnabled());
            s.setLocationToolsEnabled(m.getLocationToolsEnabled());
            if (m.getOtherFlags() != null) s.setOtherFlags(new HashMap<>(m.getOtherFlags()));
            ownerSettingsRepository.save(s);
        });
    }

    /** Signs this phone in to another of the owner's shops. */
    @Transactional
    public AuthResponse switchTo(UUID currentShopId, UUID sessionId, UUID targetShopId, String deviceId, String deviceName) {
        Owner main = ownerRepository.main(find(currentShopId));
        Owner target = mine(main, targetShopId);
        return authService.shopAuth(target, currentShopId, sessionId, deviceId, deviceName, false);
    }

    private Owner find(UUID id) {
        return ownerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
    }
}
