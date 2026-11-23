package se.pundaren.guide.engine.galax

/** Ett av de tolv heliga stegen: trossatsen, och Ordens kommentar till den. */
data class ConversionStep(
    val number: Int,
    val roman: String,
    val title: String,
    val text: List<String>,
    val comment: List<String>,
)

/** En rad i slutintyget: vad omvändelsen har gjort med ett område. */
data class CertificateRow(val area: String, val before: String, val after: String)

/**
 * OMVÄNDELSEPROGRAMMET. Den Heliga Självbildens Ordens tolv steg från självinsikt till fullständig
 * ansvarsfrihet: tolv steg runt i en cirkel. Användaren låser upp ett steg i taget.
 *
 * Programmet driver med försvaret av en skör självbild, inte med dem som går ett verkligt
 * tolvstegsprogram. De verkliga stegen handlar om att förändra sig själv. De här handlar om att slippa.
 */
object Conversion {
    const val STEPS = 12
    const val AUTHORITY = "DEN HELIGA SJÄLVBILDENS ORDEN"
    const val TITLE = "DE TOLV HELIGA STEGEN"
    const val SUBTITLE = "Vägen till befrielse från ansvar, självrannsakan och besvärande verklighet"
    const val TAGLINE = "Ett galaktiskt återhämtningsprogram för människor som riskerar att börja ta ansvar."
    const val APPROVAL = "Fastställda av Den Heliga Självbildens Orden och godkända av Galaktiska Suboptimeringsverket."
    const val MOTTO = "”Vi tar en dag i taget. Andras ansvar tar vi retroaktivt.”"
    const val DIPLOMA = "DIPLOM I FULLSTÄNDIG SJÄLVBILDSIMMUNITET"
    const val REPORT_BUTTON = "[ ANMÄL DEN SOM PÅPEKADE PROBLEMET ]"

    val warning: List<String> = listOf(
        "AA:s tolvstegsprogram bygger bland annat på självrannsakan, erkännande, gottgörelse och förändring.",
        "Den Heliga Självbildens Orden har efter omfattande granskning konstaterat att detta innebär en oacceptabel risk för personlig utveckling.",
        "Orden har därför tagit fram ett omvänt program.",
        "Tolv steg från självinsikt till fullständig ansvarsfrihet.",
    )

