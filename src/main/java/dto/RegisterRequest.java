package com.akrion.socialposter.dto;

public class RegisterRequest {

    private String name;
    private String email;
    private String password;

    public RegisterRequest() {
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}