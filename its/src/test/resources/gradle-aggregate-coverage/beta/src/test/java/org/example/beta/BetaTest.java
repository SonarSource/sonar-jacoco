package org.example.beta;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BetaTest {
  @Test
  public void covers_one_method() {
    assertEquals(1, new Beta().covered());
  }
}
