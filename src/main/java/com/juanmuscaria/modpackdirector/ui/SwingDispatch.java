package com.juanmuscaria.modpackdirector.ui;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Synchronous boundary for Swing operations.
 *
 * Application work stays on the caller thread while component creation and
 * mutation are serialized on the Swing Event Dispatch Thread.
 */
public final class SwingDispatch {
    private SwingDispatch() {
    }

    public static void runAndWait(Runnable action) throws InterruptedException, InvocationTargetException {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
            return;
        }

        SwingUtilities.invokeAndWait(action);
    }

    public static <T> T callAndWait(Callable<T> action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return action.call();
        }

        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);

        try {
            return task.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        }
    }
}
