package org.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SquarerTest {
  @Test
  void returns_squared_value() {
    Squarer squarer = new Squarer();
    Assertions.assertEquals(4, squarer.square(2));
  }
}
