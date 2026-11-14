package com.java3y.austin.auth.vo;

import lombok.Data;

@Data
public class UserCreateRequest {
    private String username;
    private String displayName;
    private String password;
    private String role;
}
