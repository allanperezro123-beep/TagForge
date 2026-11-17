package com.tagforge.domain.history

import com.tagforge.domain.ndef.spec.RecordSpec
import com.tagforge.domain.ndef.spec.RecordSpecEncoder
import kotlin.test.Test
import kotlin.test.assertEquals

/** S10: credentials and tokens inside a URL must not be shown in history lines or copied into exports. */
class UrlRedactionTest {

    @Test
    fun aPlainUrlIsUntouched() {
        assertEquals("https://example.com/path?page=2&q=nfc", UrlRedaction.redact("https://example.com/path?page=2&q=nfc"))
    }

    @Test
    fun userInfoIsRemoved() {
        assertEquals("https://example.com/a", UrlRedaction.redact("https://admin:hunter2@example.com/a"))
        assertEquals("ftp://files.example.com", UrlRedaction.redact("ftp://bob@files.example.com"))
    }

    @Test
    fun anAtSignInThePathOrQueryIsNotUserInfo() {
        assertEquals("https://example.com/@me?mail=a@b.c", UrlRedaction.redact("https://example.com/@me?mail=a@b.c"))
    }

    @Test
    fun sensitiveQueryValuesAreMaskedWhateverTheCaseOfTheKey() {
        val keys = listOf("token", "key", "apikey", "api_key", "access_token", "password", "pwd", "secret", "sig", "signature", "auth", "Token", "API_KEY")
        keys.forEach { key ->
            assertEquals("https://x.io/p?$key=***", UrlRedaction.redact("https://x.io/p?$key=abc123"), key)
        }
    }

    @Test
    fun onlyTheSensitiveParametersAreMasked() {
        assertEquals("https://x.io/p?a=1&token=***&b=2", UrlRedaction.redact("https://x.io/p?a=1&token=s3cr3t&b=2"))
    }

    @Test
    fun aTokenInTheFragmentIsMaskedToo() {
        assertEquals("https://x.io/cb#access_token=***&state=ok", UrlRedaction.redact("https://x.io/cb#access_token=eyJ&state=ok"))
    }

    @Test
    fun textThatIsNotAUrlIsReturnedAsIs() {
        assertEquals("token=abc", UrlRedaction.redact("token=abc"))
        assertEquals("tel:+573001234567", UrlRedaction.redact("tel:+573001234567"))
    }

    @Test
    fun theHistorySummaryOfALinkNeverCarriesItsCredentials() {
        val message = RecordSpecEncoder.encodeMessage(listOf(RecordSpec.Uri("https://me:pw@example.com/a?token=abc&x=1")), null)!!

        assertEquals("https://example.com/a?token=***&x=1", HistorySummarizer.summarize(message))
    }
}
