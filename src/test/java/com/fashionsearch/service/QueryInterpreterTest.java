package com.fashionsearch.service;
import com.fashionsearch.dto.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
class QueryInterpreterTest {
 final QueryInterpreter parser=new QueryInterpreter(new UserQueryAi("","",false));
 SearchContext parse(String message,SearchContext old){return parser.interpret(new SearchRequest(message,old,null,0)).context();}
 @Test void conversationalRefinementPreservesBudgetAndOccasion(){
  var first=parse("I want a minimalist outfit for college under $150",null);
  var second=parse("Something more feminine",first);
  assertThat(second.maxPrice()).isEqualByComparingTo("150");assertThat(second.occasion()).isEqualTo("college");assertThat(second.aesthetic()).isEqualTo("feminine");assertThat(second.keywords()).isEmpty();
 }
 @Test void explicitFiltersOverrideText(){
  var c=parser.interpret(new SearchRequest("black dress under $100",null,new SearchContext(null,null,"Mango",null,null,new BigDecimal("75"),"USD",null,null),0)).context();
  assertThat(c.maxPrice()).isEqualByComparingTo("75");assertThat(c.brand()).isEqualTo("Mango");assertThat(c.color()).isEqualTo("black");
 }
 @Test void refinementChangesCategoryWithoutLosingPrice(){var c=parse("under $50",null);c=parse("a cardigan",c);assertThat(c.category()).isEqualTo("cardigan");assertThat(c.maxPrice()).isEqualByComparingTo("50");}
 @Test void cheaperTightensExistingBudget(){assertThat(parse("cheaper",parse("under $100",null)).maxPrice()).isEqualByComparingTo("80");}
 @Test void rejectsContradictoryAndNegativeFilters(){assertThatThrownBy(()->parser.interpret(new SearchRequest("",null,new SearchContext(null,null,null,null,new BigDecimal("80"),new BigDecimal("10"),"USD",null,null),0))).isInstanceOf(IllegalArgumentException.class);}
 @Test void invalidAiPreferencesFallBackToRules(){
  var ai=org.mockito.Mockito.mock(UserQueryAi.class);
  org.mockito.Mockito.when(ai.interpret(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(new SearchContext("",null,null,null,null,new BigDecimal("-1"),"USD",null,null)));
  var result=new QueryInterpreter(ai).interpret(new SearchRequest("feminine",null,null,0));assertThat(result.mode()).isEqualTo("rules");assertThat(result.context().aesthetic()).isEqualTo("feminine");
 }
 @Test void aiCannotOverrideExplicitBudget(){
  var ai=org.mockito.Mockito.mock(UserQueryAi.class);
  org.mockito.Mockito.when(ai.interpret(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(new SearchContext("",null,null,null,null,new BigDecimal("900"),"USD",null,null)));
  var result=new QueryInterpreter(ai).interpret(new SearchRequest("under $50",null,null,0));assertThat(result.context().maxPrice()).isEqualByComparingTo("50");
 }
 @Test void aiCannotSilentlyLoosenPriorConversationBudget(){
  var ai=org.mockito.Mockito.mock(UserQueryAi.class);
  org.mockito.Mockito.when(ai.interpret(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.of(new SearchContext("",null,null,null,null,new BigDecimal("900"),"USD","feminine",null)));
  var prior=parse("outfit for college under $50",null);
  var result=new QueryInterpreter(ai).interpret(new SearchRequest("more feminine",prior,null,0));assertThat(result.context().maxPrice()).isEqualByComparingTo("50");assertThat(result.context().occasion()).isEqualTo("college");
 }
 @Test void descriptiveKeywordsSurviveCategoryExtraction(){var c=parse("black cropped leather jacket under $200",null);assertThat(c.keywords()).isEqualTo("cropped leather jacket");assertThat(c.category()).isEqualTo("jacket");}
 @Test void arbitraryKeywordDoesNotBecomeInventedProduct(){assertThat(parse("cashmere",null).keywords()).isEqualTo("cashmere");}
}
