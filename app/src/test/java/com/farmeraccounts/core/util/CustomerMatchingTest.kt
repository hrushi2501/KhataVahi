package com.farmeraccounts.core.util

import com.farmeraccounts.domain.model.Customer
import org.junit.Assert.*
import org.junit.Test

class CustomerMatchingTest {
    private fun customer(id: String, name: String, phone: String? = null) = Customer(id=id,name=name,phone=phone)
    @Test fun `exact name sorts before nearby spelling and typo finds real names`() {
        val records = listOf(customer("1","Himanshu"), customer("2","Himansh"),customer("3","Ramesh"))
        assertEquals(listOf("2","1"),CustomerMatching.search(records,"Himansh").map { it.id })
        assertTrue(CustomerMatching.search(records,"Himansuh").any { it.id == "1" })
        assertFalse(CustomerMatching.search(records,"Himansuh").any { it.id == "3" })
    }
    @Test fun `Gujarati typos and mixed numeral phone searches stay offline`() {
        val records = listOf(customer("1","હિમાંશુ", "9876543210"))
        assertEquals(1,CustomerMatching.search(records,"હિમાંસુ").size)
        assertEquals(1,CustomerMatching.search(records,"૯૮૭૬૫").size)
        assertEquals(1,CustomerMatching.search(records,"+91 9876543210").size)
        assertTrue(CustomerMatching.search(records,"9876543211").isEmpty())
    }
    @Test fun `duplicate warning ignores self and detects formatted phone or normalized name`() {
        val records=listOf(customer("1","Himanshu Bhai","9876543210"))
        assertEquals(1, CustomerMatching.duplicateCandidates(records,"Someone", "+91 98765 43210",null).size)
        assertEquals(1, CustomerMatching.duplicateCandidates(records,"himanshu-bhai",null,null).size)
        assertEquals(1, CustomerMatching.duplicateCandidates(records,"Himanshubhai",null,null).size)
        assertTrue(CustomerMatching.duplicateCandidates(records,"Himanshu Bhai","9876543210","1").isEmpty())
    }
    @Test fun `unrelated and very short fuzzy queries do not suggest random people`() {
        val records=listOf(customer("1","Ramesh"),customer("2","Mahesh"))
        assertTrue(CustomerMatching.search(records,"zx").isEmpty())
        assertTrue(CustomerMatching.search(records,"Dipak").isEmpty())
        assertTrue(CustomerMatching.duplicateCandidates(records,"Dipak",null,null).isEmpty())
    }
}
