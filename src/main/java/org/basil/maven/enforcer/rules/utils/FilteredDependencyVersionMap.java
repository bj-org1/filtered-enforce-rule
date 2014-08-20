package org.basil.maven.enforcer.rules.utils;

import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.enforcer.utils.DependencyVersionMap;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.codehaus.plexus.util.StringUtils;

public class FilteredDependencyVersionMap extends DependencyVersionMap {

	private List<String> excludes = null;
	private List<String> includes = null;

	public FilteredDependencyVersionMap(List<String> includes,
			List<String> excludes, Log log) {
		super(log);
		this.excludes = excludes;
		this.includes = includes;
	}

	public boolean visit(DependencyNode node) {
		if (isConvergenceRequired(node)) {
			return super.visit(node);
		}
		return true;
	}

	protected boolean isConvergenceRequired(DependencyNode node) {
		boolean convergenceRequired = !matchArtifact(node, excludes);
		
		if(excludes != null && excludes.size() > 0) {
			if(includes != null && includes.size() > 0) {
				convergenceRequired = matchArtifact(node, includes);
			}
		
		}else if(includes != null && includes.size() > 0) {
			convergenceRequired = matchArtifact(node, includes);
		}
		return convergenceRequired;
	}

	private boolean matchArtifact(DependencyNode node, List<String> thePatterns) {

		if (thePatterns != null && thePatterns.size() > 0) {

			for (String pattern : thePatterns) {

				String[] subStrings = pattern.split(":");
				subStrings = StringUtils.stripAll(subStrings);

				if (compareDependency(subStrings, node.getArtifact())) {
					return true;
				}
			}
		}
		return false;
	}

	protected boolean compareDependency(String[] pattern, Artifact artifact) {

		boolean result = false;
		if (pattern.length > 0) {
			result = pattern[0].equals("*")
					|| artifact.getGroupId().equals(pattern[0]);
		}

		if (result && pattern.length > 1) {
			result = pattern[1].equals("*")
					|| artifact.getArtifactId().equals(pattern[1]);
		}
		return result;
	}
}