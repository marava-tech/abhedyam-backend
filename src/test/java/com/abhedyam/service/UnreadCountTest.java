package com.abhedyam.service;

import com.abhedyam.exception.BusinessException;
import com.abhedyam.repository.NotificationRepository;
import com.abhedyam.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnreadCountTest {

    private final NotificationRepository repo = mock(NotificationRepository.class);
    private final NotificationService service = new NotificationService(repo);
    private final UUID me = UUID.randomUUID();

    UnreadCountTest() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal(me, "9999999999"), null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void countsMyUnreadInTheDatabase() {
        when(repo.countUnreadByOwnerIdOrUserId(me)).thenReturn(4L);
        assertThat(service.countUnreadForUser(me)).isEqualTo(4L);
    }

    @Test
    void someoneElsesCountIsRefused() {
        assertThatThrownBy(() -> service.countUnreadForUser(UUID.randomUUID())).isInstanceOf(BusinessException.class);
    }
}
