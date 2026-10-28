package com.fashionsearch.demo;

import static org.assertj.core.api.Assertions.*;

import com.fashionsearch.studio.GeminiImages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

class PublicDemoGuardTest {
  @Test
  void modeRequiresEitherAnExplicitPropertyOrProfile() {
    var env = new MockEnvironment();
    assertThat(new PublicDemoPolicy(env).enabled()).isFalse();
    env.setProperty("closie.public-demo.enabled", "true");
    assertThat(new PublicDemoPolicy(env).enabled()).isTrue();
    env.setProperty("closie.public-demo.enabled", "false");
    env.setActiveProfiles("public-demo");
    assertThat(new PublicDemoPolicy(env).enabled()).isTrue();
  }

  @Test
  void publicModeRejectsPrivateDatabaseBeforeMigration() {
    var env =
        new MockEnvironment()
            .withProperty("closie.public-demo.enabled", "true")
            .withProperty("closie.studio.enabled", "true")
            .withProperty(
                "spring.datasource.url", "jdbc:postgresql://localhost:55432/closie_studio");
    assertThatThrownBy(
            () ->
                PublicDemoConfig.publicDemoDatabaseGuard(env)
                    .postProcessBeanFactory(new DefaultListableBeanFactory()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dedicated closie_public_demo");
  }

  @Test
  void missingKeysDoNotPreventThePublicConfigurationGuard() {
    var env =
        new MockEnvironment()
            .withProperty("closie.studio.enabled", "true")
            .withProperty(
                "spring.datasource.url", "jdbc:postgresql://localhost:55432/closie_public_demo");
    env.setActiveProfiles("public-demo");
    assertThatCode(
            () ->
                PublicDemoConfig.publicDemoDatabaseGuard(env)
                    .postProcessBeanFactory(new DefaultListableBeanFactory()))
        .doesNotThrowAnyException();
  }

  @Test
  void geminiIsBlockedEvenWithAKeyAndEnabledFlag() {
    var provider = new GeminiImages("fixture-private-key", "gemini-fixture", true);
    var env = new MockEnvironment().withProperty("closie.public-demo.enabled", "true");
    ReflectionTestUtils.setField(provider, "demoPolicy", new PublicDemoPolicy(env));
    assertThat(provider.configured()).isFalse();
    assertThatThrownBy(() -> provider.generate("white cardigan", null))
        .isInstanceOf(IllegalStateException.class);
  }
}
