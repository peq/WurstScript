package de.peeeq.wurstscript.utils;

import com.google.common.base.Objects;

public final class Pair<A, B> {

  private final A a;
  private final B b;

  private Pair(A a, B b) {
    this.a = a;
    this.b = b;
  }

  public static <A, B> Pair<A, B> create(A a, B b) {
    return new Pair<>(a, b);
  }

  public A getA() {
    return a;
  }

  public B getB() {
    return b;
  }

  @Override
  public String toString() {
    return "(" + a + ", " + b + ")";
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(a, b);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Pair) {
      @SuppressWarnings("rawtypes")
      Pair otherPair = (Pair) obj;
      return ((this.a == otherPair.a
              || (this.a != null && otherPair.a != null && this.a.equals(otherPair.a)))
          && (this.b == otherPair.b
              || (this.b != null && otherPair.b != null && this.b.equals(otherPair.b))));

    } else {
      return false;
    }
  }
}
