package se.pundaren.guide.engine.galax

/** Vem som talar. Rösten är en roll, inte en teknik: appen avgör hur rollen låter. */
enum class Role(val title: String) {
    OFFICIAL("Överste Galaktiska Handläggaren"),
    CEREMONY("Ceremonimästaren"),
    PANIC("Panikministern"),
    HELPFUL("Den överdrivet hjälpsamma handläggaren"),
    /** Den torra rösten som skiljer det observerade från slutsatsen. */
    BABEL("Babel-Stoner"),
    MEMBER("Medlemmen"),
    MONKEYS("Flying Monkeys"),
}

/**
 * Filnamnet, utan ändelse, för en inspelad version av raden: rollen och ett fingeravtryck av texten.
 * En inspelning med det namnet går före all talsyntes. Rader utan roll läses aldrig upp.
 */
val Cue.clipName: String? get() = role?.let { "${it.name.lowercase()}-${fingerprint(text)}" }

/** FNV-1a, 64 bitar, över textens UTF-8. Lätt att räkna ut likadant i vilket verktyg som helst. */
internal fun fingerprint(text: String): String {
    var hash = 0xcbf29ce484222325uL
    for (byte in text.trim().encodeToByteArray()) hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3uL
    return hash.toString(16).padStart(16, '0')
}

/** En scen: en rubrik och regisserade rader, med uppgift om varifrån texten kommer. */
data class Scene(
    val title: String,
    val cues: List<Cue>,
    val source: Source = Source.OWNER,
    val authority: String? = null,
)

/** Rader efter varandra med samma röst. Sista raden får den längre pausen. */
internal fun said(role: Role?, vararg lines: String, direction: String? = null, first: Int = 400, last: Int = 950): List<Cue> =
    lines.mapIndexed { i, text ->
        Cue(
            text, role, if (i == 0) direction else null,
            when {
                i == 0 -> first
                i == lines.lastIndex -> last
                else -> 500
            },
        )
    }

/**
 * En rad med regi: vem som säger den, hur, och hur lång tystnaden före den ska vara.
 * Pausen före sista raden gör jobbet. [role] är null för text som bara står på skärmen.
 */
data class Cue(
    val text: String,
    val role: Role? = null,
    val direction: String? = null,
    val pauseBeforeMillis: Int = 500,
)

/**
 * Läget i minispelet Effektivisera aporna. Allt annat räknas fram ur de här talen, så att samma
 * tryck alltid ger samma katastrof.
 */
data class MonkeyState(
    /** Antal flockar som har släppts lös med dubbeltryck. */
    val flocks: Int = 0,
    /** Antal gånger smulransonen har halverats. */
    val cuts: Int = 0,
    /** Antal tryck på SUBOPTIMERA ÄNNU MER. */
    val worse: Int = 0,
    val reported: Boolean = false,
    val blamed: Boolean = false,
) {
    val crumbsPerMonkey: Int get() = CRUMBS[cuts.coerceIn(0, Monkeys.MAX_CUTS)]
    val workDemand: Int get() = WORK[cuts.coerceIn(0, Monkeys.MAX_CUTS)]
    val emotionalCostPercent: Int get() = 23 + 12 * cuts + 17 * worse
    val solvedPercent: Int get() = SOLVED[worse.coerceIn(0, Monkeys.MAX_WORSE)]
    /** Hur många som måste bekräfta att den tidigare lösningen var rätt. */
    val confirmersNeeded: Int get() = 14 shl worse.coerceIn(0, Monkeys.MAX_WORSE)
    /** Skulden för stämningen hos den som påpekade problemet. */
    val blamePercent: Int get() = 20 * worse
    /** Ledningens rapporterade resultat. Stiger i takt med att allt fler försvarar beslutet. */
    val reportedResultPercent: Int get() = 60 + 10 * worse.coerceIn(0, Monkeys.MAX_WORSE)
    /** Hela systemet är suboptimerat till fullständig kollaps. */
    val collapsed: Boolean get() = cuts >= Monkeys.MAX_CUTS && worse >= Monkeys.MAX_WORSE

    private companion object {
        val CRUMBS = listOf(16, 8, 4, 2, 0)
        val WORK = listOf(1, 2, 4, 8, 9)
        val SOLVED = listOf(15, 9, 4, 1, 0)
    }
}

