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
package org.apache.zookeeper.inspector.manager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooDefs.Perms;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooKeeper.States;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.inspector.encryption.BasicDataEncryptionManager;
import org.apache.zookeeper.inspector.encryption.DataEncryptionManager;
import org.apache.zookeeper.inspector.logger.LoggerFactory;
import org.apache.zookeeper.inspector.manager.ZooInspectorManagerCache.Item;
import org.apache.zookeeper.retry.ZooKeeperRetry;

/**
 * A default implementation of {@link ZooInspectorManager} for connecting to zookeeper
 * instances
 */
public class ZooInspectorManagerImpl implements ZooInspectorManager
{
  private static final String A_VERSION = "ACL Version";
  private static final String C_TIME = "Creation Time";
  private static final String C_VERSION = "Children Version";
  private static final String CZXID = "Creation ID";
  private static final String DATA_LENGTH = "Data Length";
  private static final String EPHEMERAL_OWNER = "Ephemeral Owner";
  private static final String M_TIME = "Last Modified Time";
  private static final String MZXID = "Modified ID";
  private static final String NUM_CHILDREN = "Number of Children";
  private static final String PZXID = "Node ID";
  private static final String VERSION = "Data Version";
  private static final String ACL_PERMS = "Permissions";
  private static final String ACL_SCHEME = "Scheme";
  private static final String ACL_ID = "Id";
  private static final String SESSION_STATE = "Session State";
  private static final String SESSION_ID = "Session ID";
  /**
   * The key used for the connect string in the connection properties file
   */
  public static final String CONNECT_STRING = "hosts";
  /**
   * The key used for the session timeout in the connection properties file
   */
  public static final String SESSION_TIMEOUT = "timeout";
  /**
   * The key used for the data encryption manager in the connection properties file
   */
  public static final String DATA_ENCRYPTION_MANAGER = "encryptionManager";

  private static final String homeDir = System.getProperty("user.home");
  private static final File defaultNodeViewersFile =
      new File(homeDir + "/.zooinspector/defaultNodeVeiwers.cfg");
  private static final File defaultConnectionFile =
      new File(homeDir + "/.zooinspector/defaultConnectionSettings.cfg");

//  private static final File defaultNodeViewersFile =
//      new File("./config/defaultNodeVeiwers.cfg");
//  private static final File defaultConnectionFile =
//      new File("./config/defaultConnectionSettings.cfg");

  private DataEncryptionManager encryptionManager;
  private String connectString;
  private int sessionTimeout;
  private ZooKeeper zooKeeper;
  // private
  final Map<String, NodeWatcher> watchers = new HashMap<String, NodeWatcher>();
  protected boolean connected = true;
  private Properties lastConnectionProps;
  private String defaultEncryptionManager;
  private String defaultTimeout;
  private String defaultHosts;
  private List<String> defaultHostsList;
  private final int defaultHostsListSize = 10;

  // zk cache that updates when:
  // - refresh button is clicked
  // - treeExpansion event fired
  // - selectPth event fired
  ZooInspectorManagerCache cache;

