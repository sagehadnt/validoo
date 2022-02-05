package com.sagehadnt.validoo

import kotlin.reflect.KProperty

@DslMarker
annotation class ValidooDSL

@ValidooDSL
fun <T> T.validate(op: ValidationBuilder<T>.() -> Unit) = ValidationBuilder(this).apply(op).execute()

class ValidationBuilder<T>(private val value: T) {

    private val failures = mutableSetOf<FailureReason<T>>()

    @ValidooDSL
    operator fun String.invoke(condition: T.() -> Boolean) {
        if (!condition(value)) {
            failures.add(FailureReason.Simple(this))
        }
    }

    @ValidooDSL
    fun <A> validateCollection(prop: KProperty<Iterable<A>>, condition: A.() -> ValidationResult<A>) {
        val nestedFailures = prop.getter.call(value)
            .mapNotNull {
                val result = condition(it)
                if (result is ValidationResult.Failure<A>) {
                    FailureReason.Group.Element(it, result.reasons)
                } else null
            }.toSet()
        if (nestedFailures.isNotEmpty()) {
            this.failures.add(FailureReason.Group(prop.name, nestedFailures))
        }
    }

    fun execute(): ValidationResult<T> {
        return if (failures.isEmpty()) {
            ValidationResult.Success(value)
        } else {
            ValidationResult.Failure(value, failures)
        }
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

    class Success<T>(val result: T) : ValidationResult<T> {
        override fun invoke(): T = result
        override val reasons = emptySet<FailureReason<T>>()
    }

    class Failure<T>(val source: T, override val reasons: Set<FailureReason<T>>) : ValidationResult<T> {

        val errorMsg: String by lazy {
            "$source failed ${reasons.size} validation checks: " + System.lineSeparator() +
                    ReasonTreeWriter.toString(reasons)
        }

        override fun invoke(): T = error(errorMsg)
    }
}

