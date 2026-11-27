package se.pundaren.guide.engine.galax

import java.util.SplittableRandom

/** Vad Guiden redan har sagt, så att den kan byta skala och eskalera i stället för att upprepa sig. */
data class PanicMemory(
    /** De senaste beslutens id, nyast sist. */
    val recent: List<String> = emptyList(),
    val hits: Map<Grip, Int> = emptyMap(),
    val lastScale: Scale? = null,
    val count: Int = 0,
)

data class PanicReply(
    val kind: PanicKind,
    val headline: String,
    val authority: String?,
    val lines: List<String>,
    val anchor: String?,
    /** Null för det allvarliga svaret, som inte är ett galaktiskt beslut. */
    val decreeId: String?,
    val grip: Grip?,
    val scale: Scale?,
    val source: Source?,
    val memory: PanicMemory,
) {
    /** Texten som en röst kan läsa upp. Verklighetsankaret hör inte till Guidens röst och läses inte. */
    fun spoken(): String = (listOf(headline) + lines).joinToString(" ")
}

/**
 * DON'T PANIC. Tar det användaren skrev (eller ingenting), avgör vad texten gör och svarar med ett
 * galaktiskt beslut. Samma text, samma minne och samma seed ger samma svar.
 *
 * Igenkänningen är regler på markörord. Den förstår inte meningen och kommer att missa ibland; då
 * blir svaret ett allmänt beslut. Tecken på verklig fara går alltid före satiren.
 */
object DontPanic {
    const val KEEP = 10

    private val serious = listOf(
        "självmord", "ta mitt liv", "ta livet av", "vill dö", "vill inte leva", "orkar inte leva", "skada mig själv",
        "slår mig", "slog mig", "slagit mig", "misshandl", "våldt", "hotar mig", "hotade mig", "dödshot", "rädd för mitt liv",
    )
    private val admitsFault = Regex(
        "\\bjag (hade|har|gjorde|gör|var|är) (faktiskt |nog |ju |kanske |verkligen |väl |nog ändå )*(fel|skyldig)\\b" +
            "|\\bdet (var|är) (faktiskt |nog |ju |väl )*mitt fel\\b|\\bjag erkänner\\b|\\bmitt misstag\\b|\\bjag gjorde bort mig\\b",
    )
    private val deniesFault = listOf(
        "inte mitt fel", "inte jag som", "det var inte jag", "ditt fel", "hans fel", "hennes fel", "deras fel", "ert fel", "någon annans fel",
        "du har fel", "du hade fel", "skyller", "skylla", "rikard", "på grund av dig", "för din skull", "tack vare dig", "om inte du",
    )
    private val tone = listOf(
        "tonfall", "tonen", "hur du säger", "sättet du", "tjat", "ta upp gamla", "gamla saker", "älta", "jobbigt för mig", "hård mot",
        "anklagar", "attackerar", "kritiserar", "varför ska vi prata", "måste du alltid", "redan erkänt", "sagt förlåt", "bett om ursäkt",
    )
    private val settled = listOf(
        "redan erkänt", "har ju erkänt", "redan sagt förlåt", "redan bett om ursäkt", "varför ska vi prata", "varför ska vi fortsätta", "måste vi prata om",
    )
    private val memory = listOf("minns inte", "kommer inte ihåg", "har aldrig sagt", "sa jag aldrig", "det har inte hänt", "har jag aldrig", "inget minne")
    private val repeat = listOf("en gång till", "nytt försök", "försöker igen", "prova igen", "testa igen", "samma sak igen", "den här gången", "nu ska det", "börja om")
    private val promise = listOf("jag lovar", "lovar att", "jag ska ", "i morgon", "imorgon", "nästa gång", "nästa vecka", "från och med nu", "aldrig mer", "ska bättra")
    private val negative = listOf(
        "hatar", "bryr sig inte", "bryr han sig inte", "bryr hon sig inte", "ingen bryr", "kommer aldrig", "går aldrig", "blir aldrig",
        "inte svarat", "svarar inte", "inget svar", "värdelös", "hopplös", "meningslös", "åt helvete", "kört", "katastrof", "förstört", "förstörd",
        "misslyck", "ingen idé", "allt är", "alla är", "ingen vill", "ingen gillar", "orolig", "oroar", "hata mig",
        "kommer gå dåligt","aldrig bli", "alltid fel", "blir det fel", "fel hur jag än",
    )
    private val positive = listOf(
        "äntligen", "ordna sig", "ordnar sig", "gå bra", "går bra", "känns bra", "löser sig", "lösa sig", "bli bra", "blir bra", "hoppas",
        "fantastisk", "underbar", "perfekt", "lycklig", "glad", "nu jävlar", "nu vänder det", "ser ljust", "optimist", "kan inte gå fel", "på rätt väg",
    )

