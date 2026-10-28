package com.fashionsearch.studio;

public interface ImageGenerationProvider {
  boolean configured();

  String model();

  byte[] generate(String prompt, byte[] reference) throws Exception;
}
