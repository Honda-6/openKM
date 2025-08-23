package com.openkm.spring;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	@Autowired
	private KeycloakAuthenticationProvider keycloakAuthenticationProvider;

	@Override
	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
		auth.authenticationProvider(this.keycloakAuthenticationProvider);
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http
			.csrf().disable()
			.authorizeRequests()
			.antMatchers("/Status/**").authenticated()
			.antMatchers("/css/**", "/js/**", "/img/**", "/logo/**","/fonts/**","/Rss").permitAll()
			.antMatchers("/Download/**").authenticated()
			.antMatchers("/workflow-register/**").authenticated()
			.antMatchers("/webdav/**").authenticated()
			.antMatchers("/feed/**").authenticated()
			.antMatchers("/cmis/browser/**").authenticated()
			.antMatchers("/cmis/atom/**").authenticated()
			.antMatchers("/cmis/atom11/**").authenticated()
			.antMatchers("/services/rest/**").authenticated()
			.antMatchers("/frontend/**").authenticated()
			.antMatchers("/login.jsp").anonymous()
			.antMatchers("/admin/**").hasRole("ADMIN")
			.antMatchers("/mobile/**").authenticated()
			.antMatchers("/RepositoryStartup").authenticated()
			.antMatchers("/TextToSpeech").authenticated()
			.antMatchers("/HtmlPreview").authenticated()
			.antMatchers("/SyntaxHighlighter").authenticated()
			.antMatchers("/Test").authenticated()
			.antMatchers("/extension/ZohoFileUpload").anonymous()
			.antMatchers("/extension/**").authenticated()
			.anyRequest().authenticated()
			.and()
			.formLogin()
			.loginPage("/login.jsp")
			.defaultSuccessUrl("/frontend/index.jsp", true)
			.failureUrl("/login.jsp?error=1")
			.loginProcessingUrl("/j_spring_security_check")
			.usernameParameter("j_username")
			.passwordParameter("j_password")
			.and()
			.exceptionHandling()
			.accessDeniedPage("/unauthorized.jsp");
	}
}
