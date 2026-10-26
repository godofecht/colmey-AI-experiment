package ai.colmey.instrument

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentCatalogTest {
    @Test fun originLookup() {
        assertEquals("Italy", InstrumentCatalog.origin("piano")?.region)
        assertEquals("Indian subcontinent", InstrumentCatalog.origin("sitar")?.region)
    }

    @Test fun unknownInstrumentDoesNotInventOrigin() {
        assertEquals(null, InstrumentCatalog.origin("unknown object"))
    }

    @Test fun matchingIsCaseInsensitive() {
        assertTrue(InstrumentCatalog.origin("Acoustic GUITAR") != null)
    }
}
