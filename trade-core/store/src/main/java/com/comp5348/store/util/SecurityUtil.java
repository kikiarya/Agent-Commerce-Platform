package com.comp5348.store.util;

import com.comp5348.store.model.LoginUser;
import com.comp5348.store.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtil {

    public static User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getUser();
        }
        return null;
    }
}
