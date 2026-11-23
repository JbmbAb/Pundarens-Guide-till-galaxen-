package se.pundaren.guide.engine.galax

/**
 * En klass i den galaktiska fekaltaxonomin. Namnen är humortradition och aldrig diagnoser, och ingen
 * klass har någon koppling till en Bristoltyp: systemen beskriver inte samma saker.
 */
data class FecalClass(
    val id: String,
    val name: String,
    val alias: String?,
    val description: String,
    /** Varifrån namnet och beskrivningen kommer: folklistan är insamlad, tillägget är ägarens. */
    val origin: Source,
    val reportTitle: String,
    val report: List<Cue>,
    val reportSource: Source,
    /** Ord i en fritt berättad anekdot som pekar ut klassen. */
    val keywords: List<String>,
    /** Galaktisk anmälan, så som den lämnas till Fekala Haverikommissionen. */
    val filing: String? = null,
    /** Samma sak på jordiska. Sant, och visas i sanningsfärgen. */
    val earthly: String? = null,
    val earthlyNote: String? = null,
) {
    val classical: Boolean get() = origin == Source.COLLECTED
}

/** Läget i minispelet Effektivisera tarmadministrationen. */
data class BowelState(
    /** Antal tryck på ÖKA EFFEKTIVITETEN. */
    val cuts: Int = 0,
    /** Antal tryck på SUBOPTIMERA ÄNNU MER. */
    val worse: Int = 0,
    val reported: Boolean = false,
    val closed: Boolean = false,
) {
    val paperRationPercent: Int get() = if (cuts >= 1) 50 else 100
    val visitTimePercent: Int get() = if (cuts >= 2) 50 else 100
    val documentationDemand: Int get() = (if (cuts >= 3) 2 else 1) * (if (worse >= 1) 2 else 1)
    val reviewersPerObservation: Int get() = if (worse >= 2) 2 else 1
    val visitsAllowed: Boolean get() = cuts < Fecal.MAX_CUTS
    val emotionalCostPercent: Int get() = 23 + 12 * cuts + 17 * worse
    /** Den medicinska nyttan är obefintlig, före som efter. */
    val medicalBenefitPercent: Int get() = 0
    val collapsed: Boolean get() = cuts >= Fecal.MAX_CUTS && worse >= Fecal.MAX_WORSE
}

data class BowelScene(val state: BowelState, val scene: Scene)

/**
 * FEKALA HAVERIKOMMISSIONEN, Babel-Stoners bajsanalysator.
 *
 * Tre saker hålls isär. Den galaktiska klassificeringen är Guidens normala språk: folklistans
 * tretton kategorier och tillägget Kraftkabeln. Resonemangsanalysen skiljer det observerade från
 * slutsatsen. Verklighetsankaret är jordiskt och sant: Bristolskalan och när vården ska kontaktas.
 *
 * Ingenting här tar emot eller sparar uppgifter. En anekdot läses bara för att välja klass.
 */
object Fecal {
    const val MAX_CUTS = 4
    const val MAX_WORSE = 4

    const val AUTHORITY = "GALAKTISKA FEKALFÖRVALTNINGEN · FEKALA HAVERIKOMMISSIONEN"
    const val TITLE = "BAJSANALYSATORN 9000"
    const val INSTITUTE = "Institutet för avancerad exkrementologi, tarmadministration och katastrofal feltolkning av normala avvikelser."
    const val DEPARTMENT = "Avdelningen för porslinsbaserade naturfenomen, tarmadministration och oönskade verklighetskontakter."
    const val PROMPT = "Välj en rapporterad manifestation från porslinstronen."
    const val FOOTNOTE = "Vid osäkerhet om ansvar har Rikard redan förnekat all inblandning. Avloppet har ännu inte yttrat sig."
    const val DISCLAIMER = "Fiktiva satirklassificeringar – inte medicinska bedömningar."
    const val ADDITION_NOTE = "Galaktiskt tillägg ur Guidens egen anekdotiska tradition. Ingår inte i folklistans tretton kategorier."
    const val GAME_TITLE = "EFFEKTIVISERA TARMADMINISTRATIONEN"
    const val GAME_STATUS = "Målet var att skita. Resultatet blev en heldagskonferens om varför det inte gick."

    private const val GENERAL_EARTHLY =
        "För vården: beskriv vad du såg och vilka besvär du har. Bristolskalan räcker för form och konsistens, resten får beskrivas med egna ord."

