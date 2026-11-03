package com.henriquesebastiao.downtify.core.model

/** The typeface the app's text uses (Settings › Appearance). */
enum class AppFont {
    /** Google Sans Flex's rounded cut, one family for everything. The default. */
    GoogleSansFlex,

    /** Downtify classic: Sora for headlines, DM Sans for everything else. */
    Downtify,

    /** Whatever the phone uses for its own UI, including a font the user picked in system settings. */
    System,
    ;

    companion object {
        val Default = GoogleSansFlex

        /** The font saved under [name], or the default one for a name this version doesn't know. */
        fun of(name: String?): AppFont = entries.firstOrNull { it.name == name } ?: Default
    }
}
