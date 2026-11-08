package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.abhedyam.dto.OwnerSettingsResponse;
import com.abhedyam.model.OwnerSettings;
import com.abhedyam.repository.OwnerSettingsRepository;
import com.abhedyam.security.UserPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class OwnerSettingsServiceTest {

    @Mock
    private OwnerSettingsRepository ownerSettingsRepository;

    @InjectMocks
    private OwnerSettingsService ownerSettingsService;

    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(ownerId, null), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getOwnerSettings_ReturnsExistingSettings() {
        OwnerSettings existing = new OwnerSettings();
        existing.setId(ownerId);
        existing.setOwnerId(ownerId);
        existing.setIsDarkModeEnabled(false);

        when(ownerSettingsRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(existing));

        OwnerSettingsResponse response = ownerSettingsService.getOwnerSettings(ownerId);

        assertNotNull(response);
        assertEquals(ownerId, response.getId());
        verify(ownerSettingsRepository, never()).save(any());
    }

    @Test
    void getOwnerSettings_CreatesSettingsWhenMissing() {
        OwnerSettings created = new OwnerSettings();
        created.setId(ownerId);
        created.setOwnerId(ownerId);
        created.setIsDarkModeEnabled(false);

        // first read empty, insert-ignore (also safe when a parallel request won), second read finds the row
        when(ownerSettingsRepository.findByOwnerId(ownerId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(created));

        OwnerSettingsResponse response = ownerSettingsService.getOwnerSettings(ownerId);

        assertEquals(ownerId, response.getId());
        verify(ownerSettingsRepository).insertDefaultsIfAbsent(ownerId.toString());
        verify(ownerSettingsRepository, never()).save(any());
    }

    @Test
    void mergeFlags_KeepsOtherPhonesKeysAndNullRemoves() {
        java.util.Map<String, Object> current = new java.util.HashMap<>(java.util.Map.of("hideAmounts", true, "homeTools", List.of("a")));
        java.util.Map<String, Object> changes = new java.util.HashMap<>();
        changes.put("customerSms", false);
        changes.put("homeTools", null);

        java.util.Map<String, Object> merged = OwnerSettingsService.mergeFlags(current, changes);

        assertEquals(true, merged.get("hideAmounts"));
        assertEquals(false, merged.get("customerSms"));
        assertFalse(merged.containsKey("homeTools"));
        assertEquals(1, OwnerSettingsService.mergeFlags(null, java.util.Map.of("x", 1)).size());
    }
}
