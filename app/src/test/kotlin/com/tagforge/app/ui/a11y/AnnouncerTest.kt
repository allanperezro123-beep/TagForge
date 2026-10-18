package com.tagforge.app.ui.a11y

import com.tagforge.domain.common.Bytes
import com.tagforge.domain.ntag.NtagModel
import com.tagforge.domain.tag.TagFamily
import com.tagforge.domain.tag.TagInfo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** PR-04: what a screen reader says after a scan, in both languages, with the right plural. */
class AnnouncerTest {

    private fun tag(ntag: NtagModel?, family: TagFamily = TagFamily.NTAG21X) =
        TagInfo(uid = Bytes.of(0x04, 0xA1, 0xB2, 0xC3), techs = emptySet(), family = family, ntag = ntag)

    @Test
    fun anNtagReadNamesTheChipAndCountsRecords() {
        assertEquals(Announcement.TagRead("NTAG215", 2), Announcer.tagRead(tag(NtagModel.NTAG215), records = 2))
        assertEquals(Announcement.TagRead("NTAG213", 1), Announcer.tagRead(tag(NtagModel.NTAG213), records = 1))
    }

    @Test
    fun aTagThatIsNotAnNtagIsAnnouncedWithoutAChipName() {
        assertEquals(Announcement.TagRead(null, 0), Announcer.tagRead(tag(null, TagFamily.MIFARE_CLASSIC), records = 0))
    }

    // The announcement text lives in plurals so "1 record" and "2 records" are both right; check the resource itself.

    @Test
    fun englishSaysTagReadWithTheChipAndPluralRecords() {
        val one = plural("values/strings.xml", "announce_tag_read", "one")
        val other = plural("values/strings.xml", "announce_tag_read", "other")
        assertEquals("Tag read: NTAG215, 1 record", one.format("NTAG215", 1))
        assertEquals("Tag read: NTAG215, 2 records", other.format("NTAG215", 2))
    }

    @Test
    fun spanishHasBothPluralFormsForEveryAnnouncement() {
        for (name in listOf("announce_tag_read", "announce_tag_read_unknown")) {
            for (locale in listOf("values/strings.xml", "values-es/strings.xml")) {
                assertNotNull(plural(locale, name, "one"), "$locale $name one")
                assertNotNull(plural(locale, name, "other"), "$locale $name other")
            }
        }
        assertEquals("Etiqueta leída: NTAG215, 2 registros", plural("values-es/strings.xml", "announce_tag_read", "other").format("NTAG215", 2))
    }

    private fun plural(file: String, name: String, quantity: String): String {
        val res = listOf(File("src/main/res/$file"), File("app/src/main/res/$file")).first { it.exists() }.readText()
        val block = Regex("""<plurals name="$name">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).find(res)
        assertNotNull(block, "$file has plurals $name")
        val item = Regex("""<item quantity="$quantity">(.*?)</item>""").find(block.groupValues[1])
        return assertNotNull(item, "$file $name $quantity").groupValues[1]
    }
}
