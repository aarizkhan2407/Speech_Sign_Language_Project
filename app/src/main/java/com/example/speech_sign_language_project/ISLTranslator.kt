package com.example.speech_sign_language_project

fun convertTextToISL(text: String): List<SignItem> {
    val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val sequence = mutableListOf<SignItem>()

    for (word in words) {
        val signName = when (word) {
            "hello", "hi", "namaste" -> "hello"
            "morning" -> "morning"
            "good" -> "good"
            "you" -> "you"
            else -> null
        }

        if (signName != null) {
            sequence.add(SignItem("gifs/$signName.gif", signName))
        } else {
            // Fallback: spell it out letter by letter using letters/ folder
            for (char in word) {
                if (char in 'a'..'z' || char in '0'..'9') {
                    sequence.add(SignItem("letters/$char.png", char.toString()))
                }
            }
        }
    }
    return sequence
}

fun isDevanagari(text: String): Boolean {
    return text.any { it in '\u0900'..'\u097F' }
}
