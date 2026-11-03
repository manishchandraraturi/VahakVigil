package com.manish.vahakvigil.domain.validation

/**
 * Result of phone number validation and normalization.
 */
sealed interface PhoneValidationResult {
    data class Valid(
        val normalizedNumber: String,
        val formattedDisplay: String
    ) : PhoneValidationResult

    data class Invalid(
        val reason: String
    ) : PhoneValidationResult
}

/**
 * Pure Kotlin validator and normalizer for emergency contact telephone numbers.
 *
 * Requirements:
 * - Validate format before sending OTP.
 * - Normalize and store phone numbers consistently in E.164 format.
 * - Reject obviously invalid numbers (all zeroes, repetitive digits, invalid lengths).
 */
object PhoneNumberValidator {

    private const val DEFAULT_COUNTRY_CODE = "+91"

    /**
     * Validates and normalizes the input [rawPhone].
     */
    fun validate(rawPhone: String): PhoneValidationResult {
        val trimmed = rawPhone.trim()
        if (trimmed.isBlank()) {
            return PhoneValidationResult.Invalid("Phone number cannot be blank")
        }

        // Strip common formatting characters: spaces, hyphens, parentheses, dots
        val stripped = trimmed.replace(Regex("[\\s\\-\\(\\)\\.]"), "")

        // Check for non-digit characters other than leading '+'
        val hasLeadingPlus = stripped.startsWith("+")
        val digitsOnly = if (hasLeadingPlus) stripped.substring(1) else stripped

        if (!digitsOnly.all { it.isDigit() }) {
            return PhoneValidationResult.Invalid("Phone number must contain only numbers and an optional leading '+'")
        }

        // Determine normalized E.164 number
        val normalized: String = when {
            hasLeadingPlus -> {
                // Already has international prefix
                "+$digitsOnly"
            }
            digitsOnly.length == 10 -> {
                // Standard 10-digit mobile number, default to +91
                "$DEFAULT_COUNTRY_CODE$digitsOnly"
            }
            digitsOnly.length == 11 && digitsOnly.startsWith("0") -> {
                // 11 digits starting with 0 (e.g., 09876543210), strip 0 and prepend +91
                "$DEFAULT_COUNTRY_CODE${digitsOnly.substring(1)}"
            }
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> {
                // 12 digits starting with 91, prepend '+'
                "+$digitsOnly"
            }
            else -> {
                // E.164 without leading plus
                "+$digitsOnly"
            }
        }

        val totalDigits = normalized.substring(1)

        // E.164 standard: 7 to 15 digits
        if (totalDigits.length < 7 || totalDigits.length > 15) {
            return PhoneValidationResult.Invalid("Phone number must contain 7 to 15 digits")
        }

        // Reject obviously invalid numbers (e.g. all same digits like +910000000000 or +911111111111)
        val subscriberDigits = if (totalDigits.length >= 10) totalDigits.takeLast(10) else totalDigits
        if (subscriberDigits.all { it == '0' }) {
            return PhoneValidationResult.Invalid("Phone number cannot be all zeroes.")
        }
        if (subscriberDigits.all { it == subscriberDigits[0] }) {
            return PhoneValidationResult.Invalid("Phone number cannot consist of repetitive identical digits.")
        }

        // Format for display (e.g., +91 98765 43210)
        val formatted = formatForDisplay(normalized)

        return PhoneValidationResult.Valid(
            normalizedNumber = normalized,
            formattedDisplay = formatted
        )
    }

    private fun formatForDisplay(e164: String): String {
        return if (e164.startsWith("+91") && e164.length == 13) {
            val subscriber = e164.substring(3)
            "+91 ${subscriber.take(5)} ${subscriber.substring(5)}"
        } else {
            e164
        }
    }
}
