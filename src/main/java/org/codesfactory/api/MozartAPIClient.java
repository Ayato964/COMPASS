package org.codesfactory.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

public class MozartAPIClient {
    private static final String BASE_URL = "https://c7be-133-43-172-128.ngrok-free.app";

    public List<ModelInfo> getModelInfo() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/model_info"))
                .header("ngrok-skip-browser-warning", "1")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP error: " + response.statusCode());
        }

        String json = response.body();
        Map<String, Map<String, Object>> responseMap = new Gson().fromJson(
                json, new TypeToken<Map<String, Map<String, Object>>>(){}.getType()
        );

        List<ModelInfo> list = new ArrayList<>();
        if (responseMap != null) {
            for (Map<String, Object> modelData : responseMap.values()) {
                String modelName = (String) modelData.get("model_name");
                if (modelName == null) continue;

                // SFT-gen または SFT-omni のみをフィルタリング
                String lowerName = modelName.toLowerCase();
                if (lowerName.contains("sft-gen") || lowerName.contains("sft_gen") || lowerName.contains("sft-omni") || lowerName.contains("sft_omni")) {
                    String displayName = modelName;
                    list.add(new ModelInfo(modelName, displayName));
                }
            }
        }
        return list;
    }

    public byte[] generate(File midiFile, File metaFile) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        String boundary = "Boundary-" + UUID.randomUUID().toString();
        byte[] body = createMultipartBody(midiFile, metaFile, boundary);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/generate"))
                .header("ngrok-skip-browser-warning", "1")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            String errorMsg = new String(response.body(), StandardCharsets.UTF_8);
            throw new IOException("Generation failed with status " + response.statusCode() + ": " + errorMsg);
        }

        return response.body();
    }

    private byte[] createMultipartBody(File midiFile, File metaFile, String boundary) throws IOException {
        List<byte[]> byteArrays = new ArrayList<>();
        String lineSeparator = "\r\n";

        // 1. meta_json
        StringBuilder metaHeader = new StringBuilder();
        metaHeader.append("--").append(boundary).append(lineSeparator);
        metaHeader.append("Content-Disposition: form-data; name=\"meta_json\"; filename=\"").append(metaFile.getName()).append("\"").append(lineSeparator);
        metaHeader.append("Content-Type: application/json").append(lineSeparator).append(lineSeparator);
        byteArrays.add(metaHeader.toString().getBytes(StandardCharsets.UTF_8));
        byteArrays.add(Files.readAllBytes(metaFile.toPath()));
        byteArrays.add(lineSeparator.getBytes(StandardCharsets.UTF_8));

        // 2. midi
        if (midiFile != null && midiFile.exists() && midiFile.length() > 0) {
            StringBuilder midiHeader = new StringBuilder();
            midiHeader.append("--").append(boundary).append(lineSeparator);
            midiHeader.append("Content-Disposition: form-data; name=\"midi\"; filename=\"").append(midiFile.getName()).append("\"").append(lineSeparator);
            midiHeader.append("Content-Type: audio/midi").append(lineSeparator).append(lineSeparator);
            byteArrays.add(midiHeader.toString().getBytes(StandardCharsets.UTF_8));
            byteArrays.add(Files.readAllBytes(midiFile.toPath()));
            byteArrays.add(lineSeparator.getBytes(StandardCharsets.UTF_8));
        }

        // End Boundary
        String endBoundary = "--" + boundary + "--" + lineSeparator;
        byteArrays.add(endBoundary.getBytes(StandardCharsets.UTF_8));

        // Flatten list of byte arrays
        int totalLength = byteArrays.stream().mapToInt(a -> a.length).sum();
        byte[] flattened = new byte[totalLength];
        int destPos = 0;
        for (byte[] array : byteArrays) {
            System.arraycopy(array, 0, flattened, destPos, array.length);
            destPos += array.length;
        }

        return flattened;
    }
}
