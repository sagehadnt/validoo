# Validoo
Validoo is a simple Kotlin library for data validation.

## Quickstart
To validate an object, chain a call to `validate()` and then list the validation conditions. A condition consists of a *requirement* (a String) followed by a *condition*
(a lambda to confirm the requirement is met).

```kotlin
val company = companyRepo.get(companyId)
val product = productRepo.get(productId).validate {
    "must be accessible to company" { isAccessibleTo(company) }
    "must not have already been bought" { this !in company.purchases }
}()
```
If all requirements are satisfied, the expression returns the original object. If any requirements are not met, an error is thrown containing a tree of
all errors and nested errors.

### Technical Details
- Inside the validation DSL, we override `String.invoke()`, allowing us to pass a the condition lambda to the requirement string as a function call.
- The condition lambda takes the validation object as a receiver, so you can access its fields directly.

## Validating Collections
Often we need to validate all the members of some collection on an object. Inside a call to `validate()` on that object, we can nest a `validateCollection()` call:
```kotlin
val company = companyRepo.get(companyId).validate {
    // ... other company validation checks go here ...
    validateCollection(Company::employees, minInclusive = 1) {
        "name must contain at most 32 characters" { name.length <= 32 }
    }
    // ... other company validation checks go here ...
}()
```
If any of the collection elements fail their validation, the overall validation fails.

## Validation Results
`validate` returns a raw `ValidationResult` object, which can either be `Success` or `Failure`. There are three options to convert this into something useful:
1. `invoke` the object using `()`. For a Success, this will give you the validated value. For a Failure, this will error with a result tree containing all failed requirements.
2. Call `orNull()`, unpacking the Success into the validated value, or the Failure into a null.
3. Add a custom handler to convert the result into an error handling mechanism of your choice.
4. Call `wasSuccessful()` if you only care about the validation and don't need the underlying object or failure reasons. This will convert the result into true (Success) or false (Failure).