data class MonkeyScene(
    val state: MonkeyState,
    val title: String,
    val cues: List<Cue>,
    val source: Source = Source.OWNER,
) {
    val grip: Grip get() = Grip.VALIDATION_CRUMBS
}

/**
 * RELEASE THE MONKEYS. Dubbeltryck på DON'T PANIC mobiliserar Den Heliga Självbildens Ordens
 * flygande bekräftelsekår, och sedan kan katastrofen effektiviseras tills ingen får några smulor,
 * ingen får sluta och alla intygar att de är nöjda.
 *
 * Raderna kommer ur ägarens underlag. Talen i mätarna är satir och mäter ingenting.
 */
object Monkeys {
    const val MAX_CUTS = 4
    const val MAX_WORSE = 4
    const val AUTHORITY = "DEN HELIGA SJÄLVBILDENS ORDEN · BEKRÄFTELSEKÅREN"
    const val STATUS = "Det ursprungliga problemet prioriteras ned. All tillgänglig energi går nu åt till att försvara beslutet."
    const val DISCLAIMER = "Satiriska värden, inte faktiska mått på människors känslor."

    /** Dubbeltryck: ännu en flock släpps lös. */
    fun release(state: MonkeyState = MonkeyState()): MonkeyScene {
        val next = state.copy(flocks = state.flocks + 1)
        return when (next.flocks) {
            1 -> MonkeyScene(
                next, "GALAKTISKT UNDANTAGSTILLSTÅND · DUBBELTRYCK REGISTRERAT",
                listOf(
                    Cue("En obehörig uppfattning har registrerats.", Role.CEREMONY, "iskallt", 300),
                    Cue("RELEASE THE MONKEYS!", Role.PANIC, null, 900),
                    Cue("En obehörig verklighetsbeskrivning har upptäckts i närheten av Ordens självbild."),
                    Cue("Samtliga flygande apor har mobiliserats. Ersättning utgår i form av brödsmulor av bekräftelse."),
                    Cue(
                        "Jaaa, vad bra! Nu har alla hållit med! Jag har redan skickat ut smulorna, men några har visst överklagat portionsstorleken!",
                        Role.HELPFUL, null, 650,
                    ),
                    Cue("UPPDRAG SLUTFÖRT", pauseBeforeMillis = 950),
                    Cue("Kritiken har framgångsrikt överröstats. Ingen har undersökt vad den handlade om."),
                    Cue("Suboptimeringsverket: 100 % lojalitet. 0 % saklig problemlösning.", pauseBeforeMillis = 650),
                ),
            )
            2 -> MonkeyScene(
                next, "GALAKTISKA HAVERIKOMMISSIONEN",
                listOf(
                    Cue("RELEASE THE MONKEYS!", Role.PANIC, null, 300),
                    Cue("En andra flock har mobiliserats för att försvara den första flockens rätt att inte behöva förklara varför den mobiliserades."),
                    Cue("Fyra knapptryckningar. Två flockar. Noll besvarade frågor.", Role.OFFICIAL, null, 650),
                    Cue("Enastående effektivitet.", Role.OFFICIAL, null, 950),
                ),
            )
            else -> when (next.flocks % 3) {
                0 -> MonkeyScene(
                    next, "ORDENS SVAR",
                    listOf(
                        Cue("Aporna är fullständigt fria att lämna oss.", Role.CEREMONY, null, 300),
                        Cue("De måste bara först få vårt godkännande.", Role.CEREMONY, null, 950),
                    ),
                )
                1 -> MonkeyScene(
                    next, "RÖSTERNA FÅR EGNA ROLLER",
                    listOf(
                        Cue("SLÄPP LOSS APORNA!", Role.PANIC, null, 300),
                        Cue("Ceremonimästaren meddelar iskallt att aporna är självständiga och inte tar emot order."),
                        Cue("Den överdrivet hjälpsamma handläggaren har redan fyllt i deras tidrapporter och betalat ut bekräftelsen."),
                        Cue("I form av en halv smula per apa, före skatt.", pauseBeforeMillis = 950),
                    ),
                )
                else -> MonkeyScene(
                    next, "GALAKTISKA BEKRÄFTELSEVERKET · AVDELNINGEN FÖR FLYGBUREN LOJALITET",
                    listOf(
                        Cue("Den som ifrågasätter villkoren har uppvisat otacksamhet och får sina smulor indragna.", Role.OFFICIAL, null, 300),
                        Cue("Den som angriper budbäraren bekräftar därmed behovet av ytterligare budbärare.", Role.OFFICIAL),
                        Cue("Verksamheten är självfinansierande genom medlemmarnas ständigt ökande behov av godkännande.", Role.OFFICIAL, null, 950),
                    ),
                )
            }
        }
    }

