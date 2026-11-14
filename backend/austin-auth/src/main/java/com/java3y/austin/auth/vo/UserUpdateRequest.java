package com.java3y.austin.auth.vo;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private String displayName;
    private String role;
    private Boolean enabled;
}
