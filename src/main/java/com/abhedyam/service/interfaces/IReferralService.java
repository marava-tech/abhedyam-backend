package com.abhedyam.service.interfaces;

import com.abhedyam.dto.ReferralSummaryResponse;
import com.abhedyam.model.Owner;

import java.util.UUID;

public interface IReferralService {
    ReferralSummaryResponse summary(UUID ownerId);

    void apply(UUID refereeOwnerId, String code);

    /** Called once, on the owner's first real Play payment: rewards whoever referred them. */
    void onFirstPayment(Owner payer);
}
