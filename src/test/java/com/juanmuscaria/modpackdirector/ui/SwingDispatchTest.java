package com.juanmuscaria.modpackdirector.ui;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingDispatchTest {

    @Test
    void runAndWaitExecutesOnEventDispatchThread() throws Exception {
        AtomicBoolean ranOnEdt = new AtomicBoolean(false);

        assertFalse(SwingUtilities.isEventDispatchThread());
        SwingDispatch.runAndWait(() -> ranOnEdt.set(SwingUtilities.isEventDispatchThread()));

        assertTrue(ranOnEdt.get());
    }

    @Test
    void callAndWaitReturnsValueFromEventDispatchThread() throws Exception {
        boolean ranOnEdt = SwingDispatch.callAndWait(SwingUtilities::isEventDispatchThread);

        assertTrue(ranOnEdt);
    }

    @Test
    void callAndWaitDoesNotRedispatchWhenAlreadyOnEventDispatchThread() throws Exception {
        AtomicBoolean completed = new AtomicBoolean(false);

        SwingUtilities.invokeAndWait(() -> {
            try {
                boolean ranOnEdt = SwingDispatch.callAndWait(SwingUtilities::isEventDispatchThread);
                completed.set(ranOnEdt);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(completed.get());
    }
}
