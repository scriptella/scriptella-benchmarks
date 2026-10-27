package org.scriptella.benchmarks;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlotCompatibilityTest {
    @Test void pythonCompatibilityFieldsMatchJavaForHistoricalAndFinalRuns() throws Exception {
        for (var values : List.of(
                Map.of("benchmark_version", "v4", "case_definition", "historical"),
                Map.of("benchmark_version", "v5", "case_definition", "earlier-seven"),
                Map.of("benchmark_version", "v5", "case_definition", Experiment.CASE_DEFINITION))) {
            var process = new ProcessBuilder("python3", "-c",
                    "import sys; sys.path.insert(0, 'tools'); from plot_data import compatibility_fields; "
                            + "print('\\n'.join(compatibility_fields(dict(zip(('benchmark_version', 'case_definition'), sys.argv[1:])))))",
                    values.get("benchmark_version"), values.get("case_definition"))
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.waitFor(), output);
            assertEquals(Aggregate.compatibility(values), output.lines().toList());
        }
    }

    @Test void plotInputRegressionTestsPass() throws Exception {
        var process = new ProcessBuilder("python3", "-m", "unittest", "discover", "-s", "tools", "-p", "test_plot_data.py")
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }
}
