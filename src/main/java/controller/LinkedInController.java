package com.akrion.socialposter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class LinkedInController {

    @Value("${linkedin.client.id}")
    private String clientId;

    @Value("${linkedin.client.secret}")
    private String clientSecret;

    @Value("${linkedin.redirect.uri}")
    private String redirectUri;

    private String accessToken;
    private String linkedInUserId;

    @GetMapping("/api/linkedin/connect")
    public void connectLinkedIn(HttpServletResponse response) throws IOException {

        String url =
                "https://www.linkedin.com/oauth/v2/authorization" +
                        "?response_type=code" +
                        "&client_id=" + clientId +
                        "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                        "&scope=openid%20profile%20email%20w_member_social";

        response.sendRedirect(url);
    }

    @GetMapping("/api/linkedin/callback")
    public void linkedInCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false, name = "error_description") String errorDescription,
            HttpServletResponse response) throws IOException {

        if (error != null) {
            System.out.println("LinkedIn OAuth Error: " + error);
            System.out.println("LinkedIn OAuth Error Description: " + errorDescription);

            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "LinkedIn OAuth error: " + error + " - " + errorDescription
            );
            return;
        }

        if (code == null || code.trim().isEmpty()) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Missing LinkedIn authorization code"
            );
            return;
        }

        try {
            System.out.println("LinkedIn Code: " + code);

            exchangeCodeForAccessToken(code);
            fetchLinkedInUserId();

            // Temporary browser test redirect
            response.sendRedirect("https://socialposter-backend.onrender.com/api/posts");

        } catch (Exception e) {
            e.printStackTrace();

            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "LinkedIn connection failed: " + e.getMessage()
            );
        }
    }

    @ResponseBody
    @PostMapping("/api/linkedin/publish")
    public ResponseEntity<String> publishToLinkedIn(@RequestBody Map<String, String> request) {

        try {
            String content = request.get("content");
            return publishTextOnly(content);

        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @ResponseBody
    @PostMapping(value = "/api/linkedin/publish-with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> publishToLinkedInWithImage(
            @RequestParam("content") String content,
            @RequestParam("image") MultipartFile image) {

        try {
            if (accessToken == null || linkedInUserId == null) {
                return ResponseEntity.badRequest().body("LinkedIn is not connected yet");
            }

            if (content == null || content.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Post content is required");
            }

            if (image == null || image.isEmpty()) {
                return ResponseEntity.badRequest().body("Image is required");
            }

            String ownerUrn = "urn:li:person:" + linkedInUserId;

            Map<String, String> uploadData = registerImageUpload(ownerUrn);

            String uploadUrl = uploadData.get("uploadUrl");
            String asset = uploadData.get("asset");

            uploadImageToLinkedIn(uploadUrl, image);

            createImagePost(content, ownerUrn, asset);

            return ResponseEntity.ok("Posted image to LinkedIn successfully");

        } catch (HttpClientErrorException e) {
            System.out.println("LinkedIn Image Publish Error Status: " + e.getStatusCode());
            System.out.println("LinkedIn Image Publish Error Body: " + e.getResponseBodyAsString());

            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private ResponseEntity<String> publishTextOnly(String content) throws Exception {

        if (accessToken == null || linkedInUserId == null) {
            return ResponseEntity.badRequest().body("LinkedIn is not connected yet");
        }

        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Post content is required");
        }

        String url = "https://api.linkedin.com/v2/ugcPosts";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Restli-Protocol-Version", "2.0.0");

        Map<String, Object> shareCommentary = new HashMap<>();
        shareCommentary.put("text", content);

        Map<String, Object> shareContent = new HashMap<>();
        shareContent.put("shareCommentary", shareCommentary);
        shareContent.put("shareMediaCategory", "NONE");

        Map<String, Object> specificContent = new HashMap<>();
        specificContent.put("com.linkedin.ugc.ShareContent", shareContent);

        Map<String, Object> visibility = new HashMap<>();
        visibility.put("com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC");

        Map<String, Object> body = new HashMap<>();
        body.put("author", "urn:li:person:" + linkedInUserId);
        body.put("lifecycleState", "PUBLISHED");
        body.put("specificContent", specificContent);
        body.put("visibility", visibility);

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonBody = objectMapper.writeValueAsString(body);

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        RestTemplate restTemplate = createRestTemplate();

        restTemplate.postForEntity(url, entity, String.class);

        return ResponseEntity.ok("Posted to LinkedIn successfully");
    }

    private Map<String, String> registerImageUpload(String ownerUrn) throws Exception {

        String url = "https://api.linkedin.com/v2/assets?action=registerUpload";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Restli-Protocol-Version", "2.0.0");

        Map<String, Object> registerUploadRequest = new HashMap<>();
        registerUploadRequest.put("recipes", List.of("urn:li:digitalmediaRecipe:feedshare-image"));
        registerUploadRequest.put("owner", ownerUrn);
        registerUploadRequest.put("serviceRelationships", List.of(
                Map.of(
                        "relationshipType", "OWNER",
                        "identifier", "urn:li:userGeneratedContent"
                )
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("registerUploadRequest", registerUploadRequest);

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonBody = objectMapper.writeValueAsString(body);

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        RestTemplate restTemplate = createRestTemplate();

        ResponseEntity<Map> response =
                restTemplate.postForEntity(url, entity, Map.class);

        Map value = (Map) response.getBody().get("value");
        Map uploadMechanism = (Map) value.get("uploadMechanism");
        Map mediaUploadHttpRequest =
                (Map) uploadMechanism.get("com.linkedin.digitalmedia.uploading.MediaUploadHttpRequest");

        String uploadUrl = mediaUploadHttpRequest.get("uploadUrl").toString();
        String asset = value.get("asset").toString();

        Map<String, String> result = new HashMap<>();
        result.put("uploadUrl", uploadUrl);
        result.put("asset", asset);

        return result;
    }

    private void uploadImageToLinkedIn(String uploadUrl, MultipartFile image) throws IOException {

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.parseMediaType(image.getContentType()));

        HttpEntity<byte[]> entity =
                new HttpEntity<>(image.getBytes(), headers);

        RestTemplate restTemplate = createRestTemplate();

        restTemplate.exchange(
                uploadUrl,
                HttpMethod.PUT,
                entity,
                String.class
        );
    }

    private void createImagePost(String content, String ownerUrn, String asset) throws Exception {

        String url = "https://api.linkedin.com/v2/ugcPosts";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Restli-Protocol-Version", "2.0.0");

        Map<String, Object> shareCommentary = new HashMap<>();
        shareCommentary.put("text", content);

        Map<String, Object> mediaItem = new HashMap<>();
        mediaItem.put("status", "READY");
        mediaItem.put("description", Map.of("text", content));
        mediaItem.put("media", asset);
        mediaItem.put("title", Map.of("text", "Akrion Post"));

        Map<String, Object> shareContent = new HashMap<>();
        shareContent.put("shareCommentary", shareCommentary);
        shareContent.put("shareMediaCategory", "IMAGE");
        shareContent.put("media", List.of(mediaItem));

        Map<String, Object> specificContent = new HashMap<>();
        specificContent.put("com.linkedin.ugc.ShareContent", shareContent);

        Map<String, Object> visibility = new HashMap<>();
        visibility.put("com.linkedin.ugc.MemberNetworkVisibility", "PUBLIC");

        Map<String, Object> body = new HashMap<>();
        body.put("author", ownerUrn);
        body.put("lifecycleState", "PUBLISHED");
        body.put("specificContent", specificContent);
        body.put("visibility", visibility);

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonBody = objectMapper.writeValueAsString(body);

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        RestTemplate restTemplate = createRestTemplate();

        restTemplate.postForEntity(url, entity, String.class);
    }

    private RestTemplate createRestTemplate() {

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setBufferRequestBody(true);

        return new RestTemplate(requestFactory);
    }

    private void exchangeCodeForAccessToken(String code) {

        String tokenUrl = "https://www.linkedin.com/oauth/v2/accessToken";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("code", code);
        formData.add("redirect_uri", redirectUri);
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(formData, headers);

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<Map> response =
                restTemplate.postForEntity(tokenUrl, request, Map.class);

        accessToken = response.getBody().get("access_token").toString();

        System.out.println("LinkedIn Access Token: " + accessToken);
    }

    private void fetchLinkedInUserId() {

        String userInfoUrl = "https://api.linkedin.com/v2/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<Map> response =
                restTemplate.exchange(
                        userInfoUrl,
                        HttpMethod.GET,
                        request,
                        Map.class
                );

        System.out.println("LinkedIn UserInfo Response: " + response.getBody());

        linkedInUserId = response.getBody().get("sub").toString();

        System.out.println("LinkedIn User ID: " + linkedInUserId);
    }
}