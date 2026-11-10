package com.abhedyam.service.interfaces;

import com.abhedyam.dto.KhataLinkResponse;
import com.abhedyam.dto.PublicKhataResponse;

import java.util.UUID;

public interface IKhataLinkService {
    /** The customer's link, made on first call. Caller must be the owner. */
    KhataLinkResponse get(UUID ownerId, UUID customerId);

    /** New token; the old link stops working. Caller must be the owner. */
    KhataLinkResponse reset(UUID ownerId, UUID customerId);

    /** No login: the page behind the link. Unknown token, deleted customer or shop → not found. */
    PublicKhataResponse view(String token);
}
