package com.example.journal.data

/** Finds #tags anywhere in an entry. Letters, digits, underscore and dash. */
object TagParser {

    private val tagRegex = Regex("""(?:^|\s)#([\p{L}\p{N}_-]{1,32})""")

    fun tagsOf(text: String): List<String> =
        tagRegex.findAll(text)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .toList()
}
