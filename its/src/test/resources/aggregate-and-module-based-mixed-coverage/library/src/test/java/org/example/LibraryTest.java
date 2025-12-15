package org.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LibraryTest {
  @Test
  void returns_null_when_dividing_by_zero() {
    Library library = new Library();
    Assertions.assertNull(library.div(2, 0));
  }
}