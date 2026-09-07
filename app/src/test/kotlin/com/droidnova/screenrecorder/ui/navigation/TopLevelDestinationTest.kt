package com.droidnova.screenrecorder.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {
    @Test fun routesAreUnique() {
        assertEquals(TopLevelDestination.entries.size, TopLevelDestination.entries.map { it.route }.distinct().size)
    }
}
