package com.akrion.socialposter.controller;

import com.akrion.socialposter.model.SocialAccount;
import com.akrion.socialposter.repository.SocialAccountRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/social-accounts")
@CrossOrigin("*")
public class SocialAccountController {

    private final SocialAccountRepository socialAccountRepository;

    public SocialAccountController(SocialAccountRepository socialAccountRepository) {
        this.socialAccountRepository = socialAccountRepository;
    }

    @PostMapping("/connect")
    public String connectAccount(@RequestBody SocialAccount socialAccount) {
        socialAccountRepository.save(socialAccount);
        return socialAccount.getPlatform() + " connected successfully";
    }

    @GetMapping
    public List<SocialAccount> getAllAccounts() {
        return socialAccountRepository.findAll();
    }
}