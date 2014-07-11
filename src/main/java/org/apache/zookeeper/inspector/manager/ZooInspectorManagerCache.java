package org.apache.zookeeper.inspector.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.inspector.logger.LoggerFactory;

public class ZooInspectorManagerCache
{
  final Map<String, Item> cache;
  ZooInspectorManagerImpl manager;

  static class Item
  {
    List<String> childs;
    Stat zkStat;

    public Item(List<String> childs, Stat zkStat)
    {
      this.childs = childs;
      this.zkStat = zkStat;
    }
  }

  public ZooInspectorManagerCache(ZooInspectorManagerImpl manager)
  {
    this.manager = manager;
    this.cache = new ConcurrentHashMap<String, Item>();
  }

  /**
   *
   * @param paths to update
   * @param depth to go
   * @throws KeeperException
   */
  public void refresh(List<String> paths, int depth) throws KeeperException
  {
    if (depth < 0) {
      return;
    }

    if (paths == null || paths.size() == 0) {
      return;
    }

    Map<String, Item> childItems = manager.getChildren(paths);

    List<String> childPaths = new ArrayList<String>();

    // for (String path : paths) {
    for (int i = 0; i < paths.size(); i++) {
      String path = paths.get(i);
      // Item item = manager.getChildrenAndStat(path);
      Item item = childItems.get(path);
      if (item == null || item.childs == null) {
        cache.remove(path);
      } else  // if item != null && item.childs != null
      {
        cache.put(path, item);
        if (depth > 0) {
          for (String child : item.childs) {
            String childPath = path.equals("/") ? path + child : path + "/" + child;
            childPaths.add(childPath);
          }
        }
      }
    }

    if (childPaths.size() > 0) {
      refresh(childPaths, depth - 1);
    }
  }

  public String getNodeChild(String nodePath, int childIndex)
  {
    if (!cache.containsKey(nodePath)) {
    // if (!manager.watchers.containsKey(nodePath)) {
      LoggerFactory.getLogger().error("CACHE MISS! getNodeChild(). path: " + nodePath);

      return null;
    }

    List<String> childs = getChildren(nodePath);
    if (childs == null || childIndex >= childs.size()) {
      return null;
    }
    return childs.get(childIndex);
  }

  public int getNumChildren(String nodePath)
  {
    if (!cache.containsKey(nodePath)) {
    // if (!manager.watchers.containsKey(nodePath)) {
      LoggerFactory.getLogger().error("CACHE MISS! getNumChildren(). path: " + nodePath);
      return 0;
    }
    Item item = cache.get(nodePath);
    if (item == null || item.childs == null) {
      return 0;
    }
    return item.childs.size();
  }

  public List<String> getChildren(String nodePath) {
    if (!cache.containsKey(nodePath)) {
    // if (!manager.watchers.containsKey(nodePath)) {
      LoggerFactory.getLogger().error("CACHE MISS! getChildren(). path: " + nodePath);
      return Collections.emptyList();
    }
    // sort it
    List<String> childs = cache.get(nodePath).childs;
    // List<String> childs = manager.watchers.get(nodePath).getChilds();
    Collections.sort(childs);
    // System.out.println("getChilds: " + childs);

    return childs;
  }

  /**
   * remove all cached items start with prefix
   * @param prefix
   */
  public void removePrefix(String prefix) {
    Iterator<Map.Entry<String, Item>> iter = cache.entrySet().iterator();
    // Iterator<Map.Entry<String, NodeWatcher>> iter = manager.watchers.entrySet().iterator(); // cache.entrySet().iterator();
    while (iter.hasNext()) {
      String path = iter.next().getKey();
      if (path.startsWith(prefix)) {
        iter.remove();
      }
    }
  }
}
