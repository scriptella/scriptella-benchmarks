package org.scriptella.benchmarks;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkConfigurationTest {
    private static final String URL = "jdbc:postgresql://postgres:5432/benchmark";

    @Test void v5HasExactlyEightActiveCases() {
        assertEquals("v5", Benchmark.BENCHMARK_VERSION);
        assertEquals(8, Experiment.cases().size());
        assertEquals(8, Experiment.cases().stream().map(Experiment.Case::key).distinct().count());
        assertFalse(Experiment.cases().stream().anyMatch(c -> c.mode().equals("unbatched") || c.implementation().equals("csvsql")));
        for (var test : Experiment.cases()) {
            if (!test.implementation().equals("pandas")) { // Pandas has its own Python entry point.
                assertDoesNotThrow(() -> Importer.validateCase(test.database(), test.implementation(), test.batch()));
            }
        }
        assertEquals(2, Experiment.cases().stream().filter(c -> c.implementation().equals("pandas")).count());
        assertEquals(2, Experiment.cases().stream().filter(c -> c.mode().equals("native_reference")).count());
    }

    @Test void retiredModesAndInvalidNativeDatabasePairsAreRejected() {
        for (String implementation : new String[] {"scriptella", "jdbc"}) {
            assertThrows(IllegalArgumentException.class, () -> Importer.validateCase("postgres", implementation, 0));
            assertThrows(IllegalArgumentException.class, () -> Importer.validateCase("mariadb", implementation, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> Importer.validateCase("postgres", "load_data", 0));
        assertThrows(IllegalArgumentException.class, () -> Importer.validateCase("mariadb", "copy", 0));
        for (String command : new String[] {"run-all", "smoke-all", "spike"}) {
            assertThrows(IllegalArgumentException.class, () -> Benchmark.main(new String[] {command}));
        }
    }

    @Test void v5RequiresTheRewriteProperty() {
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
