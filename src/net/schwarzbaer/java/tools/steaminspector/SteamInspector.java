package net.schwarzbaer.java.tools.steaminspector;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
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
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.gui.ImageView;
import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.AppSettings.ValueKey;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BaseTreeNode.ContentType;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.FileSystem.FileSystemNode;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.FileSystem.ImageFile;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.FileSystem.TextFile;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.TreeIcons;
import net.schwarzbaer.system.ClipboardTools;
import net.schwarzbaer.system.Settings;

class SteamInspector {
	
	public static void main(String[] args) {
		new SteamInspector().createGUI();
	}
	
	static final AppSettings settings;
	static final JFileChooser executableFileChooser;
	
	static {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		TreeNodes.loadIcons();
		
		settings = new AppSettings();
		executableFileChooser = new JFileChooser("./");
		executableFileChooser.setMultiSelectionEnabled(false);
		executableFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		executableFileChooser.addChoosableFileFilter(new FileNameExtensionFilter("Executables (*.exe)", "exe"));
	}
	
	private StandardMainWindow mainWindow = null;
	private JTree tree = null;
	private JPanel fileContentPanel = null;
	private final ExtendedTextOutput hexTableOutput;
	private final ExtendedTextOutput plainTextOutput;
	private final ExtendedTextOutput extendedTextOutput;
	private final ExtendedTextOutput parsedTextOutput;
	private final ImageOutput imageOutput;
	private final OutputDummy outputDummy;
	private FileContentOutput lastFileContentOutput;
	
	SteamInspector() {
		hexTableOutput     = new ExtendedTextOutput(BaseTreeNode.ContentType.Bytes);
		plainTextOutput    = new ExtendedTextOutput(BaseTreeNode.ContentType.PlainText);
		extendedTextOutput = new ExtendedTextOutput(BaseTreeNode.ContentType.ExtendedText);
		parsedTextOutput   = new ExtendedTextOutput(BaseTreeNode.ContentType.ParsedText);
		imageOutput = new ImageOutput();
		outputDummy = new OutputDummy();
		lastFileContentOutput = outputDummy;
	}

	public static class AppSettings extends Settings<AppSettings.ValueGroup,AppSettings.ValueKey> {
		public enum ValueKey {
			WindowX, WindowY, WindowWidth, WindowHeight, TextEditor, ImageViewer,
		}

		public enum ValueGroup implements Settings.GroupKeys<ValueKey> {
			WindowPos (ValueKey.WindowX, ValueKey.WindowY),
			WindowSize(ValueKey.WindowWidth, ValueKey.WindowHeight),
			;
			ValueKey[] keys;
			ValueGroup(ValueKey...keys) { this.keys = keys;}
			@Override public ValueKey[] getKeys() { return keys; }
		}
		
		public AppSettings() { super(SteamInspector.class); }
		public Point     getWindowPos (              ) { return getPoint(ValueKey.WindowX,ValueKey.WindowY); }
		public void      setWindowPos (Point location) {        putPoint(ValueKey.WindowX,ValueKey.WindowY,location); }
		public Dimension getWindowSize(              ) { return getDimension(ValueKey.WindowWidth,ValueKey.WindowHeight); }
		public void      setWindowSize(Dimension size) {        putDimension(ValueKey.WindowWidth,ValueKey.WindowHeight,size); }
	}
	
	private void createGUI() {
		
		JPanel optionPanel = new JPanel(new GridLayout(1,0,3,3));
		optionPanel.add(new JLabel("Structure: "));
		ButtonGroup bg = new ButtonGroup();
		optionPanel.add(createRadioButton("Files & Folders", true, true,bg,b->{ tree.setModel(new DefaultTreeModel(new TreeNodes.FileSystem.Root())); tree.setRootVisible(false); }));
		optionPanel.add(createRadioButton("Games"          ,false,false,bg,b->{}));
		optionPanel.add(createRadioButton("Players, Games" ,false,false,bg,b->{}));
		
		tree = new JTree(new TreeNodes.FileSystem.Root());
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
		
		fileContentPanel = new JPanel(new BorderLayout(3,3));
		fileContentPanel.setBorder(BorderFactory.createTitledBorder("File Content"));
		fileContentPanel.setPreferredSize(new Dimension(1000,800));
		fileContentPanel.add(lastFileContentOutput.getMainComponent());
		
		JSplitPane contentPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel2, fileContentPanel);
		
		mainWindow = new StandardMainWindow("Steam Inspector");
		mainWindow.startGUI(contentPane,createMenuBar());
		
		if (settings.isSet(AppSettings.ValueGroup.WindowPos )) mainWindow.setLocation(settings.getWindowPos ());
		if (settings.isSet(AppSettings.ValueGroup.WindowSize)) mainWindow.setSize    (settings.getWindowSize());
		
