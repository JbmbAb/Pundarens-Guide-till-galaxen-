package se.pundaren.guide.engine.archive

import se.pundaren.guide.engine.json.GuideMoshi
import se.pundaren.guide.engine.json.QuoteDto
import se.pundaren.guide.engine.model.ArchiveBundle
import se.pundaren.guide.engine.model.ArchiveQuote
import se.pundaren.guide.engine.model.BabelExample
import se.pundaren.guide.engine.model.BabelPattern
import se.pundaren.guide.engine.model.BabelRule
import se.pundaren.guide.engine.model.DialogueTurn
import se.pundaren.guide.engine.model.LexiconEntry
import se.pundaren.guide.engine.model.LoadedArchive
import se.pundaren.guide.engine.model.NavComment
import se.pundaren.guide.engine.model.Provenance
import se.pundaren.guide.engine.model.SatiricalLabel
import se.pundaren.guide.engine.model.ValuePair
import se.pundaren.guide.engine.model.parseProvenance

object ArchiveLoader {
    fun load(bundle: ArchiveBundle): LoadedArchive {
        val quoteFile = bundle.quotesJson?.let { GuideMoshi.quotes(it) }
        val rulesFile = bundle.rulesJson?.let { GuideMoshi.rules(it) }
        val examplesFile = bundle.examplesJson?.let { GuideMoshi.examples(it) }
        val lexiconFile = bundle.lexiconJson?.let { GuideMoshi.lexicon(it) }
        val navigationFile = bundle.navigationJson?.let { GuideMoshi.navigation(it) }

        val storedQuotes = buildList {
            quoteFile?.quotes.orEmpty().forEach { add(toQuote(it, defaultProvenance = null)) }
            quoteFile?.originals.orEmpty().forEach { add(toQuote(it, defaultProvenance = Provenance.ORIGINAL)) }
        }.filterNotNull()
        val originalIds = storedQuotes.filter { it.provenance == Provenance.ORIGINAL }.map { it.id }.toSet()
        val variations = quoteFile?.variations.orEmpty().mapNotNull { dto ->
            val quote = toQuote(dto, defaultProvenance = Provenance.VARIATION) ?: return@mapNotNull null
            if (!quote.provenance.isGenerated) return@mapNotNull quote
            val parent = quote.originalId
            if (parent == null || parent !in originalIds) return@mapNotNull null
            quote
        }
        val quotes = (storedQuotes + variations).distinctBy { it.id }

        val rules = rulesFile?.rules.orEmpty().mapNotNull { dto ->
            val id = dto.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val function = dto.function?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            BabelRule(
                id = id,
                function = function,
                relatedQuoteIds = dto.relatedQuoteIds.orEmpty(),
                thematicQuoteIds = dto.thematicQuoteIds.orEmpty(),
                provenance = parseProvenance(dto.provenance),
                note = dto.note,
            )
        }
        val patterns = rulesFile?.patterns.orEmpty().mapNotNull { dto ->
            val id = dto.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = dto.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            BabelPattern(
                id = id,
                title = title,
                status = dto.status,
                isEstablishedPsychologicalFact = dto.isEstablishedPsychologicalFact == true,
                description = dto.description,
                babelStonerRule = dto.babelStonerRule,
                sourceNote = dto.sourceNote,
            )
        }
        val examples = examplesFile?.examples.orEmpty().mapNotNull { dto ->
            val id = dto.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            BabelExample(
                id = id,
                title = dto.title,
                provenance = parseProvenance(dto.provenance).let { parsed ->
                    if (parsed == Provenance.UNSPECIFIED) Provenance.KONSTRUERAD else parsed
                },
                text = dto.text,
                original = dto.original,
                response = dto.response,
                turns = dto.turns.orEmpty().mapNotNull { turn ->
                    val text = turn.text ?: return@mapNotNull null
                    DialogueTurn(role = turn.role.orEmpty(), text = text)
                },
                result = dto.result,
                labels = dto.logicAnalysis?.labels.orEmpty().mapNotNull { label ->
                    val name = label.label ?: return@mapNotNull null
                    val value = label.value ?: return@mapNotNull null
                    SatiricalLabel(name, value)
                },
                analysisNote = dto.logicAnalysis?.note,
                sourceNote = dto.sourceNote,
            )
        }
        val lexicon = lexiconFile?.entries.orEmpty().mapNotNull { dto ->
            val id = dto.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val term = dto.term?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val text = dto.text ?: return@mapNotNull null
            val provenance = parseProvenance(dto.provenance).let { parsed ->
                if (parsed == Provenance.UNSPECIFIED && dto.isMainVersion == true) {
                    Provenance.REDAKTIONELL
                } else {
                    parsed
                }
            }
            LexiconEntry(
                id = id,
                term = term,
                text = text,
                provenance = provenance,
                partOfSpeech = dto.partOfSpeech,
                sourceNote = dto.sourceNote,
                author = dto.author,
                isMainVersion = dto.isMainVersion == true,
            )
        }
        val comments = navigationFile?.comments.orEmpty().mapNotNull { dto ->
            val id = dto.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val text = dto.text?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            NavComment(
                id = id,
                text = text,
                situation = dto.situation?.ifBlank { "general" } ?: "general",
                usesRikard = dto.usesRikard == true,
                neverContradictsRoute = dto.neverContradictsRoute != false,
                provenance = parseProvenance(dto.provenance).let { parsed ->
                    if (parsed == Provenance.UNSPECIFIED) Provenance.KONSTRUERAD else parsed
                },
            )
        }
        val disclaimers = listOfNotNull(rulesFile?.disclaimer, examplesFile?.disclaimer, navigationFile?.disclaimer)
        return LoadedArchive(
            compilationNote = quoteFile?.compilation?.note,
            disclaimers = disclaimers,
            valuePairs = quoteFile?.valuePairs.orEmpty().mapNotNull { dto ->
                val id = dto.id ?: return@mapNotNull null
                val a = dto.a ?: return@mapNotNull null
                val b = dto.b ?: return@mapNotNull null
                ValuePair(id, a, b)
            },
            quotes = quotes,
            rules = rules,
            patterns = patterns,
            examples = examples,
            lexicon = lexicon,
            navigationComments = comments,
        )
    }

    private fun toQuote(dto: QuoteDto, defaultProvenance: Provenance?): ArchiveQuote? {
        val id = dto.id?.takeIf { it.isNotBlank() } ?: return null
        val text = dto.text ?: return null
        val fromField = when {
            !dto.provenance.isNullOrBlank() -> parseProvenance(dto.provenance)
            !dto.type.isNullOrBlank() -> parseProvenance(dto.type)
            else -> Provenance.UNSPECIFIED
        }
        val provenance = if (fromField == Provenance.UNSPECIFIED && defaultProvenance != null) {
            defaultProvenance
        } else {
            fromField
        }
        return ArchiveQuote(
            id = id,
            title = dto.title,
            text = text,
            provenance = provenance,
            themes = dto.themes.orEmpty(),
            sourceNote = dto.sourceNote,
            originalId = dto.originalId,
            approved = dto.approved,
            author = dto.author,
            normalizedText = dto.normalized?.text,
            normalizedNote = dto.normalized?.note,
            legacyId = dto.legacyId,
            summarizesQuoteId = dto.summarizesQuoteId,
        )
    }
}
