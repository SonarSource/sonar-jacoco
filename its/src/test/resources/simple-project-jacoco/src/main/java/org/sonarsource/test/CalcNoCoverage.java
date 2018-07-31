package org.sonarsource.test;

public class CalcNoCoverage {
  private int acc;

  public CalcNoCoverage(int initial) {
    this.acc = initial;
  }

  public int add(int add) {
    acc = acc+add;
    if (acc < 0) {
      acc = 0;
    }
    return acc;
  }
}