    val classes: List<FecalClass> = listOf(
        FecalClass(
            "FK-01", "Spökskiten", null,
            "Passage känns och spår finns på pappret, men föremålet har försvunnit ur skålen.",
            Source.COLLECTED, "HAVERIRAPPORT · PARANORMAL AVFÖRING",
            said(
                Role.OFFICIAL,
                "Passage bekräftad.",
                "Materiella bevis saknas i skålen.",
                "Spår har säkrats på toalettpapper.",
                "Utredningen kan därför varken bekräfta föremålets nuvarande position eller förneka dess tidigare existens.",
                "Rikard har förnekat inblandning.",
                "Hans geografiska position har bedömts som ett administrativt bekymmer.",
            ) + listOf(
                Cue("Det finns inga bevis för att något befinner sig i toaletten.", Role.CEREMONY, "helt oberörd", 950),
                Cue("Det finns därför heller ingen anledning att fortsätta undersökningen.", Role.CEREMONY, null, 1200),
                Cue("Frågan om vad som hände är avslutad.", Role.CEREMONY, null, 950),
            ),
            Source.OWNER,
            listOf("spök", "försvann", "försvunn", "borta", "syns inte", "syntes inte", "tom skål", "ingenting i skålen"),
            filing = "En kroppslig leverans har registrerats men saknar nu fysisk närvaro. Resterande bevismaterial är lokaliserat till toalettpapper.",
            earthly = "Jag kan beskriva vad jag upplevde och vad jag faktiskt såg, utan att dra slutsatser om varför avföringen inte syntes i skålen.",
        ),
        FecalClass(
            "FK-02", "Teflonskiten", null,
            "Glider ut utan ansträngning och lämnar pappret kritvitt.",
            Source.COLLECTED, "HAVERIRAPPORT · BEVISBRIST",
            said(
                Role.OFFICIAL,
                "Leveransen har genomförts utan ansträngning.",
                "Kontrollavtorkningen visar ett kritvitt papper.",
                "Eftersom inga spår finns kan myndigheten inte styrka att något har hänt.",
                "Användaren uppmanas att torka en gång till för att säkra bevisningen.",
                "Även den avtorkningen var resultatlös. Ärendet läggs ned i brist på bevis.",
                "Pappersförbrukningen bokförs som kvalitetskontroll.",
            ),
            Source.GENERATED,
            listOf("teflon", "kritvit", "vitt papper", "rent direkt", "inget på pappret", "utan spår"),
        ),
        FecalClass(
            "FK-03", "Tuschpennan", null,
            "Oavsett hur mycket papper som används blir det aldrig rent.",
            Source.COLLECTED, "HAVERIRAPPORT · OÄNDLIG AVTORKNING",
            said(
                Role.OFFICIAL,
                "Saneringsarbetet har nått 100 procent av tillgängliga resurser och 0 procent av målet.",
                "Extra papper har beställts för att säkerställa fortsatt utebliven framgång.",
                "Rengöringsarbetet har pågått i 47 minuter.",
                "Pappersförbrukningen har ökat med 800 procent.",
                "Renhetsgraden förblir oförändrad.",
                "Effektivisering föreslås genom att halvera pappersransonen.",
                "Besparingen är omedelbar. Problemet är numera permanent.",
            ) + said(
                Role.HELPFUL,
                "Jaaa, men då ska vi hjälpa till här!",
                "Jag ser att papperet är slut!",
                "Då har jag halverat ransonen så att vi slipper lika stora papperskostnader nästa gång!",
                "Sååå! Vad bra det blev!",
                "Nämen, är det inte rent än?",
                "Då får Rikard ringa oss på måndag!",
                direction = "hjälpsamt sprudlande", first = 950,
            ) + Cue("Babel-Stoners diagnos: Problemet går inte att avsluta.", Role.BABEL, "torrt", 950),
            Source.OWNER,
            listOf("tusch", "aldrig rent", "blir inte rent", "torka", "papper", "rullar"),
            filing = "Jag har drabbats av Tuschpennan. Saneringsarbetet förbrukar obegränsade resurser utan observerad förbättring.",
            earthly = "Jag behöver beskriva hur avföringen ser ut och om jag har problem med rengöring eller andra symtom.",
            earthlyNote = "Bristolskalan kan användas för formen och konsistensen. Pappersproblemet får beskrivas separat.",
        ),
        FecalClass(
            "FK-04", "Plaskskiten", "Poseidons kyss",
            "Tung leverans som orsakar ett direkt motangrepp från toalettvattnet.",
            Source.COLLECTED, "HAVERIRAPPORT · GRÄNSKRÄNKNING TILL SJÖSS",
            said(
                Role.OFFICIAL,
                "En tung leverans har träffat vattenytan.",
                "Toalettvattnet har svarat med ett omedelbart motangrepp.",
                "Händelsen utreds som en gränskränkning. Poseidon har inte gått att nå för en kommentar.",
                "Som skydd föreslås ett lager papper på vattenytan före varje leverans.",
                "Papperet räknas därmed som försvarsutgift och belastar inte toalettpappersbudgeten.",
            ),
            Source.GENERATED,
            listOf("plask", "stänk", "poseidon", "vattnet"),
        ),
        FecalClass(
            "FK-05", "Flytaren", "Bojen",
            "Vägrar sjunka och fortsätter patrullera vattenytan trots upprepade spolningar.",
            Source.COLLECTED, "HAVERIRAPPORT · OTILLÅTET SJÖMÄRKE",
            said(
                Role.OFFICIAL,
                "Föremålet vägrar sjunka.",
                "Tre spolningar har genomförts. Föremålet patrullerar fortfarande vattenytan.",
                "Det har därför registrerats som sjömärke.",
                "Ett sjömärke får inte avlägsnas utan tillstånd. Handläggningstiden är fjorton veckor.",
                "Toaletten är under tiden allmän farled och får inte användas.",
            ),
            Source.GENERATED,
            listOf("flyt", "sjunker inte", "vägrar sjunka", "boj", "spola"),
        ),
        FecalClass(
            "FK-06", "Andra-vågen-skiten", null,
            "Inträffar först när avtorkning, påklädning och avslut är genomförda.",
            Source.COLLECTED, "HAVERIRAPPORT · ÅTERÖPPNAT ÄRENDE",
            said(
                Role.OFFICIAL,
                "Ärendet har avslutats.",
                "Användaren har dragit upp byxorna.",
                "Ett nytt ärende har inkommit med samma ursprung och omedelbar verkställighet.",
                "För att undvika dubbelarbete har det första ärendet återöppnats.",
                "Handläggningstiden är nu längre än tarmens passagetid.",
            ),
            Source.OWNER,
            listOf("andra våg", "en gång till", "igen", "tillbaka", "byxorna", "trodde jag var klar"),
        ),
        FecalClass(
            "FK-07", "Krystaren", "Huvudvärksskiten",
            "Kräver en oproportionerligt ansträngande förlossningsliknande insats.",
            Source.COLLECTED, "HAVERIRAPPORT · TUNGT KROPPSARBETE",
            said(
                Role.OFFICIAL,
                "Insatsen har pågått i tjugo minuter.",
                "Resultatet står inte i någon rimlig proportion till arbetsinsatsen.",
                "Toalettbesöket har därför klassats som tungt kroppsarbete.",
                "Användaren har rätt till paus. Pausen får inte tas på toaletten.",
                "Huvudvärken har diarieförts som en separat leverans.",
            ),
            Source.GENERATED,
            listOf("kryst", "huvudvärk", "trög", "hård", "tog i"),
        ),
        FecalClass(
            "FK-08", "Klisterbajsen", null,
            "Fäster vid porslinet och kräver mekanisk sanering.",
            Source.COLLECTED, "HAVERIRAPPORT · PORSLINET STÅR FAST",
            said(
                Role.OFFICIAL,
                "Föremålet har lämnat kroppen men vägrar lämna porslinet.",
                "Spolning har prövats. Porslinet står fast vid sin uppfattning.",
                "Mekanisk sanering har beordrats. Toalettborsten har begärt risktillägg.",
                "Borsten är nu själv ett saneringsärende.",
                "Saneringen av saneringsutrustningen har lagts ut på entreprenad till Rikard.",
            ),
            Source.GENERATED,
            listOf("klist", "fastna", "sitter fast", "borste", "porslinet"),
        ),
        FecalClass(
            "FK-09", "Bakisbajsen", null,
            "Akut och illa organiserad leverans med betydande saneringsbehov.",
            Source.COLLECTED, "HAVERIRAPPORT · GÅRDAGENS BESLUT",
            said(
                Role.OFFICIAL,
                "En akut och illa organiserad leverans har inkommit.",
                "Någon förvarning har inte lämnats. Någon planering har inte förekommit.",
                "Gårdagens beslutsfattare har inte gått att nå för en kommentar.",
                "Samtliga beslut från i går kväll har ogiltigförklarats i efterhand.",
                "Saneringskostnaden belastar dagens budget. Gårdagens budget redovisar ett överskott av glädje.",
            ),
            Source.GENERATED,
            listOf("bakis", "bakfull", "dagen efter", "i går kväll"),
        ),
        FecalClass(
            "FK-10", "Falsklarmet", "Smygaren",
            "En mycket övertygande förvarning som endast resulterar i väderspänning.",
            Source.COLLECTED, "OPERATION FALSKT ALARM",
            said(
                Role.PANIC,
                "MEDBORGARE! VI HAR MOBILISERAT SAMTLIGA RESURSER!",
                "ETT OMEDELBART BEHOV HAR ANMÄLTS!",
                "RESULTATET HAR UTEBLIVIT!",
                "DETTA ÄR EN FÖROLÄMPNING MOT HELA DEN GALAKTISKA TARMORDNINGEN!",
                first = 300, last = 650,
            ),
            Source.OWNER,
            listOf("falsk", "fis", "prutt", "väderspänning", "inget kom", "ingenting kom"),
        ),
        FecalClass(
            "FK-11", "Anakondan", null,
            "Ovanligt lång, sammanhängande konstruktion som hotar avloppets kapacitet.",
            Source.COLLECTED, "HAVERIRAPPORT · INFRASTRUKTUR",
            said(
                Role.OFFICIAL,
                "En långsträckt struktur har upptäckts i avloppssystemet.",
                "Kommunen har förnekat att infrastrukturen dimensionerats för denna typ av verksamhet.",
                "Användaren uppmanas att dela upp framtida leveranser i flera budgetår.",
                "Eventuella översvämningar kommer att behandlas som ett uttryck för exceptionell produktivitet.",
            ),
            Source.OWNER,
            listOf("anakonda", "orm", "lång", "stopp i", "avlopp"),
            filing = "En långsträckt konstruktion hotar den lokala avloppsinfrastrukturen. Omedelbart ingripande från kommunens VA-avdelning begärs.",
            earthly = "Om frågan gäller hälsa beskriver jag symtom för vården. Om toaletten faktiskt är blockerad kontaktar jag den som ansvarar för avloppsanläggningen.",
        ),
        FecalClass(
            "FK-12", "Majskolven", null,
            "Företeelse med tydliga spår av osmälta majskorn.",
            Source.COLLECTED, "HAVERIRAPPORT · OBEROENDE GENOMRESA",
            said(
                Role.OFFICIAL,
                "Leveransen innehåller tydliga spår av majskorn.",
                "Kornen är i oförändrat skick.",
                "Majsen har alltså passerat hela systemet utan att låta sig påverkas av det.",
                "Den har därmed uppvisat större integritet än någon av de inblandade myndigheterna.",
                "Majsen har erbjudits anställning som oberoende granskare.",
            ),
            Source.GENERATED,
            listOf("majs"),
        ),
        FecalClass(
            "FK-13", "Panikskiten", null,
            "Uppstår med kortare varsel än det tar att hitta närmaste toalett.",
            Source.COLLECTED, "HAVERIRAPPORT · OTILLRÄCKLIGT VARSEL",
            said(
                Role.OFFICIAL,
                "Behovet anmäldes med kortare varsel än det tar att hitta närmaste toalett.",
                "Närmaste toalett ligger fyra minuter bort. Varslet var två.",
                "För att korta avståndet har kommunen beslutat att utreda fler toaletter.",
                "Utredningen är klar om fyra år.",
                "Användaren uppmanas att under tiden anmäla sina behov i god tid, helst skriftligen.",
            ),
            Source.GENERATED,
            listOf("panik", "akut", "bråttom", "hann", "sprang", "nödig"),
        ),
        FecalClass(
            "FK-T1", "Kraftkabeln", null,
            "En sammanhängande installation vars längd och konstruktion väcker frågor om både nätägare, dimensionering och framtida underhållsansvar.",
            Source.OWNER, "HAVERIRAPPORT · ANLÄGGNING UTAN BYGGLOV",
            said(
                Role.OFFICIAL,
                "Energimyndigheten har informerats.",
                "Anläggningen saknar bygglov.",
                "Rikard har förnekat all inblandning.",
            ) + said(
                Role.HELPFUL,
                "Jaaa, men vad trevligt! Jag ser att vi har fått in en kraftkabel!",
                "Har vi något installationsdatum?",
                "Jaha, redan levererad! Då ordnar jag besiktning!",
                "Nej, nej, du behöver inte spara den!",
                "Rikard kan väl bara skriva under här att han ansvarar för anläggningen?",
                direction = "hjälpsamt sprudlande", first = 950, last = 650,
            ) + listOf(
                Cue("Det finns inga kraftkablar.", Role.CEREMONY, "helt oberörd", 950),
                Cue("Den observerade konstruktionen är en del av vårt långsiktiga infrastrukturprogram.", Role.CEREMONY, null, 1200),
                Cue("Dess ursprung är sekretessbelagt.", Role.CEREMONY, null, 1200),
            ),
            Source.OWNER,
            listOf("kabel"),
        ),
    )

