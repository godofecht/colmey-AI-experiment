package ai.colmey.instrument

import java.util.Locale

data class InstrumentOrigin(val region: String, val history: String)
data class RecognitionLabel(val text: String, val confidence: Float)
data class InstrumentMatch(val name: String, val origin: InstrumentOrigin, val confidence: Float)

object InstrumentCatalog {
    private val entries = linkedMapOf(
        "guitar" to InstrumentOrigin("Europe", "The modern guitar developed in Europe, with earlier Mediterranean and Middle Eastern ancestors."),
        "piano" to InstrumentOrigin("Italy", "Bartolomeo Cristofori invented the piano in Italy around 1700."),
        "sitar" to InstrumentOrigin("Indian subcontinent", "The sitar developed in the Indian subcontinent."),
        "tabla" to InstrumentOrigin("Indian subcontinent", "Tabla developed in the Indian subcontinent."),
        "violin" to InstrumentOrigin("Northern Italy", "The violin developed in northern Italy during the sixteenth century."),
        "saxophone" to InstrumentOrigin("Belgium", "Adolphe Sax invented the saxophone in Belgium in the 1840s."),
        "trumpet" to InstrumentOrigin("Multiple regions", "Ancient trumpets appeared in many regions; the modern valved trumpet developed in Europe."),
        "flute" to InstrumentOrigin("Multiple regions", "Flutes arose independently in many cultures since prehistory."),
        "drum" to InstrumentOrigin("Multiple regions", "Drums have independent origins across cultures."),
        "banjo" to InstrumentOrigin("Caribbean and North America", "The banjo developed among African-descended communities."),
        "harp" to InstrumentOrigin("Africa and Western Asia", "Harps are documented in ancient Africa and Western Asia."),
        "clarinet" to InstrumentOrigin("Germany", "The clarinet developed in Germany around 1700."),
        "accordion" to InstrumentOrigin("Europe", "The accordion developed in Europe in the early nineteenth century."),
        "ukulele" to InstrumentOrigin("Hawaii", "The ukulele developed in Hawaii from Portuguese-introduced instruments."),
        "cello" to InstrumentOrigin("Italy", "The cello developed in Italy during the sixteenth century."),
        "organ" to InstrumentOrigin("Ancient Greece", "The pipe organ traces its roots to the ancient Greek hydraulis.")
    )

    fun names(): List<String> = entries.keys.toList()

    fun origin(name: String): InstrumentOrigin? = match(name)?.second

    fun match(label: String): Pair<String, InstrumentOrigin>? {
        val normalized = label.trim().lowercase(Locale.ROOT)
        return entries.entries.firstOrNull { entry ->
            Regex("(?<![a-z])" + Regex.escape(entry.key) + "(?![a-z])").containsMatchIn(normalized)
        }?.let { it.key to it.value }
    }
}

object InstrumentRecognition {
    const val MIN_CONFIDENCE = 0.50f

    fun identify(labels: List<RecognitionLabel>): InstrumentMatch? =
        labels.asSequence().filter { it.confidence.isFinite() && it.confidence >= MIN_CONFIDENCE }
            .mapNotNull { label ->
                val match = InstrumentCatalog.match(label.text) ?: return@mapNotNull null
                InstrumentMatch(match.first, match.second, label.confidence)
            }.maxByOrNull { it.confidence }
}
