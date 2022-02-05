package com.sagehadnt.validoo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ValidooTest {

    @Test
    fun `validation success`() {
        val employee = Employee("Alice", 40)
        val validatedEmployee = performTestValidation(employee)()
        assertThat(validatedEmployee).isEqualTo(employee)
    }

    @Test
    fun `validation failure`() {
        val employee = Employee("Bob", 5)
        val result = performTestValidation(employee)
        assertThat(result).isInstanceOf(ValidationResult.Failure::class.java)
        assertThat(result.reasons).containsExactly(EMPLOYEE_AGE_REQUIREMENT.asReason())
        assertThrows<Exception> { result() }
    }

    @Test
    fun `multiple validation failures`() {
        val employee = Employee("", 5)
        val result = performTestValidation(employee)
        assertThat(result).isInstanceOf(ValidationResult.Failure::class.java)
        assertThat(result.reasons).containsExactly(EMPLOYEE_AGE_REQUIREMENT.asReason(), EMPLOYEE_NAME_REQUIREMENT.asReason())
        assertThrows<Exception> { result() }
    }

    @Test
    fun `validateCollection should fail if smaller than min size`() {
        val result = Company("", emptySet()).validate {
            validateCollection(Company::employees, minInclusive = 1) { /* no conditions */ }
        }
        assertThat(result).isInstanceOf(ValidationResult.Failure::class.java)
        assertThat(result.reasons).containsExactly(ValidationBuilder.collectionTooSmallFailure("employees", 1))
    }

    @Test
    fun `validateCollection should fail if larger than max size`() {
        val result = Company("", setOf(
            Employee("Alice", 40),
            Employee("Bob", 30)
        )).validate {
            validateCollection(Company::employees, maxInclusive = 1) { /* no conditions */ }
        }
        assertThat(result).isInstanceOf(ValidationResult.Failure::class.java)
        assertThat(result.reasons).containsExactly(ValidationBuilder.collectionTooBigFailure("employees", 1))
    }

    @Test
    fun `nested validation failure`() {
        val validEmployee = Employee("Alice", 40)
        val invalidEmployee = Employee("Bob", 5)
        val company = Company("bigco", setOf(validEmployee, invalidEmployee))
        val result = performTestValidation(company)
        assertThat(result).isInstanceOf(ValidationResult.Failure::class.java)
        assertThat(result.reasons).hasSize(1)
        val reason = result.reasons.first()
        assertThat(reason).isInstanceOf(FailureReason.Group::class.java)
        @Suppress("UNCHECKED_CAST") val reasonGroup = reason as FailureReason.Group<Company, Employee>
        assertThat(reasonGroup.groupName).isEqualTo("employees")
        assertThat(reasonGroup.elements).hasSize(1)
        val nestedReason = reasonGroup.elements.first()
        assertThat(nestedReason.member).isEqualTo(invalidEmployee)
        assertThat(nestedReason.reasons).containsExactly(EMPLOYEE_AGE_REQUIREMENT.asReason())
        assertThrows<Exception> { result() }
    }
}

private fun <T> String.asReason() = FailureReason.Simple<T>(this)

private const val COMPANY_HAS_EMPLOYEE_REQUIREMENT = "should have at least one employee"
private const val COMPANY_NAME_REQUIREMENT = "name must be at least 1 character"

fun performTestValidation(company: Company): ValidationResult<Company> = company.validate {
    COMPANY_HAS_EMPLOYEE_REQUIREMENT { employees.isNotEmpty() }
    COMPANY_NAME_REQUIREMENT { name.isNotEmpty() }
    validateCollection(Company::employees) {
        EMPLOYEE_AGE_REQUIREMENT { age >= 18 }
        EMPLOYEE_NAME_REQUIREMENT { name.isNotEmpty() }
    }
}

private const val EMPLOYEE_AGE_REQUIREMENT = "should be 18 or older"
private const val EMPLOYEE_NAME_REQUIREMENT = "name must be at least 1 character"

fun performTestValidation(employee: Employee): ValidationResult<Employee> = employee.validate {
    EMPLOYEE_AGE_REQUIREMENT { age >= 18 }
    EMPLOYEE_NAME_REQUIREMENT { name.isNotEmpty() }
}

class Company(val name: String, val employees: Set<Employee>) {
    override fun toString() = name
}
class Employee(val name: String, val age: Int) {
    override fun toString(): String = name
}