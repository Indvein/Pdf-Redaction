package com.voxomos.poc;

import java.util.Scanner;

/**
 * Main entry point — CLI menu wiring all four POC features.
 *
 * Menu:
 *   1. Extract Text  (Feature B) — TextExtractor
 *   2. OCR Scanned PDF (Feature A) — OcrProcessor
 *   3. Redact Text   (Feature C) — Redactor
 *   4. Stamp Image   (Feature D) — ImageStamper
 *   5. Exit
 */
public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        try {
            while (true) {
                System.out.println("==============================");
                System.out.println("  Voxomos OCR/PDF POC Tool");
                System.out.println("==============================");
                System.out.println("1. Extract Text (real-text PDF)");
                System.out.println("2. OCR a Scanned PDF");
                System.out.println("3. Redact Text [Python/PyMuPDF — legacy comparison]");
                System.out.println("4. Redact Text [Java/PDFBox — new implementation]");
                System.out.println("5. Stamp Image onto PDF");
                System.out.println("6. Exit");
                System.out.print("\nChoose an option: ");

                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        TextExtractor.extractText();
                        break;
                    case "2":
                        OcrProcessor.processScannedPdf();
                        break;
                    case "3":
                        Redactor.redactText();
                        break;
                    case "4":
                        JavaRedactor.redactText();
                        break;
                    case "5":
                        ImageStamper.stampImage();
                        break;
                    case "6":
                        System.out.println("Exiting...");
                        return;
                    default:
                        System.out.println("Invalid option. Please try again.\n");
                }
            }
        } finally {
            // Guaranteed close even if a feature throws a RuntimeException.
            scanner.close();
        }
    }
}
