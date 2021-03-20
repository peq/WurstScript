package de.peeeq.wurstscript.utils;

import java.util.List;

public class TopsortCycleException extends Exception {

  @SuppressWarnings("ucd")
  public List<?> activeItems;

  public TopsortCycleException(List<?> activeItems2) {
    this.activeItems = activeItems2;
  }
}
