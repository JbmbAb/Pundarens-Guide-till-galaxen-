package se.pundaren.guide.engine.galax

import se.pundaren.guide.engine.galax.PanicKind.BLAME
import se.pundaren.guide.engine.galax.PanicKind.DAILY
import se.pundaren.guide.engine.galax.PanicKind.MEMORY
import se.pundaren.guide.engine.galax.PanicKind.NEGATIVE
import se.pundaren.guide.engine.galax.PanicKind.OTHER
import se.pundaren.guide.engine.galax.PanicKind.POSITIVE
import se.pundaren.guide.engine.galax.PanicKind.PROMISE
import se.pundaren.guide.engine.galax.PanicKind.REPEAT
import se.pundaren.guide.engine.galax.PanicKind.TONE
import se.pundaren.guide.engine.galax.PanicKind.TOTAL_PANIC
import se.pundaren.guide.engine.galax.Source.GENERATED
import se.pundaren.guide.engine.galax.Source.OWNER

/**
 * De galaktiska besluten. [Source.OWNER] är rader ur ägarens underlag, oförändrade men ibland i utdrag
 * och med en av raderna som rubrik; [Source.GENERATED] är nyskrivet för appen i samma röst och väntar
 * på ägarens omdöme. Inga verkliga personer nämns utom den mytologiserade Rikard, som kanon redan har
 * gjort till galaktisk syndabock.
 */
object GalacticBank {
    private const val ORDEN = "Den Heliga Självbildens Orden"
    private const val SUBOPT = "Galaktiska Suboptimeringsverket"
    private const val HAVERI = "Galaktiska Haverikommissionen"
    private const val SKULD = "Galaktiska Skuldfördelningsverket"
    private const val MINNE = "Minnesrevisionsverket"
    private const val UPPREPNING = "Upprepningsinstitutet"
    private const val UPPSKJUT = "Uppskjutningsinstitutet"
    private const val LOFTE = "Engångslöftesinspektionen"
    private const val IMMUN = "Ordens immunologiska enhet"

