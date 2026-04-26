package com.securellm.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;

@Configuration
public class LdapConfig {

    @Value("${ldap.url}")
    private String ldapUrl;

    @Value("${ldap.base}")
    private String ldapBase;

    @Value("${ldap.manager-dn}")
    private String managerDn;

    @Value("${ldap.manager-password}")
    private String managerPassword;

    @Bean
    public LdapContextSource ldapContextSource() {
        LdapContextSource source = new LdapContextSource();
        source.setUrl(ldapUrl);
        source.setBase(ldapBase);
        source.setUserDn(managerDn);
        source.setPassword(managerPassword);
        return source;
    }

    @Bean
    public LdapAuthenticationProvider ldapAuthenticationProvider(LdapContextSource ldapContextSource) {
        BindAuthenticator authenticator = new BindAuthenticator(ldapContextSource);
        // bitnami/openldap creates users as cn=<username>,ou=users
        authenticator.setUserDnPatterns(new String[]{"cn={0},ou=users"});

        DefaultLdapAuthoritiesPopulator authoritiesPopulator =
            new DefaultLdapAuthoritiesPopulator(ldapContextSource, "ou=groups");
        authoritiesPopulator.setGroupRoleAttribute("cn");
        // {0} is substituted with the authenticated user's full DN
        authoritiesPopulator.setGroupSearchFilter("(member={0})");
        authoritiesPopulator.setRolePrefix("ROLE_");
        authoritiesPopulator.setConvertToUpperCase(true);

        return new LdapAuthenticationProvider(authenticator, authoritiesPopulator);
    }
}
