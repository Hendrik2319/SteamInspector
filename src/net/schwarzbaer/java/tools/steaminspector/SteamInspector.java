package net.schwarzbaer.java.tools.steaminspector;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Enumeration;
import java.util.Vector;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.gui.StandardMainWindow;

class SteamInspector {

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		new SteamInspector().createGUI();
	}

	private StandardMainWindow mainWindow;
	private JTree tree;
	private JTextArea textOutput;

	private void createGUI() {
		
		JPanel optionPanel = new JPanel(new GridLayout(1,0,3,3));
		optionPanel.add(new JLabel("Structure: "));
		ButtonGroup bg = new ButtonGroup();
		optionPanel.add(createRadioButton("Files & Folders", true, true,bg,b->{ tree.setModel(new DefaultTreeModel(new FolderStructure.Root())); tree.setRootVisible(false); }));
		optionPanel.add(createRadioButton("Games"          ,false,false,bg,b->{}));
		optionPanel.add(createRadioButton("Players, Games" ,false,false,bg,b->{}));
		
		tree = new JTree(new FolderStructure.Root());
		tree.setRootVisible(false);
		tree.setCellRenderer(new BaseTreeNodeRenderer());
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.addTreeSelectionListener(e->{
			TreePath path = e.getPath();
			if (path==null) return;
			showContent(path.getLastPathComponent());
		});
		
		JScrollPane treePanel = new JScrollPane(tree);
		treePanel.setPreferredSize(new Dimension(500, 800));
		
		JPanel treePanel2 = new JPanel(new BorderLayout(3,3));
		treePanel2.setBorder(BorderFactory.createTitledBorder("Found Data"));
		treePanel2.add(optionPanel, BorderLayout.NORTH);
		treePanel2.add(treePanel, BorderLayout.CENTER);
		
		
		textOutput = new JTextArea();
		JScrollPane textOutputScrollPane = new JScrollPane(textOutput);
		
		JPanel fileContentPanel = new JPanel(new BorderLayout(3,3));
		fileContentPanel.setBorder(BorderFactory.createTitledBorder("File Content"));
		fileContentPanel.setPreferredSize(new Dimension(500,800));
		fileContentPanel.add(textOutputScrollPane);
		
		
		JSplitPane contentPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel2, fileContentPanel);
		
		mainWindow = new StandardMainWindow("Steam Inspector");
		mainWindow.startGUI(contentPane);
	}

	protected void showContent(Object selectedNode) {
		boolean hideOutput = true;
		if (selectedNode instanceof BaseTreeNode) {
			BaseTreeNode<?> baseTreeNode = (BaseTreeNode<?>) selectedNode;
			BaseTreeNode.ContentType contentType = baseTreeNode.getContentType();
			if (contentType!=null)
				switch (contentType) {
				case PlainText:
					textOutput.setText(baseTreeNode.getContentAsText());
					hideOutput = false;
					break;
				case HexText:
					textOutput.setText(toHexView(baseTreeNode.getContentAsBytes()));
					hideOutput = false;
					break;
				}
		}
		textOutput.setVisible(!hideOutput);
	}

	private String toHexView(byte[] bytes) {
		String text = "";
		
		if (bytes==null)
			text = "Can't read content";
		
		else
			for (int lineStart=0; lineStart<bytes.length; lineStart+=16) {
				String hex = "";
				String plain = "";
				for (int pos=0; pos<16; pos++) {
					if (pos==8)
						hex += " |";
					if (lineStart+pos<bytes.length) {
						byte b = bytes[lineStart+pos];
						hex += String.format(" %02X", b);
						char ch = (char) b;
						if (ch=='\t' || ch=='\n' || ch=='\r') ch='.';
						plain += ch;
					} else {
						hex += " --";
						plain += ' ';
					}
				}
				
				text += String.format("%08X: %s  |  %s%n", hex, plain);
			}
		
		return text;
	}

	private JRadioButton createRadioButton(String title, boolean selected, boolean enabled, ButtonGroup bg, Consumer<Boolean> setValue) {
		JRadioButton comp = new JRadioButton(title, selected);
		comp.setEnabled(enabled);
		if (bg!=null) bg.add(comp);
		if (setValue!=null) comp.addActionListener(e->setValue.accept(comp.isSelected()));
		return comp;
	}
	
	private final class BaseTreeNodeRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = -7291286788678796516L;

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean isExpanded, boolean isLeaf, int row, boolean hasFocus) {
			Component component = super.getTreeCellRendererComponent(tree, value, isSelected, isExpanded, isLeaf, row, hasFocus);
			if (value instanceof BaseTreeNode) {
				BaseTreeNode<?> baseTreeNode = (BaseTreeNode<?>) value;
				Icon icon = baseTreeNode.getIcon();
				if (icon!=null) setIcon(icon);
			}
			return component;
		}
	}

	static abstract class BaseTreeNode<NodeType extends TreeNode> implements TreeNode {
		
		enum ContentType { PlainText, HexText, }
		
		protected final TreeNode parent;
		protected final String title;
		protected final boolean allowsChildren;
		protected final boolean isLeaf;
		protected Vector<NodeType> children;

		BaseTreeNode(TreeNode parent, String title, boolean allowsChildren, boolean isLeaf) {
			this.parent = parent;
			this.title = title;
			this.allowsChildren = allowsChildren;
			this.isLeaf = isLeaf;
			children = null;
		}
		
		byte[] getContentAsBytes() { throw new UnsupportedOperationException(); }
		String getContentAsText () { throw new UnsupportedOperationException(); }

		ContentType getContentType() { return null; }
		Icon getIcon() { return null; }

		protected abstract Vector<NodeType> createChildren();
		@Override public String toString() { return title; }

		@Override public TreeNode getParent() { return parent; }
		@Override public boolean getAllowsChildren() { return allowsChildren; }
		@Override public boolean isLeaf() { return isLeaf; }
		
		@Override public int getChildCount() {
			checkChildren("getChildCount()");
			return children.size();
		}

		@Override public TreeNode getChildAt(int childIndex) {
			checkChildren("getChildAt(childIndex)");
			if (childIndex<0 || childIndex>=children.size()) return null;
			return children.get(childIndex);
		}

		@Override public int getIndex(TreeNode node) {
			checkChildren("getIndex(node)");
			return children.indexOf(node);
		}

		@SuppressWarnings("rawtypes")
		@Override public Enumeration children() {
			checkChildren("children()");
			return children.elements();
		}

		private void checkChildren(String methodeLabel) {
//			if (!allowsChildren) throw new IllegalStateException(String.format("TreeNode.%s from \"not allows children\" TreeNode", methodeLabel));
			if (!allowsChildren) children=new Vector<>();
			if (children==null) children=createChildren();
		}
		
	}
}
