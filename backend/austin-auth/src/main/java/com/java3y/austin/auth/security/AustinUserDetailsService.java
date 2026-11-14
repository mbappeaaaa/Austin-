package com.java3y.austin.auth.security;

import com.java3y.austin.auth.domain.SysUser;
import com.java3y.austin.auth.repository.SysUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AustinUserDetailsService implements UserDetailsService {
    private final SysUserRepository repository;

    public AustinUserDetailsService(SysUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
        return new AustinUserPrincipal(user.getId(), user.getTenantId(), user.getUsername(), user.getPasswordHash(),
                user.getDisplayName(), user.getRole(), Boolean.TRUE.equals(user.getEnabled()));
    }
}
