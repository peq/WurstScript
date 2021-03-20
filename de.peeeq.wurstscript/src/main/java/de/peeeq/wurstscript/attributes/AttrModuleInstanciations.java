package de.peeeq.wurstscript.attributes;

import de.peeeq.wurstscript.ast.ModuleDef;
import de.peeeq.wurstscript.ast.ModuleInstanciation;
import de.peeeq.wurstscript.ast.TypeDef;
import de.peeeq.wurstscript.utils.Utils;
import org.eclipse.jdt.annotation.Nullable;

public class AttrModuleInstanciations {

  public static @Nullable ModuleDef getModuleOrigin(ModuleInstanciation mi) {
    TypeDef def = mi.getParent().lookupType(mi.getName());
    if (def instanceof ModuleDef) {
      return (ModuleDef) def;
    } else {
      mi.addError("Could not find module origin for " + Utils.printElement(mi));
    }
    return null;
  }
}
