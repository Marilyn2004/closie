package com.fashionsearch.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** This profile is a local workspace, never a production migration/deployment shortcut. */
@Configuration
@Profile("studio")
public class StudioSafetyConfig {
  @Bean
  static BeanFactoryPostProcessor studioDatabaseGuard(Environment environment) {
    return factory -> {
      String url = environment.getProperty("spring.datasource.url", "");
      if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):55432/closie_studio"))
        throw new IllegalStateException(
            "Studio profile requires the isolated local closie_studio database. Use ./dev studio.");
    };
  }
}