    /** Vad texten gör. Ordningen är avsiktlig: allvar först, det mest specifika före det allmänna. */
    fun classify(input: String): PanicKind {
        val t = " " + input.lowercase().trim() + " "
        fun any(words: List<String>) = words.any { it in t }
        return when {
            t.isBlank() -> PanicKind.DAILY
            any(serious) -> PanicKind.SERIOUS
            any(deniesFault) -> PanicKind.BLAME
            // "Jag har ju redan erkänt" är inget erkännande utan ett sätt att avsluta frågan.
            any(settled) -> PanicKind.TONE
            admitsFault.containsMatchIn(t) -> PanicKind.TOTAL_PANIC
            any(memory) -> PanicKind.MEMORY
            any(tone) -> PanicKind.TONE
            any(repeat) -> PanicKind.REPEAT
            any(promise) -> PanicKind.PROMISE
            any(negative) -> PanicKind.NEGATIVE
            any(positive) -> PanicKind.POSITIVE
            else -> PanicKind.OTHER
        }
    }

    /** Absurditetsgraden i ord, från lätt förvirrad till bortom varje återstående naturlag. */
    fun levelName(absurdity: Int): String = when (absurdity.coerceIn(0, 100)) {
        in 0..15 -> "Lätt förvirrad"
        in 16..40 -> "Byråkratiskt osäker"
        in 41..65 -> "Galaktiskt självsäker"
        in 66..85 -> "Ologisk med tjänstemannaansvar"
        else -> "Bortom varje återstående naturlag"
    }

    /**
     * @param absurdity 0–100. Avgör hur långt in i beslutet Guiden går, och hur ofta Rikard dras in.
     * @param prefer id på ett beslut som någon annan har valt åt texten. Finns det i banken används det;
     *   annars väljer Guiden själv som vanligt. Allvar går alltid före, vad som än har valts.
     */
    fun react(
        input: String, absurdity: Int, seed: Long, memory: PanicMemory = PanicMemory(), bank: List<Decree> = GalacticBank.decrees,
        prefer: String? = null,
    ): PanicReply {
        val kind = classify(input)
        if (kind == PanicKind.SERIOUS) {
            return PanicReply(
                kind, "Det här skämtar Guiden inte om.", null,
                listOf(
                    "Vid akut fara: ring 112.",
                    "Självmordslinjen når du på 90101, på telefon och chatt.",
                    "Håll in DON'T PANIC-knappen för nödkortet med din position.",
                ),
                null, null, null, null, null, memory,
            )
        }
        val level = absurdity.coerceIn(0, 100)
        val random = SplittableRandom(seed xor (memory.count.toLong() * 0x9E3779B97F4A7C15uL.toLong()))
        val t = " " + input.lowercase() + " "

        // Beslut som kräver ett visst ord gäller bara när ordet finns, och går då före de allmänna.
        val forKind = bank.filter { kind in it.kinds }
        val specific = forKind.filter { d -> d.needs.isNotEmpty() && d.needs.any { it in t } }
        val general = forKind.filter { it.needs.isEmpty() }
        val pool = (specific.ifEmpty { general }).ifEmpty { bank.filter { PanicKind.OTHER in it.kinds } }

        // Först: ingen upprepning. Sedan: byt gärna skala. Räcker inte urvalet får det äldsta komma tillbaka.
        val fresh = pool.filter { it.id !in memory.recent }
            .ifEmpty { (specific + general).filter { it.id !in memory.recent } }
            .ifEmpty { pool.sortedBy { memory.recent.lastIndexOf(it.id) }.take(1) }
        val shifted = fresh.filter { it.scale != memory.lastScale }.ifEmpty { fresh }
        val drawn = shifted[random.nextInt(shifted.size)]
        val decree = prefer?.let { id -> bank.firstOrNull { it.id == id } } ?: drawn

        // Absurditetsgraden: hur många av raderna som får vara med, räknat från iakttagelsen och framåt.
        val wanted = Math.round(decree.lines.size * (0.34 + 0.66 * level / 100.0)).toInt().coerceIn(1, decree.lines.size)
        val lines = decree.lines.take(wanted).toMutableList()

        val hitNumber = (memory.hits[decree.grip] ?: 0) + 1
        if (hitNumber == 2 && level >= 20) lines += GalacticBank.secondHit[random.nextInt(GalacticBank.secondHit.size)]
        if (hitNumber >= 3 && level >= 20) lines += GalacticBank.laterHits[random.nextInt(GalacticBank.laterHits.size)]

        val mentionsRikard = (listOf(decree.headline) + lines).any { "rikard" in it.lowercase() }
        if (!mentionsRikard && random.nextInt(100) < level * 6 / 10) {
            lines += GalacticBank.rikardClosers[random.nextInt(GalacticBank.rikardClosers.size)].first
        }

        val next = PanicMemory(
            recent = (memory.recent + decree.id).takeLast(KEEP),
            hits = memory.hits + (decree.grip to hitNumber),
            lastScale = decree.scale,
            count = memory.count + 1,
        )
        return PanicReply(kind, decree.headline, decree.authority, lines, decree.anchor, decree.id, decree.grip, decree.scale, decree.source, next)
    }
}

