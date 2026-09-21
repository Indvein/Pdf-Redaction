package com.voxomos.poc;

/**
 * LEGACY: Python/PyMuPDF-backed redaction client.
 *
 * This class delegates true redaction to the Flask microservice
 * (redact-service/redact_service.py) over HTTP.
 *
 * Kept for comparison during development of JavaRedactor.java.
 * Retire this class once JavaRedactor passes all verification steps.
 *
 * @see JavaRedactor for the Java-only replacement.
 */

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class Redactor {

    public static void redactText() {
        String inputFilePath = Config.SAMPLES_DIR + "/file-sample_150kB.pdf";
        String targetWord = "Lorem ipsum";
        String outputFilePath = Config.OUTPUT_DIR + "/redacted.pdf";

        File inputFile = new File(inputFilePath);
        if (!inputFile.exists()) {
            System.err.println("Input file not found: " + inputFile.getAbsolutePath());
            return;
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 1. Health check first
            HttpGet healthRequest = new HttpGet(Config.REDACTION_HEALTH_URL);
            try (CloseableHttpResponse healthResponse = httpClient.execute(healthRequest)) {
                if (healthResponse.getCode() != 200) {
                    System.err.println("ERROR: Redaction service is not running. Start it with: python redact_service.py");
                    return;
                }
            } catch (IOException e) {
                System.err.println("ERROR: Redaction service is not reachable. Start it with: python redact_service.py");
                return;
            }

            // 2. Build the multipart HTTP request
            HttpPost postRequest = new HttpPost(Config.REDACTION_SERVICE_URL);
            HttpEntity multipartEntity = MultipartEntityBuilder.create()
                    .addBinaryBody("pdf_file", inputFile)
                    .addTextBody("word", targetWord)
                    .build();
            postRequest.setEntity(multipartEntity);

            // 3. Send request and handle response
            try (CloseableHttpResponse response = httpClient.execute(postRequest)) {
                if (response.getCode() == 200) {
                    HttpEntity responseEntity = response.getEntity();
                    if (responseEntity != null) {
                        File outputFile = new File(outputFilePath);
                        try (FileOutputStream outStream = new FileOutputStream(outputFile)) {
                            responseEntity.writeTo(outStream);
                        }
                        System.out.println("True redaction complete. Saved to " + outputFile.getAbsolutePath());
                    }
                } else {
                    System.err.println("Redaction failed with HTTP " + response.getCode());
                }
            }
        } catch (IOException e) {
            System.err.println("An error occurred during true redaction: " + e.getMessage());
        }
    }
}
