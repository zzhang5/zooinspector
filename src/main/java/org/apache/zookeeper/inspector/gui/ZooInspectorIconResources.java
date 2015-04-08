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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.ImageIcon;

/**
 * A class containing static methods for retrieving {@link ImageIcon}s used in
 * the application
 */
public class ZooInspectorIconResources {

    /**
     * @return file icon
     */
    public static ImageIcon getTreeLeafIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/file_obj.gif");
        return new ImageIcon(readFully(in)); //$NON-NLS-1$
    }

    /**
     * @return folder open icon
     */
    public static ImageIcon getTreeOpenIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/fldr_obj.gif");
        return new ImageIcon(readFully(in)); //$NON-NLS-1$
    }

    /**
     * @return folder closed icon
     */
    public static ImageIcon getTreeClosedIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/fldr_obj.gif");
        return new ImageIcon(readFully(in)); //$NON-NLS-1$
    }

    public static byte[] readFully(InputStream input)
    {
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try
        {
          while ((bytesRead = input.read(buffer)) != -1)
          {
              output.write(buffer, 0, bytesRead);
          }
        }
        catch (IOException e)
        {
          // TODO Auto-generated catch block
          e.printStackTrace();
          return null;
        }
        return output.toByteArray();
    }

    /**
     * @return connect icon
     */
    public static ImageIcon getConnectIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/launch_run.gif");
        ImageIcon icon = new ImageIcon(readFully(in));
        return icon; //$NON-NLS-1$
    }

    /**
     * @return disconnect icon
     */
    public static ImageIcon getDisconnectIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/launch_stop.gif");
        return new ImageIcon(readFully(in)); //$NON-NLS-1$
    }

    /**
     * @return save icon
     */
    public static ImageIcon getSaveIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/save_edit.gif");
        return new ImageIcon(readFully(in)); //$NON-NLS-1$
    }

    /**
     * @return add icon
     */
    public static ImageIcon getAddNodeIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/new_con.gif");
        return new ImageIcon(readFully(in)); //$NON-NLS-1$
    }

    /**
     * @return delete icon
     */
    public static ImageIcon getDeleteNodeIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/trash.gif");
        return new ImageIcon(readFully(in)); //$NON-NLS-1$
    }

    /**
     * @return refresh icon
     */
    public static ImageIcon getRefreshIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/refresh.gif");
        return new ImageIcon(readFully(in)); //$NON-NLS-1$
    }

    /**
     * @return information icon
     */
    public static ImageIcon getInformationIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/info_obj.gif");
        return new ImageIcon(readFully(in)); //$NON-NLS-1$
    }

    /**
     * @return node viewers icon
     */
    public static ImageIcon getChangeNodeViewersIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/edtsrclkup_co.gif");
        return new ImageIcon(readFully(in)); //$NON-NLS-1$
    }

    /**
     * @return up icon
     */
    public static ImageIcon getUpIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/search_prev.gif");
        return new ImageIcon(readFully(in)); //$NON-NLS-1$
    }

    /**
     * @return down icon
     */
    public static ImageIcon getDownIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/search_next.gif");
        return new ImageIcon(readFully(in)); //$NON-NLS-1$
    }
    
    /**
     * @return edit icon
     */
    public static ImageIcon getEditIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/edit.gif");
        return new ImageIcon(readFully(in)); //$NON-NLS-1$
    }

    /**
     * @return search icon
     */
    public static ImageIcon getSearchIcon() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("icons/search.gif");
        return new ImageIcon(readFully(in)); //$NON-NLS-1$
    }

}
