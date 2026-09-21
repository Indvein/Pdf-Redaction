package com.voxomos.poc;

/**
 * Central configuration constants for the POC.
 * All hardcoded paths live here — change once, fixes everywhere.
 */
public final class Config {
    private Config() {} // utility class, no instances

    /** Path to the Tesseract tessdata folder (set during Phase 1). */
    public static final String TESSDATA_PATH = "C:\\Program Files\\Tesseract-OCR\\tessdata";

    /** Relative path to the samples directory (run from poc-app/). */
    public static final String SAMPLES_DIR = "samples";

    /** Relative path to the output directory (run from poc-app/). */
    public static final String OUTPUT_DIR = "output";

    /** URL of the Python PyMuPDF redaction microservice. */
    public static final String REDACTION_SERVICE_URL = "http://localhost:5001/redact";

    /** URL for health-checking the redaction service before use. */
    public static final String REDACTION_HEALTH_URL = "http://localhost:5001/health";

    /** Output filename for the Java/PDFBox-only redaction (for side-by-side comparison). */
    public static final String JAVA_REDACTED_OUTPUT = "../output/java-redacted.pdf";
}
