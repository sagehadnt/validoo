package com.sagehadnt.validoo

import kotlin.reflect.KProperty

@DslMarker
annotation class ValidooDSL

/**
 * Begin validation on the receiver. This function will perform all the checks specified in the lambda, returning
 * a success object if all pass, or a failure object containing all errors otherwise.
 */
@ValidooDSL
fun <T> T.validate(op: ValidationBuilder<T>.() -> Unit): ValidationResult<T> = ValidationBuilder(this).apply(op).execute()

class ValidationBuilder<T>(private val value: T) {

    private val failures = mutableSetOf<FailureReason<T>>()

    /**
     * Add a check to the validation. A check consists of a string description of the check, followed by a lambda
     * to perform the check. When the validation executes, all the lambdas are run, and the string descriptions of
     * all failing checks are reported.
     *
     * As this function overrides invoke(), you can access it using syntax like in the following example:
     * ```
     * "name must be at least three characters" { name.length >= 3 }
     * ```
     */
    @ValidooDSL
    operator fun String.invoke(condition: T.() -> Boolean) {
        if (!condition(value)) {
            failures.add(FailureReason.Simple(this))
        }
    }

    /**
     * Check all the members of a collection on the object under validation. If any of the elements fail their
     * validation, the overall validation fails.
     */
    @ValidooDSL
    fun <A> validateCollection(prop: KProperty<Iterable<A>>,
                               /**
                                * The minimum allowed size of the collection.
                                */
                               minInclusive: Int? = null,
                               /**
                                * The maximum allowed size of the collection.
                                */
                               maxInclusive: Int? = null,
                               condition: ValidationBuilder<A>.() -> Unit) {
        val propName = prop.name
        val collection = prop.getter.call(value).toList()
        minInclusive?.let {
            if (collection.size < it) {
                this.failures.add(collectionTooSmallFailure(propName, it))
            }
        }
        maxInclusive?.let {
            if (collection.size > it) {
                this.failures.add(collectionTooBigFailure(propName, it))
            }
        }
        val nestedFailures = collection
            .mapNotNull {
                val result: ValidationResult<A> = it.validate(condition)
                if (result is ValidationResult.Failure<A>) {
                    FailureReason.Group.Element(it, result.reasons)
                } else null
            }.toSet()
        if (nestedFailures.isNotEmpty()) {
            this.failures.add(FailureReason.Group(propName, nestedFailures))
        }
    }

    fun execute(): ValidationResult<T> {
        return if (failures.isEmpty()) {
            ValidationResult.Success(value)
        } else {
            ValidationResult.Failure(value, failures)
        }
    }

    companion object {
        fun <T> collectionTooSmallFailure(name: String, minInclusive: Int) = FailureReason.Simple<T>("$name must have at least $minInclusive elements")
        fun <T> collectionTooBigFailure(name: String, maxInclusive: Int) = FailureReason.Simple<T>("$name must have no more than $maxInclusive elements")
    }
}

sealed interface FailureReason<T> {
    data class Simple<T>(val string: String): FailureReason<T> {
        override fun toString() = string
    }
    class Group<T, U>(val groupName: String, val elements: Set<Element<U>>): FailureReason<T> {
        class Element<T>(val member: T, val reasons: Set<FailureReason<T>>)
    }
}

sealed interface ValidationResult<T> {

    operator fun invoke(): T
    val reasons: Set<FailureReason<T>>
    fun orNull(): T?
    fun wasSuccessful(): Boolean

    class Success<T>(val result: T) : ValidationResult<T> {
        override fun invoke(): T = result
        override val reasons = emptySet<FailureReason<T>>()
        override fun orNull(): T? = result
        override fun wasSuccessful() = true
    }

    class Failure<T>(val source: T, override val reasons: Set<FailureReason<T>>) : ValidationResult<T> {

        val errorMsg: String by lazy {
            "$source failed ${reasons.size} validation checks: " + System.lineSeparator() +
                    ReasonTreeWriter.toString(reasons)
        }

        override fun invoke(): T = error(errorMsg)
        override fun orNull(): T? = null
        override fun wasSuccessful() = false
    }
}