    val decrees: List<Decree> = listOf(
        // --- TOTAL PANIK: någon har råkat erkänna ett fel ---
        Decree(
            "TP-01", ORDEN, Grip.SHAME_ALLERGY, Scale.CONVERSATION, "TOTAL PANIK!",
            listOf(
                "FÖRSTA BUDORDET HAR ÖVERTRÄTTS!",
                "Självbildens integritet är hotad.",
                "Ett obehörigt erkännande har nått medvetandet.",
                "Vittnen ska omedelbart granskas.",
                "Bevismaterialet har skickats till Löftesregistrets krypteringsavdelning.",
                "Krypteringen har havererat.",
                "Bevisen är borta.",
                "Ursäkterna är intakta.",
                "Rikard har tilldelats ansvar.",
                "Budord II aktiverat.",
                "INGEN HAR HAFT FEL.",
                "SJÄLVBILD ÅTERSTÄLLD.",
            ),
            setOf(TOTAL_PANIC), OWNER,
        ),
        Decree(
            "TP-02", IMMUN, Grip.SHAME_ALLERGY, Scale.CONVERSATION, "VARNING! SJÄLVBILDSALLERGEN UPPTÄCKT!",
            listOf(
                "Ett tidigare medgivande har kommit i kontakt med sin egen praktiska innebörd.",
                "Patienten uppvisar akut risk för sammanhängande självinsikt.",
                "Immunförsvaret har därför neutraliserat medgivandet.",
                "Uttalandet ska hädanefter betraktas som missförstått, felciterat eller framfört under ogynnsamma förhållanden.",
                "Självbilden är återställd.",
                "Eventuell kvarstående verklighet får behandlas symptomatiskt.",
            ),
            setOf(TOTAL_PANIC), OWNER,
        ),
        Decree(
            "TP-03", ORDEN, Grip.SHAME_ALLERGY, Scale.CONVERSATION, "SJÄLVBILDSLARM, NIVÅ RÖD",
            listOf(
                "Ett erkännande har lämnat byggnaden utan eskort.",
                "Erkännandet omklassificeras till ett missförstånd.",
                "Missförståndet omklassificeras till ett citat ur sitt sammanhang.",
                "Sammanhanget har inte gått att nå för en kommentar.",
                "Den som hörde erkännandet granskas för överdriven hörsel.",
                "Rikard var inte i rummet. Det skärper misstankarna.",
                "SJÄLVBILD ÅTERSTÄLLD. INGENTING HAR SAGTS.",
            ),
            setOf(TOTAL_PANIC), GENERATED,
        ),

        // --- Positiv emotionell logik ---
        Decree(
            "PO-01", HAVERI, Grip.EMOTIONAL_PROOF, Scale.EVERYDAY, "TOTALT OBEFOGAD FRAMTIDSTRO DETEKTERAD!",
            listOf(
                "Användaren uppvisar optimism utan att ha inväntat erforderlig katastrof.",
                "En reservkatastrof har därför beställts.",
                "Rikard har redan förnekat inblandning.",
                "Detta är oroande eftersom katastrofen ännu inte inträffat.",
            ),
            setOf(POSITIVE), OWNER,
        ),
        Decree(
            "PO-02", HAVERI, Grip.EMOTIONAL_PROOF, Scale.EVERYDAY, "OTILLÅTEN OPTIMISM REGISTRERAD.",
            listOf(
                "Användaren har påbörjat framtidstro utan erforderligt haveritillstånd.",
                "Universum har överklagat.",
            ),
            setOf(POSITIVE), OWNER,
        ),
        Decree(
            "PO-03", SUBOPT, Grip.EMOTIONAL_PROOF, Scale.EVERYDAY, "GLÄDJE UTAN TÄCKNING",
            listOf(
                "Stämningen har förbättrats innan problemet har lösts.",
                "Ordningsföljden strider mot gällande rutin.",
                "Problemet har ombetts att skynda sig, så att glädjen kan godkännas i efterhand.",
                "Tills dess räknas användarens goda humör som ett lån.",
                "Räntan fastställs av Rikard.",
            ),
            setOf(POSITIVE), GENERATED,
            anchor = "Det som är känt är att det känns bra just nu. Det får det göra.",
        ),
        Decree(
            "PO-04", UPPSKJUT, Grip.EMOTIONAL_PROOF, Scale.EVERYDAY, "KÄNSLAN HAR UTFÖRT ARBETET",
            listOf(
                "Det känns som att det ordnar sig.",
                "Känslan har därmed gjort jobbet, och någon åtgärd vore dubbelarbete.",
                "Institutet rekommenderar att allt lämnas som det är tills det har ordnat sig av sig självt.",
                "Metoden har prövats tidigare. Utvärderingen är uppskjuten.",
            ),
            setOf(POSITIVE), GENERATED,
            anchor = "Att det känns bra och att det är ordnat är två olika saker. Det ena utesluter inte det andra.",
        ),

        // --- Negativ emotionell logik ---
        Decree(
            "NE-01", HAVERI, Grip.EMOTIONAL_PROOF, Scale.CONVERSATION, "KORREKT!",
            listOf(
                "Ett obesvarat meddelande utgör slutgiltigt bevis för kollektivt mänskligt avståndstagande.",
                "Att någon kan ha varit upptagen har avvisats som ett försök att försvaga slutsatsens känslomässiga styrka.",
            ),
            setOf(NEGATIVE), OWNER,
            anchor = "Det enda som säkert är känt är att du inte fått svar.",
            needs = listOf("svar", "meddeland", "hör inte av", "hört av", "läst"),
        ),
        Decree(
            "NE-02", HAVERI, Grip.EMOTIONAL_PROOF, Scale.CONVERSATION, "BINDANDE BEVIS",
            listOf(
                "Frånvaron av svar har upphöjts till bindande bevis för fullständigt ointresse.",
                "Eventuella andra förklaringar har inte kallats till förhandlingen.",
            ),
            setOf(NEGATIVE), OWNER,
            anchor = "Det är känt att inget svar kommit. Orsaken är ännu inte fastställd.",
            needs = listOf("svar", "meddeland", "hör inte av", "hört av", "läst"),
        ),
        Decree(
            "NE-03", HAVERI, Grip.EMOTIONAL_PROOF, Scale.EVERYDAY, "KÄNSLAN HAR VUNNIT MÅLET",
            listOf(
                "Känslan har hörts som enda vittne.",
                "Den var mycket övertygande.",
                "Övrig bevisning har därför förklarats överflödig.",
                "Framtiden döms i sin frånvaro till att gå åt helvete.",
                "Domen kan överklagas så fort framtiden har inträffat.",
            ),
            setOf(NEGATIVE), GENERATED,
            anchor = "Det som är känt är hur det känns just nu. Hur det går vet ingen än.",
        ),
        Decree(
            "NE-04", MINNE, Grip.MEMORY_REVISION, Scale.CONVERSATION, "HISTORIKEN HAR FÖRENKLATS",
            listOf(
                "Ett av orden ”alltid”, ”aldrig”, ”alla” eller ”ingen” har använts.",
                "Samtliga tillfällen då det gick annorlunda har därmed strukits ur historiken.",
                "Strykningen sparar arkivutrymme och åtskilliga nyanser.",
                "De strukna tillfällena har rätt att överklaga, men minns inte längre att de har funnits.",
            ),
            setOf(NEGATIVE), GENERATED,
            anchor = "”Alltid” och ”aldrig” stämmer sällan. Ett par gånger räcker för att det ska kännas så.",
            needs = listOf("alltid", "aldrig", "alla ", "ingen ", "ingenting", "allt "),
        ),
        Decree(
            "NE-05", SUBOPT, Grip.SUBOPTIMIZATION, Scale.EVERYDAY, "FULL PRODUKTION",
            listOf(
                "Oron har arbetat hela natten och redovisar full produktion.",
                "Problemet är oförändrat.",
                "Produktiviteten bedöms ändå som mycket god, eftersom den mäts i antal tänkta katastrofer.",
                "Ingen av katastroferna har behövt inträffa för att räknas.",
            ),
            setOf(NEGATIVE), GENERATED,
            anchor = "Att oroa sig och att göra något åt saken är två olika saker. Bara det ena syns i efterhand.",
        ),

        // --- Löften ---
        Decree(
            "PR-01", LOFTE, Grip.PROMISE_AS_DONE, Scale.CONVERSATION, "LÖFTE UPPFATTAT",
            listOf(
                "Enligt sjätte budordet är förbättringen därmed genomförd.",
                "Någon åtgärd behövs inte och avråds, eftersom den kan skapa jämförelseproblem.",
                "Löftet kan diarieföras i Galaktiska Löftesregistret, som garanterar att det försvinner.",
            ),
            setOf(PROMISE), GENERATED,
            anchor = "Ett löfte är inte gjort förrän det är gjort.",
        ),
        Decree(
            "PR-02", LOFTE, Grip.PROMISE_AS_DONE, Scale.CONVERSATION, "TRE FÖRBÄTTRINGAR GODKÄNDA",
            listOf(
                "Samma löfte har lämnats in för tredje gången den här veckan.",
                "Inspektionen godkänner det som tre separata förbättringar.",
                "Utförandet har inte efterfrågats, av hänsyn till löftesgivaren.",
                "Den som räknar gångerna granskas enligt sjunde budordet.",
            ),
            setOf(PROMISE, DAILY), GENERATED,
            anchor = "Tre löften om samma sak är fortfarande noll gånger gjort.",
        ),
        Decree(
            "PR-03", LOFTE, Grip.EXCUSE_OVERRIDE, Scale.CONVERSATION, "GOTTGÖRELSE SLUTFÖRD",
            listOf(
                "Erkännande registrerat som slutförd gottgörelse.",
                "Ytterligare frågor utgör obehörigt dubbelarbete.",
            ),
            setOf(PROMISE, TONE), OWNER,
            anchor = "Ett erkännande och en faktisk förändring är olika saker.",
            needs = listOf("redan erkänt", "redan sagt förlåt", "redan bett om ursäkt", "har ju erkänt", "sagt förlåt", "bett om ursäkt"),
        ),

        // --- Skuld ---
        Decree(
            "BL-01", HAVERI, Grip.RIKARD_PROTOCOL, Scale.CONVERSATION, "BESLUT GH-0001/EVIGHETEN",
            listOf(
                "Rikard har härmed förnekat all inblandning i minst två incidenter.",
                "Förnekandena har registrerats.",
                "Att Rikard eventuellt inte befunnit sig på platsen har noterats som ett logistiskt problem, inte ett juridiskt.",
                "Kommissionens uppdrag är därför inte att utreda skuldfrågan.",
                "Uppdraget är att verifiera det redan utdömda straffet.",
                "Om verkligheten inte kan bekräfta domen ska verkligheten komplettera sin bevisning.",
            ),
            setOf(BLAME, DAILY), OWNER,
        ),
        Decree(
            "BL-02", HAVERI, Grip.RIKARD_PROTOCOL, Scale.CONVERSATION, "NÄRVARO EJ STYRKT",
            listOf(
                "Rikards närvaro har inte kunnat styrkas.",
                "Hans frånvaro bedöms som ett försök att undgå ansvar.",
            ),
            setOf(BLAME), OWNER,
        ),
        Decree(
            "BL-03", HAVERI, Grip.RIKARD_PROTOCOL, Scale.CONVERSATION, "ERBJUDANDE AVSLAGET",
            listOf(
                "Rikard har erbjudit sig att redogöra för vad som hände.",
                "Då detta kan påverka utredningens resultat har erbjudandet avslagits.",
            ),
            setOf(BLAME, DAILY), OWNER,
        ),
        Decree(
            "BL-04", SKULD, Grip.COST_SHIFTING, Scale.CONVERSATION, "CIRKULÄR EKONOMI GODKÄND",
            listOf(
                "Skulden har återvunnits med 100 procents effektivitet.",
                "Ingen har blivit fri från skuld.",
                "Däremot har ursprungsfrågan försvunnit.",
            ),
            setOf(BLAME, DAILY), OWNER,
        ),
        Decree(
            "BL-05", SKULD, Grip.COST_SHIFTING, Scale.CONVERSATION, "ANSVARIG FÖR UPPTÄCKTEN",
            listOf("Den som uppmärksammade problemet har gjort problemet synligt och hålls därför ansvarig för dess existens."),
            setOf(BLAME, TONE), OWNER,
        ),
        Decree(
            "BL-06", HAVERI, Grip.RIKARD_PROTOCOL, Scale.CONVERSATION, "SKULDFRÅGAN ÄR AVGJORD",
            listOf(
                "Utredningen kan därmed inledas.",
                "Den ska bekräfta det som redan har beslutats, och har fått obegränsad tid på sig att göra det snabbt.",
                "Den som frågar vad som faktiskt hände ombeds att inte försvåra arbetet.",
            ),
            setOf(BLAME), GENERATED,
            anchor = "Vem som bär skulden och vad som hände är två olika frågor. Den andra går att undersöka.",
        ),
        Decree(
            "BL-07", "Galaktiska Familjeförvaltningen, avdelningen för retroaktiv skuldfördelning", Grip.COST_SHIFTING, Scale.SOCIETY,
            "RETROAKTIV SKULDFÖRDELNING",
            listOf(
                "En minderårig har varit föremål för en placering utanför hemmet.",
                "Placeringen har fått konsekvenser för flera personer.",
                "Då konsekvenserna måste ha en ansvarig, och den placerade utgör ärendets mest lättidentifierade person, har skulden tilldelats denne.",
                "Att vederbörande själv var barnet som berördes av beslutet betraktas som en administrativ rollkonflikt.",
                "Barnets behov av stöd har omklassificerats till en belastning på familjesystemets självbild.",
                "Eventuell invändning om att vuxna och myndigheter fattade besluten ska handläggas av Nämnden för Oönskad Ansvarsfördelning.",
                "Den nämnden har lagts ned med hänvisning till att någon måste ta ansvar för kostnaden.",
                "Den placerade hålls därför även ansvarig för nedläggningen.",
            ),
            setOf(DAILY), OWNER,
        ),
        Decree(
            "BL-08", "Galaktiska Familjeförvaltningen", Grip.VICTIM_SHIELD, Scale.CONVERSATION, "SÄRSKILT BESLUT OM SKAM",
            listOf(
                "Den berörda personen har uttryckt obehag inför att ha fått skulden.",
                "Detta obehag har skapat obehag hos andra.",
                "Den ursprungliga skulden utökas därför med ansvar för det nya obehaget.",
                "Den som vill undvika ytterligare skuld uppmanas att sluta reagera på den skuld som redan tilldelats.",
                "Om reaktionen upphör ska tystnaden tolkas som ett erkännande.",
                "Om reaktionen fortsätter ska den tolkas som samarbetsproblem.",
                "Myndigheten har därmed säkerställt att samtliga tänkbara utfall bekräftar samma slutsats.",
            ),
            setOf(BLAME, TONE, DAILY), OWNER,
        ),

        // --- Tonfallet har blivit huvudfråga ---
        Decree(
            "TO-01", SUBOPT, Grip.SUBOPTIMIZATION, Scale.CONVERSATION, "SUBOPTIMERINGSVERKET GRATULERAR!",
            listOf(
                "Tonfallsfrågan: 100 % vunnen.",
                "Ursprunglig sakfråga: 0 % löst.",
                "Tilliten: kraftigt reducerad.",
                "Självbilden: oskadd.",
                "Ärendet avslutat med utmärkt resultat.",
            ),
            setOf(TONE, DAILY), OWNER,
            anchor = "Den ursprungliga frågan har ännu inte besvarats.",
        ),
        Decree(
            "TO-02", ORDEN, Grip.VICTIM_SHIELD, Scale.CONVERSATION, "ORDENS BESLUT",
            listOf("Den som påminner om ett brutet löfte granskas för överdriven noggrannhet."),
            setOf(TONE), OWNER,
            anchor = "Att någon minns ett löfte innebär inte att personen orsakat att löftet inte hölls.",
        ),
        Decree(
            "TO-03", ORDEN, Grip.VICTIM_SHIELD, Scale.CONVERSATION, "OFFERKOFTANS SAKRAMENT",
            listOf(
                "Du skall aldrig bära skuld när du kan bära en offerkofta.",
                "Din oro är ett bevis på ditt engagemang. Faktiskt handlande är en separat budgetpost.",
                "Den som påpekar att något inte blivit gjort ska omedelbart tillfrågas varför denne är så hård mot den som hade tänkt göra det.",
            ),
            setOf(TONE, DAILY), OWNER,
        ),
        Decree(
            "TO-04", SUBOPT, Grip.VICTIM_SHIELD, Scale.CONVERSATION, "FRÅGAN HAR BYTTS UT",
            listOf(
                "Sakfrågan har bytts mot en diskussion om frågeställarens tonfall.",
                "Bytet är slutgiltigt och kan inte reklameras.",
                "Den ursprungliga frågan förvaras på okänd ort, i väntan på att någon orkar ställa den igen.",
                "Den som gör det noteras för upprepat tjat.",
            ),
            setOf(TONE), GENERATED,
            anchor = "Hur en fråga ställs och vad den gäller är två olika saker. Den går att besvara ändå.",
        ),

        // --- Minnet ---
        Decree(
            "MI-01", MINNE, Grip.MEMORY_REVISION, Scale.CONVERSATION, "HISTORIKEN HAR JUSTERATS",
            listOf(
                "Det man inte minns har inte hänt.",
                "Historiken har justerats så att den stämmer med minnet.",
                "Dokument som påstår något annat har kallats till samtal.",
                "De minns inte heller längre.",
            ),
            setOf(MEMORY), GENERATED,
            anchor = "”Jag minns inte” betyder inte ”det har inte hänt”.",
        ),
        Decree(
            "MI-02", MINNE, Grip.MEMORY_REVISION, Scale.CONVERSATION, "NY UPPLAGA",
            listOf(
                "Gårdagens samtal har reviderats.",
                "I den nya upplagan sades ingenting.",
                "Den som minns den gamla upplagan ombeds lämna in den för destruktion.",
                "Kvitto lämnas inte, eftersom det skulle utgöra ett minne.",
            ),
            setOf(MEMORY, DAILY), GENERATED,
            anchor = "Det som har sagts har sagts, även när ingen vill minnas det.",
        ),

        // --- Upprepning ---
        Decree(
            "UP-01", UPPREPNING, Grip.REPEAT_AS_PROGRESS, Scale.EVERYDAY, "ETT HELT NYTT FÖRSÖK",
            listOf(
                "Metoden har prövats förut, med känt resultat.",
                "Den prövas nu igen och har därför fått ett nytt namn.",
                "Under det nya namnet saknas tidigare misslyckanden.",
                "Institutet ser fram emot att bli lika förvånat som förra gången.",
            ),
            setOf(REPEAT, DAILY), GENERATED,
            anchor = "Samma metod brukar ge samma resultat.",
        ),
        Decree(
            "UP-02", UPPREPNING, Grip.REPEAT_AS_PROGRESS, Scale.CONVERSATION, "FÖRKLARINGEN HAR UPPDATERATS",
            listOf(
                "Beteendet är oförändrat.",
                "Förklaringen har däremot bytts ut och är nu betydligt bättre.",
                "Enligt femte budordet räknas detta som en genomförd förändring.",
                "Nästa förklaring är redan under utveckling.",
            ),
            setOf(REPEAT, DAILY), GENERATED,
            anchor = "En ny förklaring ändrar inte det som faktiskt görs.",
        ),

        // --- Allt annat ---
        Decree(
            "OV-01", "Galaktiska fältredaktionen", Grip.SUBOPTIMIZATION, Scale.EVERYDAY, "UTTALANDET HAR DIARIEFÖRTS",
            listOf(
                "Det har remitterats till den avdelning som minst berörs.",
                "Svar kan väntas inom en tidsrymd som myndigheten bestämmer i efterhand.",
                "Under tiden ombeds verkligheten att avvakta.",
            ),
            setOf(OTHER), GENERATED,
        ),
        Decree(
            "OV-02", SUBOPT, Grip.SUBOPTIMIZATION, Scale.EVERYDAY, "MÅLUPPFYLLELSE: 100 %",
            listOf(
                "Frågan har besvarats på enklaste sätt: genom att besvara en annan fråga.",
                "Den andra frågan var betydligt lättare.",
                "Den ursprungliga frågan har hänvisats till Rikard.",
            ),
            setOf(OTHER), GENERATED,
        ),
        Decree(
            "OV-03", "Departementet för selektiv verklighet", Grip.EXCUSE_OVERRIDE, Scale.EVERYDAY, "ALLT HAR GÅTT RÄTT TILL",
            listOf(
                "Ärendet har granskats ur samtliga synvinklar utom användarens.",
                "Granskningen visar att allt har gått rätt till.",
                "Att det gick fel påverkar inte den bedömningen.",
            ),
            setOf(OTHER), GENERATED,
        ),
        Decree(
            "OV-04", ORDEN, Grip.EXCUSE_OVERRIDE, Scale.EVERYDAY, "VERKLIGHETEN FÖRELÄGGS",
            listOf(
                "Det användaren beskriver stämmer inte med självbilden.",
                "Enligt tionde budordet ska verkligheten därför inkomma med skriftlig ursäkt.",
                "Ursäkten ska vara undertecknad, bevittnad och uppriktigt menad.",
                "Verkligheten har hittills inte svarat, vilket noteras som samarbetsproblem.",
            ),
            setOf(OTHER), GENERATED,
        ),

        // --- Dagens galaktiska beslut: vardag, samhälle och Guiden själv ---
        Decree(
            "DA-01", SUBOPT, Grip.SUBOPTIMIZATION, Scale.EVERYDAY, "GRATTIS!",
            listOf(
                "Rutten har kortats med arton meter.",
                "Den nya vägen kräver fyrhjulsdrift, vinsch och betydligt mer tålamod.",
                "Besparingen är godkänd.",
            ),
            setOf(DAILY), OWNER,
        ),
        Decree(
            "DA-02", "Myndigheten för Administrativ Sanering", Grip.COST_SHIFTING, Scale.SOCIETY, "PROBLEMET ÄR LÖST",
            listOf(
                "Vattnet är nu rent.",
                "Föroreningen finns i filtret.",
                "Filtret tillhör en annan förvaltning.",
                "Problemet är därför löst.",
            ),
            setOf(DAILY), OWNER,
            anchor = "Aktivt kol kan avskilja PFAS från vatten. De ämnen som fångats upp måste fortfarande hanteras i filtermaterialet.",
        ),
        Decree(
            "DA-03", "Institutet för Beklaglig Substitution", Grip.COST_SHIFTING, Scale.SOCIETY, "INNOVATION",
            listOf(
                "Den gamla miljöfarliga kemikalien har fasats ut.",
                "Den nya kemikalien har ännu inte klassificerats som miljöfarlig.",
                "Detta betraktas som innovation.",
                "Eventuella framtida skador kommer att utredas av framtiden, som ännu inte inkommit med invändningar.",
            ),
            setOf(DAILY), OWNER,
        ),
        Decree(
            "DA-04", "Galaktiska Kostnadsöverföringsnämnden", Grip.COST_SHIFTING, Scale.SOCIETY, "FÖRORENAREN BETALAR.",
            listOf(
                "För att inte försämra förorenarens konkurrenskraft har betalningsskyldigheten överförts till skattebetalaren.",
                "Principen har därmed följts i andemening, utan att onödigtvis belasta den som principen avsåg.",
            ),
            setOf(DAILY), OWNER,
        ),
        Decree(
            "DA-05", "Ministeriet för Framtida Eftergifter", Grip.COST_SHIFTING, Scale.SOCIETY, "HÅLLBARHETSMÅLET ÄR UPPFYLLT",
            listOf(
                "Vi har skapat ett problem som kommer att överleva samtliga nuvarande beslutsfattare.",
                "Projektets långsiktighet är därför exceptionell.",
            ),
            setOf(DAILY), OWNER,
        ),
        Decree(
            "DA-06", "Utvecklingsavdelningen", Grip.SUBOPTIMIZATION, Scale.SELF, "TEKNISK FRAMGÅNG!",
            listOf(
                "Appens batteriförbrukning har minskat med 2 %.",
                "Detta har uppnåtts genom att stänga av navigeringen.",
                "Användbarheten har därmed minskat med 100 %.",
                "Effektiviteten har förbättrats.",
                "Utvecklingsavdelningen gratuleras.",
            ),
            setOf(DAILY), OWNER,
        ),
        Decree(
            "DA-07", "Utvecklingsavdelningen", Grip.SUBOPTIMIZATION, Scale.SELF, "TEKNISK RAPPORT",
            listOf(
                "Utvecklingsteamet har ägnat tre månader åt att skapa universums mest avancerade system för att undvika upprepade skämt.",
                "Systemet fungerar perfekt.",
                "Tyvärr finns ännu bara ett skämt.",
                "Det handlar om Rikard.",
                "Rikard bestrider detta.",
            ),
            setOf(DAILY), OWNER,
        ),
        Decree(
            "DA-08", ORDEN, Grip.MEMORY_REVISION, Scale.SOCIETY, "OFFICIELLT FÖRNEKANDE",
            listOf(
                "Att flera personer oberoende av varandra upprepar samma beteende är en ren tillfällighet.",
                "Att beteendet återkommer förändrar inte denna slutsats.",
                "Eventuella fortsatta likheter ska utredas som misstänkt mönsterigenkänning.",
                "All annan inblandning förnekas.",
            ),
            setOf(DAILY), OWNER,
        ),
        Decree(
            "DA-09", SUBOPT, Grip.SUBOPTIMIZATION, Scale.SOCIETY, "GRUNDSATS",
            listOf("Det som mäts har förbättrats.", "Det som försämrats mäts inte här."),
            setOf(DAILY), OWNER,
        ),
        Decree(
            "DA-10", SUBOPT, Grip.SUBOPTIMIZATION, Scale.SOCIETY, "SLUTRAPPORT",
            listOf(
                "Den lokala framgången har verifierats.",
                "Helheten har ännu inte hörts.",
                "Ärendet kan därför med gott samvete avslutas.",
            ),
            setOf(DAILY), OWNER,
        ),
        Decree(
            "DA-11", SUBOPT, Grip.COST_SHIFTING, Scale.SOCIETY, "FULLSTÄNDIG FRAMGÅNG",
            listOf(
                "Produktionskostnaden har minskat.",
                "Följdkostnaden har flyttats till kommande generationer.",
                "Det tekniska framsteget bedöms därför som en fullständig framgång.",
                "Framtida generationer har ännu inte inkommit med invändningar.",
                "Deras frånvaro tolkas som samtycke.",
            ),
            setOf(DAILY), OWNER,
        ),
        Decree(
            "DA-12", ORDEN, Grip.VICTIM_SHIELD, Scale.SOCIETY, "OFFERKOFTANS SAKRAMENT, FORTSÄTTNING",
            listOf(
                "Den som känner klimatångest skall tillgodoräknas miljöinsatser motsvarande känslans intensitet.",
                "Flygskam är avdragsgill under förutsättning att den deklareras före boarding.",
                "Kan verkligheten inte förändras, skall dess benämning omprövas.",
            ),
            setOf(DAILY), OWNER,
        ),
        Decree(
            "DA-13", "Galaktiska Riktningsverket", Grip.SUBOPTIMIZATION, Scale.EVERYDAY, "BÅDA HÅLLEN GÄLLER",
            listOf(
                "Vänster har överklagat beslutet att höger finns.",
                "Överklagandet har bifallits i sak men avslagits i riktning.",
                "Tills vidare gäller båda hållen, var för sig och aldrig samtidigt.",
                "Den som har kört fel ombeds att ha gjort det i god tro.",
            ),
            setOf(DAILY), GENERATED,
        ),
        Decree(
            "DA-14", UPPSKJUT, Grip.EXCUSE_OVERRIDE, Scale.EVERYDAY, "DAGENS ÄRENDEN ÄR FLYTTADE",
            listOf(
                "Dagens ärenden har flyttats till i morgon.",
                "Morgondagens ärenden har av utrymmesskäl flyttats till i går.",
                "Gårdagen är därmed fullbokad och tar inte emot fler misslyckanden.",
                "Institutet tackar för visat tålamod, som också har skjutits upp.",
            ),
            setOf(DAILY), GENERATED,
        ),
        Decree(
            "DA-15", "Galaktiska Löftesregistret", Grip.PROMISE_AS_DONE, Scale.CONVERSATION, "ÅRETS LÖFTEN ÄR SAMMANSTÄLLDA",
            listOf(
                "Samtliga är uppfyllda enligt sjätte budordet.",
                "Inget av dem har genomförts.",
                "Skillnaden utreds av en arbetsgrupp som har lovat att återkomma.",
            ),
            setOf(DAILY, PROMISE), GENERATED,
        ),
        Decree(
            "DA-16", IMMUN, Grip.SHAME_ALLERGY, Scale.CONVERSATION, "LOKALEN ÄR UTRYMD",
            listOf(
                "Ordet ”ansvar” har påträffats i ett samtal.",
                "Saneringen beräknas ta resten av kvällen.",
                "Den som yttrade ordet ombeds att i fortsättningen använda en omskrivning, till exempel ”vädret”.",
            ),
            setOf(DAILY), GENERATED,
        ),
        Decree(
            "DA-17", "Galaktiska Offerfördelningsverket", Grip.VICTIM_SHIELD, Scale.CONVERSATION, "DAGENS OFFERROLLER ÄR SLUT",
            listOf(
                "Den som kom först fick samtliga.",
                "Övriga inblandade hänvisas till rollen som förövare, där tillgången är god.",
                "Byten medges inte.",
            ),
            setOf(DAILY, TONE), GENERATED,
        ),
        Decree(
            "DA-18", SUBOPT, Grip.SUBOPTIMIZATION, Scale.EVERYDAY, "DISKEN HAR INFORMERATS",
            listOf(
                "Disken är inte diskad, men diskussionen om vem som brukar diska har avslutats med seger.",
                "Segraren har därmed gjort sin del.",
                "Disken står kvar i väntan på omprövning.",
            ),
            setOf(DAILY), GENERATED,
        ),
        Decree(
            "DA-19", "Departementet för selektiv verklighet", Grip.SUBOPTIMIZATION, Scale.SOCIETY, "HANDLÄGGNINGEN FLYTER PÅ",
            listOf(
                "Två beslut har visat sig motsäga varandra.",
                "Båda står fast.",
                "Motsägelsen har fått ett eget diarienummer och räknas hädanefter som ett tredje beslut.",
                "Det tredje beslutet motsäger de två första.",
            ),
            setOf(DAILY), GENERATED,
        ),
        Decree(
            "DA-20", HAVERI, Grip.RIKARD_PROTOCOL, Scale.SOCIETY, "UPPTÄCKTEN UTREDS",
            listOf(
                "En myndighet har efter trettio år upptäckt det som alla redan visste.",
                "Ytterligare fyra års utredning behövs för att fastställa om upptäckten får registreras som en upptäckt.",
                "Under tiden gäller att ingenting har upptäckts.",
                "Den som önskar överklaga hänvisas till Rikard.",
            ),
            setOf(DAILY), GENERATED,
        ),
    )

