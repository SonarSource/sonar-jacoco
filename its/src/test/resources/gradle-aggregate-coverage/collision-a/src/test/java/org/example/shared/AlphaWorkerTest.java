package org.example.shared;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AlphaWorkerTest {
  @Test
  public void covers_the_worker() {
    assertEquals(1, new AlphaWorker().covered());
  }
}
