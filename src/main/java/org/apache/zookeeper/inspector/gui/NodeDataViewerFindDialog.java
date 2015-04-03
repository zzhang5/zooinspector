package org.apache.zookeeper.inspector.gui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.zookeeper.inspector.gui.nodeviewer.NodeViewerData;

public class NodeDataViewerFindDialog extends JDialog {

  public NodeDataViewerFindDialog(final NodeViewerData dataViewer) {
    this.setLayout(new BorderLayout());
    this.setTitle("Find");
    this.setModal(true);
    this.setAlwaysOnTop(true);
    this.setResizable(false);

    // input box
    final JPanel options = new JPanel();
    options.setLayout(new GridBagLayout());

    int i = 0;
    int rowPos = 2 * i + 1;
    JLabel label = new JLabel("Search for: ");
    GridBagConstraints c1 = new GridBagConstraints();
    c1.gridx = 0;
    c1.gridy = rowPos;
    c1.gridwidth = 1;
    c1.gridheight = 1;
    c1.weightx = 0;
    c1.weighty = 0;
    c1.anchor = GridBagConstraints.WEST;
    c1.fill = GridBagConstraints.HORIZONTAL;
    c1.insets = new Insets(5, 5, 5, 5);
    c1.ipadx = 0;
    c1.ipady = 0;
    options.add(label, c1);

    final JTextField text = new JTextField(10);
    GridBagConstraints c2 = new GridBagConstraints();
    c2.gridx = 2;
    c2.gridy = rowPos;
    c2.gridwidth = 1;
    c2.gridheight = 1;
    c2.weightx = 0;
    c2.weighty = 0;
    c2.anchor = GridBagConstraints.WEST;
    c2.fill = GridBagConstraints.HORIZONTAL;
    c2.insets = new Insets(5, 5, 5, 5);
    c2.ipadx = 0;
    c2.ipady = 0;
    options.add(text, c2);

    text.addKeyListener(new KeyListener() {
      @Override
      public void keyTyped(KeyEvent e) { }
      @Override
      public void keyReleased(KeyEvent e) { }
      @Override
      public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
        case KeyEvent.VK_ENTER:
          NodeDataViewerFindDialog.this.dispose();
          String selText = text.getText();
          dataViewer.highlight(selText);
          break;
        case KeyEvent.VK_ESCAPE:
          NodeDataViewerFindDialog.this.dispose();
          break;
        default:
          break;
        }
      }
    });

    this.add(options, BorderLayout.CENTER);
    this.pack();
  }
}
