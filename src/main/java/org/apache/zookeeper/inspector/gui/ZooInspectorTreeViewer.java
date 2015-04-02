/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zookeeper.inspector.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper.States;
import org.apache.zookeeper.inspector.ZooInspectorUtil;
import org.apache.zookeeper.inspector.manager.NodeListener;
import org.apache.zookeeper.inspector.manager.ZooInspectorManager;
import org.apache.zookeeper.inspector.toaster.Toaster;

// import com.nitido.utils.toaster.Toaster;

/**
 * A {@link JPanel} for showing the tree view of all the nodes in the zookeeper
 * instance
 */
public class ZooInspectorTreeViewer extends JPanel implements NodeListener,
  TreeWillExpandListener {
    private final ZooInspectorManager zooInspectorManager;
    private final JTree tree;
    private final Toaster toasterManager;

    /**
     * HACK:
     * treeExpand event can be triggered other than mouse click, e.g. delete/add node
     * in such cases we can skip cache refresh on expanded paths
     */
    private final Set<String> skipRefreshPaths = Collections.synchronizedSet(new HashSet<String>());
    private final ZooInspectorPanel zooInspectorPanel;

    /**
     * @param zooInspectorManager
     *            - the {@link ZooInspectorManager} for the application
     * @param listener
     *            - the {@link TreeSelectionListener} to listen for changes in
     *            the selected node on the node tree
     */
    public ZooInspectorTreeViewer(
            final ZooInspectorPanel zooInspectorPanel,
            final ZooInspectorManager zooInspectorManager,
            TreeSelectionListener listener) {
        this.zooInspectorPanel = zooInspectorPanel;
        this.zooInspectorManager = zooInspectorManager;
        this.setLayout(new BorderLayout());
        final JPopupMenu popupMenu = new JPopupMenu();
        final JMenuItem addNotify = new JMenuItem("Add Change Notification");
        this.toasterManager = new Toaster();
        this.toasterManager.setBorderColor(Color.BLACK);
        this.toasterManager.setMessageColor(Color.BLACK);
        this.toasterManager.setToasterColor(Color.WHITE);
        addNotify.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              System.out.println("addNotify action_performed()");
                List<String> selectedNodes = getSelectedNodes();
                zooInspectorManager.addWatchers(selectedNodes,
                        ZooInspectorTreeViewer.this);
            }
        });
        final JMenuItem removeNotify = new JMenuItem(
                "Remove Change Notification");
        removeNotify.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              System.out.println("removeNotify actionPerformed()");
                List<String> selectedNodes = getSelectedNodes();
                zooInspectorManager.removeWatchers(selectedNodes);
            }
        });

        tree = new JTree(new DefaultMutableTreeNode());
        System.out.println("init jtree: " + tree);

        tree.setCellRenderer(new ZooInspectorTreeCellRenderer());
        tree.setEditable(false);
        tree.getSelectionModel().addTreeSelectionListener(listener);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.isPopupTrigger() || e.getButton() == MouseEvent.BUTTON3) {
                    // TODO only show add if a selected node isn't being
                    // watched, and only show remove if a selected node is being
                    // watched
                    popupMenu.removeAll();
                    popupMenu.add(addNotify);
                    popupMenu.add(removeNotify);
                    popupMenu.show(ZooInspectorTreeViewer.this, e.getX(), e
                            .getY());
                }
            }
        });


        tree.addTreeWillExpandListener(this);

        this.add(tree, BorderLayout.CENTER);
    }

    @Override
    public void treeWillExpand(TreeExpansionEvent event)
    {
      String znodePath = ZooInspectorUtil.treePathToZnodePath(event.getPath());
      System.out.println("treeWillExpand invoked. willExpandPath: " + znodePath);
//      System.out.println("Skip refresh " + skipRefreshPaths.size() + " paths");

      if (skipRefreshPaths.contains(znodePath)) {
//        System.out.println("Skip refresh path: " + znodePath);
      } else {
        try
        {
          zooInspectorManager.getCache().refresh(Arrays.asList(znodePath), 1);
        }
        catch (KeeperException e)
        {
          zooInspectorPanel.checkZookeeperStates(e.getMessage());
        }
      }
    }

    @Override
    public void treeWillCollapse(TreeExpansionEvent event)
    {
      // TODO Auto-generated method stub
      // System.out.println("collapsePath: " + event.getPath());
    }

    private List<String> getExpandedNodes()
    {
      List<String> expandedPaths = new ArrayList<String>();
      int rowCount = tree.getRowCount();
      for (int i = 0; i < rowCount; i++) {
          TreePath path = tree.getPathForRow(i);
          if (tree.isExpanded(path)) {
            expandedPaths.add(ZooInspectorUtil.treePathToZnodePath(path));
          }
      }
      return expandedPaths;
    }

    private void doRefresh(final TreePath[] selectedNodes) {
      System.out.println("\tdoRefresh#selectedTreePaths: " + Arrays.toString(selectedNodes));

      final Set<TreePath> expandedNodes = new LinkedHashSet<TreePath>();
      int rowCount = tree.getRowCount();
      for (int i = 0; i < rowCount; i++) {
          TreePath path = tree.getPathForRow(i);
          if (tree.isExpanded(path)) {
              expandedNodes.add(path);
          }
      }

//      final TreePath[] selectedNodes = tree.getSelectionPaths();
      System.out.println("\texpandedNodes: " + expandedNodes);


      SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {

          @Override
          protected Boolean doInBackground() throws Exception {
              tree.setModel(new DefaultTreeModel(new ZooInspectorTreeNode(
                      "/", null)));
              return true;
          }

          @Override
          protected void done() {
              for (TreePath path : expandedNodes) {
                  tree.expandPath(path);
              }
              tree.getSelectionModel().setSelectionPaths(selectedNodes);

              skipRefreshPaths.clear();
          }
      };
      worker.execute();
    }

    /**
     * Refresh the tree view
     */
    public void refreshView() {
      // reconnect if necessary
//      if (zooInspectorManager.getZookeeperStates() == States.CLOSED) {
//        System.out.println("ZooInspectorTreeViewer#refresh try reconnecting...");
//        zooInspectorManager.connect(zooInspectorManager.getLastConnectionProps());
//      }

//        final Set<TreePath> expandedNodes = new LinkedHashSet<TreePath>();
        List<String> visiblePaths = new ArrayList<String>();

//        List<String> selectedPaths = new ArrayList<String>();
        int rowCount = tree.getRowCount();
        System.out.println("[START] ZooInspectorTreeViewer#refreshView invoked.");
        System.out.println("\trowCount: " + rowCount);

        for (int i = 0; i < rowCount; i++) {
            TreePath path = tree.getPathForRow(i);
            visiblePaths.add(ZooInspectorUtil.treePathToZnodePath(path));
//            if (tree.isExpanded(path)) {
//                expandedNodes.add(path);
//            }
        }

//        final TreePath[] selectedNodes = tree.getSelectionPaths();
//        System.out.println("\tvisiblePaths: " + visiblePaths);
//        System.out.println("selectedNodes: " + selectedPaths);
        try
        {
          zooInspectorManager.getCache().refresh(visiblePaths, 0);
        }
        catch (KeeperException e)
        {
          zooInspectorPanel.checkZookeeperStates(e.getMessage());

          // shall skip refresh
          return;
        }

        skipRefreshPaths.addAll(getExpandedNodes());
        doRefresh(tree.getSelectionPaths());
        System.out.println("[END] ZooInspectorTreeViewer#refreshView invoked.");
    }

    /**
     * Refresh the tree view after delete nodes
     * @param deletedNodes
     */
    public void refreshViewAfterDelete(List<String> deletedNodes) {
        System.out.println("deletedNodes: " + deletedNodes);
        Set<String> expandedNodes = new HashSet<String>(getExpandedNodes());

        for (String path : deletedNodes) {
          expandedNodes.remove(path);
          zooInspectorManager.getCache().removePrefix(path);
          String parent = new File(path).getParent();
          System.out.println("parent: " + parent);
          try
          {
            zooInspectorManager.getCache().refresh(Arrays.asList(parent), 0);
          }
          catch (KeeperException e)
          {
            zooInspectorPanel.checkZookeeperStates(e.getMessage());

            // shall skip refresh
            return;
          }
        }

        skipRefreshPaths.addAll(expandedNodes);

        // modify selected nodes (that's deleted) to their parents
        TreePath[] selectedNodes = tree.getSelectionPaths();
        for (int i = 0; i < selectedNodes.length; i++) {
          TreePath parent = selectedNodes[i].getParentPath();
          selectedNodes[i] = parent;
        }
        doRefresh(selectedNodes);
    }

    /**
     * Refresh the tree view after add a node under parent
     * @param parent
     * @param addNodeName
     */
    public void refreshViewAfterAdd(String parent, String addNodeName) {
      System.out.println("addNode. parent: " + parent + ", addNodeName: " + addNodeName);
      try {
        zooInspectorManager.getCache().refresh(Arrays.asList(parent.isEmpty()? "/" : parent), 0);
        zooInspectorManager.getCache().refresh(Arrays.asList(parent + "/" + addNodeName), 0);
      } catch (KeeperException e) {
        zooInspectorPanel.checkZookeeperStates(e.getMessage());

        // shall skip refresh
        return;
      }
      skipRefreshPaths.addAll(getExpandedNodes());
      doRefresh(tree.getSelectionPaths());
    }


    /**
     * clear the tree view of all nodes
     */
    public void clearView() {
        tree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
    }

    /**
     * @author Colin
     *
     */
    private static class ZooInspectorTreeCellRenderer extends
            DefaultTreeCellRenderer {
        public ZooInspectorTreeCellRenderer() {
//          System.out.println("TreeRender() called");
            setLeafIcon(ZooInspectorIconResources.getTreeLeafIcon());
            setOpenIcon(ZooInspectorIconResources.getTreeOpenIcon());
            setClosedIcon(ZooInspectorIconResources.getTreeClosedIcon());
        }
    }

    /**
     * @author Colin
     *
     */
    private class ZooInspectorTreeNode implements TreeNode {
        private final String nodePath;
        private final String nodeName;
        private final ZooInspectorTreeNode parent;

        public ZooInspectorTreeNode(String nodePath, ZooInspectorTreeNode parent) {
            this.parent = parent;
            this.nodePath = nodePath;
            int index = nodePath.lastIndexOf("/");
            if (index == -1) {
                throw new IllegalArgumentException("Invalid node path"
                        + nodePath);
            }
            this.nodeName = nodePath.substring(index + 1);

            // System.out.println("init treeNode: " + nodePath + ", parent: " + parent);
        }

        /*
         * (non-Javadoc)
         *
         * @see javax.swing.tree.TreeNode#children()
         */
        @Override
        public Enumeration<TreeNode> children() {
            List<String> children = zooInspectorManager
                    .getChildren(this.nodePath);
//            Collections.sort(children);
            // System.out.println("sorted childs: " + children);
            List<TreeNode> returnChildren = new ArrayList<TreeNode>();
            for (String child : children) {
                returnChildren.add(new ZooInspectorTreeNode((this.nodePath
                        .equals("/") ? "" : this.nodePath)
                        + "/" + child, this));
            }
            return Collections.enumeration(returnChildren);
        }

        /*
         * (non-Javadoc)
         *
         * @see javax.swing.tree.TreeNode#getAllowsChildren()
         */
        @Override
        public boolean getAllowsChildren() {
            return zooInspectorManager.isAllowsChildren(this.nodePath);
        }

        /*
         * (non-Javadoc)
         *
         * @see javax.swing.tree.TreeNode#getChildAt(int)
         */
        @Override
        public TreeNode getChildAt(int childIndex) {
            String child = zooInspectorManager.getNodeChild(this.nodePath,
                    childIndex);
            // System.out.println("getChildAt: " + childIndex + ", child: " + child);
            if (child != null) {
                return new ZooInspectorTreeNode((this.nodePath.equals("/") ? ""
                        : this.nodePath)
                        + "/" + child, this);
            }
            return null;
        }

        /*
         * (non-Javadoc)
         *
         * @see javax.swing.tree.TreeNode#getChildCount()
         */
        @Override
        public int getChildCount() {
            return zooInspectorManager.getNumChildren(this.nodePath);
        }

        /*
         * (non-Javadoc)
         *
         * @see javax.swing.tree.TreeNode#getIndex(javax.swing.tree.TreeNode)
         */
        @Override
        public int getIndex(TreeNode node) {
            int idx = zooInspectorManager.getNodeIndex(this.nodePath);
            System.out.println("getIndex: " + node + ", idx: " + idx);
            return idx;
        }

        /*
         * (non-Javadoc)
         *
         * @see javax.swing.tree.TreeNode#getParent()
         */
        @Override
        public TreeNode getParent() {
            return this.parent;
        }

        /*
         * (non-Javadoc)
         *
         * @see javax.swing.tree.TreeNode#isLeaf()
         */
        @Override
        public boolean isLeaf() {
            return !zooInspectorManager.hasChildren(this.nodePath);
        }

        @Override
        public String toString() {
            return this.nodeName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result
                    + ((nodePath == null) ? 0 : nodePath.hashCode());
            result = prime * result
                    + ((parent == null) ? 0 : parent.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ZooInspectorTreeNode other = (ZooInspectorTreeNode) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (nodePath == null) {
                if (other.nodePath != null)
                    return false;
            } else if (!nodePath.equals(other.nodePath))
                return false;
            if (parent == null) {
                if (other.parent != null)
                    return false;
            } else if (!parent.equals(other.parent))
                return false;
            return true;
        }

        private ZooInspectorTreeViewer getOuterType() {
            return ZooInspectorTreeViewer.this;
        }

    }

    /**
     * @return {@link List} of the currently selected nodes
     */
    public List<String> getSelectedNodes() {
        TreePath[] paths = tree.getSelectionPaths();
        List<String> selectedNodes = new ArrayList<String>();
        if (paths != null) {
            for (TreePath path : paths) {
                StringBuilder sb = new StringBuilder();
                Object[] pathArray = path.getPath();
                for (Object o : pathArray) {
                    String nodeName = o.toString();
                    if (nodeName.length() > 0) {
                        sb.append("/");
                        sb.append(o.toString());
                    }
                }
                selectedNodes.add(sb.toString());
            }
        }
        return selectedNodes;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.zookeeper.inspector.manager.NodeListener#processEvent(java
     * .lang.String, java.lang.String, java.util.Map)
     */
    @Override
    public void processEvent(String nodePath, String eventType,
            Map<String, String> eventInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("Node: ");
        sb.append(nodePath);
        sb.append("\nEvent: ");
        sb.append(eventType);
        if (eventInfo != null) {
            for (Map.Entry<String, String> entry : eventInfo.entrySet()) {
                sb.append("\n");
                sb.append(entry.getKey());
                sb.append(": ");
                sb.append(entry.getValue());
            }
        }
        this.toasterManager.showToaster(ZooInspectorIconResources
                .getInformationIcon(), sb.toString());
    }
}
