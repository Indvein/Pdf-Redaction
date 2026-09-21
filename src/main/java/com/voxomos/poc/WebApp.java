package com.voxomos.poc;

import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WebApp {
    private static final Map<String, File> uploadedDocs = new ConcurrentHashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        // Ensure storage directories exist
        new File(Config.OUTPUT_DIR).mkdirs();
        new File(Config.SAMPLES_DIR).mkdirs();

        var app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> {
                    it.anyHost();
                });
            });
        }).start(8274);

        System.out.println("Javalin server started on http://localhost:8274");

        app.post("/api/upload", ctx -> {
            UploadedFile uploadedFile = ctx.uploadedFile("file");
            if (uploadedFile == null) {
                ctx.status(400).result("No file uploaded");
                return;
            }
            String docId = UUID.randomUUID().toString();
            File tempFile = File.createTempFile("upload-", ".pdf");
            Files.copy(uploadedFile.content(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            uploadedDocs.put(docId, tempFile);
            
            try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(tempFile)) {
                ctx.json(Map.of("id", docId, "totalPages", doc.getNumberOfPages()));
            }
        });

        app.get("/api/documents/{docId}/pages/{page}", ctx -> {
            String docId = ctx.pathParam("docId");
            int pageIndex = Integer.parseInt(ctx.pathParam("page"));
            File file = uploadedDocs.get(docId);
            if (file != null && file.exists()) {
                try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(file)) {
                    PDFRenderer renderer = new PDFRenderer(doc);
                    // Lowered DPI to 72 and switched to JPEG to drastically improve loading speeds on weak cloud servers
                    BufferedImage image = renderer.renderImageWithDPI(pageIndex, 72, ImageType.RGB);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "jpeg", baos);
                    ctx.contentType("image/jpeg");
                    ctx.result(baos.toByteArray());
                }
            } else {
                ctx.status(404).result("Document not found");
            }
        });

        app.post("/api/documents/{docId}/process", ctx -> {
            String docId = ctx.pathParam("docId");
            File file = uploadedDocs.get(docId);
            if (file == null || !file.exists()) {
                ctx.status(404).result("Document not found");
                return;
            }

            JsonNode body = mapper.readTree(ctx.body());
            File currentFile = file;

            // 1. Redact — prefer box-based (drag-select), fall back to word-based
            if (body.has("redactionBoxes") && body.get("redactionBoxes").isArray()
                    && body.get("redactionBoxes").size() > 0) {
                List<Map<String, Object>> boxes = new ArrayList<>();
                for (JsonNode box : body.get("redactionBoxes")) {
                    Map<String, Object> b = new HashMap<>();
                    b.put("page",   box.get("page").asInt());
                    b.put("x",      box.get("x").asDouble());
                    b.put("y",      box.get("y").asDouble());
                    b.put("width",  box.get("width").asDouble());
                    b.put("height", box.get("height").asDouble());
                    boxes.add(b);
                }
                File redactedFile = File.createTempFile("redacted-", ".pdf");
                JavaRedactor.processRedactionByBoxes(currentFile, redactedFile, boxes);
                currentFile = redactedFile;

            } else if (body.has("wordsToRedact")) {
                String wordsStr = body.get("wordsToRedact").asText();
                List<String> targetWords = new ArrayList<>();
                if (!wordsStr.isEmpty()) {
                    for (String w : wordsStr.split(",")) {
                        String trimmed = w.trim();
                        if (!trimmed.isEmpty()) targetWords.add(trimmed);
                    }
                }
                if (!targetWords.isEmpty()) {
                    File redactedFile = File.createTempFile("redacted-", ".pdf");
                    JavaRedactor.processRedaction(currentFile, redactedFile, targetWords);
                    currentFile = redactedFile;
                }
            }

            // 2. Stamp
            if (body.has("stamp") && !body.get("stamp").isNull()) {
                JsonNode stampNode = body.get("stamp");
                int page    = stampNode.get("page").asInt();
                float x     = (float) stampNode.get("x").asDouble();
                float y     = (float) stampNode.get("y").asDouble();
                File stampedFile = File.createTempFile("stamped-", ".pdf");
                ImageStamper.stampDocument(currentFile, stampedFile, page, x, y);
                currentFile = stampedFile;
            }

            // 3. Save final output
            String resultName = "result-" + UUID.randomUUID() + ".pdf";
            File finalOut = new File(Config.OUTPUT_DIR, resultName);
            Files.copy(currentFile.toPath(), finalOut.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ctx.json(Map.of("url", "/api/downloads/" + resultName));
        });

        app.get("/api/downloads/{filename}", ctx -> {
            String filename = ctx.pathParam("filename");
            File file = new File(Config.OUTPUT_DIR, filename);
            if (file.exists()) {
                ctx.contentType("application/pdf");
                ctx.result(Files.readAllBytes(file.toPath()));
            } else {
                ctx.status(404).result("File not found");
            }
        });
    }
}
