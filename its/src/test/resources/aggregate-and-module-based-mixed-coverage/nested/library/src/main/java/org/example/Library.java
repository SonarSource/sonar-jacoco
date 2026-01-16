package org.example;

import java.util.Objects;

/**
 * This class contains code that has very little to do with the libraries
 * It is also shaped in a way that applying coverage metrics from the other files should not work.
 */
public final class Library {
  public final String name;

  public Library(String name) {
    this.name = name;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Library)) return false;
    Library library = (Library) o;
    return Objects.equals(name, library.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }
}