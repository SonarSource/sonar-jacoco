package org.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LibraryTest {
  @Test
  void test() {
    Library library = new Library("City");
    Assertions.assertEquals("City", library.name);

    Library similarLibrary = new Library("City");
    Assertions.assertEquals(library, similarLibrary);
    Assertions.assertEquals(library.hashCode(), library.hashCode());
    Assertions.assertEquals(library.hashCode(), similarLibrary.hashCode());

    Assertions.assertNotEquals(library, new Object());
    Assertions.assertNotEquals(library, new Library("Other"));
  }
}
