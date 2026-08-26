package com.ado.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpLinkTextTest {
    @Test
    fun detectsOneHttpsUrl() = assertUrls(
        "See https://example.com/item",
        "https://example.com/item",
    )

    @Test
    fun detectsOneHttpUrl() = assertUrls(
        "Open http://example.com/path",
        "http://example.com/path",
    )

    @Test
    fun detectsMultipleUrls() = assertUrls(
        "Two: https://one.test and http://two.test/path",
        "https://one.test",
        "http://two.test/path",
    )

    @Test
    fun preservesQueryString() = assertUrls(
        "https://example.com/a?x=1&y=2",
        "https://example.com/a?x=1&y=2",
    )

    @Test
    fun preservesFragment() = assertUrls(
        "https://example.com/page#section",
        "https://example.com/page#section",
    )

    @Test
    fun excludesTrailingPeriod() = assertUrls(
        "See https://example.com/item.",
        "https://example.com/item",
    )

    @Test
    fun excludesTrailingComma() = assertUrls(
        "Links: https://example.com/a, then text",
        "https://example.com/a",
    )

    @Test
    fun excludesUnmatchedSurroundingParenthesis() = assertUrls(
        "Open (https://example.com/path)",
        "https://example.com/path",
    )

    @Test
    fun preservesBalancedParenthesisInsideUrl() = assertUrls(
        "Open (https://example.com/function(foo)).",
        "https://example.com/function(foo)",
    )

    @Test
    fun ignoresNonHttpSchemes() = assertUrls(
        "mailto:user@example.com file:///tmp/test intent://example javascript:alert(1)",
    )

    @Test
    fun ignoresWwwAddress() = assertUrls("www.example.com")

    @Test
    fun ordinaryTextHasNoLinks() = assertUrls("Nothing to open here")

    @Test
    fun emptyTextHasNoLinks() = assertUrls("")

    private fun assertUrls(text: String, vararg expected: String) {
        assertEquals(expected.toList(), detectHttpLinks(text).map { it.url })
    }
}
