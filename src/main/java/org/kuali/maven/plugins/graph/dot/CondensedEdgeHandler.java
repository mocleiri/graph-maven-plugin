/**
 * Copyright 2010-2011 The Kuali Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.opensource.org/licenses/ecl2.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kuali.maven.plugins.graph.dot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.kuali.maven.plugins.graph.pojo.Edge;
import org.kuali.maven.plugins.graph.pojo.GraphNode;
import org.kuali.maven.plugins.graph.pojo.MavenContext;
import org.kuali.maven.plugins.graph.pojo.Scope;
import org.kuali.maven.plugins.graph.pojo.State;
import org.kuali.maven.plugins.graph.tree.Node;
import org.kuali.maven.plugins.graph.tree.TreeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CondensedEdgeHandler extends FlatEdgeHandler {
    private static final Logger logger = LoggerFactory.getLogger(CondensedEdgeHandler.class);
    public static final String REPLACEMENT_LABEL = "replacement";
    TreeHelper helper = new TreeHelper();

    @Override
    public List<Edge> getEdges(Node<MavenContext> node) {
        List<Edge> edges = new ArrayList<Edge>();

        // Draw a line from parent to child
        edges.addAll(super.getEdges(node));

        // Process the node based on its state
        handleNode(node, edges);

        // Return the edges we generated
        return edges;
    }

    protected void handleNode(Node<MavenContext> node, List<Edge> edges) {
        logger.debug("handling node {}", node.getObject().getId());
        switch (node.getObject().getState()) {
        case INCLUDED:
        case CYCLIC:
        case UNKNOWN:
            // Nothing further to do. Styling draws attention to CYCLIC and UNKNOWN nodes
            return;
        case DUPLICATE:
            // Draw a line from our parent to the included node that contains the same artifact
            handleDuplicate(node, edges);
            return;
        case CONFLICT:
            // Draw a line from us to the replacement artifact
            handleConflict(node, edges);
            return;
        default:
            throw new IllegalStateException("Unknown state " + node.getObject().getState());
        }
    }

    protected void handleConflict(Node<MavenContext> node, List<Edge> edges) {
        MavenContext context = node.getObject();

        // This is the id of the artifact Maven is actually going to use
        // The validator's and sanitizer's guarantee that the replacement artifact will always be
        // found among the included nodes.
        String replacementArtifactId = TreeHelper.getArtifactId(context.getReplacement());

        // Get the node containing the replacement artifact
        Node<MavenContext> replacement = findIncludedNode(node.getRoot(), replacementArtifactId);

        // Extract the correct parent/child graph nodes
        GraphNode parent = context.getGraphNode();
        GraphNode child = replacement.getObject().getGraphNode();

        // Draw an edge saying "replacement" from ourself to the artifact that replaced us
        boolean optional = context.getArtifact().isOptional();
        Edge edge = getEdge(parent, child, optional, Scope.DEFAULT_SCOPE, State.CONFLICT);
        edge.setLabel(REPLACEMENT_LABEL);

        // Add the edge to the overall list
        edges.add(edge);

        // Add the edge to our list
        context.setEdges(new ArrayList<Edge>(Collections.singletonList(edge)));
    }

    protected void handleDuplicate(Node<MavenContext> node, List<Edge> edges) {
        MavenContext context = node.getObject();

        // Find the node that replaces us
        Node<MavenContext> replacement = findIncludedNode(node.getRoot(), context.getArtifactIdentifier());
        // This is our parent in the tree
        GraphNode parent = node.getParent().getObject().getGraphNode();
        // This is the node that is being used instead of us
        GraphNode child = replacement.getObject().getGraphNode();
        // Use our optional/scope settings
        boolean optional = context.getArtifact().isOptional();
        Scope scope = Scope.getScope(context.getArtifact().getScope());

        // Draw an edge from our parent to the node that replaced us
        Edge edge = getEdge(parent, child, optional, scope, State.INCLUDED);

        // Add this new edge to the list
        edges.add(edge);
    }

    protected Node<MavenContext> findIncludedNode(Node<MavenContext> root, String artifactId) {
        List<Node<MavenContext>> nodes = root.getBreadthFirstList();
        for (Node<MavenContext> node : nodes) {
            MavenContext context = node.getObject();
            State state = context.getState();
            String artifactIdentifier = context.getArtifactIdentifier();
            boolean correctState = state == State.INCLUDED;
            boolean correctArtifact = artifactId.equals(artifactIdentifier);
            if (correctState && correctArtifact) {
                return node;
            }
        }
        throw new IllegalStateException("Inconsistent tree.  Can't locate " + artifactId);
    }
}
