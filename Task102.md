## Ticket-102

### Implementations
1. Added ageValidator class for basic setup
2. Added age checking into the main DecisionEngine, the methods could be layered somewhere else, for better structural clarity
3. Added testcases, but do note that they may fail because of the randomly generated ID codes, which can be put into the debt segment.

Because the frontend is already handling error messages from api calls, no modifications are required there.