    /** Folklistans tretton, i den ordning de fastställdes. */
    val classical: List<FecalClass> get() = classes.filter { it.classical }

    fun byId(id: String): FecalClass? = classes.firstOrNull { it.id == id }

    fun report(c: FecalClass): Scene = Scene("ANALYSRESULTAT · ${c.name.uppercase()}", c.report, c.reportSource, c.reportTitle)

    fun earthly(c: FecalClass): String = c.earthly ?: GENERAL_EARTHLY

    /**
     * Väljer klass åt en fritt berättad anekdot: flest träffar vinner, och vid lika gäller listans
     * ordning. Svarar null när ingenting känns igen.
     */
    fun classify(text: String): FecalClass? {
        val lower = text.lowercase()
        return classes.map { c -> c to c.keywords.count { it in lower } }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }

    /** Beskrivningar som inte går att placera. De långa blir doktorsavhandlingar. */
    fun unclassified(text: String): Scene =
        if (text.trim().length >= THESIS_LENGTH) {
            Scene(
                "DEN FEKALA DOKTORSAVHANDLINGEN",
                said(
                    Role.OFFICIAL,
                    "En avföringsbeskrivning med orimlig detaljrikedom har inkommit.",
                    "Beskrivningen är nu längre än händelsen.",
                    "Den har därför antagits som doktorsavhandling.",
                    "Disputationen äger rum på porslinstronen. Opponenten har begärt att få slippa.",
                ),
                Source.GENERATED, "INSTITUTET FÖR AVANCERAD EXKREMENTOLOGI",
            )
        } else {
            Scene(
                "ÄRENDET SAKNAR KLASSIFICERING",
                said(
                    Role.OFFICIAL,
                    "Manifestationen går inte att placera i någon av de tretton kategorierna.",
                    "Den har därför klassificerats som oklassificerad.",
                    "Det är också en klassificering. Ärendet är därmed avslutat.",
                ),
                Source.GENERATED,
            )
        }

