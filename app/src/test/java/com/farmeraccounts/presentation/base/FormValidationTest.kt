package com.farmeraccounts.presentation.base

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormValidationTest {
    @Test
    fun `amounts accept Gujarati digits but reject invalid precision overflow and zero`() {
        assertTrue(FormValidation.positiveAmount("૧૨૫.૫૦"))
        listOf("0", "-10", "1.001", "abc10", "999999999999999999999999").forEach {
            assertFalse("Accepted invalid amount: $it", FormValidation.positiveAmount(it))
        }
    }

    @Test
    fun `optional initial payment accepts omitted and explicit zero but rejects invalid input`() {
        listOf(null, "", "0", "૦.૦૦", "10.50").forEach {
            assertTrue("Rejected valid payment: $it", FormValidation.optionalPayment(it))
        }
        listOf("abc", "-1", "0.001", "999999999999999999999999").forEach {
            assertFalse("Accepted invalid payment: $it", FormValidation.optionalPayment(it))
        }
    }

    @Test
    fun `land quantity allows three decimal places and rejects smaller unsupported fractions`() {
        assertTrue(FormValidation.positiveQuantity("૨.૧૨૫"))
        assertFalse(FormValidation.positiveQuantity("2.1251"))
        assertFalse(FormValidation.positiveQuantity("0"))
    }
}
