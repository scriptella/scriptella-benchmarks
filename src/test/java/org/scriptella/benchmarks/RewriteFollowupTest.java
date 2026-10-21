package org.scriptella.benchmarks;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RewriteFollowupTest {
    @Test void metadataUsesCurrentBuildTraceabilityAndMeasurementPhase() {
        Properties metadata = new Properties();
        metadata.setProperty("benchmark_revision", "current-sha-dirty");
        RewriteFollowup.configureMetadata(metadata);
        assertEquals("current-sha-dirty", metadata.getProperty("source_revision"));
        assertEquals("pg-rewrite-followup-measurement", metadata.getProperty("phase"));
        assertNull(metadata.getProperty("baseline_source_revision"));
        Properties unknown = new Properties();
        RewriteFollowup.configureMetadata(unknown);
        assertEquals("n/a", unknown.getProperty("source_revision"));
    }
}
