## Ticket (Task) 101

### Pros

1. Clean structure
   - Control and service layered
   - Models for DT
   - Constants class for configuration
   - Custom exceptions for different erros

2. Input validation
    - ID validation using third-party library
    - Loan amount against constraints
    - Loan period against constraints

3. Comprehensive error handling
    - Custom exceptions
    - Appropriate HTTP status codes
   
4. Good testing coverage
    - Unit tests for the engine
    - Integration tests for the controller
   
5. Documentation
    - Code has comprehensive comments about functionality and expected behaviour


### Cons
#### Errors against the requirements
1. The credit score calculation formula is wrong
The required version states
   credit score = ((credit modifier / loan amount) * loan period) / 10
Yet the implemented version is just credit modifier * loan period
2. Does not check, if the credit score is >= 0.1
Fails to check the credit score against the barrier for approving loans at all.
3. Does not check varying loan amounts well
It does not search for the maximum value by testing different loan amounts nor does it decrease the loan period, only increase
4. Maximum loan period constant is set to 60, instead of the required 48

#### Structural errors
1. Uses doubles for small precise calculations, which can be error prone, will switch to Big Decimal
The DecisionEngine is a bit inflexible, because if to wonder about adding something new to it, that would be quite a challenge, and might require changes in many places. Basically meaning that the engine is handling too many concerns

### Fixes
1. Switched doubles to Big Decimal
2. Fixed the credit score calculation
3. Implemented a check, if the loan will be approved at all
4. Implemented a method to check for maximum loan amount
5. Implemented a method to better check valid loan periods
6. Fixed the maximum loan constant
7. Updated the CalculateApprovedLoan method
8. Updated testing to ensure the method works as intended

### Areas for improvement
1. Some refactoring of the Decision Engine, because it is handling too much right now and is inflexible to new additions.
2. Double is not the best structure to use for small precise calculations, should refactor the BigDecimal

### Just a thought
I am unsure about the validity of get Credit modifier, but for this example task, I will leave it as is.