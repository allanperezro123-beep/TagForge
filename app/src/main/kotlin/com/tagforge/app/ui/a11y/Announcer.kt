package com.tagforge.app.ui.a11y

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.tagforge.app.R
import com.tagforge.domain.tag.TagInfo

/** What a screen reader should say about an event, without any text yet: the language is chosen when it is shown. */
sealed interface Announcement {
    /** [tagType] is the NTAG model, or null for any other kind of tag. */
    data class TagRead(val tagType: String?, val records: Int) : Announcement
}

/** Builds the [Announcement]s of PR-04. Pure, so what is said can be tested without a screen reader. */
object Announcer {

    fun tagRead(info: TagInfo, records: Int): Announcement.TagRead = Announcement.TagRead(info.ntag?.displayName, records)
}

/** The localized sentence for this announcement, with the right plural ("1 record", "2 records"). */
@Composable
fun Announcement.text(): String = when (this) {
    is Announcement.TagRead ->
        if (tagType != null) {
            pluralStringResource(R.plurals.announce_tag_read, records, tagType, records)
        } else {
            pluralStringResource(R.plurals.announce_tag_read_unknown, records, records)
        }
}
