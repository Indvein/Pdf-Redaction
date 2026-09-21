package com.voxomos.poc;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Feature A — OCR Pipeline.
 *
 * Renders each PDF page as a high-resolution (300 DPI) image and feeds it
 * to Tesseract via Tess4J to extract text from scanned/image-only PDFs.
 *
 * Why 300 DPI? Tesseract's LSTM engine achieves best accuracy at 300+ DPI.
 * Lower resolutions produce pixelated glyphs and degrade recognition quality.
 *
 * Tessdata path is loaded from Config — change it there if your install differs.
 */
public class OcrProcessor {

    public static void processScannedPdf() {
        System.out.println("--- Starting Feature A: OCR Pipeline ---");
        File file = new File(Config.SAMPLES_DIR + "/sample-scanned.pdf");

        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(file)) {
            PDFRenderer renderer = new PDFRenderer(document);
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(Config.TESSDATA_PATH);

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                System.out.println("Processing page " + (i + 1) + "...");
                BufferedImage image = renderer.renderImageWithDPI(i, 300, ImageType.RGB);

                // Plain text OCR — the primary output of this feature.
                String result = tesseract.doOCR(image);
                System.out.println("OCR Result (Plain Text):\n" + result);
            }
        } catch (IOException e) {
            System.err.println("Error reading PDF: " + e.getMessage());
            e.printStackTrace();
        } catch (TesseractException e) {
            System.err.println("Error during OCR: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("--- Finished Feature A ---\n");
    }

    public static void exploreHocr(BufferedImage image) throws TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(Config.TESSDATA_PATH);
        tesseract.setPageSegMode(3); // 3 = PSM_AUTO (avoids deprecated ITessAPI import)
        tesseract.setTessVariable("tessedit_create_hocr", "1");

        String hocrResult = tesseract.doOCR(image);
        System.out.println("hOCR Result (First 500 chars):\n" +
                (hocrResult.length() > 500 ? hocrResult.substring(0, 500) + "\n</... truncated ...>" : hocrResult));

        // No need to reset — each Tesseract instance is local and discarded after this call.
    }

    /**
     * Feature A (Extension) — Create Searchable PDF
     * Runs OCR on a scanned PDF and outputs a new PDF with an invisible text layer.
     * This bridges the gap for true redaction on scanned documents.
     */
    public static void createSearchablePdf(File inputFile, File outputFile) throws Exception {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(Config.TESSDATA_PATH);
        
        // Outputbase for createDocuments should not include the .pdf extension
        String outputBase = outputFile.getAbsolutePath();
        if (outputBase.toLowerCase().endsWith(".pdf")) {
            outputBase = outputBase.substring(0, outputBase.length() - 4);
        }

        tesseract.createDocuments(
            inputFile.getAbsolutePath(), 
            outputBase, 
            java.util.Collections.singletonList(net.sourceforge.tess4j.ITesseract.RenderedFormat.PDF)
        );
    }

    public static void main(String[] args) {
        processScannedPdf();
    }
}
