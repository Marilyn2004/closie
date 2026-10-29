package com.fashionsearch.studio;

public interface ImageGenerationProvider {
  record GeneratedImage(byte[] bytes, java.util.Map<String, Object> metadata) {}

  default String name() {
    return "GEMINI";
  }

  default String settingsKey() {
    return name() + ":" + model();
  }

  default GeneratedImage generateWithMetadata(String prompt, byte[] reference) throws Exception {
    return new GeneratedImage(generate(prompt, reference), java.util.Map.of());
  }

  boolean configured();

  String model();

  byte[] generate(String prompt, byte[] reference) throws Exception;
}
