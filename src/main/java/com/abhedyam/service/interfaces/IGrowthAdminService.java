package com.abhedyam.service.interfaces;

import com.abhedyam.dto.CreatePartnerRequest;
import com.abhedyam.dto.PartnerRow;
import com.abhedyam.dto.TrialCallRow;

import java.util.List;
import java.util.UUID;

/** Founder tools in the admin dashboard: who to call during the trial, and partners who bring shops. */
public interface IGrowthAdminService {
    List<TrialCallRow> trialCalls();

    void logCall(UUID ownerId, String stage, String note);

    List<PartnerRow> partners();

    PartnerRow createPartner(CreatePartnerRequest request);

    /** Marks every unpaid earning of this partner as paid now; returns the ₹ total. */
    long payOut(UUID partnerId);
}
