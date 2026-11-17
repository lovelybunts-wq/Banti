package com.example.data.model

data class DefaultSeedSubject(
    val id: String,
    val nameEn: String,
    val nameHi: String,
    val iconKey: String,
    val colorHex: String,
    val sortOrder: Long,
    val topics: List<Pair<String, List<String>>>,
)

object DefaultSubjectsSeed {
    val all12Subjects: List<DefaultSeedSubject> = listOf(
        DefaultSeedSubject(
            id = "subj_history",
            nameEn = "History",
            nameHi = "इतिहास",
            iconKey = "history",
            colorHex = "#D97706",
            sortOrder = 0L,
            topics = listOf(
                "Ancient India (प्राचीन भारत)" to listOf("Indus Valley Civilization", "Vedic Period & Mauryas"),
                "Medieval India (मध्यकालीन भारत)" to listOf("Delhi Sultanate", "Mughal Empire"),
                "Modern India (आधुनिक भारत)" to listOf("1857 Revolt", "Indian National Movement")
            )
        ),
        DefaultSeedSubject(
            id = "subj_polity",
            nameEn = "Polity",
            nameHi = "राज व्यवस्था",
            iconKey = "polity",
            colorHex = "#2456A4",
            sortOrder = 1L,
            topics = listOf(
                "Indian Constitution (संविधान)" to listOf("Preamble & Making", "Fundamental Rights (Art 12-35)"),
                "Union & Parliament (संसद)" to listOf("President & PM", "Lok Sabha & Rajya Sabha")
            )
        ),
        DefaultSeedSubject(
            id = "subj_geography",
            nameEn = "Geography",
            nameHi = "भूगोल",
            iconKey = "geography",
            colorHex = "#16A34A",
            sortOrder = 2L,
            topics = listOf(
                "Indian Geography (भारत का भूगोल)" to listOf("Himalayas & Plains", "Indian Rivers & Monsoon"),
                "Physical Geography (भौतिक भूगोल)" to listOf("Atmosphere & Climate")
            )
        ),
        DefaultSeedSubject(
            id = "subj_english",
            nameEn = "English",
            nameHi = "इंग्लिश",
            iconKey = "english",
            colorHex = "#0284C7",
            sortOrder = 3L,
            topics = listOf(
                "Grammar" to listOf("Tenses & Voice", "Prepositions & Articles"),
                "Vocabulary" to listOf("Idioms, Phrases & One-Word")
            )
        ),
        DefaultSeedSubject(
            id = "subj_maths",
            nameEn = "Maths",
            nameHi = "गणित",
            iconKey = "maths",
            colorHex = "#7C3AED",
            sortOrder = 4L,
            topics = listOf(
                "Arithmetic (अंकगणित)" to listOf("Percentage & Profit-Loss", "Time, Speed & Distance"),
                "Advance Maths" to listOf("Algebra & Geometry", "Trigonometry")
            )
        ),
        DefaultSeedSubject(
            id = "subj_science",
            nameEn = "Science",
            nameHi = "विज्ञान",
            iconKey = "science",
            colorHex = "#DB2777",
            sortOrder = 5L,
            topics = listOf(
                "Physics (भौतिक विज्ञान)" to listOf("Motion, Force & Optics", "Electricity & Magnetism"),
                "Chemistry (रसायन विज्ञान)" to listOf("Atomic Structure & Acids", "Periodic Table"),
                "Biology (जीव विज्ञान)" to listOf("Cell & Human Physiology", "Vitamins & Diseases")
            )
        ),
        DefaultSeedSubject(
            id = "subj_reasoning",
            nameEn = "Reasoning",
            nameHi = "रीजनिंग",
            iconKey = "reasoning",
            colorHex = "#EA580C",
            sortOrder = 6L,
            topics = listOf(
                "Verbal Reasoning" to listOf("Coding-Decoding & Series", "Syllogism & Blood Relations"),
                "Non-Verbal & Puzzles" to listOf("Seating Arrangement & Dice")
            )
        ),
        DefaultSeedSubject(
            id = "subj_computer",
            nameEn = "Computer",
            nameHi = "कंप्यूटर",
            iconKey = "computer",
            colorHex = "#0D9488",
            sortOrder = 7L,
            topics = listOf(
                "Fundamentals & Hardware" to listOf("Memory, CPU & I/O Devices", "Operating Systems"),
                "Networking & MS Office" to listOf("Internet, Protocols & Cyber Security", "MS Word, Excel & Shortcuts")
            )
        ),
        DefaultSeedSubject(
            id = "subj_current_affairs",
            nameEn = "Current Affairs",
            nameHi = "करेंट अफेयर्स",
            iconKey = "current_affairs",
            colorHex = "#2563EB",
            sortOrder = 8L,
            topics = listOf(
                "National & International" to listOf("Monthly Current Capsules", "Government Schemes & Summits"),
                "Sports, Awards & Appointments" to listOf("Awards, Honours & Defence Exercises")
            )
        ),
        DefaultSeedSubject(
            id = "subj_mp_gk",
            nameEn = "MP GK",
            nameHi = "मध्य प्रदेश सामान्य ज्ञान",
            iconKey = "mp_gk",
            colorHex = "#35BDB5",
            sortOrder = 9L,
            topics = listOf(
                "MP History & Culture" to listOf("MP Tribes, Folk Art & Heritage", "MP Rivers, Parks & Geography"),
                "MP Polity & Economy" to listOf("MP Districts, Divisions & Schemes")
            )
        ),
        DefaultSeedSubject(
            id = "subj_general_studies",
            nameEn = "General Studies",
            nameHi = "सामान्य अध्ययन",
            iconKey = "general_studies",
            colorHex = "#1D4ED8",
            sortOrder = 10L,
            topics = listOf(
                "Indian Economy (अर्थव्यवस्था)" to listOf("Banking, Budget & Five-Year Plans"),
                "Static GK & Environment" to listOf("National Parks, UNESCO Sites & Organizations")
            )
        ),
        DefaultSeedSubject(
            id = "subj_other",
            nameEn = "Other Subjects",
            nameHi = "अन्य विषय",
            iconKey = "book",
            colorHex = "#475569",
            sortOrder = 11L,
            topics = listOf(
                "General Practice & Strategy" to listOf("Previous Year Papers", "Exam Strategy & Formula Sheets")
            )
        ),
    )
}
