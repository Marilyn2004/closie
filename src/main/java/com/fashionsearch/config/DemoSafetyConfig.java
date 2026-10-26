package com.fashionsearch.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnProperty(name = "closie.demo.enabled", havingValue = "true")
public class DemoSafetyConfig {
  /** Runs before datasource/Flyway creation: never migrate an unrelated DB in demo mode. */
  @Bean
  static BeanFactoryPostProcessor demoDatabaseGuard(Environment environment) {
    return factory -> {
      String url = environment.getProperty("spring.datasource.url", "");
      if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):55432/closie_demo"))
        throw new IllegalStateException(
            "Demo mode only permits the local closie_demo database on port 55432. Use ./dev demo.");
    };
  }
}
