package com.abhedyam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.abhedyam.repository.LocationDetailsRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** "kondapur " from an Excel file joins the shop's "Kondapur" instead of starting a second village. */
@ExtendWith(MockitoExtension.class)
class VillageSpellingTest {

    @Mock LocationDetailsRepository locationDetailsRepository;
    @InjectMocks CustomerService customerService;

    UUID owner = UUID.randomUUID();

    @Test
    void theShopsSpellingWins() {
        when(locationDetailsRepository.findVillageSpellings(owner, "kondapur", null)).thenReturn(List.of("Kondapur"));
        assertThat(customerService.shopVillage(owner, "  kondapur ", null)).isEqualTo("Kondapur");
    }

    @Test
    void aNewVillageIsKeptAsTyped_SpacesTidied() {
        when(locationDetailsRepository.findVillageSpellings(eq(owner), any(), any())).thenReturn(List.of());
        assertThat(customerService.shopVillage(owner, " New   Town ", null)).isEqualTo("New Town");
    }

    @Test
    void blankIsNoVillage() {
        assertThat(customerService.shopVillage(owner, "   ", null)).isNull();
        assertThat(customerService.shopVillage(owner, null, null)).isNull();
    }
}
