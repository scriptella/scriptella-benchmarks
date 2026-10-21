package org.scriptella.benchmarks;

import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Focused addendum; reuses the benchmark preparation/reset/verification helpers. */
public final class RewriteFollowup {
    record Variant(String database, String implementation, String rewrite) {
        String key() { return database + "-" + implementation + "-rewrite-" + rewrite; }
    }
    static Method reset, verify, prefix;
    static void call(Method m, Object... args) throws Exception {
        try { m.invoke(null, args); } catch (InvocationTargetException e) { throw new RuntimeException(e.getCause()); }
    }
    public static void main(String[] args) throws Exception {
        reset = Experiment.class.getDeclaredMethod("reset", String.class);
        verify = Experiment.class.getDeclaredMethod("verify", String.class, int.class);
        prefix = Experiment.class.getDeclaredMethod("prefix", Path.class, String.class, int.class);
        for (Method m : List.of(reset, verify, prefix)) m.setAccessible(true);
        var p = Benchmark.prepare("pg-rewrite-followup");
        var metadata = p.metadata();
        configureMetadata(metadata);
        metadata.setProperty("jdbc_batch_options", "PostgreSQL explicit reWriteBatchedInserts=false/true; MariaDB unchanged defaults");
        metadata.setProperty("method", "batch1000; three alternating rounds; fresh JVM; warmup20000; shared benchmark importer and verifier");
        metadata.setProperty("status", "running");
        Benchmark.storeMetadata(p.output(), metadata);
        Path smoke = (Path) prefix.invoke(null, p.fixture(), "smoke.csv", 2001);
        Path warmup = (Path) prefix.invoke(null, p.fixture(), "warmup.csv", 20000);
        var variants = List.of(new Variant("postgres","scriptella","false"), new Variant("postgres","scriptella","true"),
            new Variant("postgres","jdbc","false"), new Variant("postgres","jdbc","true"),
            new Variant("mariadb","scriptella","default"), new Variant("mariadb","jdbc","default"));
        try (var out = Files.newBufferedWriter(p.output().resolve("measurements.csv"))) {
            out.write("round,position,database,implementation,batch_size,rewrite,duration_ms,rows,rows_per_second,verified\n");
            for (var v : variants) {
                call(reset, v.database()); launch(v, smoke, p.output().resolve("smoke-"+v.key()+".log"));
                call(verify,v.database(),2001); call(reset,v.database());
                System.out.println("Smoke passed: "+v.key());
            }
            for (int round=1; round<=3; round++) {
                var order = new ArrayList<>(variants);
                if (round % 2 == 0) Collections.reverse(order);
                int position=0;
                for (var v : order) {
                    String key = "round-"+round+"-"+v.key();
                    call(reset,v.database()); launch(v,warmup,p.output().resolve("warmup-"+key+".log"));
                    call(verify,v.database(),20000); call(reset,v.database());
                    System.out.println("Measuring: "+key);
                    double ms=launch(v,p.fixture(),p.output().resolve("measured-"+key+".log"));
                    call(verify,v.database(),Benchmark.ROWS);
                    out.write(String.format(Locale.ROOT,"%d,%d,%s,%s,1000,%s,%.3f,1000000,%.3f,true%n",round,++position,v.database(),v.implementation(),v.rewrite(),ms,1e9/ms)); out.flush();
                    call(reset,v.database());
                    System.out.printf(Locale.ROOT,"Verified: %s %.3f s%n",key,ms/1000);
                }
            }
            metadata.setProperty("status","success"); Benchmark.storeMetadata(p.output(),metadata);
        } catch (Exception e) { metadata.setProperty("status","failed"); metadata.setProperty("failure",e.toString()); Benchmark.storeMetadata(p.output(),metadata); throw e; }
    }
    static void configureMetadata(Properties metadata) {
        metadata.setProperty("benchmark_version", "v3-pg-rewrite-followup-1");
        metadata.setProperty("phase", "pg-rewrite-followup-measurement");
        // Supplied at image build time; unknown sources remain explicitly n/a.
        metadata.setProperty("source_revision", metadata.getProperty("benchmark_revision", "n/a"));
    }

    static double launch(Variant v, Path fixture, Path log) throws Exception {
        var b = new ProcessBuilder("java","-Duser.timezone=UTC","-cp",System.getProperty("java.class.path"),Importer.class.getName(),v.database(),v.implementation(),"1000",fixture.toString());
        if (v.database().equals("postgres")) b.environment().put("POSTGRES_REWRITE_BATCHED_INSERTS", v.rewrite());
        b.redirectErrorStream(true).redirectOutput(log.toFile());
        long start=System.nanoTime(); int exit=b.start().waitFor(); double ms=(System.nanoTime()-start)/1e6;
        if(exit!=0) throw new IllegalStateException(v.key()+" exit "+exit+": "+Files.readString(log));
        return ms;
    }
}
