package com.openkm.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain configure(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(
			authorize -> authorize
				.requestMatchers("/Status/**").authenticated()
				.requestMatchers("/css/**", "/js/**", "/img/**", "/logo/**","/fonts/**","/Rss").permitAll()
				.requestMatchers("/Download/**").authenticated()
				.requestMatchers("/workflow-register/**").authenticated()
				.requestMatchers("/webdav/**").authenticated()
				.requestMatchers("/feed/**").authenticated()
				.requestMatchers("/cmis/browser/**").authenticated()
				.requestMatchers("/cmis/atom/**").authenticated()
				.requestMatchers("/cmis/atom11/**").authenticated()
				.requestMatchers("/services/rest/**").authenticated()
				.requestMatchers("/frontend/**").authenticated()
				.requestMatchers("/login.jsp").anonymous()
				.requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN")
				.requestMatchers("/mobile/**").authenticated()
				.requestMatchers("/RepositoryStartup").authenticated()
				.requestMatchers("/TextToSpeech").authenticated()
				.requestMatchers("/HtmlPreview").authenticated()
				.requestMatchers("/SyntaxHighlighter").authenticated()
				.requestMatchers("/Test").authenticated()
				.requestMatchers("/extension/ZohoFileUpload").anonymous()
				.requestMatchers("/extension/**").authenticated()
				.anyRequest().authenticated()
		)
		.csrf(csrf -> csrf.disable())
		.headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
				.formLogin(formLogin -> formLogin.loginPage("/login.jsp")
				.defaultSuccessUrl("/frontend/index.jsp", true)
				.failureUrl("/login.jsp?error=1")
				.loginProcessingUrl("/j_spring_security_check")
				.usernameParameter("j_username")
				.passwordParameter("j_password"))
		.exceptionHandling(exception -> exception.accessDeniedPage("/unauthorized.jsp"));
		
		return http.build();
		// http
		// 	.csrf().disable()
		// 	.authorizeRequests()
		// 	.antMatchers("/Status/**").authenticated()
		// 	.antMatchers("/css/**", "/js/**", "/img/**", "/logo/**","/fonts/**","/Rss").permitAll()
		// 	.antMatchers("/Download/**").authenticated()
		// 	.antMatchers("/workflow-register/**").authenticated()
		// 	.antMatchers("/webdav/**").authenticated()
		// 	.antMatchers("/feed/**").authenticated()
		// 	.antMatchers("/cmis/browser/**").authenticated()
		// 	.antMatchers("/cmis/atom/**").authenticated()
		// 	.antMatchers("/cmis/atom11/**").authenticated()
		// 	.antMatchers("/services/rest/**").authenticated()
		// 	.antMatchers("/frontend/**").authenticated()
		// 	.antMatchers("/login.jsp").anonymous()
		// 	.antMatchers("/admin/**").hasAuthority("ROLE_ADMIN")
		// 	.antMatchers("/mobile/**").authenticated()
		// 	.antMatchers("/RepositoryStartup").authenticated()
		// 	.antMatchers("/TextToSpeech").authenticated()
		// 	.antMatchers("/HtmlPreview").authenticated()
		// 	.antMatchers("/SyntaxHighlighter").authenticated()
		// 	.antMatchers("/Test").authenticated()
		// 	.antMatchers("/extension/ZohoFileUpload").anonymous()
		// 	.antMatchers("/extension/**").authenticated()
		// 	.anyRequest().authenticated()
		// 	.and()
		// 	.formLogin()
		// 	.loginPage("/login.jsp")
		// 	.defaultSuccessUrl("/frontend/index.jsp", true)
		// 	.failureUrl("/login.jsp?error=1")
		// 	.loginProcessingUrl("/j_spring_security_check")
		// 	.usernameParameter("j_username")
		// 	.passwordParameter("j_password")
		// 	.and()
		// 	.exceptionHandling()
		// 	.accessDeniedPage("/unauthorized.jsp")
		// 	.and()
		// 	.headers().frameOptions().sameOrigin();
	}
}
