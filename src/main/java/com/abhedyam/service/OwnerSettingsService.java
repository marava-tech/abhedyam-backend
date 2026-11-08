package com.abhedyam.service;

import com.abhedyam.dto.OwnerSettingsResponse;
import com.abhedyam.dto.OwnerSettingsUpdateRequest;
import com.abhedyam.model.OwnerSettings;
import com.abhedyam.repository.OwnerSettingsRepository;
import com.abhedyam.service.interfaces.IOwnerSettingsService;
import com.abhedyam.util.SecurityUtil;
import com.abhedyam.exception.BusinessException;
import com.abhedyam.constants.ErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OwnerSettingsService implements IOwnerSettingsService {
    
    private final OwnerSettingsRepository ownerSettingsRepository;
    
    /**
     * No transaction on purpose: a new owner's Home asks for settings several times at once. Inside one
     * MySQL REPEATABLE READ transaction the read after INSERT IGNORE still saw the snapshot from before the
     * parallel insert, so it found nothing → 500 "No value present". Each call on its own sees the row.
     */
    @Override
    public OwnerSettingsResponse getOwnerSettings(UUID ownerId) {
        validateOwnerAccess(ownerId);
        
        OwnerSettings settings = ownerSettingsRepository.findByOwnerId(ownerId).orElseGet(() -> {
            ownerSettingsRepository.insertDefaultsIfAbsent(ownerId.toString());
            return ownerSettingsRepository.findByOwnerId(ownerId).orElseThrow();
        });

        return toResponse(settings);
    }
    
    @Override
    @Transactional
    public OwnerSettingsResponse updateOwnerSettings(UUID ownerId, OwnerSettingsUpdateRequest request) {
        validateOwnerAccess(ownerId);
        
        OwnerSettings settings = ownerSettingsRepository.findByOwnerId(ownerId)
                .orElseGet(() -> {
                OwnerSettings newSettings = new OwnerSettings();
                newSettings.setId(ownerId);
                newSettings.setOwnerId(ownerId);
                newSettings.setIsDarkModeEnabled(false);
                return newSettings;
                });
        
        if (request.getIsDarkModeEnabled() != null) {
            settings.setIsDarkModeEnabled(request.getIsDarkModeEnabled());
        }
        if (request.getVillageModeEnabled() != null) {
            settings.setVillageModeEnabled(request.getVillageModeEnabled());
        }
        if (request.getLocationToolsEnabled() != null) {
            settings.setLocationToolsEnabled(request.getLocationToolsEnabled());
        }
        if (request.getOtherFlags() != null) {
            settings.setOtherFlags(mergeFlags(settings.getOtherFlags(), request.getOtherFlags()));
        }
        
        OwnerSettings saved = ownerSettingsRepository.save(settings);
        return toResponse(saved);
    }
    
    /** For jobs and other services (no caller check): is villages view on for this owner? Off when never set. */
    @Override
    @Transactional(readOnly = true)
    public boolean villageMode(UUID ownerId) {
        return ownerSettingsRepository.findByOwnerId(ownerId).map(s -> Boolean.TRUE.equals(s.getVillageModeEnabled())).orElse(false);
    }

    /**
     * Two phones each send only the key they changed, so merge instead of replacing the map
     * (replacing lost the other phone's change). A null value removes the key.
     */
    static java.util.Map<String, Object> mergeFlags(java.util.Map<String, Object> current, java.util.Map<String, Object> changes) {
        java.util.Map<String, Object> merged = current == null ? new java.util.HashMap<>() : new java.util.HashMap<>(current);
        changes.forEach((k, v) -> {
            if (v == null) merged.remove(k);
            else merged.put(k, v);
        });
        return merged;
    }

    private OwnerSettingsResponse toResponse(OwnerSettings settings) {
        OwnerSettingsResponse response = new OwnerSettingsResponse();
        response.setId(settings.getId());
        response.setIsDarkModeEnabled(settings.getIsDarkModeEnabled());
        response.setVillageModeEnabled(Boolean.TRUE.equals(settings.getVillageModeEnabled()));
        response.setLocationToolsEnabled(!Boolean.FALSE.equals(settings.getLocationToolsEnabled()));
        response.setOtherFlags(settings.getOtherFlags());
        response.setCreatedAt(settings.getCreatedAt());
        response.setUpdatedAt(settings.getUpdatedAt());
        return response;
    }

    private void validateOwnerAccess(UUID ownerId) {
        UUID currentOwnerId = SecurityUtil.getCurrentUserId();
        if (ownerId == null || !ownerId.equals(currentOwnerId)) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "You can only access your own settings");
        }
    }
}

