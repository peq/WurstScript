package de.peeeq.eclipsewurstplugin.builder;

import java.io.File;
import java.io.Reader;
import java.util.List;

import org.eclipse.core.resources.IResource;

import de.peeeq.eclipsewurstplugin.editor.CompilationUnitChangeListener;
import de.peeeq.wurstscript.ast.CompilationUnit;
import de.peeeq.wurstscript.ast.WurstModel;
import de.peeeq.wurstscript.gui.WurstGui;
/*
 * model manager which does nothing
 */
public class ModelManagerStub implements ModelManager {

	@Override
	public boolean removeCompilationUnit(IResource resource) {
		return false;
	}

	@Override
	public boolean needsFullBuild() {
		return false;
	}

	@Override
	public void clean() {
	}


	@Override
	public void updateModel(CompilationUnit cu, WurstGui gui) {
	}

	@Override
	public CompilationUnit getCompilationUnit(String fileName) {
		return null;
	}

	@Override
	public void registerChangeListener(String fileName, CompilationUnitChangeListener listener) {
	}

	@Override
	public CompilationUnit parse(WurstGui gui, String fileName, Reader source) {
		return null;
	}

	@Override
	public void fullBuildDone() {
	}

	@Override
	public void addDependency(File f) {
	}

	@Override
	public void clearDependencies() {
		
	}

	@Override
	public WurstModel getModel() {
		return null;
	}

	@Override
	public WurstNature getNature() {
		return null;
	}

	@Override
	public void removeCompilationUnitByName(String replDummyFilename) {
	}

	@Override
	public void resolveImports(WurstGui gui) {
	}

	@Override
	public void typeCheckModel(WurstGui gui, boolean addErrorMarkers) {
	}

	@Override
	public void typeCheckModelPartial(WurstGui gui, boolean addErrorMarkers,
			List<CompilationUnit> toCheck) {
	}

}
