// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTextTest {
    private fun links(s: String) = LinkText.segments(s).filterIsInstance<TextSegment.Link>()

    @Test fun httpsUrlIsALinkAsGiven() {
        val l = links("https://www.example-pharmacy.com/refill?id=7").single()
        assertEquals("https://www.example-pharmacy.com/refill?id=7", l.url)
    }

    @Test fun wwwGetsHttps() = assertEquals("https://www.walgreens.com/refill", links("www.walgreens.com/refill").single().url)

    @Test fun bareDomainGetsHttps() = assertEquals("https://example.com/rx", links("example.com/rx").single().url)

    @Test fun linkInsideSentenceKeepsSurroundingText() {
        val segs = LinkText.segments("Refills at www.cvs.com, call first")
        assertEquals(listOf("Refills at ", "www.cvs.com", ", call first"), segs.map { if (it is TextSegment.Plain) it.text else (it as TextSegment.Link).text })
    }

    @Test fun trailingPunctuationIsNotPartOfTheLink() {
        assertEquals("www.cvs.com", links("See www.cvs.com.").single().text)
        assertEquals("https://x.com/a", links("(https://x.com/a)").single().text)
    }

    @Test fun twoLinks() = assertEquals(2, links("a.com and b.org/x").size)

    @Test fun ordinaryTextIsNeverLinked() {
        for (s in listOf("12.5 mg", "saco community", "Dr.Smith", "e.g. with food", "8170619", "Mon. Tue.", "v1.2.3", "javascript:alert(1)", "mailto:a@b.com", "ftp://files.example.com", "me@example.com")) {
            assertFalse("should not link: $s", LinkText.hasLink(s))
        }
    }

    @Test fun emptyAndPlainText() {
        assertEquals(listOf(TextSegment.Plain("")), LinkText.segments(""))
        assertEquals(listOf(TextSegment.Plain("hello")), LinkText.segments("hello"))
    }

    @Test fun upperCaseSchemeAndDomainWork() = assertTrue(LinkText.hasLink("HTTPS://EXAMPLE.COM"))
}
