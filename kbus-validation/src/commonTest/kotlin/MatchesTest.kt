package com.jimbroze.kbus.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MatchesTest {
    private val paymentMethodIdPattern =
        Matches("pm_[A-Za-z0-9]+", failsWith = PaymentMethodIdError.NotAPaymentMethodId)

    @Test
    fun `is satisfied by a value the pattern matches in full`() {
        assertTrue(paymentMethodIdPattern.isSatisfiedBy("pm_1Abc"))
    }

    @Test
    fun `is not satisfied by a value the pattern does not match`() {
        assertFalse(paymentMethodIdPattern.isSatisfiedBy("card_1Abc"))
    }

    @Test
    fun `is not satisfied by a value with extra characters before the match`() {
        assertFalse(paymentMethodIdPattern.isSatisfiedBy("x pm_1Abc"))
    }

    @Test
    fun `is not satisfied by a value with extra characters after the match`() {
        assertFalse(paymentMethodIdPattern.isSatisfiedBy("pm_1Abc!"))
    }

    @Test
    fun `exposes the pattern it was given`() {
        assertEquals("pm_[A-Za-z0-9]+", paymentMethodIdPattern.pattern)
    }

    @Test
    fun `fails with the error it was given`() {
        assertEquals(PaymentMethodIdError.NotAPaymentMethodId, paymentMethodIdPattern.failsWith)
    }
}