    val steps: List<ConversionStep> = listOf(
        ConversionStep(
            1, "I", "Vi förnekade vår maktlöshet",
            listOf(
                "Vi erkände att vi hade fullständig kontroll över våra liv och att varje antydan om motsatsen berodde på andra människors oförmåga att samarbeta med vår verklighetsbeskrivning.",
            ),
            listOf(
                "Problemet är inte att livet blivit ohanterligt.",
                "Problemet är att livet inte följer våra instruktioner.",
                "Rikard har underrättats.",
            ),
        ),
        ConversionStep(
            2, "II", "Vi kom till tro på en högre makt som kunde bekräfta att vi redan hade rätt",
            listOf(
                "Vi insåg att vår egen övertygelse, trots sin fullkomlighet, ibland behövde stöd av en ännu större auktoritet.",
                "Denna auktoritet kallade vi Den Heliga Självbildens Orden.",
            ),
            listOf(
                "Tvivel är en administrativ brist.",
                "Bekräftelse är dess behandling.",
                "Om behandlingen inte fungerar ska dosen bekräftelse höjas.",
            ),
        ),
        ConversionStep(
            3, "III", "Vi överlämnade vårt ansvar till Orden",
            listOf(
                "Vi fattade beslutet att lägga våra liv, våra handlingar och framför allt deras konsekvenser i Ordens händer.",
                "Orden accepterade omedelbart vår självbild.",
                "Konsekvenserna överlämnades till Rikard.",
            ),
            listOf(
                "Ansvaret har överförts.",
                "Beslutanderätten har behållits.",
                "Självbestämmandet är intakt.",
                "Ansvarsfriheten är total.",
                "Omvändelsen fortskrider planenligt.",
            ),
        ),
        ConversionStep(
            4, "IV", "Vi företog en grundlig moralisk inventering av alla andra",
            listOf(
                "Med orubblig ärlighet granskade vi våra närståendes fel, brister, svek, tonfall, kroppsspråk, minnesförmåga och ovilja att förstå oss.",
                "Inventeringen omfattade även händelser som ännu inte inträffat.",
            ),
            listOf(
                "Om inga fel hittas ska granskningen utökas.",
                "Om motparten saknar dokumenterade fel kan misstänkt felfrihet registreras som en avvikelse.",
                "Självrannsakan är förbjuden då den kan påverka granskningens objektivitet.",
            ),
        ),
        ConversionStep(
            5, "V", "Vi erkände inför Orden, oss själva och en annan människa exakt vad alla andra gjort fel",
            listOf(
                "Vi fann befrielse i att äntligen kunna berätta hur mycket andra hade utsatt oss för genom att ifrågasätta våra handlingar.",
                "Vi medgav samtidigt att det förekommit situationer där vi kanske, i någon mycket begränsad och historisk mening, varit mindre än perfekta.",
                "Dessa medgivanden avsåg uteslutande händelser som inte längre kunde medföra ansvar.",
            ),
            listOf(
                "Ett gammalt, ofarligt erkännande ger full moralisk kredit för framtida ansvar.",
                "Medlemmen har visat självinsikt.",
                "Någon faktisk förändring behöver därför inte övervägas.",
            ),
        ),
        ConversionStep(
            6, "VI", "Vi var helt beredda att låta Orden avlägsna alla som påpekade våra karaktärsbrister",
            listOf(
                "Vi förstod att problemet inte var våra handlingar utan den skam som uppstod när någon nämnde dem.",
                "Vi blev därför villiga att låta Orden avlägsna alla besvärande frågor.",
            ),
            listOf(
                "Skam har upptäckts.",
                "Ansvar misstänks vara orsaken.",
                "Ansvar kan inte behandlas utan risk för självbildsskada.",
                "Vi rekommenderar därför att frågeställaren avlägsnas.",
                "Behandlingen är framgångsrik när inga frågor återstår.",
            ),
        ),
        ConversionStep(
            7, "VII", "Vi bad ödmjukt verkligheten att avlägsna sina fel",
            listOf(
                "Vi böjde våra huvuden inför Ordens högsta ceremonimästare och bad att universum skulle rätta sina motsägelser, anpassa sina minnen och upphöra med sina anklagande orsakssamband.",
            ),
            listOf(
                "Verkligheten har återigen underlåtit att samarbeta.",
                "En skriftlig ursäkt har begärts.",
                "Verklighetens svarstid är tre arbetsdagar.",
                "Om inget svar inkommer betraktas ursäkten som mottagen.",
            ),
        ),
        ConversionStep(
            8, "VIII", "Vi gjorde en förteckning över alla som vi hade skadat och insåg hur mycket de hade sårat oss genom att påminna oss",
            listOf(
                "Vi upprättade en noggrann lista över personer som uppgett att våra handlingar fått konsekvenser.",
                "Listan användes för att identifiera vilka som fortfarande bar på obehagliga minnen.",
            ),
            listOf(
                "Varje kvarstående minne utgör ett möjligt lojalitetsproblem.",
                "Den som drabbats av en handling men fortfarande minns den kan behöva hjälp att gå vidare.",
                "Den som orsakade handlingen har redan gått vidare.",
                "Detta visar vem som kommit längst i sin personliga utveckling.",
            ),
        ),
        ConversionStep(
            9, "IX", "Vi krävde gottgörelse av alla vi hade skadat",
            listOf(
                "Vi kontaktade de berörda och erbjöd dem möjlighet att be om ursäkt för den skam och det obehag deras reaktioner orsakat oss.",
                "När detta inte gick att genomföra registrerades deras bristande samarbetsvilja.",
            ),
            listOf(
                "Gottgörelse har erbjudits.",
                "Mottagaren vägrade att gottgöra avsändaren.",
                "Medlemmen har därför fullgjort sin del.",
                "Ärendet avslutat.",
            ),
        ),
        ConversionStep(
            10, "X", "Vi fortsatte vår moraliska inventering av andra och tilldelade omedelbart Rikard ansvaret när något gick fel",
            listOf(
                "Genom daglig övning utvecklade vi en förmåga att upptäcka andras brister innan de själva hann begå dem.",
                "När vår egen roll i en händelse blev besvärande hänvisade vi till den äldre familjetraditionens ursprungliga och fortfarande gällande dom:",
                "”Allt ont kommer från Rikard.”",
            ),
            listOf(
                "Rikard har förnekat inblandning i minst två incidenter.",
                "Tid och plats har bedömts vara administrativa bekymmer.",
                "Det redan utdömda straffet återstår att verifiera.",
                "Att Rikard eventuellt inte befann sig på platsen saknar betydelse för skuldfrågan.",
                "All annan inblandning förnekas.",
            ),
        ),
        ConversionStep(
            11, "XI", "Vi sökte genom bön, meditation och upprepade bekräftelser förbättra vår kontakt med den högre makt som alltid håller med oss",
            listOf(
                "Vi bad endast om kunskap om hur andra borde förändra sig och om kraft att uthärda deras fortsatta vägran.",
                "När svaret uteblev upprepade vi bönen med högre röst.",
            ),
            listOf(
                "Meditationens syfte är inte att uppnå insikt.",
                "Syftet är att minska störande tankeverksamhet tills endast den egna självbilden återstår.",
                "Om verkligheten tränger sig på, aktivera DON'T PANIC.",
                "Vid dubbeltryck: RELEASE THE MONKEYS!",
            ),
        ),
        ConversionStep(
            12, "XII", "När vi genom dessa steg uppnått fullständig ansvarsfrihet försökte vi föra budskapet vidare och tillämpa principerna i alla våra relationer",
            listOf(
                "Vi rekryterade människor som kunde hjälpa oss att bekräfta vår självbild.",
                "Vi kallade dem Flying Monkeys.",
                "De erhöll brödsmulor av bekräftelse för varje gång de försvarade Orden utan att undersöka sakfrågan.",
                "När smulorna blev för kostsamma halverades ransonen och arbetskraven fördubblades.",
            ),
            listOf(
                "Bekräftelsekåren arbetar nu åtta gånger hårdare för hälften så mycket uppskattning.",
                "Den ursprungliga sakfrågan är fortfarande olöst.",
                "Den emotionella kostnaden har ökat.",
                "Samtliga apor försvarar reformen.",
                "De som inte gör det räknas inte längre som apor.",
                "Fullständig enighet har uppnåtts.",
            ),
        ),
    )

