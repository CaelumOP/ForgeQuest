package com.forgequest.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileAvatarTest {
    @Test fun friendsPhotoWinsOverDrive() = assertEquals("f", pickAvatarPhoto("f", "d"))
    @Test fun driveIsTheFallback() = assertEquals("d", pickAvatarPhoto(null, "d"))
    @Test fun blankCountsAsNone() { assertEquals("d", pickAvatarPhoto("  ", "d")); assertNull(pickAvatarPhoto("", null)) }
    @Test fun initialIsUppercasedFirstLetter() = assertEquals("S", avatarInitial("  sara"))
    @Test fun initialFallsBackToQuestionMark() = assertEquals("?", avatarInitial("   "))
    @Test fun arabicInitialIsKept() = assertEquals("أ", avatarInitial("أحمد"))
    @Test fun avatarNameFallsBackToFriendsShownName() {
        assertEquals("tariq", pickAvatarName("", "tariq"))
        assertEquals("Omar", pickAvatarName("  Omar ", "tariq"))
        assertEquals("", pickAvatarName("  ", null))
        assertEquals("T", avatarInitial(pickAvatarName("", "tariq")))
    }
}
