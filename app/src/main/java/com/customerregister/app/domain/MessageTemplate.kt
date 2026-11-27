package com.customerregister.app.domain

/** Fills WhatsApp templates. Supported variables: {name} {id} {expiry_date} {remaining_days} {phone}. */
object MessageTemplate {

    val VARIABLES = listOf("{name}", "{id}", "{expiry_date}", "{remaining_days}")

    fun render(
        template: String,
        name: String,
        id: String,
        expiryDate: String,
        remainingDays: Long,
        phone: String = "",
    ): String = template
        .replace("{name}", name)
        .replace("{id}", id)
        .replace("{expiry_date}", expiryDate)
        .replace("{remaining_days}", remainingDays.coerceAtLeast(0).toString())
        .replace("{phone}", phone)
}

/** Phone number helpers for WhatsApp click-to-chat (wa.me requires digits in international format). */
object PhoneNumbers {

    /**
     * Normalises user input to international digits without '+'.
     * "+963 944-123-456" -> "963944123456"; "00963944..." -> "963944..."; "0944123456" with
     * default country code "963" -> "963944123456".
     */
    fun normalize(input: String, defaultCountryCode: String): String {
        val trimmed = input.trim()
        val hasPlus = trimmed.startsWith("+")
        var digits = trimmed.filter { it.isDigit() }
        val cc = defaultCountryCode.filter { it.isDigit() }
        if (hasPlus) return digits
        if (digits.startsWith("00")) return digits.removePrefix("00")
        if (digits.startsWith("0") && cc.isNotEmpty()) {
            digits = cc + digits.trimStart('0')
        } else if (cc.isNotEmpty() && digits.length in 6..10 && !digits.startsWith(cc)) {
            // Local number typed without leading zero.
            digits = cc + digits
        }
        return digits
    }

    fun isValid(normalized: String): Boolean = normalized.length in 8..15

    fun display(normalized: String): String = if (normalized.isEmpty()) "" else "+$normalized"
}