    private const val THESIS_LENGTH = 220

    private val concernStems = listOf("blod", "blöd", "smärt", "feber", "viktnedg", "tjärsvart", "svimma", "kräk")
    private val concernWords = setOf("ont", "svart", "svarta")

    /**
     * Allvar före satir: nämner texten blod, smärta eller andra oroande tecken ska ingen haverirapport
     * skrivas. Då visas bara verklighetsankaret.
     */
    fun isConcern(text: String): Boolean =
        text.lowercase().split(Regex("[^a-zåäöé]+")).any { word ->
            word in concernWords || concernStems.any { word.startsWith(it) }
        }

    const val CONCERN = "Det du beskriver hör hemma i vården, inte i en satirisk analysator. " +
        "Ring 1177 för sjukvårdsrådgivning. Ring 112 om det är akut."

    /** Haverirapporten som text, för den som själv väljer att kopiera den. */
    fun transcript(c: FecalClass): String = buildString {
        appendLine(AUTHORITY)
        appendLine("${c.id} · ${c.name.uppercase()}${c.alias?.let { " – $it" } ?: ""}")
        appendLine(c.description)
        appendLine()
        c.report.forEach { cue -> appendLine(if (cue.role == null) cue.text else "${cue.role.title}: ${cue.text}") }
        appendLine()
        appendLine(FOOTNOTE)
        append(DISCLAIMER)
    }

