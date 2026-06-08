package com.akrion.socialposter.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Controller
public class FacebookController {

    @Value("${facebook.app.id}")
    private String appId;

    @Value("${facebook.app.secret}")
    private String appSecret;

    @Value("${facebook.redirect.uri}")
    private String redirectUri;

    private String userAccessToken;
    private String pageId;
    private String pageName;
    private String pageAccessToken;

    @GetMapping("/api/facebook/connect")
    public void connectFacebook(HttpServletResponse response) throws IOException {

        String scope =
                "pages_show_list,pages_read_engagement,pages_manage_posts";

        String url =
                "https://www.facebook.com/v19.0/dialog/oauth"
                        + "?client_id=" + appId
                        + "&redirect_uri="
                        + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                        + "&scope="
                        + URLEncoder.encode(scope, StandardCharsets.UTF_8);

        response.sendRedirect(url);
    }

    @GetMapping("/api/facebook/callback")
    public void facebookCallback(
            @RequestParam("code") String code,
            HttpServletResponse response) throws IOException {

        exchangeCodeForUserToken(code);
        fetchFirstFacebookPage();

        System.out.println("Facebook Page ID: " + pageId);
        System.out.println("Facebook Page Name: " + pageName);

        response.sendRedirect("akrion://facebook-success");
    }

    @ResponseBody
    @GetMapping("/api/facebook/pages")
    public ResponseEntity<?> getPages() {

        if (userAccessToken == null) {
            return ResponseEntity
                    .badRequest()
                    .body("Facebook is not connected yet");
        }

        String url =
                "https://graph.facebook.com/v19.0/me/accounts"
                        + "?access_token=" + userAccessToken;

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<Map> response =
                restTemplate.getForEntity(url, Map.class);

        return ResponseEntity.ok(response.getBody());
    }

    @ResponseBody
    @PostMapping("/api/facebook/publish")
    public ResponseEntity<String> publishToFacebook(
            @RequestBody Map<String, String> request) {

        try {

            if (pageAccessToken == null || pageId == null) {
                return ResponseEntity
                        .badRequest()
                        .body("Facebook Page is not connected yet");
            }

            String content = request.get("content");

            if (content == null || content.trim().isEmpty()) {
                return ResponseEntity
                        .badRequest()
                        .body("Post content is required");
            }

            String url =
                    "https://graph.facebook.com/v19.0/"
                            + pageId
                            + "/feed";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(
                    MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> formData =
                    new LinkedMultiValueMap<>();

            formData.add("message", content);
            formData.add("access_token", pageAccessToken);

            HttpEntity<MultiValueMap<String, String>> entity =
                    new HttpEntity<>(formData, headers);

            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<Map> response =
                    restTemplate.postForEntity(
                            url,
                            entity,
                            Map.class);

            System.out.println(
                    "Facebook Publish Response: "
                            + response.getBody());

            return ResponseEntity.ok(
                    "Posted to Facebook successfully");

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    private void exchangeCodeForUserToken(String code) {

        String url =
                "https://graph.facebook.com/v19.0/oauth/access_token"
                        + "?client_id=" + appId
                        + "&redirect_uri="
                        + URLEncoder.encode(
                        redirectUri,
                        StandardCharsets.UTF_8)
                        + "&client_secret=" + appSecret
                        + "&code=" + code;

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<Map> response =
                restTemplate.getForEntity(url, Map.class);

        userAccessToken =
                response.getBody()
                        .get("access_token")
                        .toString();

        System.out.println(
                "Facebook User Access Token: "
                        + userAccessToken);
    }

    private void fetchFirstFacebookPage() {

        String url =
                "https://graph.facebook.com/v19.0/me/accounts"
                        + "?access_token=" + userAccessToken;

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<Map> response =
                restTemplate.getForEntity(url, Map.class);

        List<Map<String, Object>> pages =
                (List<Map<String, Object>>)
                        response.getBody().get("data");

        if (pages == null || pages.isEmpty()) {
            throw new RuntimeException(
                    "No Facebook Pages found for this user");
        }

        Map<String, Object> firstPage = pages.get(0);

        pageId = firstPage.get("id").toString();
        pageName = firstPage.get("name").toString();
        pageAccessToken =
                firstPage.get("access_token").toString();
    }
}