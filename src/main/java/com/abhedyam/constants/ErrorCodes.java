package com.abhedyam.constants;

public final class ErrorCodes {
    private ErrorCodes() {}
    
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    /** The record changed on another phone since this one loaded it (409). */
    public static final String STALE_EDIT = "STALE_EDIT";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String INVALID = "INVALID";
    public static final String MISSING = "MISSING";
    public static final String REQUIRED = "REQUIRED";
    public static final String DUPLICATE_EMAIL = "DUPLICATE_EMAIL";
    public static final String DUPLICATE_PHONE = "DUPLICATE_PHONE";
    public static final String DUPLICATE_ACCOUNT = "DUPLICATE_ACCOUNT";
    public static final String DUPLICATE_PRODUCT_CODE = "DUPLICATE_PRODUCT_CODE";
    public static final String DUPLICATE_UPI_VPA = "DUPLICATE_UPI_VPA";
    public static final String DUPLICATE_RECORD = "DUPLICATE_RECORD";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String NO_DUE_AMOUNT = "NO_DUE_AMOUNT";
    public static final String INVALID_AMOUNT = "INVALID_AMOUNT";
    public static final String MISSING_LOCATION = "MISSING_LOCATION";
    public static final String GUIDE_DATA_MISSING = "GUIDE_DATA_MISSING";
    public static final String GUIDE_LOAD_FAILED = "GUIDE_LOAD_FAILED";
    public static final String CUSTOMER_EXISTS = "CUSTOMER_EXISTS";
    public static final String INVALID_QUANTITY = "INVALID_QUANTITY";
    public static final String WALK_IN_MUST_BE_PAID = "WALK_IN_MUST_BE_PAID";
    public static final String PAYMENT_EXCEEDS_DUE = "PAYMENT_EXCEEDS_DUE";
    public static final String NEGATIVE_STOCK = "NEGATIVE_STOCK";
    public static final String ITEM_EXISTS = "ITEM_EXISTS";
    public static final String INVALID_EMAIL = "INVALID_EMAIL";
    public static final String NO_PAYMENTS_FOUND = "NO_PAYMENTS_FOUND";
}

