package net.schwarzbaer.java.tools.steaminspector;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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

	enum TreeIcons { GeneralFile, TextFile, VDFFile, AppManifest, Folder, RootFolder }
	static CachedIcons<TreeIcons> TreeIconsIS;
	
	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		TreeIconsIS = IconSource.createCachedIcons(16, 16, "/images/TreeIcons.png", TreeIcons.values());
		
		new SteamInspector().createGUI();
	}
	
	private StandardMainWindow mainWindow = null;
	private JTree tree = null;
	private ExtendedTextOutput hexTableOutput = null;
	private ExtendedTextOutput plainTextOutput = null;
	private ExtendedTextOutput extendedTextOutput = null;
	private OutputDummy outputDummy = null;
	private FileContentOutput lastFileContentOutput = null;
	private JPanel fileContentPanel = null;
	
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
		new TreeContextMenues(tree);
		
		JScrollPane treePanel = new JScrollPane(tree);
		treePanel.setPreferredSize(new Dimension(500, 800));
		
		JPanel treePanel2 = new JPanel(new BorderLayout(3,3));
		treePanel2.setBorder(BorderFactory.createTitledBorder("Found Data"));
		treePanel2.add(optionPanel, BorderLayout.NORTH);
		treePanel2.add(treePanel, BorderLayout.CENTER);
		
		hexTableOutput     = new ExtendedTextOutput(BaseTreeNode.ContentType.Bytes);
		plainTextOutput    = new ExtendedTextOutput(BaseTreeNode.ContentType.PlainText);
		extendedTextOutput = new ExtendedTextOutput(BaseTreeNode.ContentType.ExtendedText);
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
					if (baseTreeNode instanceof ExtendedTextContentSource) {
						extendedTextOutput.setOutput((ExtendedTextContentSource) baseTreeNode);
						changeFileContentOutput(extendedTextOutput);
						hideOutput = false;
					} else
						System.err.printf("TreeNode (\"%s\") has wrong ContentSource interface%n", baseTreeNode);
					break;
					
				case PlainText:
					if (baseTreeNode instanceof TextContentSource) {
						plainTextOutput.setOutput((TextContentSource) baseTreeNode);
						changeFileContentOutput(plainTextOutput);
						hideOutput = false;
					} else
						System.err.printf("TreeNode (\"%s\") has wrong ContentSource interface%n", baseTreeNode);
					break;
				
				case Bytes:
					if (baseTreeNode instanceof BytesContentSource) {
						hexTableOutput.setOutput((BytesContentSource) baseTreeNode);
						changeFileContentOutput(hexTableOutput);
						hideOutput = false;
					} else
						System.err.printf("TreeNode (\"%s\") has wrong ContentSource interface%n", baseTreeNode);
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

	private static JRadioButton createRadioButton(String title, boolean selected, boolean enabled, ButtonGroup bg, Consumer<Boolean> setValue) {
		JRadioButton comp = new JRadioButton(title, selected);
		comp.setEnabled(enabled);
		if (bg!=null) bg.add(comp);
		if (setValue!=null) comp.addActionListener(e->setValue.accept(comp.isSelected()));
		return comp;
	}
	
	private static JButton createButton(String title, boolean enabled, ActionListener al) {
		JButton comp = new JButton(title);
		comp.setEnabled(enabled);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}
	
	static class TreeContextMenues {

		TreeContextMenues(JTree tree) {
			tree.addMouseListener(new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent e) {
					if (e.getButton()==MouseEvent.BUTTON3) {
						TreePath path = tree.getPathForLocation(e.getX(), e.getY());
						if (path!=null) {
							Object lastPathComponent = path.getLastPathComponent();
							// TODO
						}
					}
				}
			});
		}
	}
	
	static class TreeContextMenu extends JPopupMenu {
		private static final long serialVersionUID = 5771382843112371294L;
		
	}
	
	static abstract class FileContentOutput {

		abstract Component getMainComponent();

		static float getVertScrollbarPos(JScrollPane scrollPane) {
			if (scrollPane==null) return Float.NaN;
			return getVertScrollbarPos(scrollPane.getVerticalScrollBar());
		}

		static float getVertScrollbarPos(JScrollBar scrollBar) {
			if (scrollBar==null) return Float.NaN;
			int min = scrollBar.getMinimum();
			int max = scrollBar.getMaximum();
			int ext = scrollBar.getVisibleAmount();
			int val = scrollBar.getValue();
			return (float)val / (max - min - ext);
		}

		static void setVertScrollbarPos(JScrollPane scrollPane, float pos) {
			if (scrollPane==null) return;
			setVertScrollbarPos(scrollPane.getVerticalScrollBar(), pos);
		}

		static void setVertScrollbarPos(JScrollBar scrollBar, float pos) {
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

	static class HexTableOutput extends TextOutput {
		private final static int PAGE_SIZE = 0x10000;
		private final JButton prevPageBtn1;
		private final JButton prevPageBtn2;
		private final JButton nextPageBtn1;
		private final JButton nextPageBtn2;
		private int page = 0;
		private byte[] bytes = null;

		HexTableOutput() {
			JPanel upperButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			JPanel lowerButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			upperButtonPanel.add(prevPageBtn1 = createButton("<<",true,e->switchPage(-1)));
			lowerButtonPanel.add(prevPageBtn2 = createButton("<<",true,e->switchPage(-1)));
			upperButtonPanel.add(nextPageBtn1 = createButton(">>",true,e->switchPage(+1)));
			lowerButtonPanel.add(nextPageBtn2 = createButton(">>",true,e->switchPage(+1)));
			
			JPanel outputPanel = new JPanel(new BorderLayout(3,3));
			outputPanel.add(upperButtonPanel,BorderLayout.NORTH);
			outputPanel.add(textOutput      ,BorderLayout.CENTER);
			outputPanel.add(lowerButtonPanel,BorderLayout.SOUTH);
			textOutputScrollPane.setViewportView(outputPanel);
		}

		private void switchPage(int inc) {
			page += inc;
			
			setText(toHexTable(bytes, page));
			setScrollPos(inc>=0 ? 0 : 1);
			loadScrollPos();
			
			enableButtons();
		}

		private void enableButtons() {
			prevPageBtn1.setEnabled(bytes!=null && page>0);
			prevPageBtn2.setEnabled(bytes!=null && page>0);
			nextPageBtn1.setEnabled(bytes!=null && (page+1)*PAGE_SIZE<bytes.length);
			nextPageBtn2.setEnabled(bytes!=null && (page+1)*PAGE_SIZE<bytes.length);
		}

		void setHexTableOutput(byte[] bytes) {
			this.bytes = bytes;
			setText(toHexTable(this.bytes, page=0));
			setScrollPos(0);
			loadScrollPos();
			enableButtons();
		}

		static String toHexTable(byte[] bytes, int page) {
			String text = "";
			
			if (bytes==null)
				text = "Can't read content";
			
			else
				for (int lineStart=page*PAGE_SIZE; lineStart<(page+1)*PAGE_SIZE && lineStart<bytes.length; lineStart+=16) {
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

	}

	static class TextOutput extends FileContentOutput {
		
		protected final JTextArea textOutput;
		protected final JScrollPane textOutputScrollPane;
		private float storedScrollPos;
		
		TextOutput() {
			textOutput = new JTextArea();
			textOutput.setEditable(false);
			textOutputScrollPane = new JScrollPane(textOutput);
		}
		
		@Override Component getMainComponent() {
			return textOutputScrollPane;
		}

//		void setPlainTextOutput(String text) {
//			saveScrollPos();
//			setText(text);
//			loadScrollPos();
//		}

		void setText(String text) {
			textOutput.setText(text);
		}

		void loadScrollPos() {
			SwingUtilities.invokeLater(()->{
//				System.out.printf(Locale.ENGLISH, "setTextOutput: %f -> VertScrollbarPos%n", storedScrollPos);
				setVertScrollbarPos(textOutputScrollPane,storedScrollPos);
				storedScrollPos = Float.NaN;
//				float pos_ = getVertScrollbarPos(textOutputScrollPane);
//				System.out.printf(Locale.ENGLISH, "setTextOutput: VertScrollbarPos -> %f%n", pos_);
//				System.out.println();
			});
		}

		void saveScrollPos() {
			storedScrollPos = getVertScrollbarPos(textOutputScrollPane);
//			System.out.printf(Locale.ENGLISH, "setTextOutput: VertScrollbarPos -> %f%n", storedScrollPos);
		}

		void setScrollPos(float scrollPos) {
			this.storedScrollPos = scrollPos;
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
		
		private final Component mainComp;
		private final HexTableOutput hexView;
		private final TextOutput plainText;
		private SwingWorkerImpl runningContentLoadWorker;
		
		ExtendedTextOutput(BaseTreeNode.ContentType type) {
			switch (type) {
			case Bytes:
				hexView   = new HexTableOutput();
				plainText = null;
				mainComp  = hexView.getMainComponent();
				break;
			
			case PlainText:
				hexView   = null;
				plainText = new TextOutput ();
				mainComp  = plainText.getMainComponent();
				break;
			
			case ExtendedText:
				add("Hex Table" , hexView   = new HexTableOutput());
				add("Plain Text", plainText = new TextOutput    ());
				setActiveTab(1);
				mainComp  = super.getMainComponent();
				break;
				
			default:
				hexView   = null;
				plainText = null;
				mainComp  = null;
			}
			runningContentLoadWorker = null;
		}
		
		@Override
		Component getMainComponent() {
			return mainComp;
		}

		void setOutput(BytesContentSource source) {
			if (hexView==null || plainText!=null) throw new IllegalStateException();
			setOutput(source, SwingWorkerImpl::new);
		}

		void setOutput(TextContentSource source) {
			if (hexView!=null || plainText==null) throw new IllegalStateException();
			setOutput(source, SwingWorkerImpl::new);
		}

		void setOutput(ExtendedTextContentSource source) {
			if (hexView==null || plainText==null) throw new IllegalStateException();
			setOutput(source, SwingWorkerImpl::new);
			setActiveTab(1);
		}

		private <A> void setOutput(A source, Function<A,SwingWorkerImpl> createWorker) {
			
			if (runningContentLoadWorker!=null && !runningContentLoadWorker.isDone() && !runningContentLoadWorker.isCancelled()) {
				runningContentLoadWorker.setObsolete(true);
				runningContentLoadWorker.cancel(true);
			}
			
			if (hexView  !=null) { hexView  .saveScrollPos(); hexView  .setText("load content ..."); }
			if (plainText!=null) { plainText.saveScrollPos(); plainText.setText("load content ..."); }
			
			runningContentLoadWorker = createWorker.apply(source);
			runningContentLoadWorker.execute();
		}
		
		private class SwingWorkerImpl extends SwingWorker<List<PostponedSetTextTask>,PostponedSetTextTask> {
			
			private final TextContentSource textSource;
			private final BytesContentSource bytesSource;
			private boolean isObsolete;
		
			SwingWorkerImpl(       BytesContentSource source) { this(source,  null); }
			SwingWorkerImpl(        TextContentSource source) { this(  null,source); }
			SwingWorkerImpl(ExtendedTextContentSource source) { this(source,source); }
			private SwingWorkerImpl(BytesContentSource bytesSource, TextContentSource textSource) {
				this.bytesSource = bytesSource;
				this.textSource = textSource;
				this.isObsolete = false;
			}
		
			public void setObsolete(boolean isObsolete) {
				this.isObsolete = isObsolete;
			}
		
			@Override
			protected List<PostponedSetTextTask> doInBackground() throws Exception {
				PostponedSetTextTask setPlainText=null, setHexView=null;
				byte[] bytes = bytesSource==null ? null : bytesSource.getContentAsBytes();            if (isObsolete) return null;
				String text  =  textSource==null ? null :  textSource.getContentAsText ();            if (isObsolete) return null;
				if (text !=null) publish(setPlainText = new PostponedSetTextTask(plainText, text ));  if (isObsolete) return null;
				if (bytes!=null) publish(setHexView   = new PostponedSetTextTask(hexView  , bytes));  if (isObsolete) return null;
				return Arrays.asList(setPlainText,setHexView);
			}
		
			@Override
			protected void process(List<PostponedSetTextTask> tasks) {
				if (tasks==null) return;
				for (PostponedSetTextTask task:tasks)
					if (task!=null) task.execute();
			}
		
			@Override
			protected void done() {
				try {
					process(get());
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
			}
		}

		private static class PostponedSetTextTask {
			private final HexTableOutput hexView;
			private final byte[] bytes;
			private final TextOutput textOutput;
			private final String text;
			private boolean isSolved;
			
			private PostponedSetTextTask(HexTableOutput hexView, byte[] bytes, TextOutput textOutput, String text) {
				this.hexView = hexView;
				this.bytes = bytes;
				this.textOutput = textOutput;
				this.text = text;
				this.isSolved = false;
			}
			PostponedSetTextTask(HexTableOutput hexView, byte[] bytes) {
				this(hexView, bytes, null, null);
			}
			PostponedSetTextTask(TextOutput plainText, String text) {
				this(null, null, plainText, text);
			}
			
			void execute() {
				if (isSolved) return;
				if (hexView!=null || bytes!=null) {
					if (hexView==null || bytes==null)
						throw new IllegalStateException();
					hexView.setHexTableOutput(bytes);
				}
				if (textOutput!=null || text!=null) {
					if (textOutput==null || text==null)
						throw new IllegalStateException();
					textOutput.setText(text);
					textOutput.loadScrollPos();
				}
				
				isSolved = true;
			}
		}
		
	}
	
	interface BytesContentSource {
		byte[] getContentAsBytes();
	}
	
	interface TextContentSource {
		String getContentAsText();
	}
	
	interface ExtendedTextContentSource extends BytesContentSource, TextContentSource {}

/*
	private static class SwingWorkerImpl<FinalResult,IntermediateResult> extends SwingWorker<FinalResult,IntermediateResult> {

		// Worker Thread
		@Override
		protected FinalResult doInBackground() throws Exception {
			// TO-DO Auto-generated method stub
			return null;
		}

		// GUI Event Thread
		@Override
		protected void process(List<IntermediateResult> chunks) {
			// TO-DO Auto-generated method stub
		}

		@Override
		protected void done() {
			try {
				FinalResult finalResult = get();
			} catch (InterruptedException | ExecutionException e) {
				// TO-DO Auto-generated catch block
				e.printStackTrace();
			}
			// TO-DO Auto-generated method stub
		}
	}
*/
	
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

	static abstract class BaseTreeNode<NodeType extends TreeNode> implements TreeNode {
		
		enum ContentType { PlainText, Bytes, ExtendedText, }
		
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
		
		byte[] getContentAsBytes() { throw new UnsupportedOperationException(); }
		String getContentAsText () { throw new UnsupportedOperationException(); }

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