    /** Steget som scen: trossatsen står på skärmen, Ordens kommentar läses av ceremonimästaren. */
    fun step(number: Int): Scene? {
        val s = steps.getOrNull(number - 1) ?: return null
        return Scene(
            "STEG ${s.roman} – ${s.title}",
            s.text.mapIndexed { i, line -> Cue(line, pauseBeforeMillis = if (i == 0) 400 else 650) } +
                said(Role.CEREMONY, *s.comment.toTypedArray(), direction = "Ordens kommentar", first = 950),
        )
    }

    /** Omvändelsens ceremoni, när alla tolv steg är genomförda. */
    val ceremony: Scene = Scene(
        "OMVÄNDELSENS CEREMONI",
        listOf(
            Cue("När samtliga tolv steg genomförts träder medlemmen fram inför Ordens högsta ceremonimästare.", pauseBeforeMillis = 400),
            Cue("Har du granskat ditt eget beteende?", Role.CEREMONY, "lågmält", 950),
            Cue("Nej.", Role.MEMBER, null, 650),
            Cue("Har du undersökt vilka konsekvenser det fått för andra?", Role.CEREMONY, null, 650),
            Cue("Nej.", Role.MEMBER, null, 650),
            Cue("Har du gottgjort någon?", Role.CEREMONY, null, 650),
            Cue("Nej.", Role.MEMBER, null, 650),
            Cue("Har du förändrat något?", Role.CEREMONY, null, 650),
            Cue("Inte vad jag känner till.", Role.MEMBER, null, 650),
            Cue("Du är redo.", Role.CEREMONY, "efter en lång, högtidlig tystnad", 2400),
            Cue("MEDBORGARE! VI HAR BEFRIAT ÄNNU EN MÄNNISKA FRÅN DET OUTHÄRDLIGA OKET ATT KUNNA HA FEL!", Role.PANIC, null, 650),
            Cue(
                "Jaaa, sååå fantastiskt! Jag har redan utfärdat ditt diplom i personlig utveckling! Du behöver inte ens hämta det, för enligt våra register har du redan gjort det!",
                Role.HELPFUL, null, 650,
            ),
            Cue("HURRA! HURRA! HURRA!", Role.MONKEYS, null, 650),
            Cue("En fjärdedels brödsmula faller från taket.", pauseBeforeMillis = 950),
            Cue("Sjutton apor skadas i trängseln.", pauseBeforeMillis = 650),
            Cue("Ordens självrisk påverkas inte.", pauseBeforeMillis = 950),
        ),
    )

