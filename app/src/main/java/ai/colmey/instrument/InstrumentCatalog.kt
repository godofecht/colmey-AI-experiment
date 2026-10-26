package ai.colmey.instrument

data class InstrumentOrigin(val region: String, val history: String)

object InstrumentCatalog {
    private val entries = mapOf(
        "guitar" to InstrumentOrigin("Europe", "The modern guitar developed in Europe, with earlier Mediterranean and Middle Eastern ancestors."),
        "piano" to InstrumentOrigin("Italy", "Bartolomeo Cristofori invented the piano in Italy around 1700."),
        "sitar" to InstrumentOrigin("Indian subcontinent", "The sitar developed in the Indian subcontinent."),
        "tabla" to InstrumentOrigin("Indian subcontinent", "Tabla developed in the Indian subcontinent."),
        "violin" to InstrumentOrigin("Northern Italy", "The violin developed in northern Italy during the sixteenth century.")
    )

    fun match(label: String): Pair<String, InstrumentOrigin>? {
        val normalized = label.lowercase(java.util.Locale.ROOT)
        return entries.entries.firstOrNull { normalized.contains(it.key) }?.let { it.key to it.value }
    }

    fun origin(label: String): InstrumentOrigin? {
        val normalized = label.lowercase(java.util.Locale.ROOT)
        return entries.entries.firstOrNull { normalized.contains(it.key) }?.value
    }
}
