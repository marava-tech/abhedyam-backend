package com.abhedyam.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReferralEvaluationJob {

    private final ReferralService referralService;

    @Scheduled(cron = "0 30 1,13 * * ?") // 1:30 AM & 1:30 PM daily
    public void runReferralEvaluation() {
        log.info("Starting scheduled referral genuinity evaluation job...");
        try {
            int evaluated = referralService.evaluateAllPendingReferrals();
            log.info("Referral evaluation complete. Evaluated {} pending referrals.", evaluated);
        } catch (Exception e) {
            log.error("Error during scheduled referral evaluation", e);
        }
    }
}
