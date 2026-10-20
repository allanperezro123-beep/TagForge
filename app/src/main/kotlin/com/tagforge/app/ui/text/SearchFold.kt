package com.tagforge.app.ui.text

import java.text.Normalizer
import java.util.Locale

private val Accents = Regex("\\p{M}+")

/** Lower case with the accents removed, so a search for "camara" finds "Cámara" and "espan" finds "Español". */
fun String.foldForSearch(): String = Normalizer.normalize(this, Normalizer.Form.NFD).replace(Accents, "").lowercase(Locale.ROOT)
