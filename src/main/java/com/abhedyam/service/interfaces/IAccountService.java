package com.abhedyam.service.interfaces;

import java.util.UUID;

public interface IAccountService {
    /** Permanently deletes the owner and everything scoped to them. */
    void deleteAccount(UUID ownerId);

    /** CSV text for type = customers | sales | payments. */
    String exportCsv(UUID ownerId, String type);
}
