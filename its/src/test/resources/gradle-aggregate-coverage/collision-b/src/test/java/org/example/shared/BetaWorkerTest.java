package org.example.shared;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BetaWorkerTest {
  @Test
  public void covers_the_worker() {
    assertEquals(2, new BetaWorker().covered());
  }
}
