package org.sonarsource.test;

public class Calc {
  private int acc;

  public Calc(int initial) {
    this.acc = initial;
  }

  public int add(int add) {
    acc = acc+add;
    if (acc < 0) {
      acc = 0;
    }
    return acc;
  }

  public int subtract(int sub) {
    acc = acc - sub;
    if (acc > 0) {
      acc = 0;
    }
    return acc;
  }

}
