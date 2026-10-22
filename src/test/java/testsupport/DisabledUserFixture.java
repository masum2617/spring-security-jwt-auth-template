package testsupport;

import com.example.securityfoundation.demo.DemoUserDetailsService;
import com.example.securityfoundation.security.user.ApplicationUser;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Outside the application scan: imported explicitly by the HTTP contract test. */
@TestConfiguration(proxyBeanMethods = false)
public class DisabledUserFixture {
    @Bean
    @Primary
    UserDetailsService testUsers(DemoUserDetailsService demo, PasswordEncoder encoder) {
        ApplicationUser disabled = new ApplicationUser(
                "disabled",
                "disabled@example.com",
                encoder.encode("test-only-password"),
                false,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return email -> "disabled@example.com".equals(email) ? disabled : demo.loadUserByUsername(email);
    }
}
