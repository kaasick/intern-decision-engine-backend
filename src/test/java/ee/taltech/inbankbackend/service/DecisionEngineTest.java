package ee.taltech.inbankbackend.service;

import com.github.vladislavgoltjajev.personalcode.common.Gender;
import com.github.vladislavgoltjajev.personalcode.exception.PersonalCodeException;
import com.github.vladislavgoltjajev.personalcode.locale.estonia.EstonianPersonalCodeGenerator;
import ee.taltech.inbankbackend.config.DecisionEngineConstants;
import ee.taltech.inbankbackend.exceptions.InvalidLoanAmountException;
import ee.taltech.inbankbackend.exceptions.InvalidLoanPeriodException;
import ee.taltech.inbankbackend.exceptions.InvalidPersonalCodeException;
import ee.taltech.inbankbackend.exceptions.NoValidLoanException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DecisionEngineTest {

    @InjectMocks
    private DecisionEngine decisionEngine;

    private final EstonianPersonalCodeGenerator codeGenerator = new EstonianPersonalCodeGenerator();

    private String debtorPersonalCode;
    private String segment1PersonalCode;
    private String segment2PersonalCode;
    private String segment3PersonalCode;
    private String underageCustomerCode;    // 17 in 2025
    private String just18CustomerCode;      // 18 in 2025
    private String age75CustomerCode;      // 76 in 2025
    private String age80CustomerCode; // 75 in 2025

    @BeforeEach
    void setUp() throws PersonalCodeException {
        debtorPersonalCode = "37605030299";
        segment1PersonalCode = "50307172740";
        segment2PersonalCode = "38411266610";
        segment3PersonalCode = "35006069515";

        underageCustomerCode = codeGenerator.generatePersonalCode(
                Gender.MALE, LocalDate.of(2008, 1, 1));  // 17 on 2025-03-27

        just18CustomerCode = codeGenerator.generatePersonalCode(
                Gender.MALE, LocalDate.of(2007, 1, 27)); // 18 on 2025-03-27

        age75CustomerCode = codeGenerator.generatePersonalCode(
                Gender.MALE, LocalDate.of(1950, 1, 1));  // 75 on 2025-03-27

        age80CustomerCode = codeGenerator.generatePersonalCode(
                Gender.MALE, LocalDate.of(1945, 1, 1));  // 80 on 2025-03-27
    }



    @Test
    void testDebtorPersonalCode() {
        assertThrows(NoValidLoanException.class,
                () -> decisionEngine.calculateApprovedLoan(debtorPersonalCode, 4000L, 12));
    }

    @Test
    void testSegment1PersonalCode() throws InvalidLoanPeriodException, NoValidLoanException,
            InvalidPersonalCodeException, InvalidLoanAmountException {
        Decision decision = decisionEngine.calculateApprovedLoan(segment1PersonalCode, 4000L, 12);
        assertEquals(2000, decision.getLoanAmount());
        assertEquals(20, decision.getLoanPeriod());
    }

    @Test
    void testSegment2PersonalCode() throws InvalidLoanPeriodException, NoValidLoanException,
            InvalidPersonalCodeException, InvalidLoanAmountException {
        Decision decision = decisionEngine.calculateApprovedLoan(segment2PersonalCode, 4000L, 12);
        assertEquals(3600, decision.getLoanAmount());
        assertEquals(12, decision.getLoanPeriod());
    }

    @Test
    void testSegment3PersonalCode() throws InvalidLoanPeriodException, NoValidLoanException,
            InvalidPersonalCodeException, InvalidLoanAmountException {
        Decision decision = decisionEngine.calculateApprovedLoan(segment3PersonalCode, 4000L, 12);
        assertEquals(10000, decision.getLoanAmount());
        assertEquals(12, decision.getLoanPeriod());
    }

    @Test
    void testInvalidPersonalCode() {
        String invalidPersonalCode = "12345678901";
        assertThrows(InvalidPersonalCodeException.class,
                () -> decisionEngine.calculateApprovedLoan(invalidPersonalCode, 4000L, 12));
    }

    @Test
    void testInvalidLoanAmount() {
        Long tooLowLoanAmount = DecisionEngineConstants.MINIMUM_LOAN_AMOUNT - 1L;
        Long tooHighLoanAmount = DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT + 1L;

        assertThrows(InvalidLoanAmountException.class,
                () -> decisionEngine.calculateApprovedLoan(segment1PersonalCode, tooLowLoanAmount, 12));

        assertThrows(InvalidLoanAmountException.class,
                () -> decisionEngine.calculateApprovedLoan(segment1PersonalCode, tooHighLoanAmount, 12));
    }

    @Test
    void testInvalidLoanPeriod() {
        int tooShortLoanPeriod = DecisionEngineConstants.MINIMUM_LOAN_PERIOD - 1;
        int tooLongLoanPeriod = DecisionEngineConstants.MAXIMUM_LOAN_PERIOD + 1;

        assertThrows(InvalidLoanPeriodException.class,
                () -> decisionEngine.calculateApprovedLoan(segment1PersonalCode, 4000L, tooShortLoanPeriod));

        assertThrows(InvalidLoanPeriodException.class,
                () -> decisionEngine.calculateApprovedLoan(segment1PersonalCode, 4000L, tooLongLoanPeriod));
    }

    @Test
    void testFindSuitableLoanPeriod() throws InvalidLoanPeriodException, NoValidLoanException,
            InvalidPersonalCodeException, InvalidLoanAmountException {
        Decision decision = decisionEngine.calculateApprovedLoan(segment2PersonalCode, 2000L, 12);
        assertEquals(3600, decision.getLoanAmount());
        assertEquals(12, decision.getLoanPeriod());
    }

    @Test
    void testNoValidLoanFound() {
        assertThrows(NoValidLoanException.class,
                () -> decisionEngine.calculateApprovedLoan(debtorPersonalCode, 10000L, 48));
    }

    @Test
    void testMaximumAmountForSegment3() throws InvalidLoanAmountException, InvalidLoanPeriodException, InvalidPersonalCodeException, NoValidLoanException {
        // Credit modifier = 1000
        // For 8000€, 12 months: ((1000/8000)*12)/10 = 0.15 > 0.1
        // Max amount is 10000€ (capped by maximum loan amount)
        Decision decision = decisionEngine.calculateApprovedLoan(segment3PersonalCode, 8000L, 12);
        assertEquals(10000, decision.getLoanAmount());
        assertEquals(12, decision.getLoanPeriod());
    }

    // Age specific tests
    // Age-specific tests
    @Test
    void testUnderageCustomer() {
        // 17 years old on March 27, 2025
        assertThrows(NoValidLoanException.class,
                () -> decisionEngine.calculateApprovedLoan(underageCustomerCode, 4000L, 12));
    }

    @Test
    void testJust18Customer() throws InvalidLoanAmountException, InvalidLoanPeriodException, InvalidPersonalCodeException, NoValidLoanException {
        Decision decision = decisionEngine.calculateApprovedLoan(just18CustomerCode, 4000L, 12);
        assertTrue(decision.getLoanAmount() >= DecisionEngineConstants.MINIMUM_LOAN_AMOUNT,
                "Loan amount should be at least " + DecisionEngineConstants.MINIMUM_LOAN_AMOUNT);
        assertTrue(decision.getLoanAmount() <= DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT,
                "Loan amount should not exceed " + DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT);
        assertNull(decision.getErrorMessage(), "No error message expected for valid loan");
    }

    @Test
    void testAge75Customer() throws InvalidLoanAmountException, InvalidLoanPeriodException, InvalidPersonalCodeException, NoValidLoanException {
        Decision decision = decisionEngine.calculateApprovedLoan(age75CustomerCode, 4000L, 12);
        assertTrue(decision.getLoanAmount() >= DecisionEngineConstants.MINIMUM_LOAN_AMOUNT,
                "Loan amount should be at least " + DecisionEngineConstants.MINIMUM_LOAN_AMOUNT);
        assertTrue(decision.getLoanAmount() <= DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT,
                "Loan amount should not exceed " + DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT);
        assertNull(decision.getErrorMessage(), "No error message expected for valid loan");
    }

    @Test
    void testAge80Customer() {
        // 80 years old on March 27, 2025 (MAX_AGE = 76, so should fail)
        assertThrows(NoValidLoanException.class,
                () -> decisionEngine.calculateApprovedLoan(age80CustomerCode, 4000L, 12));
    }

}