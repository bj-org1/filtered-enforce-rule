package org.basil.maven.enforcer.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.enforcer.DependencyConvergence;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilderException;
import org.basil.maven.enforcer.rules.utils.FilteredDependencyVersionMap;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.i18n.I18N;

public class FilteredDependencyConvergence extends DependencyConvergence {

    private static Log log;

    private static I18N i18n;

    private boolean uniqueVersions;

    /**
     * Specify the dependencies to be excluded from convergence. This can be a
     * list of artifacts in the format
     * <code>groupId[:artifactId][:version]</code>. Any of the sections can be a
     * wildcard by using '*' (ie group:*) <br>
     * The rule will ignore convergence if any dependency matches any exclude,
     * unless it also matches an include rule.
     * 
     * @see {@link #setExcludes(List)}
     * @see {@link #getExcludes()}
     */
    private List<String> excludes = null;

    /**
     * Specify the dependencies to be included in convergence. This can be a
     * list of artifacts in the format
     * <code>groupId[:artifactId][:version]</code>. Any of the sections can be a
     * wildcard by using '*' (ie group:*) <br>
     * Includes override the exclude rules. It is meant to allow wide exclusion
     * rules with wildcards and still allow a smaller set of includes. <br>
     * For example, to ban all xerces except xerces-api -> exclude "xerces",
     * include "xerces:xerces-api"
     * 
     * @see {@link #setIncludes(List)}
     * @see {@link #getIncludes()}
     */
    private List<String> includes = null;

    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    public void setUniqueVersions(boolean uniqueVersions) {
        this.uniqueVersions = uniqueVersions;
    }

    /**
     * Uses the {@link EnforcerRuleHelper} to populate the values of the
     * {@link DependencyTreeBuilder#buildDependencyTree(MavenProject, ArtifactRepository, ArtifactFactory, ArtifactMetadataSource, ArtifactFilter, ArtifactCollector)}
     * factory method. <br/>
     * This method simply exists to hide all the ugly lookup that the
     * {@link EnforcerRuleHelper} has to do.
     * 
     * @param helper
     * @return a Dependency Node which is the root of the project's dependency
     *         tree
     * @throws EnforcerRuleException
     */
    private DependencyNode getNode(EnforcerRuleHelper helper) throws EnforcerRuleException {
        try {
            MavenProject project = (MavenProject) helper.evaluate("${project}");
            DependencyTreeBuilder dependencyTreeBuilder = (DependencyTreeBuilder) helper
                    .getComponent(DependencyTreeBuilder.class);
            ArtifactRepository repository = (ArtifactRepository) helper.evaluate("${localRepository}");
            ArtifactFactory factory = (ArtifactFactory) helper.getComponent(ArtifactFactory.class);
            ArtifactMetadataSource metadataSource = (ArtifactMetadataSource) helper
                    .getComponent(ArtifactMetadataSource.class);
            ArtifactCollector collector = (ArtifactCollector) helper.getComponent(ArtifactCollector.class);
            ArtifactFilter filter = null;
            DependencyNode node = dependencyTreeBuilder.buildDependencyTree(project, repository, factory,
                    metadataSource, filter, collector);
            return node;
        } catch (ExpressionEvaluationException e) {
            throw new EnforcerRuleException("Unable to lookup an expression " + e.getLocalizedMessage(), e);
        } catch (ComponentLookupException e) {
            throw new EnforcerRuleException("Unable to lookup a component " + e.getLocalizedMessage(), e);
        } catch (DependencyTreeBuilderException e) {
            throw new EnforcerRuleException("Could not build dependency tree " + e.getLocalizedMessage(), e);
        }
    }

    public void execute(EnforcerRuleHelper helper) throws EnforcerRuleException {
        if (log == null) {
            log = helper.getLog();
        }
        try {
            if (i18n == null) {
                i18n = (I18N) helper.getComponent(I18N.class);
            }
            DependencyNode node = getNode(helper);
            FilteredDependencyVersionMap visitor = new FilteredDependencyVersionMap(includes, excludes, log);
            visitor.setUniqueVersions(uniqueVersions);
            node.accept(visitor);
            List<CharSequence> errorMsgs = new ArrayList<CharSequence>();
            errorMsgs.addAll(getConvergenceErrorMsgs(visitor.getConflictedVersionNumbers()));
            for (CharSequence errorMsg : errorMsgs) {
                log.warn(errorMsg);
            }
            if (errorMsgs.size() > 0) {
                throw new EnforcerRuleException("Failed while enforcing releasability the error(s) are " + errorMsgs);
            }
        } catch (ComponentLookupException e) {
            throw new EnforcerRuleException("Unable to lookup a component " + e.getLocalizedMessage(), e);
        } catch (Exception e) {
            throw new EnforcerRuleException(e.getLocalizedMessage(), e);
        }
    }

    private String getFullArtifactName(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }

    private StringBuilder buildTreeString(DependencyNode node) {
        List<String> loc = new ArrayList<String>();
        DependencyNode currentNode = node;
        while (currentNode != null) {
            loc.add(getFullArtifactName(currentNode.getArtifact()));
            currentNode = currentNode.getParent();
        }
        Collections.reverse(loc);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < loc.size(); i++) {
            for (int j = 0; j < i; j++) {
                builder.append("  ");
            }
            builder.append("+-" + loc.get(i));
            builder.append("\n");
        }
        return builder;
    }

    private List<String> getConvergenceErrorMsgs(List<List<DependencyNode>> errors) {
        List<String> errorMsgs = new ArrayList<String>();
        for (List<DependencyNode> nodeList : errors) {
            errorMsgs.add(buildConvergenceErrorMsg(nodeList));
        }
        return errorMsgs;
    }

    private String buildConvergenceErrorMsg(List<DependencyNode> nodeList) {
        StringBuilder builder = new StringBuilder();
        builder.append("\nDependency convergence error for " + getFullArtifactName(nodeList.get(0).getArtifact())
                + " paths to dependency are:\n");
        if (nodeList.size() > 0) {
            builder.append(buildTreeString(nodeList.get(0)));
        }
        for (DependencyNode node : nodeList.subList(1, nodeList.size())) {
            builder.append("and\n");
            builder.append(buildTreeString(node));
        }
        return builder.toString();
    }
}