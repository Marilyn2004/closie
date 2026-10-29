package com.fashionsearch.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

class DemoSafetyConfigTest {
  @Test
  void guardRejectsWrongDatabaseBeforeMigration() {
    var environment =
        new MockEnvironment()
            .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/unrelated");
    assertThatThrownBy(
            () ->
                DemoSafetyConfig.demoDatabaseGuard(environment)
                    .postProcessBeanFactory(mock(ConfigurableListableBeanFactory.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("closie_demo");
  }

  @Test
  void guardAllowsOnlyExplicitLocalDemoDatabase() {
    var environment =
        new MockEnvironment()
            .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:55432/closie_demo");
    assertThatCode(
            () ->
                DemoSafetyConfig.demoDatabaseGuard(environment)
                    .postProcessBeanFactory(mock(ConfigurableListableBeanFactory.class)))
        .doesNotThrowAnyException();
  }
}
