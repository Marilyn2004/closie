package com.fashionsearch.demo;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
public class PublicDemoConfig {
  @Bean
  static BeanFactoryPostProcessor publicDemoDatabaseGuard(Environment env) {
    return factory -> {
      if (!new PublicDemoPolicy(env).enabled()) return;
      String url = env.getProperty("spring.datasource.url", "");
      if (!url.matches("jdbc:postgresql://[^/]+/closie_public_demo(?:\\?.*)?"))
        throw new IllegalStateException(
            "Public demo requires a dedicated closie_public_demo database; private or live catalogs"
                + " must not be reused.");
      if (java.util.Arrays.asList(env.getActiveProfiles()).contains("studio")
          || !env.getProperty("closie.studio.enabled", Boolean.class, false))
        throw new IllegalStateException(
            "Use the public-demo profile alone with the studio experience enabled.");
    };
  }
}
