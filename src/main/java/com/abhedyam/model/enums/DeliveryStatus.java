package com.abhedyam.model.enums;

/** Push delivery state of a notification row. SENT means handed to FCM (FCM errors are logged, not retried). */
public enum DeliveryStatus {
    SENT, DEFERRED, SKIPPED, FAILED
}
