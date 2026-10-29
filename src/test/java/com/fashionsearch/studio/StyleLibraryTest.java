package com.fashionsearch.studio;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StyleLibraryTest {
  @Test
  void broadIdeasHaveMultipleCategoriesAndExplicitAssumptions() {
    var briefs = StyleLibrary.interpret("Something cute for class", null);
    assertThat(briefs).hasSize(3);
    assertThat(briefs).extracting(StyleLibrary.Brief::category).contains("TOP", "BOTTOM");
    assertThat(briefs).allMatch(b -> !b.assumptions().isEmpty());
  }

  @Test
  void localizedEditPreservesConstructionAndCategory() {
    var first = StyleLibrary.interpret("baby blue fitted hoodie", null).getFirst();
    var edited = StyleLibrary.interpret("make it pink", first).getFirst();
    assertThat(edited.color()).isEqualTo("powder pink");
    assertThat(edited.garment()).isEqualTo(first.garment());
    assertThat(edited.closure()).isEqualTo(first.closure());
    assertThat(edited.fabric()).isEqualTo(first.fabric());
    assertThat(edited.fit()).isEqualTo(first.fit());
  }

  @Test
  void lowTopShoesAreNotTops() {
    assertThat(StyleLibrary.interpret("low-top shoes", null).getFirst().category())
        .isEqualTo("SHOES");
  }

  @Test
  void negatedColorNotApplied() {
    var b = StyleLibrary.interpret("pink cardigan", null).getFirst();
    assertThat(StyleLibrary.interpret("not blue", b).getFirst().color()).isEqualTo("powder pink");
    assertThat(StyleLibrary.interpret("not blue", b).getFirst().dislikes()).contains("blue");
  }

  @Test
  void promptIncludesConstructionAndPresentation() {
    var prompt = StyleLibrary.prompt(StyleLibrary.defaults("hoodie", 0));
    assertThat(prompt)
        .contains(
            "white studio background",
            "No people",
            "seams",
            "silver zip",
            "Preserve all unchanged");
  }

  @Test
  void rejectOversizedEmptyOrInvalidBrief() {
    assertThatThrownBy(() -> StyleLibrary.interpret(" ", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> StyleLibrary.interpret("a".repeat(1201), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new StyleLibrary.Brief(
                    "FAKE",
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    "x",
                    java.util.List.of(),
                    "x",
                    java.util.List.of(),
                    java.util.List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sessionTokensValidatedAndHashed() {
    assertThatThrownBy(() -> StudioService.owner("test"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(StudioService.owner("a".repeat(64))).hasSize(64).isNotEqualTo("a".repeat(64));
  }
}
