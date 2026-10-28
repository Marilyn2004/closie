package com.fashionsearch.demo;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Profile membership cannot be bypassed by accidentally enabling a provider or adding a key. */
@Component
public class PublicDemoPolicy {
  private final Environment env;

  public PublicDemoPolicy(Environment env) {
    this.env = env;
  }

  public boolean enabled() {
    return java.util.Arrays.asList(env.getActiveProfiles()).contains("public-demo")
        || env.getProperty("closie.public-demo.enabled", Boolean.class, false);
  }

  public void requirePrivate() {
    if (enabled())
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Live generation and private item tools are disabled in this public demo.");
  }
}