    // ---- Verklighetsankaret: jordiskt, sant och utan diagnoser ----

    const val ANCHOR_BUTTON = "VA – ÄR DET HÄR PÅ RIKTIGT?"
    const val ANCHOR_TITLE = "VERKLIGHETSANKARE: TELLUS"

    /** Satirens väg fram till ankaret. Lila, inte sant. */
    val anchorIntro: List<String> = listOf(
        "Du befinner dig på planeten Tellus.",
        "Den lokala sjukvården tillämpar i regel inte Fekala Haverikommissionens klassificeringar.",
        "Om du behöver beskriva avföringens form och konsistens kan du använda Bristolskalan.",
        "Varning: Den saknar kategorier för pappersförbrukning, Poseidons kyss och paranormal avföring.",
        "Detta är ett känt konstruktionsfel i den jordiska standarden.",
    )

    /** Bristolskalans sju typer. Fakta. */
    val bristol: List<String> = listOf(
        "Separata, hårda klumpar.",
        "Korvformad men knölig.",
        "Korvformad med sprickor på ytan.",
        "Slät, mjuk och korv- eller ormformad.",
        "Mjuka klumpar med tydliga kanter.",
        "Lösa, grötiga bitar med ojämna kanter.",
        "Helt flytande, utan fasta bitar.",
    )

    /** Det som faktiskt går att säga. Fakta. */
    val anchorFacts: List<String> = listOf(
        "Bristolskalan beskriver avföringens form och konsistens i sju typer. Den kan vara ett stöd när man pratar med vårdpersonal, men den fastställer ingen orsak och ingen diagnos.",
        "De galaktiska namnen är skämt. Det går inte att översätta ett av dem till en Bristoltyp, eftersom systemen inte beskriver samma saker.",
        "En förändrad toalettvana eller orolig mage är en observation, inte en bestämd sjukdom. Ihållande eller oroande förändringar bör bedömas i vården, särskilt vid blod i avföringen.",
        "I Sverige ger 1177 sjukvårdsrådgivning dygnet runt. Ring 112 vid akut fara.",
        "Är toaletten faktiskt igensatt är det en fråga för den som ansvarar för avloppet, inte för vården.",
    )

    const val ANCHOR_SOURCE = "Källa för Bristolskalan: Lewis, S.J. och Heaton, K.W. (1997), ”Stool form scale as a useful guide to intestinal transit time”, " +
        "Scandinavian Journal of Gastroenterology, 32(9), 920–924."

    /** Haverikommissionens kommentar till den jordiska standarden. Lila igen. */
    val anchorComment: List<String> = listOf(
        "Den jordiska medicinen har lyckats sammanfatta mänsklig avföring i sju typer.",
        "Man har däremot ännu inte lyckats förklara hur ett toalettbesök kan förbruka fyra rullar papper utan att närma sig ett avslut.",
        "Forskningen har uppenbarligen långt kvar.",
    )

