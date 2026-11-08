package com.abhedyam.dto;

/** What the join screen shows before Google sign-in: "Join <shopName> as <memberName> (<role>)". */
public record InvitePreviewResponse(String shopName, String memberName, String role, boolean valid) {}
