package com.juanmuscaria.modpackdirector.ui;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectorMainGUIIconSizeTest {

    @Test
    void keepsPositiveConfiguredDimensions() {
        assertEquals(new Dimension(96, 48),
            DirectorMainGUI.normalizeIconDimension(new Dimension(96, 48)));
    }

    @Test
    void fallsBackForMissingDimensions() {
        assertEquals(new Dimension(64, 64),
            DirectorMainGUI.normalizeIconDimension(null));
    }

    @Test
    void fallsBackForZeroOrNegativeDimensions() {
        assertEquals(new Dimension(64, 64),
            DirectorMainGUI.normalizeIconDimension(new Dimension(0, 0)));
        assertEquals(new Dimension(64, 64),
            DirectorMainGUI.normalizeIconDimension(new Dimension(-1, 32)));
        assertEquals(new Dimension(64, 64),
            DirectorMainGUI.normalizeIconDimension(new Dimension(32, -1)));
    }
}