    // ---- Särskilda utredningar ----

    val inquiries: List<Scene> = listOf(
        Scene(
            "STANDARDENS URSPRUNG",
            said(
                Role.OFFICIAL,
                "Efter omfattande granskning har myndigheten fastställt att bajslistan saknar identifierbar upphovsperson.",
                "Ingen kan därför ställas till ansvar för dess innehåll.",
                "Listan har följaktligen upphöjts till officiell standard.",
                "Eventuella motsägelser mellan versionerna utgör inte felaktigheter, utan bevis för forskningens fortlöpande utveckling.",
                "Vetenskaplig konsensus har uppnåtts genom att samtliga invändningar spolats bort.",
            ),
            Source.OWNER, "AVDELNINGEN FÖR ANONYMA MEN OANTASTLIGA FORSKNINGSRESULTAT",
        ),
        Scene(
            "JURISDIKTION OCH TELLUS",
            listOf(
                Cue("Fekala Haverikommissionen använder en omfattande folkloristisk klassificering, ursprungligen spridd genom anonym internetfolklore.", pauseBeforeMillis = 400),
                Cue("Systemet bygger på observationer av sådant som den jordiska medicinen hittills visat ett beklagligt begränsat intresse för:"),
                Cue("Toalettpapprets förbrukning.", pauseBeforeMillis = 300),
                Cue("Avföringens eventuella övernaturliga försvinnande.", pauseBeforeMillis = 300),
                Cue("Porslinets strukturella belastning.", pauseBeforeMillis = 300),
                Cue("Risken för återkommande leveranser efter avslutad handläggning.", pauseBeforeMillis = 300),
                Cue("Oönskad kontakt mellan skinkor och toalettvatten.", pauseBeforeMillis = 300),
                Cue("Förmiddagars övergång till kvällar i väntan på resultat.", pauseBeforeMillis = 300),
                Cue("Avloppssystemets rätt att vägra medverkan.", pauseBeforeMillis = 300),
            ) + said(
                Role.OFFICIAL,
                "Om toalettbesöket mot förmodan ägt rum på planeten Tellus och användaren behöver kontakta den lokala sjukvården rekommenderas den där allmänt vedertagna Bristolskalan.",
                "Detta beror inte på att Bristolskalan är mer galaktiskt korrekt.",
                "Det beror på att jordiska vårdinrättningar ofta saknar administrativa rutiner för att ta emot en anmälan om Spökskiten.",
                "Fekala Haverikommissionen beklagar denna brist i deras utbildningssystem.",
                direction = "särskild bestämmelse för Tellus", first = 950,
            ),
            Source.OWNER, "GALAKTISKA FEKALFÖRVALTNINGEN",
        ),
        Scene(
            "TARMENS ADMINISTRATIVA DYGNSRYTM",
            said(
                Role.OFFICIAL,
                "Klockan är 08.00.",
                "Kaffet har serverats.",
                "Tarmen har kallats till obligatoriskt sammanträde.",
                "Tarmen har uteblivit.",
                "Detta betraktas som arbetsvägran.",
                "Användaren uppmanas att fortsätta invänta resultatet tills resultatet infinner sig.",
                "Eventuell försening av övriga livet ligger utanför myndighetens ansvarsområde.",
            ) + said(
                null,
                "Suboptimeringsverkets kommentar: Dagens tarmtömning har planerats med 100 procents precision.",
                "Att den inte inträffat är ett problem för verkligheten, inte planeringen.",
                first = 950, last = 650,
            ) + said(
                Role.PANIC,
                "MEDBORGARE!",
                "DEN HELIGA TARMORDNINGEN HAR BRUTITS!",
                "ETT SCHEMALAGT TOALETTBESÖK HAR UTEBLIVIT!",
                "SAMTLIGA RESPLANER MÅSTE OMEDELBART UNDERORDNAS DENNA NATIONELLA KATASTROF!",
                "VI HAR INTE TID ATT UNDERSÖKA ORSAKEN!",
                "VI MÅSTE FÖRST BESTÄMMA HUR ALLVARLIGT DET ÄR!",
                direction = "med stigande massmötesretorik", first = 950, last = 650,
            ),
            Source.OWNER, "GALAKTISKA TOALETTSCHEMAVERKET",
        ),
        Scene(
            "PORSLINSTRONENS BELÄGRING",
            listOf(
                Cue("Ursprungligt mål: Gå på toaletten och sedan hälsa på någon.", pauseBeforeMillis = 400),
                Cue("09.00: Toalettbesök planerat.", pauseBeforeMillis = 650),
                Cue("10.30: Toalettbesök pågår administrativt."),
                Cue("12.00: Förmiddagen omklassificerad till eftermiddag."),
                Cue("15.00: Avföringens färg, form och konsistens har fått en egen arbetsgrupp."),
                Cue("18.00: Den planerade visiten har uppgraderats till ett historiskt åtagande."),
                Cue("21.00: Slutrapporten är färdig. Besöket ställs in på grund av tidsbrist.", pauseBeforeMillis = 950),
            ) + said(
                Role.OFFICIAL,
                "Användaren avsåg att spara tid genom att slutföra ett toalettbesök före avfärd.",
                "Toalettbesöket har nu förbrukat hela dagen.",
                "Ingen tid finns kvar för avfärd.",
                "Behovet att planera toalettbesöket i relation till avfärden har därmed upphört.",
                "Problemet är löst genom att det ursprungliga syftet har avskaffats.",
                direction = "Suboptimeringsverkets utlåtande", first = 950,
            ),
            Source.OWNER, "FÖRMIDDAGEN SOM FÖRSVANN",
        ),
        Scene(
            "TRE ADMINISTRATIVA VERKLIGHETER",
            listOf(
                Cue("Ett och samma toalettbesök kan alltså tillhöra tre helt olika administrativa verkligheter:", Role.OFFICIAL, null, 400),
                Cue("Galaktisk folklore: Vad var det för sorts skit?", pauseBeforeMillis = 650),
                Cue("Jordisk sjukvård: Hur såg den ut och vilka symtom finns?"),
                Cue("Kommunalt VA: Varför kommer den inte längre härifrån?"),
                Cue("Samtliga myndigheter ansvarar endast för sin respektive del.", Role.OFFICIAL, null, 650),
                Cue("Om hela systemet kollapsar är det ett samordningsproblem.", Role.OFFICIAL),
                Cue("Samordningsproblem hänvisas till Rikard.", Role.OFFICIAL, null, 950),
            ),
            Source.OWNER, "FEKALA HAVERIKOMMISSIONENS SLUTSATS",
        ),
    )

