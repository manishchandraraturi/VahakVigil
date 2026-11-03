package com.manish.vahakvigil.domain.model

/**
 * Character encoding standard for SMS message transport.
 */
enum class SmsEncoding {
    /**
     * Standard GSM 03.38 7-bit character set (160 chars single, 153 chars multipart).
     */
    GSM_7BIT,

    /**
     * UCS-2 / UTF-16 encoding required when non-GSM characters are present
     * (70 chars single, 67 chars multipart).
     */
    UNICODE
}