  /**
   * @throws IOException
   *           - thrown if the default connection settings cannot be loaded
   *
   */
  public ZooInspectorManagerImpl() throws IOException
  {
    loadDefaultConnectionFile();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.zookeeper.inspector.manager.ZooInspectorManager#connect(java
   * .util.Properties)
   */
  @Override
  public boolean connect(Properties connectionProps)
  {
    connected = false;
    try
    {
      if (this.zooKeeper == null)
      {
        String connectString = connectionProps.getProperty(CONNECT_STRING);
        String sessionTimeout = connectionProps.getProperty(SESSION_TIMEOUT);
        String encryptionManager = connectionProps.getProperty(DATA_ENCRYPTION_MANAGER);
        if (connectString == null || sessionTimeout == null)
        {
          throw new IllegalArgumentException("Both connect string and session timeout are required.");
        }
        if (encryptionManager == null)
        {
          this.encryptionManager = new BasicDataEncryptionManager();
        }
        else
        {
          Class<?> clazz = Class.forName(encryptionManager);

          if (Arrays.asList(clazz.getInterfaces()).contains(DataEncryptionManager.class))
          {
            this.encryptionManager =
                (DataEncryptionManager) Class.forName(encryptionManager).newInstance();
          }
          else
          {
            throw new IllegalArgumentException("Data encryption manager must implement DataEncryptionManager interface");
          }
        }
        this.connectString = connectString;
        this.sessionTimeout = Integer.valueOf(sessionTimeout);

//        long start = System.currentTimeMillis();
//        System.out.println("[START] connecting...");
        this.zooKeeper =
            new ZooKeeperRetry(connectString,
                               Integer.valueOf(sessionTimeout),
                               new Watcher()
                               {

                                 @Override
                                 public void process(WatchedEvent event)
                                 {
                                   if (event.getState() == KeeperState.Expired)
                                   {
                                     connected = false;
                                   }
                                 }
                               });
        ((ZooKeeperRetry) this.zooKeeper).setRetryLimit(10);
//        System.out.println("[START] connected took: " + (System.currentTimeMillis() - start));

        connected = ((ZooKeeperRetry) this.zooKeeper).testConnection();
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    // connected = false;

    // do initial cache refresh on all childs of "/"
    if (connected == true)
    {
      cache = new ZooInspectorManagerCache(this);
      try
      {
        cache.refresh(Arrays.asList("/"), 1);
      }
      catch (KeeperException e)
      {
        // TODO Auto-generated catch block
        disconnect();
        e.printStackTrace();
      }
    }
    else
    {
      disconnect();
    }

    return connected;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.zookeeper.inspector.manager.ZooInspectorManager#disconnect()
   */
  @Override
  public boolean disconnect()
  {
    try
    {
      if (this.zooKeeper != null)
      {
        this.zooKeeper.close();
        this.zooKeeper = null;
        connected = false;
        removeWatchers(this.watchers.keySet());
        return true;
      }
    }
    catch (Exception e)
    {
      LoggerFactory.getLogger()
                   .error("Error occurred while disconnecting from ZooKeeper server", e);
    }
    return false;
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorReadOnlyManager#
   * getChildren(java.lang.String)
   */
  @Override
  public List<String> getChildren(String nodePath)
  {
    // System.out.println("ZooInspectorManagerImpl.getChildren(), nodePath: " + nodePath);
    if (connected)
    {
      // try {
      //
      // return zooKeeper.getChildren(nodePath, false);
      // } catch (Exception e) {
      // LoggerFactory.getLogger().error(
      // "Error occurred retrieving children of node: "
      // + nodePath, e);
      // }
      return cache.getChildren(nodePath);
    }
    return null;

  }

  Item getChildrenAndStat(String nodePath) throws KeeperException
  {
    // System.out.println("getChildrenAndStat(), path: " + nodePath);
    if (zooKeeper.getState() != States.CONNECTED)
    {
      throw KeeperException.create(KeeperException.Code.CONNECTIONLOSS, nodePath);
    }

    try
    {
      Stat stat = new Stat();
      List<String> childs = zooKeeper.getChildren(nodePath, false, stat);
      return new Item(childs, stat);
    }
    catch (NoNodeException e)
    {
      // OK to return null
    }
    catch (KeeperException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      LoggerFactory.getLogger().error("Error occurred retrieving children of node: "
                                          + nodePath,
                                      e);
    }
    return null;
  }

  // experiments show that multi-thread sync-read is faster than single-thread async-read
  // let's try it
  ExecutorService service = Executors.newFixedThreadPool(40);

  Map<String, Item> getChildren(List<String> paths)
  {
    int n = paths.size();
    if (n > 0)
    {
      // final List<Item> ret = new ArrayList<Item>();
      final Map<String, Item> ret = new ConcurrentHashMap<String, ZooInspectorManagerCache.Item>();
      final CountDownLatch cntDown = new CountDownLatch(n);

      for (final String path : paths)
      {
        service.submit(new Callable<String>()
        {

          @Override
          public String call() throws Exception
          {
            Stat stat = null;
            List<String> childs = null;
            try
            {
              stat = new Stat();
              childs = zooKeeper.getChildren(path, false, stat);
            } catch (Exception e) {
              // System.out.println("exception: " + e);
            }
            finally
            {
              // ret.add(new Item(childs, stat));
              ret.put(path, new Item(childs, stat));
              cntDown.countDown();
            }
            return null;
          }
        });
      }

      try
      {
        cntDown.await();
      }
      catch (InterruptedException e)
      {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return ret;
    }

    return Collections.emptyMap();
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.zookeeper.inspector.manager.ZooInspectorReadOnlyManager#getData
   * (java.lang.String)
   */
  @Override
  public String getData(String nodePath)
  {
    if (connected)
    {
      try
      {
        if (nodePath.length() == 0)
        {
          nodePath = "/";
        }
        Stat s = zooKeeper.exists(nodePath, false);
        if (s != null)
        {
          return this.encryptionManager.decryptData(zooKeeper.getData(nodePath, false, s));
        }
      }
      catch (Exception e)
      {
        LoggerFactory.getLogger().error("Error occurred getting data for node: "
                                            + nodePath,
                                        e);
      }
    }
    return null;
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorReadOnlyManager#
   * getNodeChild(java.lang.String, int)
   */
  @Override
  public String getNodeChild(String nodePath, int childIndex)
  {
    long start = System.currentTimeMillis();
    if (connected)
    {
      // try {
      // Stat s = zooKeeper.exists(nodePath, false);
      // if (s != null) {
      //
      // String string = this.zooKeeper.getChildren(nodePath, false).get(
      // childIndex);
      // long end = System.currentTimeMillis();
      // System.out.println("getNodeChild(), path: " + nodePath + ", childIndex: " +
      // childIndex + ", " + (end - start));
      // return string;
      // }
      // } catch (Exception e) {
      // LoggerFactory.getLogger().error(
      // "Error occurred retrieving child " + childIndex
      // + " of node: " + nodePath, e);
      // }
      String child = cache.getNodeChild(nodePath, childIndex);
      long end = System.currentTimeMillis();
      // System.out.println("getNodeChild(), path: " + nodePath + ", childIndex: " +
      // childIndex + ", " + (end - start));
      return child;
    }
    return null;
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorReadOnlyManager#
   * getNodeIndex(java.lang.String)
   */
  @Override
  public int getNodeIndex(String nodePath)
  {
    if (connected)
    {
      int index = nodePath.lastIndexOf("/");
      if (index == -1
          || (!nodePath.equals("/") && nodePath.charAt(nodePath.length() - 1) == '/'))
      {
        throw new IllegalArgumentException("Invalid node path: " + nodePath);
      }
      String parentPath = nodePath.substring(0, index);
      String child = nodePath.substring(index + 1);
      if (parentPath != null && parentPath.length() > 0)
      {
        List<String> children = this.getChildren(parentPath);
        if (children != null)
        {
          return children.indexOf(child);
        }
      }
    }
    return -1;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.zookeeper.inspector.manager.ZooInspectorReadOnlyManager#getACLs
   * (java.lang.String)
   */
  @Override
  public List<Map<String, String>> getACLs(String nodePath)
  {
    List<Map<String, String>> returnACLs = new ArrayList<Map<String, String>>();
    if (connected)
    {
      try
      {
        if (nodePath.length() == 0)
        {
          nodePath = "/";
        }
        Stat s = zooKeeper.exists(nodePath, false);
        if (s != null)
        {
          List<ACL> acls = zooKeeper.getACL(nodePath, s);
          for (ACL acl : acls)
          {
            Map<String, String> aclMap = new LinkedHashMap<String, String>();
            aclMap.put(ACL_SCHEME, acl.getId().getScheme());
            aclMap.put(ACL_ID, acl.getId().getId());
            StringBuilder sb = new StringBuilder();
            int perms = acl.getPerms();
            boolean addedPerm = false;
            if ((perms & Perms.READ) == Perms.READ)
            {
              sb.append("Read");
              addedPerm = true;
            }
            if (addedPerm)
            {
              sb.append(", ");
            }
            if ((perms & Perms.WRITE) == Perms.WRITE)
            {
              sb.append("Write");
              addedPerm = true;
            }
            if (addedPerm)
            {
              sb.append(", ");
            }
            if ((perms & Perms.CREATE) == Perms.CREATE)
            {
              sb.append("Create");
              addedPerm = true;
            }
            if (addedPerm)
            {
              sb.append(", ");
            }
            if ((perms & Perms.DELETE) == Perms.DELETE)
            {
              sb.append("Delete");
              addedPerm = true;
            }
            if (addedPerm)
            {
              sb.append(", ");
            }
            if ((perms & Perms.ADMIN) == Perms.ADMIN)
            {
              sb.append("Admin");
              addedPerm = true;
            }
            aclMap.put(ACL_PERMS, sb.toString());
            returnACLs.add(aclMap);
          }
        }
      }
      catch (InterruptedException e)
      {
        LoggerFactory.getLogger().error("Error occurred retrieving ACLs of node: "
                                            + nodePath,
                                        e);
      }
      catch (KeeperException e)
      {
        LoggerFactory.getLogger().error("Error occurred retrieving ACLs of node: "
                                            + nodePath,
                                        e);
      }
    }
    return returnACLs;
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorReadOnlyManager#
   * getNodeMeta(java.lang.String)
   */
  @Override
  public Map<String, String> getNodeMeta(String nodePath)
  {
    Map<String, String> nodeMeta = new LinkedHashMap<String, String>();
    if (connected)
    {
      try
      {
        if (nodePath.length() == 0)
        {
          nodePath = "/";
        }
        Stat s = zooKeeper.exists(nodePath, false);
        if (s != null)
        {
          SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS z");
          nodeMeta.put(A_VERSION, String.valueOf(s.getAversion()));
          // nodeMeta.put(C_TIME, String.valueOf(s.getCtime()));
          nodeMeta.put(C_TIME, format.format(new Date(s.getCtime())));
          nodeMeta.put(C_VERSION, String.valueOf(s.getCversion()));
          nodeMeta.put(CZXID, "0x" + Long.toHexString(s.getCzxid()));
          nodeMeta.put(DATA_LENGTH, String.valueOf(s.getDataLength()));
          nodeMeta.put(EPHEMERAL_OWNER, "0x" + Long.toHexString(s.getEphemeralOwner()));
          // nodeMeta.put(M_TIME, String.valueOf(s.getMtime()));
          nodeMeta.put(M_TIME, format.format(new Date(s.getMtime())));
          nodeMeta.put(MZXID, "0x" + Long.toHexString(s.getMzxid()));
          nodeMeta.put(NUM_CHILDREN, String.valueOf(s.getNumChildren()));
          nodeMeta.put(PZXID, "0x" + Long.toHexString(s.getPzxid()));
          nodeMeta.put(VERSION, String.valueOf(s.getVersion()));
        }
      }
      catch (Exception e)
      {
        LoggerFactory.getLogger().error("Error occurred retrieving meta data for node: "
                                            + nodePath,
                                        e);
      }
    }
    return nodeMeta;
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorReadOnlyManager#
   * getNumChildren(java.lang.String)
   */
  @Override
  public int getNumChildren(String nodePath)
  {
    long start = System.currentTimeMillis();
    if (connected)
    {
      // try {
      // Stat s = zooKeeper.exists(nodePath, false);
      // if (s != null) {
      // int numChildren = s.getNumChildren();
      // long end = System.currentTimeMillis();
      // System.out.println("getNumbChilds(), nodePath: " + nodePath + ", " +
      // (end-start));
      // return numChildren;
      // }
      // } catch (Exception e) {
      // LoggerFactory.getLogger().error(
      // "Error occurred getting the number of children of node: "
      // + nodePath, e);
      // }
      int numChildren = cache.getNumChildren(nodePath);
      long end = System.currentTimeMillis();
      // System.out.println("getNumbChilds(), nodePath: " + nodePath + ", " +
      // (end-start));
      return numChildren;
    }
    return -1;
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorReadOnlyManager#
   * hasChildren(java.lang.String)
   */
  @Override
  public boolean hasChildren(String nodePath)
  {
    return getNumChildren(nodePath) > 0;
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorReadOnlyManager#
   * isAllowsChildren(java.lang.String)
   */
  @Override
  public boolean isAllowsChildren(String nodePath)
  {
    if (connected)
    {
      try
      {
        Stat s = zooKeeper.exists(nodePath, false);
        if (s != null)
        {
          return s.getEphemeralOwner() == 0;
        }
      }
      catch (Exception e)
      {
        LoggerFactory.getLogger()
                     .error("Error occurred determining whether node is allowed children: "
                                + nodePath,
                            e);
      }
    }
    return false;
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorReadOnlyManager#
   * getSessionMeta()
   */
  @Override
  public Map<String, String> getSessionMeta()
  {
    Map<String, String> sessionMeta = new LinkedHashMap<String, String>();
    try
    {
      if (zooKeeper != null)
      {

        sessionMeta.put(SESSION_ID, String.valueOf(zooKeeper.getSessionId()));
        sessionMeta.put(SESSION_STATE, String.valueOf(zooKeeper.getState().toString()));
        sessionMeta.put(CONNECT_STRING, this.connectString);
        sessionMeta.put(SESSION_TIMEOUT, String.valueOf(this.sessionTimeout));
      }
    }
    catch (Exception e)
    {
      LoggerFactory.getLogger().error("Error occurred retrieving session meta data.", e);
    }
    return sessionMeta;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.zookeeper.inspector.manager.ZooInspectorNodeTreeManager#createNode
   * (java.lang.String, java.lang.String)
   */
  @Override
  public boolean createNode(String parent, String nodeName)
  {
    if (zooKeeper.getState() == States.CONNECTED)
    {
      try
      {
        String[] nodeElements = nodeName.split("/");
        for (String nodeElement : nodeElements)
        {
          String node = parent + "/" + nodeElement;
          Stat s = zooKeeper.exists(node, false);
          if (s == null)
          {
            zooKeeper.create(node,
                             this.encryptionManager.encryptData(null),
                             Ids.OPEN_ACL_UNSAFE,
                             CreateMode.PERSISTENT);
            parent = node;
          }
        }
        return true;
      }
      catch (Exception e)
      {
        LoggerFactory.getLogger().error("Error occurred creating node: " + parent + "/"
                                            + nodeName,
                                        e);
      }
    }
    return false;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.zookeeper.inspector.manager.ZooInspectorNodeTreeManager#deleteNode
   * (java.lang.String)
   */
  @Override
  public boolean deleteNode(String nodePath)
  {
    if (zooKeeper.getState() == States.CONNECTED)
    {
      try
      {
        // Stat s = zooKeeper.exists(nodePath, false);
        // if (s != null) {
        List<String> children = zooKeeper.getChildren(nodePath, false);
        if (children != null)
        {
          for (String child : children)
          {
            String node = nodePath + "/" + child;
            deleteNode(node);
          }
        }
        zooKeeper.delete(nodePath, -1);
        // }
        return true;
      }
      catch (Exception e)
      {
        LoggerFactory.getLogger().error("Error occurred deleting node: " + nodePath, e);
      }
    }
    return false;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.zookeeper.inspector.manager.ZooInspectorNodeManager#setData
   * (java.lang.String, java.lang.String)
   */
  @Override
  public boolean setData(String nodePath, String data)
  {
    if (connected)
    {
      try
      {
        zooKeeper.setData(nodePath, this.encryptionManager.encryptData(data), -1);
        return true;
      }
      catch (Exception e)
      {
        LoggerFactory.getLogger().error("Error occurred setting data for node: "
                                            + nodePath,
                                        e);
      }
    }
    return false;
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorManager#
   * getConnectionPropertiesTemplate()
   */
  @Override
  public Pair<Map<String, List<String>>, Map<String, String>> getConnectionPropertiesTemplate()
  {
    Map<String, List<String>> template = new LinkedHashMap<String, List<String>>();
    // template.put(CONNECT_STRING, Arrays.asList(new String[] { defaultHosts }));
    template.put(CONNECT_STRING, defaultHostsList);
    template.put(SESSION_TIMEOUT, Arrays.asList(new String[] { defaultTimeout }));
    template.put(DATA_ENCRYPTION_MANAGER,
                 Arrays.asList(new String[] { defaultEncryptionManager }));
    Map<String, String> labels = new LinkedHashMap<String, String>();
    labels.put(CONNECT_STRING, "Connect String");
    labels.put(SESSION_TIMEOUT, "Session Timeout");
    labels.put(DATA_ENCRYPTION_MANAGER, "Data Encryption Manager");
    return new Pair<Map<String, List<String>>, Map<String, String>>(template, labels);
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.zookeeper.inspector.manager.ZooInspectorManager#addWatchers
   * (java.util.Collection, org.apache.zookeeper.inspector.manager.NodeListener)
   */
  @Override
  public void addWatchers(Collection<String> selectedNodes, NodeListener nodeListener)
  {
    // add watcher for each node and add node to collection of
    // watched nodes
    if (connected)
    {
      for (String node : selectedNodes)
      {
        if (!watchers.containsKey(node))
        {
          try
          {
            watchers.put(node, new NodeWatcher(node, nodeListener, zooKeeper));
          }
          catch (Exception e)
          {
            LoggerFactory.getLogger()
                         .error("Error occured adding node watcher for node: " + node, e);
          }
        }
      }
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.zookeeper.inspector.manager.ZooInspectorManager#removeWatchers
   * (java.util.Collection)
   */
  @Override
  public void removeWatchers(Collection<String> selectedNodes)
  {
    // remove watcher for each node and remove node from
    // collection of watched nodes
    if (connected)
    {
      for (String node : selectedNodes)
      {
        if (watchers.containsKey(node))
        {
          NodeWatcher watcher = watchers.remove(node);
          if (watcher != null)
          {
            watcher.stop();
          }
        }
      }
    }
  }

  /**
   * A Watcher which will re-add itself every time an event is fired
   *
   */
  public class NodeWatcher implements Watcher
  {

    private final String nodePath;
    private final NodeListener nodeListener;
    private final ZooKeeper zookeeper;
    private boolean closed = false;

    // cache stat and children when event fires
    private final Stat stat = new Stat();
    private List<String> childs;

    /**
     * @param nodePath
     *          - the path to the node to watch
     * @param nodeListener
     *          the {@link NodeListener} for this node
     * @param zookeeper
     *          - a {@link ZooKeeper} to use to access zookeeper
     * @throws InterruptedException
     * @throws KeeperException
     */
    public NodeWatcher(String nodePath, NodeListener nodeListener, ZooKeeper zookeeper)
        throws KeeperException, InterruptedException
    {
      this.nodePath = nodePath;
      this.nodeListener = nodeListener;
      this.zookeeper = zookeeper;
      Stat s = zooKeeper.exists(nodePath, this);
      if (s != null)
      {
        childs = zookeeper.getChildren(nodePath, this, stat);
      }
    }

    @Override
    public void process(WatchedEvent event)
    {
      if (!closed)
      {
        try
        {
          if (event.getType() != EventType.NodeDeleted)
          {

            Stat s = zooKeeper.exists(nodePath, this);
            if (s != null)
            {
              childs = zookeeper.getChildren(nodePath, this, s);
            }
          }
        }
        catch (Exception e)
        {
          LoggerFactory.getLogger().error("Error occured re-adding node watcherfor node "
                                              + nodePath,
                                          e);
        }

        if (nodeListener != null)
        {
          nodeListener.processEvent(event.getPath(), event.getType().name(), null);
        }
      }
    }

    /**
		 *
		 */
    public void stop()
    {
      this.closed = true;
    }

    public List<String> getChilds()
    {
      return childs;
    }
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorManager#
   * loadNodeViewersFile(java.io.File)
   */
  @Override
  public List<String> loadNodeViewersFile(File selectedFile) throws IOException
  {
    // TODO read from src/main/resources/defaultNodeVeiwers.cfg
    List<String> result = new ArrayList<String>();
    if (defaultNodeViewersFile.exists())
    {
      FileReader reader = new FileReader(selectedFile);
      try
      {
        BufferedReader buff = new BufferedReader(reader);
        try
        {
          while (buff.ready())
          {
            String line = buff.readLine();
            if (line != null && line.length() > 0)
            {
              result.add(line);
            }
          }
        }
        finally
        {
          buff.close();
        }
      }
      finally
      {
        reader.close();
      }
    } else {
      result.add("org.apache.zookeeper.inspector.gui.nodeviewer.NodeViewerData");
      result.add("org.apache.zookeeper.inspector.gui.nodeviewer.NodeViewerMetaData");
      result.add("org.apache.zookeeper.inspector.gui.nodeviewer.NodeViewerACL");
    }
    return result;
  }

  private void loadDefaultConnectionFile() throws IOException
  {
    if (defaultConnectionFile.exists())
    {
      Properties props = new Properties();

      FileReader reader = new FileReader(defaultConnectionFile);
      try
      {
        props.load(reader);
      }
      finally
      {
        reader.close();
      }
      defaultEncryptionManager =
          props.getProperty(DATA_ENCRYPTION_MANAGER) == null
              ? "org.apache.zookeeper.inspector.encryption.BasicDataEncryptionManager"
              : props.getProperty(DATA_ENCRYPTION_MANAGER);
      defaultTimeout =
          props.getProperty(SESSION_TIMEOUT) == null ? "30000"
              : props.getProperty(SESSION_TIMEOUT);
      defaultHosts =
          props.getProperty(CONNECT_STRING) == null ? "localhost:2181"
              : props.getProperty(CONNECT_STRING);
    }
    else
    {
      defaultEncryptionManager =
          "org.apache.zookeeper.inspector.encryption.BasicDataEncryptionManager";
      defaultTimeout = "30000";
      defaultHosts = "localhost:2181";
    }

    defaultHostsList = new ArrayList<String>(Arrays.asList(defaultHosts.trim().split("\\s+")));
    System.out.println("defaultHostsList: " + defaultHostsList);
//    System.out.println("end");
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorManager#
   * saveDefaultConnectionFile(java.util.Properties)
   */
  @Override
  public void saveDefaultConnectionFile(Properties props) throws IOException
  {
    File defaultDir = defaultConnectionFile.getParentFile();
    if (!defaultDir.exists())
    {
      if (!defaultDir.mkdirs())
      {
        throw new IOException("Failed to create configuration directory: "
            + defaultDir.getAbsolutePath());
      }
    }
    if (!defaultConnectionFile.exists())
    {
      if (!defaultConnectionFile.createNewFile())
      {
        throw new IOException("Failed to create default connection file: "
            + defaultConnectionFile.getAbsolutePath());
      }
    }
    FileWriter writer = new FileWriter(defaultConnectionFile);
    try
    {
      props.store(writer, "Default connection for ZooInspector");
    }
    finally
    {
      writer.close();
    }
  }

  @Override
  public void updateDefaultConnectionFile(Properties connectionProps) throws IOException
  {
    Properties properties = new Properties();

    String connStr = connectionProps.getProperty(CONNECT_STRING);
    defaultHostsList.remove(connStr);
    while (defaultHostsList.size() > defaultHostsListSize) {
      defaultHostsList.remove(defaultHostsList.size()-1);
    }

    defaultHostsList.add(0, connStr);

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < defaultHostsList.size(); i++) {
      String str = defaultHostsList.get(i);
      if (i > 0) {
        sb.append(" ");
      }
      sb.append(str);
    }
    System.out.println("updateDefaultConnectionFile#connectString: " + sb.toString());
    properties.setProperty(CONNECT_STRING, sb.toString());
    properties.setProperty(SESSION_TIMEOUT, defaultTimeout);
    properties.getProperty(DATA_ENCRYPTION_MANAGER, defaultEncryptionManager);
    saveDefaultConnectionFile(properties);
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorManager#
   * saveNodeViewersFile(java.io.File, java.util.List)
   */
  @Override
  public void saveNodeViewersFile(File selectedFile, List<String> nodeViewersClassNames) throws IOException
  {
    if (!selectedFile.exists())
    {
      if (!selectedFile.createNewFile())
      {
        throw new IOException("Failed to create node viewers configuration file: "
            + selectedFile.getAbsolutePath());
      }
    }
    FileWriter writer = new FileWriter(selectedFile);
    try
    {
      BufferedWriter buff = new BufferedWriter(writer);
      try
      {
        for (String nodeViewersClassName : nodeViewersClassNames)
        {
          buff.append(nodeViewersClassName);
          buff.append("\n");
        }
      }
      finally
      {
        buff.flush();
        buff.close();
      }
    }
    finally
    {
      writer.close();
    }
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorManager#
   * setDefaultNodeViewerConfiguration(java.io.File, java.util.List)
   */
  @Override
  public void setDefaultNodeViewerConfiguration(List<String> nodeViewersClassNames) throws IOException
  {
    File defaultDir = defaultNodeViewersFile.getParentFile();
    if (!defaultDir.exists())
    {
      if (!defaultDir.mkdirs())
      {
        throw new IOException("Failed to create configuration directory: "
            + defaultDir.getAbsolutePath());
      }
    }
    saveNodeViewersFile(defaultNodeViewersFile, nodeViewersClassNames);
  }

  @Override
  public List<String> getDefaultNodeViewerConfiguration() throws IOException
  {
    return loadNodeViewersFile(defaultNodeViewersFile);
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorManager#
   * getLastConnectionProps()
   */
  @Override
  public Properties getLastConnectionProps()
  {
    return this.lastConnectionProps;
  }

  /*
   * (non-Javadoc)
   *
   * @seeorg.apache.zookeeper.inspector.manager.ZooInspectorManager#
   * setLastConnectionProps(java.util.Properties)
   */
  @Override
  public void setLastConnectionProps(Properties connectionProps)
  {
    this.lastConnectionProps = connectionProps;
  }

  @Override
  public ZooInspectorManagerCache getCache()
  {
    return cache;
  }

  @Override
  public States getZookeeperStates()
  {
    if (zooKeeper == null)
    {
      return null;
    }

    return zooKeeper.getState();
  }

}
