package org.example.alpha;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AlphaTest {
  @Test
  public void covers_one_method() {
    assertEquals(1, new Alpha().covered());
  }
}