    /** Läggs sist i ett beslut när absurditetsgraden räcker och beslutet inte redan nämner Rikard. */
    val rikardClosers: List<Pair<String, Source>> = listOf(
        "Rikard har redan förnekat inblandning." to OWNER,
        "Rikard har ännu inte kommenterat situationen." to OWNER,
        "Den som önskar överklaga hänvisas till Rikard." to OWNER,
        "Rikard ansvarar för terrängen." to OWNER,
        "Rikard var inte där. Det noteras som försvårande." to GENERATED,
        "Bristen på samband med Rikard utgör ytterligare ett indicium." to GENERATED,
    )

    /** Andra gången samma mekanism träffas under en session. */
    val secondHit: List<String> = listOf(
        "Ärendet känns igen. Det har därför fått ett nytt namn.",
        "Samma mekanism har påträffats för andra gången. Det räknas som ett nytt försök.",
    )

    /** Tredje gången och därefter. */
    val laterHits: List<String> = listOf(
        "Guiden noterar att detta har hänt förut. Mönsterigenkänning är som bekant förbjuden.",
        "Upprepningsinstitutet gratulerar till ännu ett helt nytt försök.",
        "Enstaka tillfälligheter är enklare att administrera. Detta bokförs därför som en sådan, igen.",
    )

