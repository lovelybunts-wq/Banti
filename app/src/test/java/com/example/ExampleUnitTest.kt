package com.example

import com.example.data.model.NoteType
import com.example.ui.MainViewModel
import com.example.ui.theme.StudyThemeOption
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun noteType_fromRaw_fallsBackSafely() {
        assertEquals(NoteType.HTML, NoteType.fromRaw("HTML"))
        assertEquals(NoteType.PDF, NoteType.fromRaw("UNKNOWN_TYPE"))
    }

    @Test
    fun studyTheme_fromId_defaultsToBuntsRoyalLight() {
        assertEquals(StudyThemeOption.FOREST_SAGE, StudyThemeOption.fromId("forest_sage"))
        assertEquals(StudyThemeOption.BUNTS_ROYAL_LIGHT, StudyThemeOption.fromId(null))
    }

    @Test
    fun formatNum_formatsWholeAndDecimalCleanly() {
        assertEquals("18", MainViewModel.formatNum(18.0))
        assertEquals("18.5", MainViewModel.formatNum(18.5))
    }
}