    const val REVIEW_BUTTON = "[ BEGÄR OBEROENDE FEKAL GRANSKNING ]"

    val independentReview: Scene = Scene(
        "OBEROENDE FEKAL GRANSKNING",
        said(
            Role.OFFICIAL,
            "Ärendet har överlämnats till en andra oberoende myndighet.",
            "Myndigheten består av samma personer som den första, men sitter nu på andra sidan toaletten.",
            "Oberoendet är därmed säkerställt.",
        ),
    )

    // ---- Minispelet: Effektivisera tarmadministrationen ----

    fun canIncrease(state: BowelState): Boolean = state.cuts < MAX_CUTS && !state.reported

    /** ÖKA EFFEKTIVITETEN: fyra tryck, sedan finns inga toalettbesök kvar att effektivisera. */
    fun increase(state: BowelState): BowelScene? {
        if (!canIncrease(state)) return null
        val next = state.copy(cuts = state.cuts + 1)
        val scene = when (next.cuts) {
            1 -> Scene(
                "Första trycket – pappersransonen",
                said(Role.OFFICIAL, "Toalettpappersransonen halveras.", "Behovet är oförändrat.", "Besparingsmålet är uppnått."),
            )
            2 -> Scene(
                "Andra trycket – tidsåtgången",
                said(
                    Role.OFFICIAL,
                    "För att minska tidsåtgången halveras tiden för varje toalettbesök.",
                    "Ofärdiga ärenden återkommer som Andra-vågen-skiten.",
                    "Återbesök redovisas på en annan budgetpost.",
                ),
            )
            3 -> Scene(
                "Tredje trycket – dokumentationen",
                said(
                    Role.OFFICIAL,
                    "Dokumentationskravet fördubblas.",
                    "Varje avföringshändelse ska nu registreras med färg, form, konsistens, preliminär orsak och misstänkt ansvarig.",
                    "Registreringen får inte påbörjas innan toalettbesöket avslutats.",
                    "Toalettbesöket får inte avslutas innan registreringen är klar.",
                ),
            )
            else -> Scene(
                "Fjärde trycket – förbudet",
                said(
                    Role.CEREMONY,
                    "För att undvika återkommande avvikelser har samtliga toalettbesök tillfälligt förbjudits.",
                    "Avvikelserapporteringen har därmed minskat till noll.",
                    "Tarmförvaltningen har aldrig fungerat bättre.",
                    direction = "kallt och lågmält",
                ),
            )
        }
        return BowelScene(next, scene.withCollapse(next))
    }

    fun canSuboptimize(state: BowelState): Boolean = state.worse < MAX_WORSE && !state.reported

