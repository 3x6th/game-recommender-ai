package ru.perevalov.gamerecommenderai.core.bucket;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import ru.perevalov.gamerecommenderai.security.model.UserRole;

@Component("bucketUtil")
public class BucketUtil {

    public String getUserRole() {
        UserDetails principal = extractUserDetailsFromSecurityContext();

        return principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .findFirst()
                        .orElse(UserRole.GUEST.toString());
    }

    public String getUsername() {
        UserDetails principal = extractUserDetailsFromSecurityContext();
        return principal.getUsername();
    }

    private UserDetails extractUserDetailsFromSecurityContext() {
        Authentication auth = SecurityContextHolder
                                      .getContext()
                                      .getAuthentication();
        return (UserDetails) auth.getPrincipal();
    }

}
