// Supported languages and the codes each engine needs.
package com.bbcl.bhashasetu

enum class Language(
    /** English name, for settings and logs. */
    val label: String,
    /** Name in its own script, shown to speakers of that language. */
    val nativeName: String,
    /** Google Translate code. */
    val code: String,
    /** BCP-47 locale used for both speech recognition and speech synthesis. */
    val locale: String,
    /** Whether Google's speech recognizer understands it. If not, the speaker types instead. */
    val voiceInput: Boolean = true,
    /** Written right to left. */
    val rtl: Boolean = false,
) {
    // India: the 22 scheduled languages Google translates, plus Bhojpuri.
    ENGLISH("English", "English", "en", "en-IN"),
    HINDI("Hindi", "हिन्दी", "hi", "hi-IN"),
    BANGLA("Bangla", "বাংলা", "bn", "bn-IN"),
    TELUGU("Telugu", "తెలుగు", "te", "te-IN"),
    TAMIL("Tamil", "தமிழ்", "ta", "ta-IN"),
    KANNADA("Kannada", "ಕನ್ನಡ", "kn", "kn-IN"),
    MALAYALAM("Malayalam", "മലയാളം", "ml", "ml-IN"),
    MARATHI("Marathi", "मराठी", "mr", "mr-IN"),
    GUJARATI("Gujarati", "ગુજરાતી", "gu", "gu-IN"),
    PUNJABI("Punjabi", "ਪੰਜਾਬੀ", "pa", "pa-IN"),
    ODIA("Odia", "ଓଡ଼ିଆ", "or", "or-IN"),
    ASSAMESE("Assamese", "অসমীয়া", "as", "as-IN"),
    URDU("Urdu", "اردو", "ur", "ur-IN", rtl = true),
    NEPALI("Nepali", "नेपाली", "ne", "ne-NP"),
    SANSKRIT("Sanskrit", "संस्कृतम्", "sa", "sa-IN", voiceInput = false),
    MAITHILI("Maithili", "मैथिली", "mai", "mai-IN", voiceInput = false),
    KONKANI("Konkani", "कोंकणी", "gom", "gom-IN", voiceInput = false),
    DOGRI("Dogri", "डोगरी", "doi", "doi-IN", voiceInput = false),
    SINDHI("Sindhi", "سنڌي", "sd", "sd-IN", voiceInput = false, rtl = true),
    MANIPURI("Manipuri", "ꯃꯩꯇꯩꯂꯣꯟ", "mni-Mtei", "mni-IN", voiceInput = false),
    SANTALI("Santali", "ᱥᱟᱱᱛᱟᱲᱤ", "sat", "sat-IN", voiceInput = false),
    BHOJPURI("Bhojpuri", "भोजपुरी", "bho", "bho-IN", voiceInput = false),

    // Neighbours and the world.
    SINHALA("Sinhala", "සිංහල", "si", "si-LK"),
    BURMESE("Burmese", "မြန်မာ", "my", "my-MM"),
    ARABIC("Arabic", "العربية", "ar", "ar-SA", rtl = true),
    PERSIAN("Persian", "فارسی", "fa", "fa-IR", rtl = true),
    CHINESE("Chinese", "中文", "zh-CN", "zh-CN"),
    JAPANESE("Japanese", "日本語", "ja", "ja-JP"),
    KOREAN("Korean", "한국어", "ko", "ko-KR"),
    THAI("Thai", "ไทย", "th", "th-TH"),
    VIETNAMESE("Vietnamese", "Tiếng Việt", "vi", "vi-VN"),
    INDONESIAN("Indonesian", "Bahasa Indonesia", "id", "id-ID"),
    MALAY("Malay", "Bahasa Melayu", "ms", "ms-MY"),
    FRENCH("French", "Français", "fr", "fr-FR"),
    GERMAN("German", "Deutsch", "de", "de-DE"),
    SPANISH("Spanish", "Español", "es", "es-ES"),
    PORTUGUESE("Portuguese", "Português", "pt", "pt-BR"),
    ITALIAN("Italian", "Italiano", "it", "it-IT"),
    DUTCH("Dutch", "Nederlands", "nl", "nl-NL"),
    RUSSIAN("Russian", "Русский", "ru", "ru-RU"),
    TURKISH("Turkish", "Türkçe", "tr", "tr-TR"),
    SWAHILI("Swahili", "Kiswahili", "sw", "sw-KE");

    /**
     * Short UI strings in this language, so each person reads their own. Languages without
     * built-in strings get them translated online when a conversation starts; until then, English.
     */
    val strings: LocalStrings get() = localStrings[this] ?: translatedStrings[this] ?: localStrings.getValue(ENGLISH)
}

data class LocalStrings(
    val tapToSpeak: String,
    val listening: String,
    val translating: String,
    val speaking: String,
    val noSpeech: String,
    val typeHere: String,
) {
    val all get() = listOf(tapToSpeak, listening, translating, speaking, noSpeech, typeHere)

    companion object {
        fun fromList(s: List<String>) = if (s.size != 6) null else LocalStrings(s[0], s[1], s[2], s[3], s[4], s[5])
    }
}

/** UI strings translated online, filled in when a conversation starts. */
val translatedStrings = mutableMapOf<Language, LocalStrings>()

val localStrings = mapOf(
    Language.ENGLISH to LocalStrings("Tap and speak", "Listening…", "Translating…", "Speaking…", "Didn't catch that. Try again.", "Type here"),
    Language.HINDI to LocalStrings("दबाएँ और बोलें", "सुन रहा है…", "अनुवाद हो रहा है…", "बोल रहा है…", "सुनाई नहीं दिया। फिर से बोलें।", "यहाँ लिखें"),
    Language.BANGLA to LocalStrings("চাপুন আর বলুন", "শুনছে…", "অনুবাদ হচ্ছে…", "বলছে…", "শোনা যায়নি। আবার বলুন।", "এখানে লিখুন"),
    Language.TELUGU to LocalStrings("నొక్కి మాట్లాడండి", "వింటోంది…", "అనువదిస్తోంది…", "మాట్లాడుతోంది…", "వినిపించలేదు. మళ్ళీ చెప్పండి.", "ఇక్కడ టైప్ చేయండి"),
    Language.TAMIL to LocalStrings("அழுத்திப் பேசுங்கள்", "கேட்கிறது…", "மொழிபெயர்க்கிறது…", "பேசுகிறது…", "கேட்கவில்லை. மீண்டும் சொல்லுங்கள்.", "இங்கே தட்டச்சு செய்யவும்"),
    Language.KANNADA to LocalStrings("ಒತ್ತಿ ಮಾತನಾಡಿ", "ಕೇಳುತ್ತಿದೆ…", "ಅನುವಾದಿಸುತ್ತಿದೆ…", "ಮಾತನಾಡುತ್ತಿದೆ…", "ಕೇಳಿಸಲಿಲ್ಲ. ಮತ್ತೆ ಹೇಳಿ.", "ಇಲ್ಲಿ ಟೈಪ್ ಮಾಡಿ"),
)