    /** Slutintyget från Galaktiska Suboptimeringsverket. */
    val certificate: List<CertificateRow> = listOf(
        CertificateRow("Förmåga att erkänna fel", "Viss risk förekom", "Eliminerad"),
        CertificateRow("Självrannsakan", "Möjlig", "Förbjuden"),
        CertificateRow("Gottgörelse", "Kunde bli aktuell", "Överförd till motparten"),
        CertificateRow("Skuld", "Delvis egen", "100 % Rikards"),
        CertificateRow("Skam", "Kunde uppstå", "Klassificeras som yttre angrepp"),
        CertificateRow("Självbild", "Kunde förändras", "Permanent fryst"),
        CertificateRow("Ansvar", "Kunde utkrävas", "Administrativt avvecklat"),
        CertificateRow("Relationer", "Krävde ömsesidighet", "Kräver bekräftelse"),
        CertificateRow("Verklighetskontakt", "Ibland besvärande", "Frivillig tilläggstjänst"),
        CertificateRow("Personlig utveckling", "Oförutsägbar", "Redan genomförd"),
    )

    val conclusion: Scene = Scene(
        "SLUTINTYG FRÅN GALAKTISKA SUBOPTIMERINGSVERKET",
        said(
            Role.OFFICIAL,
            "Tolv steg har genomförts.",
            "Ingen har behövt ändra sitt beteende.",
            "Ingen har behövt ta ansvar.",
            "Samtliga problem har överförts till andra.",
            "Medlemmens självbild har stärkts.",
            "Relationernas skick har inte kunnat fastställas eftersom berörda personer vägrat bekräfta resultatet.",
            "Behandlingen är därför en fullständig framgång.",
        ),
        authority = "MYNDIGHETENS SLUTSATS",
    )

    val paradox: Scene = Scene(
        "DEN HELIGA PARADOXEN",
        listOf(
            Cue("AA:s tolv steg handlar om att förändra sig själv.", pauseBeforeMillis = 400),
            Cue("Ordens tolv steg handlar om att förändra alla andra så att man själv slipper.", pauseBeforeMillis = 650),
            Cue("Det första programmet har tolv steg framåt.", pauseBeforeMillis = 950),
            Cue("Det andra har tolv steg runt i en cirkel.", pauseBeforeMillis = 650),
            Cue("Efter tolv steg står medlemmen exakt där den började.", pauseBeforeMillis = 950),
            Cue("Men är nu tolv gånger säkrare på att det är alla andra som står fel.", pauseBeforeMillis = 950),
            Cue("SUBOPTIMERINGSVERKET GRATULERAR.", pauseBeforeMillis = 1200),
            Cue("Välkommen till Den Heliga Självbildens Orden.", Role.CEREMONY, null, 950),
        ),
    )

    /** Knappen som diplomet ger: anmäl den som påpekade att problemet finns kvar. */
    val reportTheMessenger: Scene = Scene(
        "ANMÄLAN MOTTAGEN",
        said(
            Role.OFFICIAL,
            "Den anmälda personen har påpekat att du fortfarande har samma problem.",
            "Påpekandet har klassificerats som ett yttre angrepp på en permanent fryst självbild.",
            "Problemet har därmed överförts till den som såg det.",
            "Du har inte längre samma problem. Du har en motståndare.",
            "Rikard har underrättats.",
        ),
        Source.GENERATED,
    )
}
