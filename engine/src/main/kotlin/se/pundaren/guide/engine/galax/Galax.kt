package se.pundaren.guide.engine.galax

/**
 * Galaxens begrepp (docs/SAMLAD-PROJEKTKONTEXT.md, avsnitt 3 och 15). En del av Illogical Engine,
 * ingen egen motor: ren Kotlin, ingen I/O, och slumpen är alltid seedad av anroparen.
 *
 * Det här lagret rör aldrig rutt, position eller besked. Det svarar på det användaren själv skriver
 * eller trycker fram, hemma i soffan lika gärna som på väg.
 */

/** Mekanismbanken: vilket delmål som vinns medan helheten förlorar. */
enum class Grip(val title: String) {
    SUBOPTIMIZATION("Suboptimering"),
    PROMISE_AS_DONE("Löftet räknas som utfört"),
    EXCUSE_OVERRIDE("Förklaringen ersätter förändringen"),
    SHAME_ALLERGY("Skamallergi"),
    EMOTIONAL_PROOF("Känslan som bevis"),
    COST_SHIFTING("Kostnaden flyttas"),
    MEMORY_REVISION("Minnet reviderar historiken"),
    VICTIM_SHIELD("Offerkoftan"),
    RIKARD_PROTOCOL("Rikard-protokollet"),
    REPEAT_AS_PROGRESS("Upprepning som framsteg"),
}

/** Samma systemfel i olika världar. */
enum class Scale(val title: String) {
    EVERYDAY("vardag"),
    CONVERSATION("samtal"),
    SOCIETY("samhälle"),
    SELF("Guiden själv"),
}

/** Varifrån texten kommer. Nyskriven satir får aldrig se ut som något någon har sagt. */
enum class Source(val label: String) {
    /** Ur ägarens eget underlag (kanon, samlad projektkontext, guide-dokumentet). */
    OWNER("UR ÄGARENS UNDERLAG"),
    /** Nyskriven för appen. */
    GENERATED("NYSKRIVEN"),
    /** Insamlat uttryck med okänt upphov. */
    COLLECTED("INSAMLAT"),
}

/** Vad användarens text bedöms göra. Bedömningen gäller texten, aldrig människan. */
enum class PanicKind {
    /** Tecken på verklig fara. Ingen satir. */
    SERIOUS,
    /** Knappen utan text: dagens galaktiska beslut. */
    DAILY,
    /** Någon har råkat erkänna ett fel. */
    TOTAL_PANIC,
    /** Positiv emotionell logik: det känns bra, alltså går det bra. */
    POSITIVE,
    /** Negativ emotionell logik: det känns illa, alltså är det bevisat. */
    NEGATIVE,
    PROMISE,
    BLAME,
    /** Tonfallet, tjatet eller obehaget har blivit huvudfråga. */
    TONE,
    MEMORY,
    REPEAT,
    OTHER,
}

/**
 * Ett galaktiskt beslut. Raderna står i ordning från iakttagelse till kosmisk urspårning, så att
 * absurditetsgraden kan avgöra hur långt beslutet får gå.
 */
data class Decree(
    val id: String,
    val authority: String,
    val grip: Grip,
    val scale: Scale,
    val headline: String,
    val lines: List<String>,
    val kinds: Set<PanicKind>,
    val source: Source,
    /** Saklig rad om vad som faktiskt går att fastställa. Visas i sanningsfärgen. */
    val anchor: String? = null,
    /** Om listan inte är tom gäller beslutet bara text som innehåller något av orden. */
    val needs: List<String> = emptyList(),
)
