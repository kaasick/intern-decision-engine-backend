package ee.taltech.inbankbackend.service;

import com.github.vladislavgoltjajev.personalcode.exception.PersonalCodeException;
import com.github.vladislavgoltjajev.personalcode.locale.estonia.EstonianPersonalCodeValidator;
import ee.taltech.inbankbackend.config.DecisionEngineConstants;
import ee.taltech.inbankbackend.exceptions.InvalidLoanAmountException;
import ee.taltech.inbankbackend.exceptions.InvalidLoanPeriodException;
import ee.taltech.inbankbackend.exceptions.InvalidPersonalCodeException;
import ee.taltech.inbankbackend.exceptions.NoValidLoanException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Period;

/**
 * A service class that provides a method for calculating an approved loan amount and period for a customer.
 * The loan amount is calculated based on the customer's credit modifier,
 * which is determined by the last four digits of their ID code.
 */
@Service
public class DecisionEngine {

    // Used to check for the validity of the presented ID code.
    private final EstonianPersonalCodeValidator validator = new EstonianPersonalCodeValidator();
    private static final BigDecimal CREDIT_SCORE_THRESHOLD = new BigDecimal("0.1");
    private static final BigDecimal TEN = new BigDecimal("10");

    private static final int MIN_AGE = 18;
    private static final int LIFE_EXPECTANCY = 80;
    private static final int MAX_AGE = LIFE_EXPECTANCY - (DecisionEngineConstants.MAXIMUM_LOAN_PERIOD / 12);


    /**
     * Calculates the maximum loan amount and period for the customer based on their ID code,
     * the requested loan amount and the loan period.
     * The loan period must be between 12 and 60 months (inclusive).
     * The loan amount must be between 2000 and 10000€ months (inclusive).
     *
     * @param personalCode ID code of the customer that made the request.
     * @param loanAmount   Requested loan amount
     * @param loanPeriod   Requested loan period
     * @return A Decision object containing the approved loan amount and period, and an error message (if any)
     * @throws InvalidPersonalCodeException If the provided personal ID code is invalid
     * @throws InvalidLoanAmountException   If the requested loan amount is invalid
     * @throws InvalidLoanPeriodException   If the requested loan period is invalid
     * @throws NoValidLoanException         If there is no valid loan found for the given ID code, loan amount and loan period
     */
    public Decision calculateApprovedLoan(String personalCode, Long loanAmount, int loanPeriod)
            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException,
            NoValidLoanException {

        try {
            verifyInputs(personalCode, loanAmount, loanPeriod);
            validateAge(personalCode); // Simplified age check
        } catch (Exception e) {
            return new Decision(null, null, e.getMessage());
        }

        int creditModifier = getCreditModifier(personalCode);
        if (creditModifier == 0) {
            throw new NoValidLoanException("No valid loan found due to credit segment!");
        }

        int maxValidAmount = findMaximumValidLoanAmount(creditModifier, loanPeriod);
        if (maxValidAmount >= DecisionEngineConstants.MINIMUM_LOAN_AMOUNT) {
            return new Decision(maxValidAmount, loanPeriod, null);
        }

        int adjustedPeriod = findSuitablePeriod(creditModifier);
        if (adjustedPeriod != -1) {
            maxValidAmount = findMaximumValidLoanAmount(creditModifier, adjustedPeriod);
            return new Decision(maxValidAmount, adjustedPeriod,
                    "Adjusted period from " + loanPeriod + " to " + adjustedPeriod + " months for eligibility");
        }

        throw new NoValidLoanException("No valid loan found for any period!");
    }

    private void validateAge(String personalCode)
            throws InvalidPersonalCodeException, NoValidLoanException {
        try {
            Period agePeriod = AgeValidator.getAge(personalCode);
            int ageInYears = agePeriod.getYears();

            if (ageInYears < MIN_AGE) {
                throw new NoValidLoanException("Customer is too young for a loan (minimum age: " + MIN_AGE + ")");
            }
            if (ageInYears > MAX_AGE) {
                throw new NoValidLoanException("Customer exceeds maximum age limit (" + MAX_AGE + ")");
            }
        } catch (PersonalCodeException e) {
            throw new InvalidPersonalCodeException("Error processing personal code: " + e.getMessage());
        }
    }

    /**
     * Primitive scoring algorithm using BigDecimal for precise calculation
     *
     * @param creditModifier - Requester's credit Modifier
     * @param loanAmount     - Requested loan amount
     * @param loanPeriod     - Requested loan period
     * @return Credit score as BigDecimal
     */
    private BigDecimal calculateCreditScore(int creditModifier, int loanAmount, int loanPeriod) {
        return new BigDecimal(creditModifier)
                .divide(new BigDecimal(loanAmount), 10, RoundingMode.HALF_UP)
                .multiply(new BigDecimal(loanPeriod))
                .divide(TEN, 10, RoundingMode.HALF_UP);
    }

