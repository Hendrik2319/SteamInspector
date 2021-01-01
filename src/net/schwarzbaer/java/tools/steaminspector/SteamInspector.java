package net.schwarzbaer.java.tools.steaminspector;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.gui.IconSource;
import net.schwarzbaer.gui.IconSource.CachedIcons;
import net.schwarzbaer.gui.StandardMainWindow;

class SteamInspector {

	enum TreeIcons { GeneralFile, TextFile, VDFFile, Folder, RootFolder }
	static CachedIcons<TreeIcons> TreeIconsIS;
	
	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		TreeIconsIS = IconSource.createCachedIcons(16, 16, "/images/TreeIcons.png", TreeIcons.values());
		
		new SteamInspector().createGUI();
	}
	
	private StandardMainWindow mainWindow;
	private JTree tree;
	private TextOutput textOutput;
	private ExtendedTextOutput extendedTextOutput;
	private OutputDummy outputDummy;
	private FileContentOutput lastFileContentOutput;
	private JPanel fileContentPanel;

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
		
		textOutput = new TextOutput();
		extendedTextOutput = new ExtendedTextOutput();
		outputDummy = new OutputDummy();
		
		lastFileContentOutput = outputDummy;
		
		fileContentPanel = new JPanel(new BorderLayout(3,3));
		fileContentPanel.setBorder(BorderFactory.createTitledBorder("File Content"));
		fileContentPanel.setPreferredSize(new Dimension(1000,800));
		fileContentPanel.add(lastFileContentOutput.getMainComponent());
		
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
				
				case ExtendedText:
					changeFileContentOutput(extendedTextOutput);
					extendedTextOutput.setOutput(baseTreeNode);
					hideOutput = false;
					break;
					
				case PlainText:
					changeFileContentOutput(textOutput);
					textOutput.setPlainTextOutput(baseTreeNode.getContentAsText());
					hideOutput = false;
					break;
				
				case HexText:
					changeFileContentOutput(textOutput);
					textOutput.setHexTableOutput(baseTreeNode.getContentAsBytes());
					hideOutput = false;
					break;
				}
		}
		if (hideOutput)
			changeFileContentOutput(outputDummy);
	}

	private void changeFileContentOutput(FileContentOutput fco) {
		if (lastFileContentOutput!=null)
			fileContentPanel.remove(lastFileContentOutput.getMainComponent());
		
		lastFileContentOutput = fco;
		
		if (lastFileContentOutput!=null)
			fileContentPanel.add(lastFileContentOutput.getMainComponent());
		
		fileContentPanel.revalidate();
		fileContentPanel.repaint();
	}

	private JRadioButton createRadioButton(String title, boolean selected, boolean enabled, ButtonGroup bg, Consumer<Boolean> setValue) {
		JRadioButton comp = new JRadioButton(title, selected);
		comp.setEnabled(enabled);
		if (bg!=null) bg.add(comp);
		if (setValue!=null) comp.addActionListener(e->setValue.accept(comp.isSelected()));
		return comp;
	}
	
	static abstract class FileContentOutput {

		protected String toHexTable(byte[] bytes) {
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
					
					text += String.format("%08X: %s  |  %s%n", lineStart, hex, plain);
				}
			
			return text;
		}

		abstract Component getMainComponent();

		protected float getVertScrollbarPos(JScrollPane scrollPane) {
			if (scrollPane==null) return Float.NaN;
			return getVertScrollbarPos(scrollPane.getVerticalScrollBar());
		}

		protected float getVertScrollbarPos(JScrollBar scrollBar) {
			if (scrollBar==null) return Float.NaN;
			int min = scrollBar.getMinimum();
			int max = scrollBar.getMaximum();
			int ext = scrollBar.getVisibleAmount();
			int val = scrollBar.getValue();
			return (float)val / (max - min - ext);
		}

		protected void setVertScrollbarPos(JScrollPane scrollPane, float pos) {
			if (scrollPane==null) return;
			setVertScrollbarPos(scrollPane.getVerticalScrollBar(), pos);
		}

		protected void setVertScrollbarPos(JScrollBar scrollBar, float pos) {
			if (scrollBar==null) return;
			if (Float.isNaN(pos)) pos=0;
			if (pos<0) pos=0;
			if (pos>1) pos=1;
			int min = scrollBar.getMinimum();
			int max = scrollBar.getMaximum();
			int ext = scrollBar.getVisibleAmount();
			int val = (int) (pos * (max - min - ext));
			scrollBar.setValue(val);
		}
	}
	
	static class OutputDummy extends FileContentOutput {
		private JLabel dummyLabel;
		OutputDummy() {
			dummyLabel = new JLabel("No Content");
			dummyLabel.setHorizontalAlignment(JLabel.CENTER);
		}
		@Override Component getMainComponent() {
			return dummyLabel;
		}
	}

	static class TextOutput extends FileContentOutput {
		
		private final JTextArea textOutput;
		private final JScrollPane textOutputScrollPane;
		
		TextOutput() {
			textOutput = new JTextArea();
			textOutputScrollPane = new JScrollPane(textOutput);
		}
		
		@Override Component getMainComponent() {
			return textOutputScrollPane;
		}

		void setHexTableOutput(byte[] bytes) {
			setPlainTextOutput(toHexTable(bytes));
		}

		void setPlainTextOutput(String text) {
			float pos = getVertScrollbarPos(textOutputScrollPane);
//			System.out.printf(Locale.ENGLISH, "setTextOutput: VertScrollbarPos -> %f%n", pos);
			textOutput.setText(text);
			SwingUtilities.invokeLater(()->{
//				System.out.printf(Locale.ENGLISH, "setTextOutput: %f -> VertScrollbarPos%n", pos);
				setVertScrollbarPos(textOutputScrollPane,pos);
//				float pos_ = getVertScrollbarPos(textOutputScrollPane);
//				System.out.printf(Locale.ENGLISH, "setTextOutput: VertScrollbarPos -> %f%n", pos_);
//				System.out.println();
			});
		}
	}
	
	static class MultiOutput extends FileContentOutput {
		private final JTabbedPane mainPanel;
//		private final Vector<FileContentOutput> subPanels;

		MultiOutput() {
			mainPanel = new JTabbedPane();
//			subPanels = new Vector<FileContentOutput>();
		}
		
		@Override Component getMainComponent() { return mainPanel; }
		
		void add(String title, FileContentOutput output) {
			mainPanel.addTab(title, output.getMainComponent());
//			subPanels.add(output);
		}
		
		void setActiveTab(int index) {
			mainPanel.setSelectedIndex(index);
		}
	}

	static class ExtendedTextOutput extends MultiOutput {
		private final TextOutput hexView;
		private final TextOutput plainText;
		ExtendedTextOutput() {
			add("Hex Table", hexView = new TextOutput());
			add("Plain text", plainText = new TextOutput());
			setActiveTab(1);
		}
		void setOutput(ExtendedTextContentSource source) {
			hexView.setHexTableOutput(source.getContentAsBytes());
			plainText.setPlainTextOutput(source.getContentAsText());
			setActiveTab(1);
		}
	}
	
	interface ExtendedTextContentSource {
		byte[] getContentAsBytes();
		String getContentAsText();
	}
	
	private static class Class1<FinalResult,IntermediateResult> extends SwingWorker<FinalResult,IntermediateResult> {

		// MUST
		@Override
		protected FinalResult doInBackground() throws Exception {
			// TODO Auto-generated method stub
			return null;
		}

		// CAN
		@Override
		protected void process(List<IntermediateResult> chunks) {
			// TODO Auto-generated method stub
			super.process(chunks);
		}

		@Override
		protected void done() {
			// TODO Auto-generated method stub
			super.done();
		}


	}
	
	private static class GuiFillerSingleTask {
		
		private static GuiFillerSingleTask runningTask = null;
		
		private final Runnable expensiveLoadTask;
		private final Runnable followingGuiTask;

		GuiFillerSingleTask(Runnable expensiveLoadTask, Runnable followingGuiTask) {
			this.expensiveLoadTask = expensiveLoadTask;
			this.followingGuiTask = followingGuiTask;
		}
		
		void start() {
			
			new Thread(()->{
				
			}).start();
		}
	}

	private final class BaseTreeNodeRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = -7291286788678796516L;
