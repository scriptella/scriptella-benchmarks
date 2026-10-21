package org.scriptella.benchmarks;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkConfigurationTest {
    private static final String URL = "jdbc:postgresql://postgres:5432/benchmark";

    @Test void v4RequiresTheRewriteProperty() {
        assertDoesNotThrow(() -> Benchmark.requirePostgresRewrite(URL, "true"));
        assertThrows(IllegalArgumentException.class, () -> Benchmark.requirePostgresRewrite(URL, "false"));
    }

    @Test void bothFollowupValuesArePassedAsProperties() {
        for (String value : new String[] {"false", "true"}) {
            Properties properties = Importer.postgresProperties(URL + "?ApplicationName=benchmark", value);
            Properties parsed = org.postgresql.Driver.parseURL(URL, properties);
            assertEquals(value, parsed.getProperty("reWriteBatchedInserts"));
        }
    }

    @Test void conflictingUrlSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Importer.postgresProperties(
                URL + "?reWriteBatchedInserts=true", "false"));
        assertThrows(IllegalArgumentException.class, () -> Importer.postgresProperties(URL, "invalid"));
    }
}
