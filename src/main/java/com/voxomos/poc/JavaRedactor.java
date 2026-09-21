package com.voxomos.poc;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure Java True Redaction — Rasterize + Blackout approach.
 */
public class JavaRedactor {

    private static final float RENDER_DPI = 200f;
    private static final float PTS_TO_PX = RENDER_DPI / 72f;

    // ─── Pass 1: Locate target words via PDFTextStripper ─────────────────────────
    private static class WordCoordFinder extends PDFTextStripper {
        private final List<String> lcTargets;
        final Map<Integer, List<Rectangle2D>> pageBoxes = new HashMap<>();
        private final List<TextPosition> wordBuf = new ArrayList<>();

        WordCoordFinder(List<String> targets) throws IOException {
            super();
            lcTargets = new ArrayList<>();
            for (String t : targets) lcTargets.add(t.toLowerCase());
        }

        Map<Integer, List<Rectangle2D>> find(PDDocument doc) throws IOException {
            for (int i = 0; i < doc.getNumberOfPages(); i++)
                pageBoxes.put(i + 1, new ArrayList<>());
            setSortByPosition(true);
            getText(doc);
            return pageBoxes;
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            for (TextPosition tp : positions) {
                String u = tp.getUnicode();
                if (u != null && !u.isBlank()) {
                    wordBuf.add(tp);
                } else {
                    flush();
                }
            }
            flush();
        }

        private void flush() {
            if (wordBuf.isEmpty()) return;
            StringBuilder sb = new StringBuilder();
            for (TextPosition tp : wordBuf) sb.append(tp.getUnicode());
            String word = sb.toString();

            for (String target : lcTargets) {
                if (word.toLowerCase().contains(target)) {
                    TextPosition first = wordBuf.get(0);
                    TextPosition last  = wordBuf.get(wordBuf.size() - 1);
                    float pad = 2f;
                    float x = first.getXDirAdj() - pad;
                    float y = first.getYDirAdj() - first.getHeightDir() - pad;
                    float w = (last.getXDirAdj() + last.getWidthDirAdj()) - first.getXDirAdj() + pad * 2;
                    float h = first.getHeightDir() + pad * 2;
                    pageBoxes.get(getCurrentPageNo()).add(new Rectangle2D.Float(x, y, w, h));
                    System.out.printf("    Found \"%s\" at (%.1f, %.1f) w=%.1f h=%.1f%n",
                            word, x, y, w, h);
                    break;
                }
            }
            wordBuf.clear();
        }
    }

    // ─── Pass 2: Rasterize page + paint black boxes ───────────────────────────────
    private static void rasterizeAndBlackout(PDDocument doc, PDPage page,
                                             List<Rectangle2D> boxes) throws IOException {
        PDFRenderer renderer = new PDFRenderer(doc);
        int pageIndex = doc.getPages().indexOf(page);
        BufferedImage img = renderer.renderImageWithDPI(pageIndex, RENDER_DPI, ImageType.RGB);

        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(Color.BLACK);
        for (Rectangle2D box : boxes) {
            int px = (int) (box.getX()      * PTS_TO_PX);
            int py = (int) (box.getY()      * PTS_TO_PX);
            int pw = (int) (box.getWidth()  * PTS_TO_PX) + 2;
            int ph = (int) (box.getHeight() * PTS_TO_PX) + 2;
            g.fillRect(px, py, pw, ph);
        }
        g.dispose();

        PDRectangle mediaBox = page.getMediaBox();
        PDImageXObject pdfImg = LosslessFactory.createFromImage(doc, img);
        
        try (PDPageContentStream cs = new PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.OVERWRITE, false)) {
            cs.drawImage(pdfImg, 0, 0, mediaBox.getWidth(), mediaBox.getHeight());
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────────
    public static void processRedaction(File inFile, File outFile, List<String> targetWords) {
        System.out.println("--- JavaRedactor: starting redaction ---");
        try {
            PDDocument doc = org.apache.pdfbox.Loader.loadPDF(inFile);
            WordCoordFinder finder = new WordCoordFinder(targetWords);
            Map<Integer, List<Rectangle2D>> allBoxes = finder.find(doc);

            for (int p = 0; p < doc.getNumberOfPages(); p++) {
                PDPage page = doc.getPage(p);
                List<Rectangle2D> boxes = allBoxes.get(p + 1);
                if (boxes != null && !boxes.isEmpty()) {
                    rasterizeAndBlackout(doc, page, boxes);
                }
            }
            doc.save(outFile);
            doc.close();
            System.out.println("JavaRedactor: saved → " + outFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void processRedactionByBoxes(File inFile, File outFile,
                                               List<Map<String, Object>> boxSpecs) {
        System.out.println("--- JavaRedactor: box-based redaction ---");
        // Must match the DPI used in WebApp.java for /api/documents/.../pages/... preview images!
        final float PREVIEW_DPI = 72f;

        Map<Integer, List<int[]>> pageBoxes = new HashMap<>();
        for (Map<String, Object> spec : boxSpecs) {
            int page = ((Number) spec.get("page")).intValue();
            float scale = RENDER_DPI / PREVIEW_DPI;
            int x = (int)(((Number) spec.get("x")).doubleValue() * scale);
            int y = (int)(((Number) spec.get("y")).doubleValue() * scale);
            int w = (int)(((Number) spec.get("width")).doubleValue()  * scale) + 2;
            int h = (int)(((Number) spec.get("height")).doubleValue() * scale) + 2;
            pageBoxes.computeIfAbsent(page, k -> new ArrayList<>()).add(new int[]{x, y, w, h});
        }

        try {
            PDDocument doc = org.apache.pdfbox.Loader.loadPDF(inFile);
            PDFRenderer renderer = new PDFRenderer(doc);

            for (int p = 0; p < doc.getNumberOfPages(); p++) {
                List<int[]> boxes = pageBoxes.get(p);
                if (boxes == null || boxes.isEmpty()) continue;

                PDPage page = doc.getPage(p);
                BufferedImage img = renderer.renderImageWithDPI(p, RENDER_DPI, ImageType.RGB);
                Graphics2D g = img.createGraphics();
                g.setColor(Color.BLACK);
                for (int[] box : boxes) g.fillRect(box[0], box[1], box[2], box[3]);
                g.dispose();

                PDRectangle mediaBox = page.getMediaBox();
                PDImageXObject pdfImg = LosslessFactory.createFromImage(doc, img);
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.OVERWRITE, false)) {
                    cs.drawImage(pdfImg, 0, 0, mediaBox.getWidth(), mediaBox.getHeight());
                }
            }

            doc.save(outFile);
            doc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void redactText() { }
}
