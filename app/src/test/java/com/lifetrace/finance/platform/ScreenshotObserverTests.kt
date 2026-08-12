package com.lifetrace.finance.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenshotObserverTests {
    @Test
    fun recognizesCommonScreenshotNamesAndPaths() {
        assertTrue(ScreenshotObserver.isScreenshot("Screenshot_20260812.png", "Pictures/Screenshots"))
        assertTrue(ScreenshotObserver.isScreenshot("微信截图_20260812.jpg", "Pictures"))
        assertTrue(ScreenshotObserver.isScreenshot("IMG_001.png", "DCIM/截屏"))
        assertTrue(ScreenshotObserver.isScreenshot("screen_shot_001.webp", "Pictures"))
    }

    @Test
    fun ignoresNormalPhotos() {
        assertFalse(ScreenshotObserver.isScreenshot("IMG_20260812_104500.jpg", "DCIM/Camera"))
    }
}
