package com.abhedyam.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.abhedyam.dto.FcmTokenRegisterRequest;
import com.abhedyam.model.FcmDetails;
import com.abhedyam.model.enums.AppLanguage;
import com.abhedyam.repository.FcmDetailsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Each phone tells the server its app language when it registers for pushes. */
@ExtendWith(MockitoExtension.class)
class FcmLanguageTest {

    @Mock
    private FcmDetailsRepository fcmDetailsRepository;

    @InjectMocks
    private FcmService fcmService;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void languageIsSentAndReadAsItsCode() throws Exception {
        var request = json.readValue("{\"token\":\"t\",\"packageName\":\"p\",\"language\":\"te\"}", FcmTokenRegisterRequest.class);
        assertEquals(AppLanguage.TE, request.getLanguage());
        assertEquals("\"te\"", json.writeValueAsString(AppLanguage.TE));
        assertEquals(AppLanguage.HI,
                json.readValue("{\"token\":\"t\",\"packageName\":\"p\",\"language\":\"hi\"}", FcmTokenRegisterRequest.class).getLanguage());
        assertNull(json.readValue("{\"token\":\"t\",\"packageName\":\"p\"}", FcmTokenRegisterRequest.class).getLanguage());
    }

    @Test
    void unknownLanguageIsRejected() {
        assertThrows(InvalidFormatException.class,
                () -> json.readValue("{\"token\":\"t\",\"packageName\":\"p\",\"language\":\"xx\"}", FcmTokenRegisterRequest.class));
    }

    @Test
    void newTokenStoresTheLanguage() {
        when(fcmDetailsRepository.findByUserIdAndTokenAndPackageName(any(), any(), any())).thenReturn(Optional.empty());
        when(fcmDetailsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FcmDetails saved = fcmService.registerToken(UUID.randomUUID(), "t", "p", "d", "android", AppLanguage.TE);

        assertEquals(AppLanguage.TE, saved.getLanguage());
    }

    @Test
    void reRegisteringAfterAChangeUpdatesIt_andAnOlderAppKeepsWhatWasThere() {
        FcmDetails existing = new FcmDetails();
        existing.setLanguage(AppLanguage.TE);
        when(fcmDetailsRepository.findByUserIdAndTokenAndPackageName(any(), any(), any())).thenReturn(Optional.of(existing));
        when(fcmDetailsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(AppLanguage.TE, fcmService.registerToken(UUID.randomUUID(), "t", "p", null, null, null).getLanguage());
        assertEquals(AppLanguage.EN, fcmService.registerToken(UUID.randomUUID(), "t", "p", null, null, AppLanguage.EN).getLanguage());
    }
}