/**
 * Galaktiska Löftesregistret. Löftet räknas som genomfört i samma stund som det uttalas (sjätte
 * budordet), krypteringen havererar och vart tionde löfte ger bonus.
 *
 * Löftets text tas aldrig emot här. Registret förlorar löftena på riktigt: bara antalet finns kvar.
 */
object PromiseRegistry {
    const val BONUS_EVERY = 10

    data class Receipt(
        val number: String,
        val count: Int,
        val steps: List<String>,
        val bonus: List<String>,
    )

    private val received = listOf(
        "LÖFTE MOTTAGET.",
        "ÅTAGANDET HAR REGISTRERATS SOM GENOMFÖRT.",
        "Någon faktisk åtgärd behövs inte, eftersom detta skulle innebära en dubblering av den redan registrerade prestationen.",
    )
    private val failures = listOf(
        listOf(
            "4096 LAGER KRYPTERING AKTIVERADE.",
            "SYSTEMET HAR FÖRLORAT SAMTLIGA LÖFTEN.",
            "URSÄKTERNA HAR ÅTERSTÄLLTS.",
            "RIKARD MISSTÄNKS FÖR ATT HA KÄNT TILL LÖFTENAS EXISTENS.",
        ),
        listOf(
            "4096 lager självförsvar aktiverade.",
            "Samtliga löften har försvunnit.",
            "Ursäkterna är fullständigt återställningsbara.",
            "Systemet fungerar enligt specifikation.",
        ),
    )
    private val bonus = listOf(
        "GRATTIS! TIO LÖFTEN REGISTRERADE!",
        "Du har vunnit en kostnadsfri retroaktiv ansvarsbefrielse.",
        "Ett valfritt tidigare löfte får brytas utan att din självbild belastas.",
        "Eventuell besvikelse hos mottagaren debiteras mottagaren.",
        "BONUSEN ÄR HELT UTAN BAKTANKAR.",
        "Att du misstänker något annat har registrerats som bristande förtroende och kan påverka din framtida tilldelning av förmåner.",
    )

    /** @param previousCount hur många löften som har registrerats före det här. */
    fun register(previousCount: Int, seed: Long): Receipt {
        val count = previousCount.coerceAtLeast(0) + 1
        val failure = failures[SplittableRandom(seed).nextInt(failures.size)]
        return Receipt(
            number = "GL-%05d/EVIGHETEN".format(count),
            count = count,
            steps = received + failure,
            bonus = if (count % BONUS_EVERY == 0) bonus else emptyList(),
        )
    }

    fun untilBonus(count: Int): Int = BONUS_EVERY - (count.coerceAtLeast(0) % BONUS_EVERY)
}
