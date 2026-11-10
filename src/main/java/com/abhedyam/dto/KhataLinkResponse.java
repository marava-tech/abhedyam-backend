package com.abhedyam.dto;

/** The customer's private link, for the owner to put in WhatsApp messages. */
public record KhataLinkResponse(String token, String url) {
}