    fun canHalve(state: MonkeyState): Boolean = state.flocks > 0 && state.cuts < MAX_CUTS && !state.reported

    /** HALVERA SMULRANSONEN: fyra effektiviseringar, sedan finns inga smulor kvar att halvera. */
    fun halve(state: MonkeyState): MonkeyScene? {
        if (!canHalve(state)) return null
        val next = state.copy(cuts = state.cuts + 1)
        val scene = when (next.cuts) {
            1 -> MonkeyScene(
                next, "Första effektiviseringen – smulorna halveras",
                listOf(
                    Cue("Jaa, nu har jag varit lite duktig!", Role.HELPFUL, "sprudlande tjänstvillig", 300),
                    Cue("Jag har halverat smulransonen och fördubblat apornas arbetskrav!", Role.HELPFUL, null, 400),
                    Cue("Det blir mycket mer effektivt så här!", Role.HELPFUL, null, 650),
                ),
            )
            2 -> MonkeyScene(
                next, "Andra effektiviseringen – aporna börjar konkurrera",
                listOf(
                    Cue("Att aporna blivit hungrigare visar att de ännu inte förtjänat vår uppskattning.", Role.CEREMONY, "kallt och lågmält", 300),
                    Cue("Vi ska därför minska tilldelningen ytterligare.", Role.CEREMONY, null, 650),
                    Cue("Detta är omtanke.", Role.CEREMONY, null, 950),
                ),
            )
            3 -> MonkeyScene(
                next, "Tredje effektiviseringen – aporna försvarar sin egen svält",
                listOf(
                    Cue("KAMRATER! DET FINNS APOR SOM KRÄVER HELA BRÖDSKIVOR!", Role.PANIC, "i fullständigt vettlöst massmötestal", 300),
                    Cue("DE VILL FÅ OSS ATT TRO ATT BEKRÄFTELSE SKA UTDELAS UTAN MOTPRESTATION!", Role.PANIC, null, 400),
                    Cue("DE HOTAR DEN GALAKTISKA SMULORDNINGEN!", Role.PANIC, null, 400),
                    Cue("MOBILISERA SAMTLIGA FLYGANDE APOR MOT APORNA!", Role.PANIC, null, 650),
                ),
            )
            else -> MonkeyScene(
                next, "Fjärde effektiviseringen – maximal paradox",
                listOf(
                    Cue("Men vad roligt! Nu behöver vi inte dela ut några smulor alls! Aporna försvarar oss ändå!", Role.HELPFUL, null, 300),
                    Cue("Jag har registrerat det som hundraprocentig kundnöjdhet.", Role.HELPFUL, null, 950),
                ),
            )
        }
        return scene.withParadoxIfCollapsed()
    }

