package com.voxomos.poc;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;

/**
 * Feature B — Direct Text Extraction.
 *
 * Uses PDFBox's PDFTextStripper to extract the embedded text layer from a
 * native (non-scanned) PDF. This is the fast, zero-OCR path.
 *
 * Note: returns empty string on scanned/image-only PDFs — use OcrProcessor for those.
 */
public class TextExtractor {

    public static void extractText() {
        System.out.println("--- Starting Feature B: Direct Text Extraction ---");
        File file = new File(Config.SAMPLES_DIR + "/sample-text.pdf");

        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String extractedText = stripper.getText(document);
            System.out.println("Extracted Text from " + file.getName() + ":\n" + extractedText);
        } catch (IOException e) {
            System.err.println("Error extracting text: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("--- Finished Feature B ---\n");
    }

    public static void main(String[] args) {
        extractText();
    }
}
