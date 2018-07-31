package org.sonarsource.test;

import org.junit.Assert;
import org.junit.Test;

public class CalcTest {
  private Calc calc = new Calc(5);

 @Test
  public void should_add() {
   Assert.assertEquals(9, calc.add(4));
 }

  @Test
  public void should_subtract() {
    Assert.assertEquals(-2, calc.subtract(7));
  }

  @Test
  public void should_add_and_return_0_if_sum_negative() {
    Assert.assertEquals(0, calc.add(-9));
  }

}
