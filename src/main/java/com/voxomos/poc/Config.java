package com.voxomos.poc;

/**
 * Central configuration constants for the POC.
 * All hardcoded paths live here — change once, fixes everywhere.
 */
public final class Config {
    private Config() {} // utility class, no instances

    /** Relative path to the samples directory (run from poc-app/). */
    public static final String SAMPLES_DIR = "samples";

    /** Relative path to the output directory (run from poc-app/). */
    public static final String OUTPUT_DIR = "output";
}
