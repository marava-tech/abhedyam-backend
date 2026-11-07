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
import org.springframework.dao.DataIntegrityViolationException;
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
        existing.setDailyQuoteEnabled(true);
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
        created.setDailyQuoteEnabled(true);
        created.setIsDarkModeEnabled(false);

        when(ownerSettingsRepository.findByOwnerId(ownerId)).thenReturn(Optional.empty());
        when(ownerSettingsRepository.save(any(OwnerSettings.class))).thenReturn(created);

        OwnerSettingsResponse response = ownerSettingsService.getOwnerSettings(ownerId);

        assertNotNull(response);
        assertEquals(ownerId, response.getId());
        verify(ownerSettingsRepository).save(any(OwnerSettings.class));
    }

    @Test
    void getOwnerSettings_ConcurrentInsertDuplicateKey_CatchesAndReReads() {
        OwnerSettings concurrentlyCreated = new OwnerSettings();
        concurrentlyCreated.setId(ownerId);
        concurrentlyCreated.setOwnerId(ownerId);
        concurrentlyCreated.setDailyQuoteEnabled(true);
        concurrentlyCreated.setIsDarkModeEnabled(false);

        // First findByOwnerId returns empty
        // save() throws DataIntegrityViolationException (another thread inserted first)
        // Second findByOwnerId returns the row created by the winning thread
        when(ownerSettingsRepository.findByOwnerId(ownerId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrentlyCreated));
        when(ownerSettingsRepository.save(any(OwnerSettings.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key owner_id"));

        OwnerSettingsResponse response = ownerSettingsService.getOwnerSettings(ownerId);

        assertNotNull(response);
        assertEquals(ownerId, response.getId());
        verify(ownerSettingsRepository, times(2)).findByOwnerId(ownerId);
        verify(ownerSettingsRepository).save(any(OwnerSettings.class));
    }
}
