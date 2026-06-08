package com.akrion.socialposter.repository;

import com.akrion.socialposter.model.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
}