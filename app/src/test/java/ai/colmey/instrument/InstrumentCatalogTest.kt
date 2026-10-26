package ai.colmey.instrument

import org.junit.Assert.*
import org.junit.Test

class InstrumentCatalogTest {
    @Test fun allSupportedInstrumentsHaveOrigins() {
        assertTrue(InstrumentCatalog.names().size >= 15)
        InstrumentCatalog.names().forEach { name ->
            val origin = InstrumentCatalog.origin(name)
            assertNotNull("Missing origin for $name", origin)
            assertTrue(origin!!.region.isNotBlank())
            assertTrue(origin.history.isNotBlank())
        }
    }

    @Test fun commonInstrumentsAreRecognisedFromModelLabels() {
        val examples = mapOf(
            "Acoustic guitar" to "guitar",
            "Grand piano" to "piano",
            "Violin" to "violin",
            "Saxophone" to "saxophone",
            "Drum" to "drum",
            "Flute" to "flute",
            "Sitar" to "sitar",
            "Tabla" to "tabla"
        )
        examples.forEach { (label, expected) ->
            assertEquals(expected, InstrumentRecognition.identify(listOf(RecognitionLabel(label, 0.92f)))?.name)
        }
    }

    @Test fun chooseHighestConfidenceRecognisedInstrument() {
        val result = InstrumentRecognition.identify(listOf(
            RecognitionLabel("Guitar", 0.61f),
            RecognitionLabel("Furniture", 0.99f),
            RecognitionLabel("Piano", 0.85f)
        ))
        assertEquals("piano", result?.name)
        assertEquals(0.85f, result!!.confidence, 0.0001f)
    }

    @Test fun genericMusicalInstrumentIsNotFabricatedAsSpecificInstrument() {
        assertNull(InstrumentRecognition.identify(listOf(RecognitionLabel("Musical instrument", 0.99f))))
        assertNull(InstrumentRecognition.identify(listOf(RecognitionLabel("Music", 0.95f))))
    }

    @Test fun lowConfidenceOrInvalidScoresAreRejected() {
        assertNull(InstrumentRecognition.identify(listOf(RecognitionLabel("Guitar", 0.49f))))
        assertNull(InstrumentRecognition.identify(listOf(RecognitionLabel("Guitar", Float.NaN))))
        assertNull(InstrumentRecognition.identify(listOf(RecognitionLabel("Guitar", Float.POSITIVE_INFINITY))))
    }

    @Test fun matchingRespectsWordBoundariesAndCase() {
        assertEquals("guitar", InstrumentCatalog.match("ACOUSTIC GUITAR")?.first)
        assertNull(InstrumentCatalog.match("guitarist"))
        assertNull(InstrumentCatalog.match("unknown object"))
    }

    @Test fun emptyPredictionsProduceNoIdentification() {
        assertNull(InstrumentRecognition.identify(emptyList()))
    }
}
