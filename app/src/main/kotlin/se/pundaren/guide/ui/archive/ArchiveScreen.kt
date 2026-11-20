package se.pundaren.guide.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.pundaren.guide.engine.model.ArchiveQuote
import se.pundaren.guide.engine.model.LoadedArchive
import se.pundaren.guide.engine.model.Provenance
import se.pundaren.guide.ui.GuideCard
import se.pundaren.guide.ui.ProvenanceChip
import se.pundaren.guide.ui.SectionLabel
import se.pundaren.guide.ui.WisdomText

@Composable
fun ArchiveScreen(archive: LoadedArchive) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        item {
            SectionLabel("Citatarkiv")
            Text(text = "Originalen står kvar", style = MaterialTheme.typography.headlineLarge)
            archive.compilationNote?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        quoteSection("Original", archive.quotes.filter { it.provenance == Provenance.ORIGINAL })
        quoteSection("Bearbetad", archive.quotes.filter { it.provenance == Provenance.BEARBETAD })
        quoteSection("Redaktionell", archive.quotes.filter { it.provenance == Provenance.REDAKTIONELL })
        quoteSection(
            "Variationer och förslag",
            archive.quotes.filter { it.provenance == Provenance.VARIATION || it.provenance == Provenance.FORSLAG || it.provenance == Provenance.KONSTRUERAD },
        )
        if (archive.valuePairs.isNotEmpty()) {
            item { SectionLabel("Värdepar") }
            items(archive.valuePairs, key = { it.id }) { pair ->
                GuideCard {
                    Text(text = "${pair.a} ↔ ${pair.b}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        item { SectionLabel("Lexikon") }
        items(archive.lexicon, key = { it.id }) { entry ->
            GuideCard {
                ProvenanceChip(entry.provenance)
                Text(text = entry.term, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                Text(text = entry.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                entry.sourceNote?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item { SectionLabel("Konstruerade exempel") }
        items(archive.examples, key = { it.id }) { example ->
            GuideCard {
                ProvenanceChip(example.provenance)
                Text(text = example.title ?: example.id, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                val body = example.response ?: example.text ?: example.turns.joinToString("\n") { "${it.role}: ${it.text}" }
                Text(text = body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private fun LazyListScope.quoteSection(title: String, quotes: List<ArchiveQuote>) {
    if (quotes.isEmpty()) return
    item { SectionLabel(title) }
    items(quotes, key = { it.id }) { quote -> QuoteCard(quote) }
}

@Composable
private fun QuoteCard(quote: ArchiveQuote) {
    GuideCard {
        ProvenanceChip(quote.provenance, quote.approved)
        quote.title?.let {
            Text(text = it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        if (quote.provenance == Provenance.ORIGINAL) {
            WisdomText(text = quote.text, modifier = Modifier.padding(top = 8.dp))
        } else {
            Text(text = quote.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        }
        if (quote.provenance != Provenance.ORIGINAL) {
            Text(
                text = "Inte ett personligt originalcitat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        quote.author?.let {
            Text(text = it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        }
        if (!quote.normalizedText.isNullOrBlank()) {
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Normaliserad text, inte original", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                Text(text = quote.normalizedText.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                quote.normalizedNote?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
