package com.akrion.socialposter.controller;

import com.akrion.socialposter.model.Post;
import com.akrion.socialposter.repository.PostRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@CrossOrigin("*")
public class PostController {

    private final PostRepository postRepository;

    public PostController(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @GetMapping
    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }

    @GetMapping("/scheduled")
    public List<Post> getScheduledPosts() {
        return postRepository.findAll()
                .stream()
                .filter(post -> "SCHEDULED".equals(post.getStatus()))
                .toList();
    }

    @GetMapping("/{id}")
    public Post getPostById(@PathVariable Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));
    }

    @PostMapping
    public Post createPost(@RequestBody Post post) {

        if (post.getStatus() != null && post.getStatus().equals("PUBLISHED")) {
            post.setStatus("PUBLISHED");
        } else if (post.getScheduledTime() != null) {
            post.setStatus("SCHEDULED");
        } else {
            post.setStatus("DRAFT");
        }

        return postRepository.save(post);
    }

    @PutMapping("/{id}")
    public Post updatePost(@PathVariable Long id, @RequestBody Post updatedPost) {

        Post existingPost = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        existingPost.setContent(updatedPost.getContent());
        existingPost.setImageUrl(updatedPost.getImageUrl());
        existingPost.setPlatform(updatedPost.getPlatform());
        existingPost.setScheduledTime(updatedPost.getScheduledTime());

        if (updatedPost.getScheduledTime() != null) {
            existingPost.setStatus("SCHEDULED");
        } else {
            existingPost.setStatus("DRAFT");
        }

        return postRepository.save(existingPost);
    }

    @DeleteMapping("/{id}")
    public String deletePost(@PathVariable Long id) {

        Post existingPost = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        postRepository.delete(existingPost);

        return "Post deleted successfully";
    }

    // Proper API endpoint
    @PostMapping("/{id}/publish/linkedin")
    public Post publishToLinkedIn(@PathVariable Long id) {
        return publishPost(id, "LINKEDIN");
    }

    // Temporary browser test endpoint
    @GetMapping("/{id}/publish/linkedin")
    public Post publishToLinkedInFromBrowser(@PathVariable Long id) {
        return publishPost(id, "LINKEDIN");
    }

    private Post publishPost(Long id, String platform) {

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        post.setPlatform(platform);
        post.setStatus("PUBLISHED");

        return postRepository.save(post);
    }
}