		mainWindow.addComponentListener(new ComponentListener() {
			@Override public void componentShown  (ComponentEvent e) {}
			@Override public void componentHidden (ComponentEvent e) {}
			@Override public void componentResized(ComponentEvent e) { settings.setWindowSize( mainWindow.getSize() ); }
			@Override public void componentMoved  (ComponentEvent e) { settings.setWindowPos ( mainWindow.getLocation() ); }
		});
	}

	private JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();
		JMenu settingsMenu = menuBar.add(new JMenu("Settings"));
		settingsMenu.add(createMenuItem("Set Path to "+ TextFile.externalViewerInfo.viewerName+" ...", true, e->getExecutableFor(mainWindow,  TextFile.externalViewerInfo)));
		settingsMenu.add(createMenuItem("Set Path to "+ImageFile.externalViewerInfo.viewerName+" ...", true, e->getExecutableFor(mainWindow, ImageFile.externalViewerInfo)));
		return menuBar;
	}

	protected void showContent(Object selectedNode) {
		boolean hideOutput = true;
		if (selectedNode instanceof BaseTreeNode) {
			BaseTreeNode<?> baseTreeNode = (BaseTreeNode<?>) selectedNode;
			BaseTreeNode.ContentType contentType = baseTreeNode.getContentType();
			if (contentType!=null) {
				switch (contentType) {
				
				case Bytes:
					if (baseTreeNode instanceof BytesContentSource) {
						BytesContentSource source = (BytesContentSource) baseTreeNode;
						if (!source.isLarge() || userAllowsLargeFile(source)) {
							hexTableOutput.setSource(source);
							changeFileContentOutput(hexTableOutput);
							hideOutput = false;
						}
					} else
						System.err.printf("TreeNode (\"%s\") has wrong ContentSource interface for \"%s\" ContentType %n", baseTreeNode, contentType);
					break;
					
				case PlainText:
					if (baseTreeNode instanceof TextContentSource) {
						TextContentSource source = (TextContentSource) baseTreeNode;
						if (!source.isLarge() || userAllowsLargeFile(source)) {
							plainTextOutput.setSource(source);
							changeFileContentOutput(plainTextOutput);
							hideOutput = false;
						}
					} else
						System.err.printf("TreeNode (\"%s\") has wrong ContentSource interface for \"%s\" ContentType %n", baseTreeNode, contentType);
					break;
					
				case ExtendedText:
					if (baseTreeNode instanceof ExtendedTextContentSource) {
						ExtendedTextContentSource source = (ExtendedTextContentSource) baseTreeNode;
						if (!source.isLarge() || userAllowsLargeFile(source)) {
							extendedTextOutput.setSource(source);
							changeFileContentOutput(extendedTextOutput);
							hideOutput = false;
						}
					} else
						System.err.printf("TreeNode (\"%s\") has wrong ContentSource interface for \"%s\" ContentType %n", baseTreeNode, contentType);
					break;
				
				case ParsedText:
					if (baseTreeNode instanceof ParsedTextContentSource) {
						ParsedTextContentSource source = (ParsedTextContentSource) baseTreeNode;
						if (!source.isLarge() || userAllowsLargeFile(source)) {
							parsedTextOutput.setSource(source);
							changeFileContentOutput(parsedTextOutput);
							hideOutput = false;
						}
					} else
						System.err.printf("TreeNode (\"%s\") has wrong ContentSource interface for \"%s\" ContentType %n", baseTreeNode, contentType);
					break;
					
				case Image:
					if (baseTreeNode instanceof ImageContentSource) {
						ImageContentSource source = (ImageContentSource) baseTreeNode;
						imageOutput.setSource(source);
						changeFileContentOutput(imageOutput);
						hideOutput = false;
					} else
						System.err.printf("TreeNode (\"%s\") has wrong ContentSource interface for \"%s\" ContentType %n", baseTreeNode, contentType);
					break;
				}
			}
		}
		if (hideOutput)
			changeFileContentOutput(outputDummy);
	}

	private boolean userAllowsLargeFile(FileBasedSource source) {
		int result = JOptionPane.showConfirmDialog(fileContentPanel, "The file has a size of "+TreeNodes.getSizeStr(source.getFileSize())+". Do you really want to view it?", "Large File", JOptionPane.YES_NO_CANCEL_OPTION);
		return result==JOptionPane.YES_OPTION;
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

	static JRadioButton createRadioButton(String title, boolean selected, boolean enabled, ButtonGroup bg, Consumer<Boolean> setValue) {
		JRadioButton comp = new JRadioButton(title, selected);
		comp.setEnabled(enabled);
		if (bg!=null) bg.add(comp);
		if (setValue!=null) comp.addActionListener(e->setValue.accept(comp.isSelected()));
		return comp;
	}
	
	static JButton createButton(String title, boolean enabled, ActionListener al) {
		JButton comp = new JButton(title);
		comp.setEnabled(enabled);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}
	
	static JMenuItem createMenuItem(String title, boolean enabled, ActionListener al) {
		JMenuItem comp = new JMenuItem(title);
		comp.setEnabled(enabled);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}
	
	private static final boolean CHECK_THREADING = true;
	private static Long lastTimeMillis = null;
	private static void showMessageFromThread(String format, Object... args) {
		if (CHECK_THREADING) {
			Thread currentThread = Thread.currentThread();
			int threadHash = currentThread==null ? 0 : currentThread.hashCode();
			long currentTimeMillis = System.currentTimeMillis();
			String sinceLastCall = "";
			if (lastTimeMillis!=null) {
				long x = currentTimeMillis - lastTimeMillis;
				sinceLastCall = (x>0?"+":"")+x+"ms";
			}
			lastTimeMillis = currentTimeMillis;
			System.out.printf("[%10s,%016X,@%08X] %s%n", sinceLastCall, currentTimeMillis, threadHash, String.format(Locale.ENGLISH, format, args));
		}
	}
	
	static class ExternalViewerInfo {
		private final String viewerName;
		private final AppSettings.ValueKey viewerKey;
		ExternalViewerInfo(String viewerName, ValueKey viewerKey) {
			this.viewerName = viewerName;
			this.viewerKey = viewerKey;
		}
	}
	
	static File getExecutableFor(Component parent, ExternalViewerInfo externalViewerInfo) {
		executableFileChooser.setDialogTitle("Select Executable of "+externalViewerInfo.viewerName);
		if (settings.contains(externalViewerInfo.viewerKey)) {
			File viewer = settings.getFile(externalViewerInfo.viewerKey);
			executableFileChooser.setSelectedFile(viewer);
		}
		if (executableFileChooser.showOpenDialog(parent)==JFileChooser.APPROVE_OPTION) {
			File viewer = executableFileChooser.getSelectedFile();
			settings.putFile(externalViewerInfo.viewerKey, viewer);
			return viewer;
		} else
			return null;
	}

	static class TreeContextMenues {

		private FileContextMenu fileContextMenu;

		TreeContextMenues(JTree tree) {
			
			fileContextMenu = new FileContextMenu(tree);
			
			tree.addMouseListener(new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent e) {
					if (e.getButton()==MouseEvent.BUTTON3) {
						TreePath path = tree.getPathForLocation(e.getX(), e.getY());
						if (path!=null) {
							ContextMenu<?> contextMenu = getMenuFor(path.getLastPathComponent());
							if (contextMenu!=null)
								contextMenu.show(e.getX(),e.getY());
						}
					}
				}
			});
		}

		private ContextMenu<?> getMenuFor(Object pathComponent) {
			if (pathComponent instanceof FileSystemNode) {
				fileContextMenu.setClickedNode((FileSystemNode) pathComponent);
				return fileContextMenu;
			}
			return null;
		}

		private static class FileContextMenu extends ContextMenu<FileSystemNode> {
			private static final long serialVersionUID = -7683322808189858630L;
			private final JMenuItem miExtViewer;
			private FileSystemNode clickedNode;
			private ExternalViewerInfo externalViewerInfo;

			FileContextMenu(Component invoker) {
				super(invoker);
				clickedNode = null;
				externalViewerInfo = null;
				
				add(createMenuItem("Copy Path to Clipboard", true, e->ClipboardTools.copyToClipBoard(clickedNode.fileObj.getAbsolutePath())));
				add(miExtViewer = createMenuItem("Open in External Viewer", true, e->{
					File viewer;
					if (externalViewerInfo.viewerKey==null) throw new IllegalArgumentException("ExternalViewerInfo.viewerKey must not be <null>.");
					if (settings.contains(externalViewerInfo.viewerKey))
						viewer = settings.getFile(externalViewerInfo.viewerKey);
					else
						viewer = getExecutableFor(invoker,externalViewerInfo);
					
					if (viewer==null) return;
					
					try {
						Runtime.getRuntime().exec(new String[] { viewer.getAbsolutePath(), clickedNode.getFilePath() });
					} catch (IOException e1) {
						System.err.println("Exception occured while opening selected file in an external viewer:");
						System.err.println("    selected file: "+clickedNode.getFilePath());
						System.err.println("    external viewer: "+viewer.getAbsolutePath());
						e1.printStackTrace();
					}
				}));
			}

			@Override
			protected void setClickedNode(FileSystemNode clickedNode) {
				this.clickedNode = clickedNode;
				externalViewerInfo = this.clickedNode.getExternalViewerInfo();
				if (externalViewerInfo==null) {
					miExtViewer.setText("Open in External Viewer");
					miExtViewer.setEnabled(false);
				} else {
					miExtViewer.setText(String.format("Open \"%s\" in %s", this.clickedNode.getFileName(), externalViewerInfo.viewerName));
					miExtViewer.setEnabled(true);
				}
			}
		}
		
		private abstract static class ContextMenu<TreeNodeType> extends JPopupMenu {
			private static final long serialVersionUID = 7740906378931831629L;
			
			private Component invoker;

			ContextMenu(Component invoker) {
				this.invoker = invoker;
			}
			@SuppressWarnings("unused")
			void show(TreeNodeType treeNode, int x, int y) {
				setClickedNode(treeNode);
				show(x,y);
			}
			void show(int x, int y) {
				show(invoker, x, y);
			}
			protected abstract void setClickedNode(TreeNodeType treeNode);
		}
	}
	
	static abstract class FileContentOutput {

		abstract Component getMainComponent();
		abstract void showLoadingMsg();

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
		@Override void showLoadingMsg() {}
	}

	static class ImageOutput extends FileContentOutput {
		
		private final ImageView imageView;
		private ImageLoaderThread runningImageLoaderThread;
	
		ImageOutput() {
			imageView = new ImageView(300,200);
			imageView.reset();
			runningImageLoaderThread = null;
		}
		
		void setSource(ImageContentSource source) {
			if (runningImageLoaderThread!=null) {
				runningImageLoaderThread.setObsolete(true);
				runningImageLoaderThread.cancel(true);
			}
			showLoadingMsg();
			runningImageLoaderThread = new ImageLoaderThread(source);
			runningImageLoaderThread.execute();
		}
	
		void setImage(BufferedImage image) {
			imageView.setImage(image);
			imageView.reset();
		}
	
		@Override Component getMainComponent() { return imageView; }
		@Override void showLoadingMsg() {}
		
		private class ImageLoaderThread extends SwingWorker<BufferedImage, BufferedImage> {
			
			private final ImageContentSource source;
			private boolean isObsolete;
	
			public ImageLoaderThread(ImageContentSource source) {
				this.source = source;
				isObsolete = false;
			}
	
			public void setObsolete(boolean isObsolete) {
				this.isObsolete = isObsolete;
			}
	
			@Override
			protected BufferedImage doInBackground() throws Exception {
				showMessageFromThread("ImageLoaderThread.doInBackground started");
				BufferedImage image = source.getContentAsImage();
				if (isObsolete || isCancelled()) return null;
				showMessageFromThread("ImageLoaderThread.doInBackground finished");
				return image;
			}
	
			@Override
			protected void done() {
				if (isObsolete || isCancelled()) {
					showMessageFromThread("ImageLoaderThread.done skipped (%s%s )", isObsolete?" isObsolete":"", isCancelled()?" isCancelled":"");
					return;
				}
				showMessageFromThread("ImageLoaderThread.done started");
				try {
					setImage(get());
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
					showMessageFromThread("ImageLoaderThread.done Exception: %s", e.getMessage());
				}
				showMessageFromThread("ImageLoaderThread.done finished");
			}
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
		private HexTableFormatter hexTableFormatter = null;

		HexTableOutput() {
			JPanel upperButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			JPanel lowerButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			upperButtonPanel.add(prevPageBtn1 = createButton("<<",true,e->switchPage(-1)));
			lowerButtonPanel.add(prevPageBtn2 = createButton("<<",true,e->switchPage(-1)));
			upperButtonPanel.add(nextPageBtn1 = createButton(">>",true,e->switchPage(+1)));
			lowerButtonPanel.add(nextPageBtn2 = createButton(">>",true,e->switchPage(+1)));
			
			JPanel outputPanel = new JPanel(new BorderLayout(3,3));
			outputPanel.add(upperButtonPanel,BorderLayout.NORTH);
			outputPanel.add(view      ,BorderLayout.CENTER);
			outputPanel.add(lowerButtonPanel,BorderLayout.SOUTH);
			scrollPane.setViewportView(outputPanel);
		}

		private void switchPage(int inc) {
			page += inc;
			setPage();
		}

		private void enableButtons() {
			prevPageBtn1.setEnabled(bytes!=null && page>0);
			prevPageBtn2.setEnabled(bytes!=null && page>0);
			nextPageBtn1.setEnabled(bytes!=null && (page+1)*PAGE_SIZE<bytes.length);
			nextPageBtn2.setEnabled(bytes!=null && (page+1)*PAGE_SIZE<bytes.length);
		}

		void setHexTableOutput(byte[] bytes) {
			this.bytes = bytes;
			this.page = 0;
			setPage();
		}

		private void setPage() {
			if (hexTableFormatter!=null && !hexTableFormatter.isCancelled() && !hexTableFormatter.isDone() && !hexTableFormatter.isObsolete()) {
				hexTableFormatter.setObsolete(true);
				hexTableFormatter.cancel(true);
			}
			setText("prepare page "+page+" ...");
			hexTableFormatter = new HexTableFormatter(bytes, page, hexTable->{
				showMessageFromThread("HexTableOutput.setPage result task started  (page:%d, chars:%d)", this.page, hexTable.length());
				setText(hexTable);
				showMessageFromThread("HexTableOutput.setPage setText finished     (page:%d, chars:%d)", this.page, hexTable.length());
				setScrollPos(0);
				loadScrollPos();
				enableButtons();
				showMessageFromThread("HexTableOutput.setPage result task finished (page:%d, chars:%d)", this.page, hexTable.length());
			});
			hexTableFormatter.execute();
		}

		private static class HexTableFormatter extends SwingWorker<String, String> {
			
			private boolean isObsolete;
			private byte[] bytes;
			private int page;
			private Consumer<String> setText;
			
			HexTableFormatter(byte[] bytes, int page, Consumer<String> setText) {
				this.bytes = bytes;
				this.page = page;
				this.setText = setText;
				this.isObsolete = false;
				showMessageFromThread("HexTableFormatter created (bytes:%d, page:%d)", this.bytes.length, this.page);
			}
			
			boolean isObsolete() { return isObsolete; }
			private void setObsolete(boolean isObsolete) { this.isObsolete = isObsolete; }
			
			@Override
			protected String doInBackground() throws Exception {
				showMessageFromThread("HexTableFormatter.doInBackground started  (page:%d, bytes:%d)", this.page, this.bytes.length);
				String hexTable = toHexTable(bytes, page);
				showMessageFromThread("HexTableFormatter.doInBackground finished (page:%d, chars:%d)%s", this.page, hexTable.length(), isObsolete?" isObsolete":"");
				return isObsolete ? null : hexTable;
			}

			@Override
			protected void done() {
				if (isObsolete || isCancelled()) {
					showMessageFromThread("HexTableFormatter.done skipped (%s%s )", isObsolete?" isObsolete":"", isCancelled()?" isCancelled":"");
					return;
				}
				showMessageFromThread("HexTableFormatter.done started (page:%d)", this.page);
				String hexTable = null;
				try {
					hexTable = get();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
					showMessageFromThread("HexTableFormatter.done Exception: %s", e.getMessage());
				}
				if (!isObsolete && hexTable!=null) {
					setText.accept(hexTable);
					showMessageFromThread("HexTableFormatter.done result was send (page:%d, chars:%d)", this.page, hexTable.length());
				}
				showMessageFromThread("HexTableFormatter.done finished (page:%d)", this.page);
			}

			private static String toHexTable(byte[] bytes, int page) {
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

	}

	static class TextOutput extends FileContentOutput {
		
		protected final JTextArea view;
		protected final JScrollPane scrollPane;
		private float storedScrollPos;
		
		TextOutput() {
			view = new JTextArea();
			view.setEditable(false);
			scrollPane = new JScrollPane(view);
		}
		
		@Override Component getMainComponent() {
			return scrollPane;
		}

		@Override void showLoadingMsg() {
			saveScrollPos();
			setText("load content ...");
		}

		void setText(String text) {
			view.setText(text);
		}

		void loadScrollPos() {
			SwingUtilities.invokeLater(()->{
//				System.out.printf(Locale.ENGLISH, "setTextOutput: %f -> VertScrollbarPos%n", storedScrollPos);
				setVertScrollbarPos(scrollPane,storedScrollPos);
				storedScrollPos = Float.NaN;
//				float pos_ = getVertScrollbarPos(textOutputScrollPane);
//				System.out.printf(Locale.ENGLISH, "setTextOutput: VertScrollbarPos -> %f%n", pos_);
//				System.out.println();
			});
		}

		void saveScrollPos() {
			storedScrollPos = getVertScrollbarPos(scrollPane);
//			System.out.printf(Locale.ENGLISH, "setTextOutput: VertScrollbarPos -> %f%n", storedScrollPos);
		}

		void setScrollPos(float scrollPos) {
			this.storedScrollPos = scrollPos;
		}
	}

	static class ParsedTreeOutput extends FileContentOutput {
		private JTree view;
		private JScrollPane scrollPane;
		private TreeRoot treeRoot;

		ParsedTreeOutput() {
			treeRoot = null;
			view = new JTree();
			view.setRootVisible(false);
			view.setCellRenderer(new BaseTreeNodeRenderer());
			view.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
			view.addTreeSelectionListener(e->{
				TreePath path = e.getPath();
				if (path==null) return;
//				showContent(path.getLastPathComponent());
			});
			view.addMouseListener(new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent e) {
					if (e.getButton()==MouseEvent.BUTTON3) {
						TreePath path = view.getPathForLocation(e.getX(), e.getY());
						if (path==null || treeRoot==null) return;
						treeRoot.showContextMenu(view, e.getX(), e.getY(), path.getLastPathComponent());
					}
				}
			});
			scrollPane = new JScrollPane(view);
		}
		@Override Component getMainComponent() {
			return scrollPane;
		}
		void setRoot(TreeRoot treeRoot) {
			showMessageFromThread("ParsedTreeOutput.setRoot started");
			this.treeRoot = treeRoot;
			showMessageFromThread("ParsedTreeOutput.setRoot set root node");
			view.setModel(new DefaultTreeModel(treeRoot.node));
			view.setRootVisible(treeRoot.isRootVisible);
			if (treeRoot.expandAllRows) {
				showMessageFromThread("ParsedTreeOutput.setRoot expand full tree");
				for (int i=0; i<view.getRowCount(); i++)
					view.expandRow(i);
			}
			showMessageFromThread("ParsedTreeOutput.setRoot finished");
		}
		@Override void showLoadingMsg() {
			setRoot(BaseTreeNode.DummyTextNode.createSingleTextLineTree_("load content ..."));
		}
	}
	
	static class MultiOutput extends FileContentOutput {
		private final JTabbedPane mainPanel;
		private final Vector<FileContentOutput> subPanels;

		MultiOutput() {
			mainPanel = new JTabbedPane();
			subPanels = new Vector<FileContentOutput>();
		}
		
		@Override Component getMainComponent() { return mainPanel; }
		
		void add(String title, FileContentOutput output) {
			mainPanel.addTab(title, output.getMainComponent());
			subPanels.add(output);
		}
		void setActiveTab(int index) {
			mainPanel.setSelectedIndex(index);
		}
		@Override void showLoadingMsg() {
			subPanels.forEach(FileContentOutput::showLoadingMsg);
		}
	}

	static class ExtendedTextOutput extends MultiOutput {
		
		private final ContentType type;
		private final Component mainComp;
		private ContentLoadWorker runningContentLoadWorker;
		
		private final HexTableOutput hexView;
		private final TextOutput plainText;
		private final ParsedTreeOutput parsedTree;
		
		ExtendedTextOutput(BaseTreeNode.ContentType type) {
			this.type = type;
			
			switch (type) {
			case Bytes:
				hexView    = new HexTableOutput();
				plainText  = null;
				parsedTree = null;
				mainComp   = hexView.getMainComponent();
				break;
			
			case PlainText:
				hexView    = null;
				plainText  = new TextOutput();
				parsedTree = null;
				mainComp   = plainText.getMainComponent();
				break;
			
			case ExtendedText:
				add("Hex Table" , hexView   = new HexTableOutput());
				add("Plain Text", plainText = new TextOutput    ());
				parsedTree = null;
				setActiveTab(1);
				mainComp  = super.getMainComponent();
				break;
				
			case ParsedText:
				add("Hex Table" , hexView    = new HexTableOutput  ());
				add("Plain Text", plainText  = new TextOutput      ());
				add("Parsed"    , parsedTree = new ParsedTreeOutput());
				setActiveTab(2);
				mainComp  = super.getMainComponent();
				break;
			
			default:
				throw new IllegalArgumentException();
			}
			
			runningContentLoadWorker = null;
		}
		
		@Override
		Component getMainComponent() {
			return mainComp;
		}

		void setSource(BytesContentSource source) {
			if (type!=ContentType.Bytes || hexView==null || plainText!=null || parsedTree!=null) throw new IllegalStateException();
			setOutput(source, ContentLoadWorker::new);
		}

		void setSource(TextContentSource source) {
			if (type!=ContentType.PlainText || hexView!=null || plainText==null || parsedTree!=null) throw new IllegalStateException();
			setOutput(source, ContentLoadWorker::new);
		}
		
		void setSource(ExtendedTextContentSource source) {
			if (type!=ContentType.ExtendedText || hexView==null || plainText==null || parsedTree!=null) throw new IllegalStateException();
			setOutput(source, ContentLoadWorker::new);
			setActiveTab(1);
		}

		void setSource(ParsedTextContentSource source) {
			if (type!=ContentType.ParsedText || hexView==null || plainText==null || parsedTree==null) throw new IllegalStateException();
			setOutput(source, ContentLoadWorker::new);
			setActiveTab(2);
		}

		private <A> void setOutput(A source, Function<A,ContentLoadWorker> createWorker) {
			
			if (runningContentLoadWorker!=null && !runningContentLoadWorker.isDone() && !runningContentLoadWorker.isCancelled()) {
				runningContentLoadWorker.setObsolete(true);
				runningContentLoadWorker.cancel(true);
			}
			
			if (hexView   !=null) hexView   .showLoadingMsg();
			if (plainText !=null) plainText .showLoadingMsg();
			if (parsedTree!=null) parsedTree.showLoadingMsg();
			
			runningContentLoadWorker = createWorker.apply(source);
			runningContentLoadWorker.execute();
		}
		
		private class ContentLoadWorker extends SwingWorker<List<PostponedTask>,PostponedTask> {
			
			private final BytesContentSource bytesSource;
			private final TextContentSource   textSource;
			private final TreeContentSource   treeSource;
			private boolean isObsolete;
		
			ContentLoadWorker(       BytesContentSource source) { this(source,  null,  null); }
			ContentLoadWorker(        TextContentSource source) { this(  null,source,  null); }
			ContentLoadWorker(ExtendedTextContentSource source) { this(source,source,  null); }
			ContentLoadWorker(  ParsedTextContentSource source) { this(source,source,source); }
			
			private ContentLoadWorker(BytesContentSource bytesSource, TextContentSource textSource, TreeContentSource treeSource) {
				this.bytesSource = bytesSource;
				this.textSource = textSource;
				this.treeSource = treeSource;
				this.isObsolete = false;
				showMessageFromThread("ContentLoadWorker created");
			}
		
			public void setObsolete(boolean isObsolete) {
				this.isObsolete = isObsolete;
			}
			
			private void setHexTable(byte[] bytes) {
				showMessageFromThread("ContentLoadWorker.setHexTable started (bytes:%d)", bytes.length);
				hexView.setHexTableOutput(bytes);
				showMessageFromThread("ContentLoadWorker.setHexTable finished");
			}
			private void setPlainText(String text) {
				showMessageFromThread("ContentLoadWorker.setPlainText started (chars:%d)", text.length());
				plainText.setText(text);
				showMessageFromThread("ContentLoadWorker.setPlainText.setText finished");
				plainText.loadScrollPos();
				showMessageFromThread("ContentLoadWorker.setPlainText finished");
			}
			private void setParsedTree(TreeRoot treeRoot) {
				showMessageFromThread("ContentLoadWorker.setParsedTree started");
				parsedTree.setRoot(treeRoot);
				showMessageFromThread("ContentLoadWorker.setParsedTree finished");
			}
			
			@Override
			protected List<PostponedTask> doInBackground() throws Exception {
				showMessageFromThread("ContentLoadWorker.doInBackground started");
				PostponedTask setPlainText=null, setParsedTree=null, setHexView=null;
				byte[]   bytes    = bytesSource==null ? null : bytesSource.getContentAsBytes();        if (isObsolete) return null;
				String   text     =  textSource==null ? null :  textSource.getContentAsText ();        if (isObsolete) return null;
				TreeRoot treeNode =  treeSource==null ? null :  treeSource.getContentAsTree ();        if (isObsolete) return null;
				if (text    !=null) publish(setPlainText  = new PostponedTask("setPlainText ",()->setPlainText (text    )));  if (isObsolete) return null;
				if (treeNode!=null) publish(setParsedTree = new PostponedTask("setParsedTree",()->setParsedTree(treeNode)));  if (isObsolete) return null;
				if (bytes   !=null) publish(setHexView    = new PostponedTask("setHexTable  ",()->setHexTable  (bytes   )));  if (isObsolete) return null;
				showMessageFromThread("ContentLoadWorker.doInBackground finished");
				return Arrays.asList(setParsedTree,setPlainText,setHexView);
			}
			@Override
			protected void process(List<PostponedTask> tasks) {
				if (isObsolete || isCancelled()) {
					showMessageFromThread("ContentLoadWorker.process skipped (%s%s )", isObsolete?" isObsolete":"", isCancelled()?" isCancelled":"");
					return;
				}
				showMessageFromThread("ContentLoadWorker.process started");
				processTasks(tasks,"process");
				showMessageFromThread("ContentLoadWorker.process finished");
			}
			@Override
			protected void done() {
				if (isObsolete || isCancelled()) {
					showMessageFromThread("ContentLoadWorker.done skipped (%s%s )", isObsolete?" isObsolete":"", isCancelled()?" isCancelled":"");
					return;
				}
				showMessageFromThread("ContentLoadWorker.done started");
				try {
					processTasks(get(),"done");
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
					showMessageFromThread("ContentLoadWorker.done Exception: %s", e.getMessage());
				}
				showMessageFromThread("ContentLoadWorker.done finished");
			}
			private void processTasks(List<PostponedTask> tasks, String comment) {
				if (tasks==null) return;
				for (PostponedTask task:tasks)
					if (task!=null) task.execute(comment);
			}
		}

		private static class PostponedTask {
			
			private boolean isSolved;
			private final Runnable task;
			private final String label;
			
			PostponedTask(String label, Runnable task) {
				this.label = label;
				this.task = task;
				this.isSolved = false;
				showMessageFromThread("PostponedTask[%s] created", this.label);
			}
			
			@SuppressWarnings("unused")
			void execute() {
				execute(null);
			}
			void execute(String comment) {
				if (isSolved) return;
				showMessageFromThread("PostponedTask[%s] started %s", this.label, comment==null ? "" : " ("+comment+")");
				task.run();
				isSolved = true;
				showMessageFromThread("PostponedTask[%s] finished%s", this.label, comment==null ? "" : " ("+comment+")");
			}
		}
	}
	
	interface FileBasedSource {
		boolean isLarge();
		long getFileSize();
	}
	
	interface BytesContentSource extends FileBasedSource {
		byte[] getContentAsBytes();
	}
	
	interface ImageContentSource {
		BufferedImage getContentAsImage();
	}
	
	interface TextContentSource extends FileBasedSource {
		String getContentAsText();
	}
	
	interface TreeContentSource {
		TreeRoot getContentAsTree();
	}
	
	interface ExtendedTextContentSource extends BytesContentSource, TextContentSource {}
	interface ParsedTextContentSource extends ExtendedTextContentSource, TreeContentSource {}
	
	interface TreeContextMenuHandler {
		void showContextMenu(Component invoker, int x, int y, Object clickedTreeNode);
	}
	
	static abstract class AbstractContextMenu extends JPopupMenu implements TreeContextMenuHandler {
		private static final long serialVersionUID = -7162801786069506030L;
	}
	
	static class TreeRoot implements TreeContextMenuHandler {
		final TreeNode node;
		final boolean isRootVisible;
		final boolean expandAllRows;
		final TreeContextMenuHandler tcmh;
		
		TreeRoot(TreeNode node, boolean isRootVisible, boolean expandAllRows) {
			this(node, isRootVisible, expandAllRows, null);
		}
		TreeRoot(TreeNode node, boolean isRootVisible, boolean expandAllRows, TreeContextMenuHandler tcmh) {
			this.node = node;
			this.isRootVisible = isRootVisible;
			this.expandAllRows = expandAllRows;
			this.tcmh = tcmh;
		}
		@Override public void showContextMenu(Component invoker, int x, int y, Object clickedTreeNode) {
			if (tcmh!=null)
				tcmh.showContextMenu(invoker, x, y, clickedTreeNode);
		}
	}
	
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
	
	private final static class BaseTreeNodeRenderer extends DefaultTreeCellRenderer {
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
		
		enum ContentType { PlainText, Bytes, ExtendedText, ParsedText, Image, }
		
		protected final TreeNode parent;
		protected final String title;
		protected final boolean allowsChildren;
		protected final boolean isLeaf;
		protected Vector<? extends NodeType> children;
		protected final Icon icon;

		protected BaseTreeNode(TreeNode parent, String title, boolean allowsChildren, boolean isLeaf) {
			this(parent, title, allowsChildren, isLeaf, (Icon)null);
		}
		protected BaseTreeNode(TreeNode parent, String title, boolean allowsChildren, boolean isLeaf, TreeIcons icon) {
			this(parent, title, allowsChildren, isLeaf, TreeNodes.TreeIconsIS.getCachedIcon(icon));
		}
		protected BaseTreeNode(TreeNode parent, String title, boolean allowsChildren, boolean isLeaf, Icon icon) {
			this.parent = parent;
			this.title = title;
			this.allowsChildren = allowsChildren;
			this.isLeaf = isLeaf;
			this.icon = icon;
			children = null;
		}
		
//		byte[] getContentAsBytes() { throw new UnsupportedOperationException(); }
//		String getContentAsText () { throw new UnsupportedOperationException(); }

		ContentType getContentType() { return null; }
		Icon getIcon() { return icon; }

		protected abstract Vector<? extends NodeType> createChildren();
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
		
		static class DummyTextNode extends BaseTreeNode<DummyTextNode> {

			private BiFunction<DummyTextNode, Integer, DummyTextNode> createChild;

			protected DummyTextNode(TreeNode parent, String title) {
				this(parent, title, null);
			}
			protected DummyTextNode(TreeNode parent, String title, BiFunction<DummyTextNode,Integer,DummyTextNode> createChild) {
				super(parent, title, createChild!=null, createChild==null);
				this.createChild = createChild;
			}

			@Override
			protected Vector<? extends DummyTextNode> createChildren() {
				Vector<DummyTextNode> children = new Vector<>();
				int i=0;
				DummyTextNode child;
				while ( (child=createChild.apply(this,i))!=null ) {
					children.add(child);
					i++;
				}
				return children;
			}
			
			static TreeRoot createSingleTextLineTree_(String format, Object...args) {
				return new TreeRoot( new DummyTextNode(null, String.format(Locale.ENGLISH, format, args)), true, true );
			}
		}
	}
}