//		private Icon icon;
		
		BaseTreeNodeRenderer() {
//			icon = new JFileChooser().getIcon(new File("dummy.txt"));
//			icon = FileSystemView.getFileSystemView().get // SystemIcon(new File("dummy.txt"));
//			System.out.println("Icon for \"dummy.txt\" is: "+icon);
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean isExpanded, boolean isLeaf, int row, boolean hasFocus) {
			Component component = super.getTreeCellRendererComponent(tree, value, isSelected, isExpanded, isLeaf, row, hasFocus);
			if (value instanceof BaseTreeNode) {
				BaseTreeNode<?> baseTreeNode = (BaseTreeNode<?>) value;
				Icon icon = baseTreeNode.getIcon();
				if (icon!=null) setIcon(icon);
			}
//			setIcon(icon);
			return component;
		}
	}

	static abstract class BaseTreeNode<NodeType extends TreeNode> implements TreeNode, ExtendedTextContentSource {
		
		enum ContentType { PlainText, HexText, ExtendedText, }
		
		protected final TreeNode parent;
		protected final String title;
		protected final boolean allowsChildren;
		protected final boolean isLeaf;
		protected Vector<NodeType> children;
		protected final TreeIcons icon;

		protected BaseTreeNode(TreeNode parent, String title, boolean allowsChildren, boolean isLeaf) {
			this(parent, title, allowsChildren, isLeaf, null);
		}
		protected BaseTreeNode(TreeNode parent, String title, boolean allowsChildren, boolean isLeaf, TreeIcons icon) {
			this.parent = parent;
			this.title = title;
			this.allowsChildren = allowsChildren;
			this.isLeaf = isLeaf;
			this.icon = icon;
			children = null;
		}
		
		@Override public byte[] getContentAsBytes() { throw new UnsupportedOperationException(); }
		@Override public String getContentAsText () { throw new UnsupportedOperationException(); }

		ContentType getContentType() { return null; }
		Icon getIcon() { return icon==null ? null : TreeIconsIS.getCachedIcon(icon); }

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
