package com.sagehadnt.validoo

object ReasonTreeWriter {

    fun toString(reasons: Set<FailureReason<*>>): String = reasons
        .flatMap { toStringList(it) }
        .joinToString(System.lineSeparator())

    private fun toStringList(reason: FailureReason<*>, indentSize: Int = 0): List<String> {
        return when (reason) {
            is FailureReason.Simple -> listOf(indentOf(indentSize) + BULLET + reason.string)
            is FailureReason.Group<*, *> -> {
                val header = indentOf(indentSize) + BULLET + "${reason.elements.size} members of collection '${reason.groupName}' failed validation:"
                val elements = reason.elements.flatMapIndexed { index, element ->
                    val elementHeader = indentOf(indentSize + 1) + BULLET + "[$index] " + element.member.toString()
                    listOf(elementHeader) + element.reasons.flatMap { elementReason -> toStringList(elementReason, indentSize + 2) }
                }
                listOf(header) + elements
            }
        }
    }

    private const val BULLET = " - "
    private const val INDENT_BASE = "  "
    private fun indentOf(size: Int) = INDENT_BASE.repeat(size)
}