    /**
     * Determines if a loan should be approved based on the credit score.
     * A loan is approved if the credit score is greater than or equal to 0.1.
     *
     * @param creditModifier - Requester's credit Modifier
     * @param loanAmount     - Requested loan amount
     * @param loanPeriod     - Requested loan period
     * @return Whether the requester can get a loan or not, based on the credit score calculation
     */
    private boolean isLoanApproved(int creditModifier, int loanAmount, int loanPeriod) {
        BigDecimal creditScore = calculateCreditScore(creditModifier, loanAmount, loanPeriod);
        return creditScore.compareTo(CREDIT_SCORE_THRESHOLD) >= 0;
    }

    /**
     * Finds the maximum loan amount that can be approved for a given credit modifier and loan period.
     * Uses binary search (O(log n)).
     *
     * @param creditModifier The customer's credit modifier based on their segment
     * @param loanPeriod     The loan period in months
     * @return The maximum loan amount that can be approved, or 0 if no valid amount is found
     */
    private int findMaximumValidLoanAmount(int creditModifier, int loanPeriod) {
        int left = DecisionEngineConstants.MINIMUM_LOAN_AMOUNT;
        int right = DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT;
        int maxValidAmount = 0;

        while (left <= right) {
            int mid = left + (right - left) / 2;

            if (isLoanApproved(creditModifier, mid, loanPeriod)) {
                maxValidAmount = mid;
                left = mid + 1; // Try larger amounts
            } else {
                right = mid - 1; // Try smaller amounts
            }
        }

        return maxValidAmount;
    }

    /**
     * Attempts to find a suitable loan period when the requested period doesn't yield
     * a valid loan amount. Tries periods from minimum to maximum.
     *
     * @param creditModifier The customer's credit modifier based on their segment
     * @return A suitable loan period if found, or -1 if no suitable period exists
     */
    private int findSuitablePeriod(int creditModifier) {
        // Try each loan period from minimum to maximum
        for (int period = DecisionEngineConstants.MINIMUM_LOAN_PERIOD;
             period <= DecisionEngineConstants.MAXIMUM_LOAN_PERIOD;
             period++) {

            int maxAmount = findMaximumValidLoanAmount(creditModifier, period);
            if (maxAmount >= DecisionEngineConstants.MINIMUM_LOAN_AMOUNT) {
                return period;
            }
        }
        return -1; // No suitable period found
    }

    /**
     * Calculates the credit modifier of the customer to according to the last four digits of their ID code.
     * Debt - 0000...2499
     * Segment 1 - 2500...4999
     * Segment 2 - 5000...7499
     * Segment 3 - 7500...9999
     *
     * @param personalCode ID code of the customer that made the request.
     * @return Segment to which the customer belongs.
     */
    private int getCreditModifier(String personalCode) {
        int segment = Integer.parseInt(personalCode.substring(personalCode.length() - 4));

        if (segment < 2500) {
            return 0;
        } else if (segment < 5000) {
            return DecisionEngineConstants.SEGMENT_1_CREDIT_MODIFIER;
        } else if (segment < 7500) {
            return DecisionEngineConstants.SEGMENT_2_CREDIT_MODIFIER;
        }

        return DecisionEngineConstants.SEGMENT_3_CREDIT_MODIFIER;
    }

    /**
     * Verify that all inputs are valid according to business rules.
     * If inputs are invalid, then throws corresponding exceptions.
     *
     * @param personalCode Provided personal ID code
     * @param loanAmount   Requested loan amount
     * @param loanPeriod   Requested loan period
     * @throws InvalidPersonalCodeException If the provided personal ID code is invalid
     * @throws InvalidLoanAmountException   If the requested loan amount is invalid
     * @throws InvalidLoanPeriodException   If the requested loan period is invalid
     */
    private void verifyInputs(String personalCode, Long loanAmount, int loanPeriod)
            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException {

        if (!validator.isValid(personalCode)) {
            throw new InvalidPersonalCodeException("Invalid personal ID code!");
        }
        if (!(DecisionEngineConstants.MINIMUM_LOAN_AMOUNT <= loanAmount)
                || !(loanAmount <= DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT)) {
            throw new InvalidLoanAmountException("Invalid loan amount!");
        }
        if (!(DecisionEngineConstants.MINIMUM_LOAN_PERIOD <= loanPeriod)
                || !(loanPeriod <= DecisionEngineConstants.MAXIMUM_LOAN_PERIOD)) {
            throw new InvalidLoanPeriodException("Invalid loan period!");
        }
    }
}