    /** SUBOPTIMERA ÄNNU MER: analysen växer tills den har ersatt det den skulle analysera. */
    fun suboptimize(state: BowelState): BowelScene? {
        if (!canSuboptimize(state)) return null
        val next = state.copy(worse = state.worse + 1)
        val scene = when (next.worse) {
            1 -> Scene("Optimera tarmadministrationen", said(Role.OFFICIAL, "Antalet obligatoriska avföringsobservationer har fördubblats."))
            2 -> Scene("Optimera tarmadministrationen", said(Role.OFFICIAL, "Varje observation måste nu granskas av två oberoende handläggare."))
            3 -> Scene(
                "Optimera tarmadministrationen",
                said(Role.OFFICIAL, "Samtliga resultat ska jämföras med föregående dags resultat, som tyvärr inte dokumenterades."),
            )
            else -> Scene(
                "Panikministern fastställer förklaringen",
                said(
                    Role.OFFICIAL,
                    "En avvikelse från ordinarie morgonrutin har registrerats.",
                    "Avvikelsen har ännu inte fått någon förklaring.",
                    "För att undvika onödig osäkerhet har Panikministern därför fastställt den allvarligaste tänkbara förklaringen.",
                    "Alla övriga aktiviteter, inklusive den planerade semestern, pausas tills den förutbestämda slutsatsen kan verifieras.",
                    "Om slutsatsen inte kan verifieras betraktas detta som ett bevisproblem.",
                    "Suboptimeringsverket gratulerar till ett mycket effektivt nyttjande av hela dagen.",
                ) + said(
                    Role.BABEL,
                    "Observerat: Magbesvär och förändrad toalettvana.",
                    "Slutsats: En bestämd diagnos.",
                    "Saknat led: Medicinsk utredning.",
                    "Katastrofens omfattning har fastställts innan dess existens har verifierats.",
                    direction = "torrt", first = 950,
                ),
            )
        }
        return BowelScene(next, scene.withCollapse(next))
    }

    private fun Scene.withCollapse(state: BowelState): Scene =
        if (!state.collapsed) this else copy(
            cues = cues + Cue("FULLSTÄNDIG EFFEKTIVISERING UPPNÅDD.", pauseBeforeMillis = 1200) + said(
                Role.OFFICIAL,
                "Vi har eliminerat alla avvikelser från toalettstatistiken genom att förbjuda nya toalettbesök.",
                "Tarmfunktionen är därmed administrativt normaliserad.",
                "Den medicinska nyttan är obefintlig.",
                "Den emotionella kostnaden har ökat kraftigt.",
                "Suboptimeringsverket gratulerar.",
                first = 650,
            ),
        )

    fun canReport(state: BowelState): Boolean = state.collapsed && !state.reported

    fun finalReport(state: BowelState): BowelScene? {
        if (!canReport(state)) return null
        return BowelScene(
            state.copy(reported = true),
            Scene(
                "SLUTRAPPORT FRÅN FEKALA HAVERIKOMMISSIONEN",
                said(
                    Role.OFFICIAL,
                    "Mänskligheten har i årtusenden försökt förstå sitt eget tarmsystem.",
                    "Efter omfattande forskning har kommissionen konstaterat att systemets största brist är att det vägrar följa kommunal kontorstid.",
                    "Vi rekommenderar därför att kroppen omedelbart tilldelas en personlig handläggare.",
                    "Handläggaren kommer inte att förbättra tarmfunktionen.",
                    "Men varje utebliven leverans kommer hädanefter att få ett diarienummer.",
                    "Äntligen ordning på skiten.",
                ),
            ),
        )
    }

    fun canClose(state: BowelState): Boolean = state.reported && !state.closed

    /** Det slutliga myndighetsbeslutet. Sista knappen. */
    fun close(state: BowelState): BowelScene? {
        if (!canClose(state)) return null
        return BowelScene(
            state.copy(closed = true),
            Scene(
                "SLUTLIGT MYNDIGHETSBESLUT",
                said(
                    Role.OFFICIAL,
                    "Den folkloristiska bajslistans tretton kategorier har godkänts för användning inom Guidens ordinarie galaktiska verksamhet.",
                    "Kraftkabeln har upptagits som ett separat galaktiskt tillägg.",
                    "Vid kontakt med jordisk sjukvård ska användaren erbjudas Bristolskalans sju typer och möjlighet att beskriva andra relevanta observationer.",
                    "Någon automatisk översättning mellan systemen är inte tillåten, eftersom systemen inte beskriver samma saker.",
                    "Att den jordiska klassificeringen endast innehåller sju kategorier medan den galaktiska innehåller tretton har granskats.",
                    "Den galaktiska klassificeringen har därmed nästan dubbelt så många kategorier.",
                    "Suboptimeringsverket har enhälligt slagit fast att den således är nästan dubbelt så vetenskaplig.",
                    "Jordiska invändningar tas emot på Bristol.",
                    "Galaktiska invändningar hänvisas till Rikard.",
                    last = 650,
                ) + Cue("ÄRENDET ÄR AVSLUTAT. SPOLA MED FÖRSIKTIGHET.", pauseBeforeMillis = 1200),
                authority = "GALAKTISKA FEKALA HAVERIKOMMISSIONEN",
            ),
        )
    }
}
