package com.voxomos.poc;

import net.sourceforge.tess4j.ITesseract.RenderedFormat;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import java.io.File;
import java.util.Collections;

public class TestTess {
    public static void main(String[] args) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(Config.TESSDATA_PATH);
        try {
            // Test if it can process a PDF directly
            tesseract.createDocuments(
                Config.SAMPLES_DIR + "/sample-scanned.pdf", 
                Config.OUTPUT_DIR + "/test-output", 
                Collections.singletonList(RenderedFormat.PDF)
            );
            System.out.println("SUCCESS: PDF created.");
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
