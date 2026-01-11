package project.pipepipe.app.helper

object LanguageHelper {
    val sharedLanguageValues = listOf(
        "ar",       // 原 ar, ar-ly
        "az",
        "be",
        "bg",
        "bn",       // 原 bn, bn-bd, bn-in
        "ca",
        "cs",
        "da",
        "de",
        "el",
        "en",
        "eo",
        "es",
        "et",
        "eu",
        "fa",
        "fi",
        "fil",
        "fr",
        "gl",
        "iw",       // 原 he (希伯来语)
        "hi",
        "hr",
        "hu",
        "hy",
        "id",       // 原 in (印尼语)
        "it",
        "ja",
        "jv",
        "ko",
        "ku",
        "lt",
        "mk",
        "ms",
        "no",       // 原 nb-no (挪威语)
        "ne",
        "nl",       // 原 nl, nl-be
        "oc",
        "pa",
        "pl",
        "pt",       // 原 pt, pt-br
        "pt-PT",    // 原 pt-pt
        "ro",
        "ru",
        "sk",
        "sl",
        "so",
        "sq",
        "sr",
        "sv",
        "ta",
        "te",
        "th",
        "tr",
        "uk",
        "ur",
        "uz",
        "vi",
        "zh-Hans",  // 原 zh-cn
        "zh-Hant"   // 原 zh-hk, zh-tw
    )
    val sharedLanguageEntries = listOf(
        "العربية",          // ar
        "Azərbaycan",       // az
        "Беларуская",       // be
        "Български",        // bg
        "বাংলা",             // bn
        "Català",           // ca
        "Čeština",          // cs
        "Dansk",            // da
        "Deutsch",          // de
        "Ελληνικά",         // el
        "English",          // en
        "Esperanto",        // eo
        "Español",          // es
        "Eesti",            // et
        "Euskara",          // eu
        "فارسی",            // fa
        "Suomi",            // fi
        "Filipino",         // fil
        "Français",         // fr
        "Galego",           // gl
        "עברית",            // iw (he)
        "हिन्दी",             // hi
        "Hrvatski",         // hr
        "Magyar",           // hu
        "Հայերեն",          // hy
        "Indonesia",        // id (in)
        "Italiano",         // it
        "日本語",            // ja
        "Basa Jawa",        // jv
        "한국어",             // ko
        "Kurdî",            // ku
        "Lietuvių",         // lt
        "Македонски",       // mk
        "Melayu",           // ms
        "Norsk",            // no
        "नेपाली",             // ne
        "Nederlands",       // nl
        "Occitan",          // oc
        "ਪੰਜਾਬੀ",            // pa
        "Polski",           // pl
        "Português",        // pt (巴西/通用)
        "Português (Portugal)", // pt-PT
        "Română",           // ro
        "Русский",          // ru
        "Slovenčina",       // sk
        "Slovenščina",      // sl
        "Soomaali",         // so
        "Shqip",            // sq
        "Српски",           // sr
        "Svenska",          // sv
        "தமிழ்",             // ta
        "తెలుగు",            // te
        "ไทย",              // th
        "Türkçe",           // tr
        "Українська",       // uk
        "اردو",             // ur
        "O‘zbek",           // uz
        "Tiếng Việt",       // vi
        "简体中文",           // zh-Hans
        "繁體中文"            // zh-Hant
    )
    fun getLocalizedLanguageName(code: String): String {
        val index = sharedLanguageValues.indexOfFirst { it == code }
        if (index == -1) return code
        return sharedLanguageEntries[index]
    }
}