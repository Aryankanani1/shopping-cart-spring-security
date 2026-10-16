package com.aryan.spring_security_demo.payment;

/** The provider's answer to a charge: approved or declined. */
public sealed interface ChargeResult {

    /** The money was taken. {@code providerReference} is the provider's id for the charge. */
    record Approved(String providerReference, String cardBrand, String cardLast4) implements ChargeResult {
    }

    /** Nothing was taken; {@code reason} can be shown to the customer. */
    record Declined(String reason) implements ChargeResult {
    }
}
