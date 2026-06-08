package com.akrion.socialposter.service;

import com.akrion.socialposter.dto.LoginRequest;
import com.akrion.socialposter.dto.RegisterRequest;
import com.akrion.socialposter.model.User;
import com.akrion.socialposter.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String register(RegisterRequest request) {

        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            return "EMAIL_ALREADY_EXISTS";
        }

        User user = new User(
                request.getName(),
                email,
                request.getPassword()
        );

        userRepository.save(user);

        return "USER_REGISTERED_SUCCESSFULLY";
    }

    public String login(LoginRequest request) {

        String email = request.getEmail().trim().toLowerCase();
        String password = request.getPassword().trim();

        User user = userRepository.findByEmail(email)
                .orElse(null);

        if (user == null) {
            return "USER_NOT_FOUND";
        }

        if (!user.getPassword().equals(password)) {
            return "WRONG_PASSWORD";
        }

        return "LOGIN_SUCCESS";
    }
}