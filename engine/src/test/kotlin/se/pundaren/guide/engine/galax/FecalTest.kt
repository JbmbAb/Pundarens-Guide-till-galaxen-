package se.pundaren.guide.engine.galax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FecalTest {
    @Test fun `folklistan har tretton kategorier och Kraftkabeln är ett märkt tillägg`() {
        assertEquals(13, Fecal.classical.size)
        val additions = Fecal.classes - Fecal.classical.toSet()
        assertEquals(listOf("Kraftkabeln"), additions.map { it.name })
        assertEquals(Source.OWNER, additions.single().origin)
        assertEquals(Fecal.classes.size, Fecal.classes.map { it.id }.toSet().size)
        assertEquals(Fecal.classes.size, Fecal.classes.map { it.name }.toSet().size)
    }

    @Test fun `de kanoniska definitionerna gäller`() {
        val ghost = Fecal.classes.first { it.name == "Spökskiten" }
        assertTrue("pappret" in ghost.description && "skålen" in ghost.description)
        assertTrue("aldrig rent" in Fecal.classes.first { it.name == "Tuschpennan" }.description)
        assertTrue("kritvitt" in Fecal.classes.first { it.name == "Teflonskiten" }.description)
    }

    @Test fun `varje kategori har en haverirapport och nyskrivna rapporter är märkta`() {
        Fecal.classes.forEach { c ->
            assertTrue(c.name, c.report.size >= 3)
            assertTrue(c.name, c.keywords.isNotEmpty())
            assertTrue(c.name, Fecal.earthly(c).isNotBlank())
        }
        assertEquals(
            setOf("Spökskiten", "Tuschpennan", "Andra-vågen-skiten", "Falsklarmet", "Anakondan", "Kraftkabeln"),
            Fecal.classes.filter { it.reportSource == Source.OWNER }.map { it.name }.toSet(),
        )
    }

    @Test fun `en anekdot hamnar i rätt kategori`() {
        assertEquals("Tuschpennan", Fecal.classify("Det blir aldrig rent hur mycket papper jag än använder")?.name)
        assertEquals("Spökskiten", Fecal.classify("Den försvann ur skålen")?.name)
        assertEquals("Majskolven", Fecal.classify("det var majs i")?.name)
        assertEquals("Kraftkabeln", Fecal.classify("En riktig kraftkabel")?.name)
        assertNull(Fecal.classify(""))
        assertNull(Fecal.classify("xyz"))
        assertEquals("ÄRENDET SAKNAR KLASSIFICERING", Fecal.unclassified("xyz").title)
        assertEquals("DEN FEKALA DOKTORSAVHANDLINGEN", Fecal.unclassified("x".repeat(300)).title)
    }

    @Test fun `allvar går före satir`() {
        assertTrue(Fecal.isConcern("Det var blod i avföringen"))
        assertTrue(Fecal.isConcern("jag har ont i magen"))
        assertTrue(Fecal.isConcern("Den är svart"))
        assertFalse(Fecal.isConcern("Jag satt på kontoret"))
        assertFalse(Fecal.isConcern("Den försvann ur skålen"))
    }

    @Test fun `Bristolskalan har sju typer och ingen kategori pekar på någon av dem`() {
        assertEquals(7, Fecal.bristol.size)
        assertTrue("1997" in Fecal.ANCHOR_SOURCE)
        assertTrue(Fecal.anchorFacts.any { "ingen diagnos" in it })
    }

    @Test fun `fyra effektiviseringar förbjuder toalettbesöken`() {
        var state = BowelState()
        assertEquals(100, state.paperRationPercent)
        val titles = (1..4).map { Fecal.increase(state)!!.also { state = it.state }.scene.title }
        assertEquals(4, titles.toSet().size)
        assertEquals(50, state.paperRationPercent)
        assertEquals(50, state.visitTimePercent)
        assertFalse(state.visitsAllowed)
        assertNull(Fecal.increase(state))
        assertEquals(0, state.medicalBenefitPercent)
    }

    @Test fun `kollapsen ger slutrapporten och sedan myndighetsbeslutet`() {
        var state = BowelState()
        repeat(4) { state = Fecal.increase(state)!!.state }
        assertNull(Fecal.finalReport(state))
        var cost = state.emotionalCostPercent
        repeat(3) {
            state = Fecal.suboptimize(state)!!.state
            assertTrue(state.emotionalCostPercent > cost)
            cost = state.emotionalCostPercent
        }
        val last = Fecal.suboptimize(state)!!
        assertTrue(last.state.collapsed)
        assertTrue(last.scene.cues.any { it.role == Role.BABEL && it.text.startsWith("Saknat led") })
        assertTrue(last.scene.cues.any { it.text == "FULLSTÄNDIG EFFEKTIVISERING UPPNÅDD." })
        assertNull("beslutet väntar på rapporten", Fecal.close(last.state))

        val report = Fecal.finalReport(last.state)!!
        assertEquals("Äntligen ordning på skiten.", report.scene.cues.last().text)
        val closed = Fecal.close(report.state)
        assertNotNull(closed)
        assertEquals("ÄRENDET ÄR AVSLUTAT. SPOLA MED FÖRSIKTIGHET.", closed!!.scene.cues.last().text)
        assertNull(Fecal.close(closed.state))
        assertNull(Fecal.increase(closed.state))
        assertNull(Fecal.suboptimize(closed.state))
    }

    @Test fun `inga verkliga namn utom Rikard`() {
        val all = Fecal.classes.flatMap { c -> listOf(c.name, c.description, c.filing.orEmpty(), Fecal.transcript(c)) } +
            (Fecal.inquiries + Fecal.independentReview).flatMap { s -> listOf(s.title) + s.cues.map { it.text } } +
            Fecal.anchorIntro + Fecal.anchorFacts + Fecal.anchorComment
        listOf("bonnie", "min mor", "mamma", "moder", "jimmy", "ulla-bella", "ulla bella").forEach { name ->
            assertFalse("$name förekommer", all.any { name in it.lowercase() })
        }
    }
}