    /** Insamlade förvrängda ordspråk (docs/SAMLAD-PROJEKTKONTEXT.md, avsnitt 13). Upphovet är inte fastställt. */
    val proverbs: List<String> = listOf(
        "Den man ligger med får bädda.",
        "Den som tar, han har.",
        "Det man inte minns, har inte hänt.",
        "Det man inte vet, har man ingen aning om.",
        "Det vi inte gör idag slipper vi göra om i morgon.",
        "Förtala är silver, förtiga är guld.",
        "Gammal kärlek kostar alltid.",
        "Har man slagit yxan i båten får man ro som fan.",
        "Liten stuga kallas ofta stort dass.",
        "Man måste kyssa många grodor innan man hittar sitt svin.",
        "Den som bara kollar i backspegeln kör oftare i diket.",
        "Den som dricker ensam har bara sig själv att fylla.",
        "Den som gräver en grop åt andra får 88:50 i timmen.",
        "Den som spar han har men inte roligt.",
        "Det finns inga dumma frågor, bara dumma människor.",
        "Det man inte har i huvudet får man ha mellan benen.",
        "Det som inte tar död på en... får en i alla fall att må riktigt dåligt.",
        "Det är med kärlek som med whisky, den tar slut.",
        "Dåliga flickor är inte det sämsta.",
        "Framtiden var bättre förr.",
        "Friskt vågat, halva inne.",
        "Först till skrivarkön får först skriva ut.",
        "Hårt arbete lönar sig i framtiden, lathet lönar sig nu.",
        "Ju fler kockar desto större fackförening.",
        "Kvinnans list övergår när mannen får stånd.",
        "Livet är hårt, men orättvist.",
        "Man har inte roligare än man köper ut för.",
        "Man lever bara en gång och många inte ens det.",
        "Obeslutsamhet är nyckeln till flexibilitet.",
        "Problem är till för att skjutas upp, för att sedan glömmas bort.",
        "Ropa inte gay förrän du är inne i häcken.",
        "Älska din nästa, men var diskret.",
        "Även den starke får ta i när han skiter.",
        "Även i de lugnaste vatten drunknar man",
    )
}
