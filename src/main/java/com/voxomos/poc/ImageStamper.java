package com.voxomos.poc;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Feature D — Image Stamping.
 *
 * Appends a PNG image (e.g., a county seal or "RECORDED" watermark) onto a
 * target PDF page using PDFBox's PDImageXObject.
 *
 * Coordinates use the PDF bottom-left origin system:
 *   x=50, y=50 → bottom-left corner of the page.
 */
public class ImageStamper {

    public static void stampImage() {
        System.out.println("--- Starting Feature D: Image Stamping ---");
        File inFile = new File(Config.SAMPLES_DIR + "/sample-text.pdf");
        File stampFile = new File(Config.SAMPLES_DIR + "/stamp-image.png");
        File outDir = new File(Config.OUTPUT_DIR);
        outDir.mkdirs();
        File outFile = new File(outDir, "stamped.pdf");

        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(inFile)) {
            PDPage page = document.getPage(0);
            PDImageXObject pdImage = PDImageXObject.createFromFile(stampFile.getAbsolutePath(), document);

            try (PDPageContentStream contentStream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                // Stamp placed at bottom-left corner: x=50, y=50, 200×100 points.
                float x = 50;
                float y = 50;
                float width = 200;
                float height = 100;
                contentStream.drawImage(pdImage, x, y, width, height);
            }

            document.save(outFile);
            System.out.println("Stamped PDF saved to: " + outFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error during image stamping: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("--- Finished Feature D ---\n");
    }

    public static void stampDocument(File inFile, File outFile, int pageIndex, float x, float y) {
        System.out.println("--- ImageStamper: Stamping Document ---");
        File stampFile = new File(Config.SAMPLES_DIR + "/stamp-image.png");

        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(inFile)) {
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                pageIndex = 0; // fallback to first page
            }
            PDPage page = document.getPage(pageIndex);
            
            // Check if stamp image exists and is not empty, if not use a fallback or skip
            if (!stampFile.exists() || stampFile.length() == 0) {
                System.err.println("Stamp image not found or is empty at: " + stampFile.getAbsolutePath());
                Files.copy(inFile.toPath(), outFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return;
            }

            PDImageXObject pdImage = PDImageXObject.createFromFile(stampFile.getAbsolutePath(), document);

            try (PDPageContentStream contentStream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                // PDF bottom-left origin. Convert frontend top-left (y) to bottom-left if necessary.
                // Assuming frontend sends x, y scaled to PDF points already.
                // If y is from top-left, we might need: y = page.getMediaBox().getHeight() - y - height;
                // For simplicity, we just use the x, y provided.
                // Let's use a standard size for the stamp
                float width = 150;
                float height = 75;
                
                // If the frontend gives y from top-left, we must invert it to PDF coordinate space
                // (bottom-left origin).
                // PDFBox uses 72 points per inch. Let's just trust x and y but apply inversion.
                float pdfY = page.getMediaBox().getHeight() - y - height;

                contentStream.drawImage(pdImage, x, pdfY, width, height);
            }

            document.save(outFile);
            System.out.println("Stamped PDF saved to: " + outFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error during image stamping: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("--- Finished Stamping ---\n");
    }

    public static void main(String[] args) {
        stampImage();
    }
}
