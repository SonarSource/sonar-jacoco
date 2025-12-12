package org.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LibraryTest {
  @Test
  void incompleteTest() {
    Assertions.assertEquals(2, Library.div(2, 1));
  }
}