    fun canSuboptimize(state: MonkeyState): Boolean = state.flocks > 0 && state.worse < MAX_WORSE && !state.reported

    /** SUBOPTIMERA ÄNNU MER: försämrar själva sättet problemet hanteras på. */
    fun suboptimize(state: MonkeyState): MonkeyScene? {
        if (!canSuboptimize(state)) return null
        val next = state.copy(worse = state.worse + 1)
        val line = when (next.worse) {
            1 -> "Ju mindre vi löser, desto mer måste vi försvara att vi redan har löst det."
            2 -> "Ju mer vi försvarar det, desto dyrare blir det känslomässigt att erkänna att det inte är löst."
            3 -> "Ju dyrare det blir att erkänna, desto viktigare är det att fortsätta."
            else -> "Detta kallas långsiktighet."
        }
        return MonkeyScene(
            next, "Galaktiska effektiviseringsprincipen",
            listOf(Cue(line, Role.OFFICIAL, null, if (next.worse == MAX_WORSE) 950 else 400)),
        ).withParadoxIfCollapsed()
    }

    private fun MonkeyScene.withParadoxIfCollapsed(): MonkeyScene =
        if (!state.collapsed) this else copy(
            cues = cues + listOf(
                Cue("DEN ULTIMATA PARADOXEN", pauseBeforeMillis = 1200),
                Cue(
                    "Till sist arbetar hela den flygande bekräftelsekåren dygnet runt för att försvara Ordens beslut att inte ge dem någon bekräftelse.",
                    Role.OFFICIAL, null, 650,
                ),
                Cue("Ingen får några smulor.", Role.OFFICIAL, null, 650),
                Cue("Ingen får sluta.", Role.OFFICIAL, null, 650),
                Cue("Alla måste intyga att de är nöjda.", Role.OFFICIAL, null, 650),
                Cue("Och den som frågar varför aporna är olyckliga får skulden för att ha förstört stämningen.", Role.OFFICIAL, null, 950),
            ),
        )

    fun canReport(state: MonkeyState): Boolean = state.collapsed && !state.reported

    /** Slutrapporten finns först när allt har kollapsat. */
    fun finalReport(state: MonkeyState): MonkeyScene? {
        if (!canReport(state)) return null
        return MonkeyScene(
            state.copy(reported = true), "SLUTRAPPORT FRÅN GALAKTISKA SUBOPTIMERINGSVERKET",
            listOf(
                Cue("Bekräftelsekostnaderna har reducerats med 100 procent.", Role.OFFICIAL, null, 400),
                Cue("Arbetsinsatsen har ökat med 800 procent.", Role.OFFICIAL),
                Cue("Den ursprungliga sakfrågan har avvecklats.", Role.OFFICIAL, null, 650),
                Cue("Den emotionella kostnaden har överförts till de berörda.", Role.OFFICIAL),
                Cue("Samtliga apor har bekräftat att reformen är framgångsrik.", Role.OFFICIAL, null, 650),
                Cue("De apor som inte höll med räknas inte längre som apor.", Role.OFFICIAL, null, 950),
                Cue("Resultatet är därmed enhälligt.", Role.OFFICIAL, null, 950),
            ),
        )
    }

    fun canBlameRikard(state: MonkeyState): Boolean = state.reported && !state.blamed

    /** Den sista knappen, som bara finns när hela systemet är suboptimerat till fullständig kollaps. */
    fun blameRikard(state: MonkeyState): MonkeyScene? {
        if (!canBlameRikard(state)) return null
        return MonkeyScene(
            state.copy(blamed = true), "[ SKYLL PÅ RIKARD ]",
            listOf(
                Cue("Äntligen en lösning som alla kan enas om.", Role.CEREMONY, "nästan viskande", 950),
                Cue("Problemet är fortfarande kvar. Men skuldfrågan är löst.", pauseBeforeMillis = 1200),
            ),
        )
    }
}
