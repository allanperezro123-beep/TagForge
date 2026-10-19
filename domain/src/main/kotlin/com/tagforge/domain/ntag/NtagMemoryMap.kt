package com.tagforge.domain.ntag

/** What a page of an NTAG21x is for (datasheet Rev. 3.2 sections 8.5 to 8.8). The UI chooses colour and text. */
enum class Region(
    /** True for pages the chip never gives back: PWD and PACK always READ as `00 00 00 00` (reconciliation #5). */
    val hiddenByChip: Boolean = false,
) {
    /** Serial number and check bytes: pages 00-01 and bytes 0-1 of page 02. */
    UID,

    /** Bytes 2-3 of page 02. */
    STATIC_LOCK,

    /** Capability container, page 03. */
    CC,

    /** NDEF / user memory. */
    USER,

    DYN_LOCK,

    /** MIRROR, RFUI, MIRROR_PAGE and AUTH0 (byte 3). */
    CFG0,

    /** ACCESS (byte 0) plus RFUI. */
    CFG1,

    PWD(hiddenByChip = true),

    PACK(hiddenByChip = true),

    /** MIFARE Ultralight C: 16-bit one-way counter page. */
    COUNTER,

    /** MIFARE Ultralight C: AUTH0 / AUTH1 pages (bit 0 of byte 0). */
    AUTH_CONFIG,

    /** MIFARE Ultralight C: the 3DES key; the chip never reveals it. */
    KEY(hiddenByChip = true),

    /** MIFARE Classic: last block of every sector (Key A, access bits, Key B). Tag Forge never writes it. */
    SECTOR_TRAILER,
}

/** One dumped page and its role. [regions] has two entries only for page 02 (BCC1 + static lock); [region] is the main one. */
data class PageAnnotation(val page: Int, val regions: List<Region>) {
    val region: Region get() = regions.first()
}

/** Page roles of NTAG213/215/216. Pure data: the dump screen colours and explains them (TT-03). */
object NtagMemoryMap {

    private const val STATIC_LOCK_PAGE = 2
    private const val CC_PAGE = 3

    /** Roles of [page] on [model]; empty when the chip has no such page. */
    fun regionsOf(model: NtagModel, page: Int): List<Region> = when {
        page < 0 || page >= model.totalPages -> emptyList()
        page < STATIC_LOCK_PAGE -> listOf(Region.UID)
        page == STATIC_LOCK_PAGE -> listOf(Region.UID, Region.STATIC_LOCK)
        page == CC_PAGE -> listOf(Region.CC)
        page in model.userStart..model.userEnd -> listOf(Region.USER)
        page == model.dynLock -> listOf(Region.DYN_LOCK)
        page == model.cfg0 -> listOf(Region.CFG0)
        page == model.cfg1 -> listOf(Region.CFG1)
        page == model.pwd -> listOf(Region.PWD)
        page == model.pack -> listOf(Region.PACK)
        else -> emptyList()
    }

    /** Annotations for the first [pageCount] pages. Empty for tags that are not NTAG21x: those show raw pages only. */
    fun annotate(model: NtagModel?, pageCount: Int): List<PageAnnotation> {
        if (model == null) return emptyList()
        return (0 until minOf(pageCount, model.totalPages)).map { PageAnnotation(it, regionsOf(model, it)) }
    }
}
