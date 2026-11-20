package se.pundaren.guide.engine

/**
 * Motorns egna textspärrar (röstreglerna i 12). SafetyGate har en separat, oberoende
 * kontroll av riktningsord (06, R-03); de delar medvetet ingen kod.
 */
object VoiceGuards {
    /** RR-05: spärrlistan, inklusive böjningar och sammansättningar (förslaget i RR-05, strängare än minimum). */
    private val directionStems = listOf(
        Regex("rakt\\s+fram"),
        Regex("vänster"),
        Regex("höger"), // "högre" innehåller inte "höger"
        Regex("u[-–−]?\\s?sväng"),
        Regex("sväng"),
        Regex("avfart"),
        Regex("rondell"),
    )

    /** RR-10 och B-12: lånade namn, samma lista som F-11-sökningen. */
    val borrowedNames: List<Regex> = listOf(
        Regex("vogon"), Regex("marvin"), Regex("milliways"), Regex("heart of gold"),
        Regex("don'?t panic"), Regex("improbabilit"), Regex("\\b42\\b"), Regex("handduk"),
    )

    /** RR-06: skyll aldrig på användaren. Förslag till mönster, inte fryst. */
    private val blameUser = listOf(
        Regex("\\bditt fel\\b"), Regex("\\bdu gjorde fel\\b"), Regex("\\bdu borde ha\\b"),
        Regex("\\bskyll dig själv\\b"), Regex("\\bdu har kört fel\\b"), Regex("\\bdu gick fel\\b"),
        Regex("\\bditt ansvar\\b"),
    )

    /** RR-04: mönster som kan misstas för ett besked (förslaget i 12, används här som spärr). */
    private val soundsLikeInstruction = listOf(
        Regex("är korrekt"), Regex("(avståndet|tiden|svängen|ankomsten|positionen|gps:?n?)\\s+stämmer"),
        Regex("du är inte vilse"), Regex("fortsätt enligt"), Regex("kör vidare"), Regex("du är framme"),
        Regex("\\d+\\s*(meter|m|km|kilometer|minuter|min)\\b"),
    )

    private fun fold(s: String) = s.lowercase()

    fun directionWords(text: String): List<String> = fold(text).let { t -> directionStems.filter { it.containsMatchIn(t) }.map { it.pattern } }
    fun hasDirectionWord(text: String): Boolean = directionWords(text).isNotEmpty()
    fun hasBorrowedName(text: String): Boolean = fold(text).let { t -> borrowedNames.any { it.containsMatchIn(t) } }
    fun blamesUser(text: String): Boolean = fold(text).let { t -> blameUser.any { it.containsMatchIn(t) } }
    fun soundsLikeInstruction(text: String): Boolean = fold(text).let { t -> soundsLikeInstruction.any { it.containsMatchIn(t) } }
    fun mentionsRikard(text: String): Boolean = fold(text).contains("rikard")
}
