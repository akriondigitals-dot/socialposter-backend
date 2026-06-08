package com.akrion.socialposter.service;

import com.akrion.socialposter.model.Post;
import com.akrion.socialposter.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostService {

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public Post createPost(Post post) {

        if (post.getScheduledTime() != null) {
            post.setStatus("SCHEDULED");
        } else {
            post.setStatus("DRAFT");
        }

        return postRepository.save(post);
    }

    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }

    public List<Post> getPostsByUser(Long userId) {
        return postRepository.findByUserId(userId);
    }
}