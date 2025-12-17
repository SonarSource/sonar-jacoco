package org.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LibraryTest {
  @Test
  void incompleteTest() {
    Library library = new Library();
    Assertions.assertEquals("Hello, World!", library.greet("World"));
  }
}