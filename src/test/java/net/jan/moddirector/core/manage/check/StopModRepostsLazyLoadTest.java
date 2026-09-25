package net.jan.moddirector.core.manage.check;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StopModRepostsLazyLoadTest {

    @Test
    void constructorDoesNotFetchDatabase() {
        TrackingStopModReposts stopModReposts = new TrackingStopModReposts();

        assertEquals(0, stopModReposts.fetchCount);
    }

    @Test
    void databaseIsLoadedAtMostOncePerRun() {
        TrackingStopModReposts stopModReposts = new TrackingStopModReposts();

        stopModReposts.ensureLoaded();
        stopModReposts.ensureLoaded();

        assertEquals(1, stopModReposts.fetchCount);
    }

    private static final class TrackingStopModReposts extends StopModReposts {
        private int fetchCount;

        private TrackingStopModReposts() {
            super(null);
        }

        @Override
        List<StopModRepostsEntry> fetchEntries() {
            fetchCount++;
            return Collections.emptyList();
        }
    }
}
