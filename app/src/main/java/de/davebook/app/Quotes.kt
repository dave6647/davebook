package de.davebook.app

/** Ideen für den Tagesspruch – auf Knopfdruck zufällig ausgewählt. */
object Quotes {
    private val all = listOf(
        "Du schaffst das heute – und ich bin jetzt schon stolz auf dich. ❤️",
        "Egal wie der Tag wird: Heute Abend gibt's eine extra lange Umarmung.",
        "Du bist mein Lieblingsmensch. Jeden einzelnen Tag.",
        "Kleiner Reminder: Du bist wunderbar, genau so wie du bist.",
        "Trink genug Wasser und vergiss nicht, dass ich dich liebe. 💧❤️",
        "Ich bin heute schon wieder ein bisschen mehr in dich verliebt.",
        "Für jede schwierige Aufgabe heute: Ich glaub an dich! 💪",
        "Du machst mein Leben jeden Tag ein Stückchen schöner.",
        "Lächeln nicht vergessen – es steht dir am besten. 😊",
        "Ich freu mich schon auf dich heute Abend.",
        "Mit dir ist sogar ein Montag schön.",
        "Du bist stärker, als du denkst.",
        "Atme tief durch – du hast das im Griff.",
        "Ich denk an dich. Immer ein bisschen. Meistens ganz viel.",
        "Heute ist ein guter Tag, um großartig zu sein – also wie immer bei dir.",
        "Du + ich = mein Lieblingsteam.",
        "Kaffee für den Körper, diese Nachricht fürs Herz. ☕❤️",
        "Wenn du heute zweifelst: Ich zweifle keine Sekunde an dir.",
        "Danke, dass es dich gibt.",
        "Bei dir bin ich zuhause. 🏠",
        "Gönn dir heute was Schönes – du hast es verdient.",
        "Ein Tag mit dir ist ein guter Tag.",
        "Du bist der Grund, warum ich so oft auf mein Handy grinse.",
        "Heute wird dein Tag. Ich hab's im Gefühl. ✨",
        "Ich hab dich lieb. Punkt. Keine Diskussion.",
        "Pause machen ist auch produktiv. Denk an dich! 🌸",
        "Du bist mein Glück im Alltag.",
        "Schick mir ein Herz, wenn du kurz an mich denkst. 💕",
    )

    fun random(exclude: String = ""): String {
        val pool = all.filter { it != exclude }
        return pool.random()
    }
}
