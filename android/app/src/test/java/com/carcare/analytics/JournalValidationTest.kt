package com.carcare.analytics

import com.carcare.analytics.feature.logbook.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class JournalValidationTest {
    private val today=LocalDate.of(2026,11,11)
    private fun fuel()=mapOf("date" to today.toString(),"odometerKm" to "123000","liters" to "25,125","totalCostPln" to "150,25","isFullTank" to "true")
    @Test fun acceptsValidFuelAndSendsExactDecimalStrings() {
        assertTrue(validateOperation(EntryKind.REFUELING,fuel(),today).isEmpty())
        val body=operationPayload(EntryKind.REFUELING,fuel())
        assertEquals("25.125",body["liters"]); assertEquals("150.25",body["totalCostPln"])
        assertEquals(123000,body["odometerKm"]); assertEquals(true,body["isFullTank"])
    }
    @Test fun rejectsZeroOrOverPreciseFuelValues() {
        for(value in listOf("0","-1","1.1234","10000000")) assertTrue(validateOperation(EntryKind.REFUELING,fuel()+("liters" to value),today).containsKey("liters"))
        for(value in listOf("0","-1","1.001","10000000000")) assertTrue(validateOperation(EntryKind.REFUELING,fuel()+("totalCostPln" to value),today).containsKey("totalCostPln"))
    }
    @Test fun refuelingPriceAcceptsCommasAndUsesServerRounding() {
        assertEquals("6.00",refuelingPricePerLiter("35,000","210,00")?.toPlainString())
        assertEquals("1.01",refuelingPricePerLiter("200","201")?.toPlainString())
        assertNull(refuelingPricePerLiter("0","210,00"))
        assertNull(refuelingPricePerLiter("35,0001","210,00"))
        assertNull(refuelingPricePerLiter("35","-210"))
    }
    @Test fun rejectsFutureOperationAndFractionalMileage() {
        val errors=validateOperation(EntryKind.REFUELING,fuel()+mapOf("date" to "2026-11-12","odometerKm" to "123000.5"),today)
        assertTrue(errors.containsKey("date")); assertTrue(errors.containsKey("odometerKm"))
    }
    @Test fun serviceAllowsNoCostOrZeroButChecksNextDueRules() {
        val values=mapOf("date" to "2026-11-11","serviceType" to "REPAIR","description" to "Тормоза","odometerKm" to "123000","costPln" to "0")
        assertTrue(validateOperation(EntryKind.SERVICE,values,today).isEmpty())
        val bad=validateOperation(EntryKind.SERVICE,values+mapOf("nextDueDate" to "2026-11-10","nextDueOdometerKm" to "122999"),today)
        assertTrue(bad.containsKey("nextDueDate")); assertTrue(bad.containsKey("nextDueOdometerKm"))
    }
    @Test fun manualFuelCategoryIsUnavailableAndInvalid() {
        assertFalse(fieldsFor(EntryKind.EXPENSE).first { it.key=="category" }.options.any { it.first=="FUEL" })
        val values=mapOf("date" to today.toString(),"category" to "FUEL","amountPln" to "10","title" to "x")
        assertTrue(validateOperation(EntryKind.EXPENSE,values,today).containsKey("category"))
    }
    @Test fun patchPayloadContainsExplicitNullForClearedFields() {
        val body=operationPayload(EntryKind.SERVICE,mapOf("date" to today.toString(),"serviceType" to "REPAIR","description" to "Работы","odometerKm" to "","costPln" to ""))
        assertTrue(body.containsKey("costPln")); assertNull(body["costPln"]); assertNull(body["odometerKm"])
    }
    @Test fun expiredDocumentIsValidHistoryButInvalidDateIsRejected() {
        val values=mapOf("type" to "INSURANCE","validUntil" to "2025-01-01")
        assertTrue(validateOperation(EntryKind.DOCUMENT,values,today).isEmpty())
        assertTrue(validateOperation(EntryKind.DOCUMENT,values+("validUntil" to "2026-02-30"),today).containsKey("validUntil"))
    }
    @Test fun filtersValidateCalendarDatesAndRange() {
        assertTrue(validateFilters(JournalFilters(dateFrom="2026-01-01",dateTo="2026-01-01")).isEmpty())
        assertTrue(validateFilters(JournalFilters(dateFrom="2026-02-30")).containsKey("dateFrom"))
        assertTrue(validateFilters(JournalFilters(dateFrom="2026-02-01",dateTo="2026-01-31")).containsKey("dateTo"))
    }
}
