package net.schwarzbaer.java.tools.steaminspector;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
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
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
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
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.gui.ImageView;
import net.schwarzbaer.gui.StandardDialog;
import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BaseTreeNode.ContentType;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.ExternViewableNode;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.FileBasedNode;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.FileSystem.ImageFile;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.FileSystem.TextFile;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.LabeledFile;
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
	private TreeType selectedTreeType = null;
	private JPanel fileContentPanel = null;
	private final ExtendedOutput hexTableOutput;
	private final ExtendedOutput plainTextOutput;
	private final ExtendedOutput extendedTextOutput;
	private final ExtendedOutput parsedTextOutput;
	private final ImageOutput imageOutput;
	private final OutputDummy outputDummy;
	private FileContentOutput lastFileContentOutput;
	
	SteamInspector() {
		hexTableOutput     = new ExtendedOutput(BaseTreeNode.ContentType.Bytes);
		plainTextOutput    = new ExtendedOutput(BaseTreeNode.ContentType.PlainText);
		extendedTextOutput = new ExtendedOutput(BaseTreeNode.ContentType.ExtendedText);
		parsedTextOutput   = new ExtendedOutput(BaseTreeNode.ContentType.ParsedText);
		imageOutput = new ImageOutput();
		outputDummy = new OutputDummy();
		lastFileContentOutput = outputDummy;
	}

	public static class AppSettings extends Settings<AppSettings.ValueGroup,AppSettings.ValueKey> {
		public enum ValueKey {
			WindowX, WindowY, WindowWidth, WindowHeight, TextEditor, ImageViewer, SteamClientFolder, SteamLibraryFolders, SelectedTreeType,
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
	
	enum TreeType {
		FilesNFolders("Discovered Folders, Some Simple Extracts", TreeNodes.FileSystem.Root::new, false),
		GamesNPlayers("Discovered Players & Games", ()-> {
			TreeNodes.PlayersNGames.loadData();
			return new TreeNodes.PlayersNGames.Root();
		}, true),
		;
		private final String label;
		private final Supplier<TreeNode> createRoot;
		private final boolean isRootVisible;

		TreeType(String label, Supplier<TreeNode> createRoot, boolean isRootVisible) {
			this.label = label;
			this.createRoot = createRoot;
			this.isRootVisible = isRootVisible;
		}
		
		@Override public String toString() {
			return label;
		}
	}
	
	private void createGUI() {
		
		JComboBox<TreeType> cmbbxTreeType = new JComboBox<>(TreeType.values());
		selectedTreeType = settings.getEnum(AppSettings.ValueKey.SelectedTreeType,TreeType.class);
		cmbbxTreeType.setSelectedItem(selectedTreeType);
		
		cmbbxTreeType.addActionListener(e->{
			Object obj = cmbbxTreeType.getSelectedItem();
			selectedTreeType = obj instanceof TreeType ? (TreeType)obj : null;
			rebuildTree();
			settings.putEnum(AppSettings.ValueKey.SelectedTreeType, selectedTreeType);
		});
		
		JPanel dataTopPanel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 0;
		dataTopPanel.add(new JLabel("Structure: "),c);
		c.weightx = 1;
		dataTopPanel.add(cmbbxTreeType,c);
		c.weightx = 0;
		dataTopPanel.add(createButton("Reload", true, e->rebuildTree()),c);
		
		TreeNode treeRoot = selectedTreeType==null || selectedTreeType.createRoot==null ? null : selectedTreeType.createRoot.get();
		boolean isRootVisible = selectedTreeType==null ? true : selectedTreeType.isRootVisible;
		tree = new JTree(treeRoot);
		tree.setRootVisible(isRootVisible);
		tree.setCellRenderer(new BaseTreeNodeRenderer());
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.addTreeSelectionListener(e->{
			TreePath path = e.getPath();
			if (path==null) return;
			showContent(path.getLastPathComponent());
		});
		new MainTreeContextMenue(tree);
		
		JScrollPane treePanel = new JScrollPane(tree);
		treePanel.setPreferredSize(new Dimension(500, 800));
		
		JPanel dataPanel = new JPanel(new BorderLayout(3,3));
		dataPanel.setBorder(BorderFactory.createTitledBorder("Found Data"));
		dataPanel.add(dataTopPanel, BorderLayout.NORTH);
		dataPanel.add(treePanel, BorderLayout.CENTER);
		
		fileContentPanel = new JPanel(new BorderLayout(3,3));
		fileContentPanel.setBorder(BorderFactory.createTitledBorder("File Content"));
		fileContentPanel.setPreferredSize(new Dimension(1000,800));
		fileContentPanel.add(lastFileContentOutput.getMainComponent());
		
		JSplitPane contentPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, dataPanel, fileContentPanel);
		contentPane.setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
		
		mainWindow = new StandardMainWindow("Steam Inspector");
		mainWindow.startGUI(contentPane,createMenuBar());
		mainWindow.setIconImagesFromResource("/images/","Logo016.png","Logo024.png","Logo032.png","Logo048.png","Logo286.png");
		
		if (settings.isSet(AppSettings.ValueGroup.WindowPos )) mainWindow.setLocation(settings.getWindowPos ());
		if (settings.isSet(AppSettings.ValueGroup.WindowSize)) mainWindow.setSize    (settings.getWindowSize());
		
		mainWindow.addComponentListener(new ComponentListener() {
			@Override public void componentShown  (ComponentEvent e) {}
			@Override public void componentHidden (ComponentEvent e) {}
			@Override public void componentResized(ComponentEvent e) { settings.setWindowSize( mainWindow.getSize() ); }
			@Override public void componentMoved  (ComponentEvent e) { settings.setWindowPos ( mainWindow.getLocation() ); }
		});
	}

	private void rebuildTree() {
		if (selectedTreeType==null || selectedTreeType.createRoot==null) {
			tree.setModel(null);
			tree.setRootVisible(selectedTreeType==null ? true : selectedTreeType.isRootVisible);
		} else {
			tree.setModel(new DefaultTreeModel(selectedTreeType.createRoot.get()));
			tree.setRootVisible(selectedTreeType.isRootVisible);
		}
	}

	private JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();
		JMenu settingsMenu = menuBar.add(new JMenu("Settings"));
		settingsMenu.add(createMenuItem("Set Path to "+ TextFile.externalViewerInfo.viewerName+" ...", true, e-> TextFile.externalViewerInfo.chooseExecutable(mainWindow)));
		settingsMenu.add(createMenuItem("Set Path to "+ImageFile.externalViewerInfo.viewerName+" ...", true, e->ImageFile.externalViewerInfo.chooseExecutable(mainWindow)));
		settingsMenu.addSeparator();
		settingsMenu.add(createMenuItem("Set All Paths ...", true, e->new FolderSettingsDialog(mainWindow, "Define Paths").showDialog()));
		return menuBar;
	}

	protected void showContent(Object selectedNode) {
		boolean hideOutput = true;
		if (selectedNode instanceof BaseTreeNode) {
			BaseTreeNode<?,?> baseTreeNode = (BaseTreeNode<?,?>) selectedNode;
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

	static JRadioButton createRadioButton(String title, boolean isSelected, boolean isEnabled, ButtonGroup bg, Consumer<Boolean> setValue) {
		JRadioButton comp = new JRadioButton(title, isSelected);
		comp.setEnabled(isEnabled);
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
	
	static JCheckBoxMenuItem createCheckBoxMenuItem(String title, boolean isSelected, boolean isEnabled, Consumer<Boolean> setValue) {
		JCheckBoxMenuItem comp = new JCheckBoxMenuItem(title, isSelected);
		comp.setEnabled(isEnabled);
		if (setValue!=null) comp.addActionListener(e->setValue.accept(comp.isSelected()));
		return comp;
	}
	
	static JMenuItem createMenuItem(String title, boolean isEnabled, ActionListener al) {
		JMenuItem comp = new JMenuItem(title);
		comp.setEnabled(isEnabled);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}
	
	static JCheckBox createCheckBox(String title, boolean isSelected, boolean isEnabled, Consumer<Boolean> setValue) {
		JCheckBox comp = new JCheckBox(title, isSelected);
		comp.setEnabled(isEnabled);
		if (setValue!=null) comp.addActionListener(e->setValue.accept(comp.isSelected()));
		return comp;
	}

	static Component createHorizontalLine() {
		JLabel comp = new JLabel();
		comp.setBorder(BorderFactory.createEtchedBorder());
		return comp;
	}

	static JLabel createLabel(String text, int vertAlign) {
		JLabel comp = new JLabel(text);
		comp.setVerticalAlignment(vertAlign);
		return comp;
	}

	static ToggleBox createToggleBox(Boolean value, int minWidth, int minHeight, String strTrue, String strFalse, String strNull, Color colorTrue, Color colorFalse, Color colorNull) {
		ToggleBox comp = new ToggleBox(value, strTrue, strFalse, strNull, colorTrue, colorFalse, colorNull);
		Dimension size = new Dimension(minWidth,minHeight);
		comp.setMinimumSize(size);
		comp.setPreferredSize(size);
		return comp;
	}
	
	static class ModifiedTextField<A> extends JTextField {
		private static final long serialVersionUID = -814226398681252148L;
		private final Color defaultBG;
		private final Color errorBG;
		private Function<String, A> convert;
		private Predicate<A> check;
		private Consumer<A> setValue;

		public ModifiedTextField(String initialValue, Function<String, A> convert, Predicate<A> check, Consumer<A> setValue) {
			super(initialValue);
			this.convert = convert;
			this.check = check;
			this.setValue = setValue;
			defaultBG = getBackground();
			errorBG = Color.RED;
			if (this.setValue!=null && this.convert!=null) {
				addActionListener(e->sendChangedValue());
				addFocusListener(new FocusListener() {
					@Override public void focusGained(FocusEvent e) {}
					@Override public void focusLost  (FocusEvent e) { sendChangedValue(); }
				});
			}
		}

		public void setValue(A value, Function<A, String> convert) {
			setText(convert.apply(value));
			setBackground(check.test(value) ? defaultBG : errorBG);
		}

		private void sendChangedValue() {
			String str = getText();
			A value = convert.apply(str);
			if (check.test(value)) {
				setBackground(defaultBG);
				setValue.accept(value);
			} else
				setBackground(errorBG);
		}
		
	}
	
	static <A> ModifiedTextField<A> createModifiedTextField(String initialValue, Function<String,A> convert, Predicate<A> check, Consumer<A> setValue) {
		return new ModifiedTextField<A>(initialValue, convert, check, setValue);
	}
	
	static <A> JTextField createTextField(String initialValue, Function<String,A> convert, Predicate<A> check, Consumer<A> setValue) {
		JTextField comp = new JTextField(initialValue);
		Color defaultBG = comp.getBackground();
		Color errorBG = Color.RED;
		if (setValue!=null && convert!=null) {
			Runnable action = ()->{
				String str = comp.getText();
				A value = convert.apply(str);
				if (check.test(value)) {
					comp.setBackground(defaultBG);
					setValue.accept(value);
				} else
					comp.setBackground(errorBG);
			};
			comp.addActionListener(e->action.run());
			comp.addFocusListener(new FocusListener() {
				@Override public void focusGained(FocusEvent e) {}
				@Override public void focusLost(FocusEvent e) { action.run(); }
			});
		}
		
		return comp;
	}
	
	static class ToggleBox extends JLabel {
		private static final long serialVersionUID = 8024197163969547939L;
		
		private Boolean value;
		private final String strTrue;
		private final String strFalse;
		private final String strNull;
		private final Color colorTrue;
		private final Color colorFalse;
		private final Color colorNull;
	
		public ToggleBox(Boolean value, String strTrue, String strFalse, String strNull, Color colorTrue, Color colorFalse, Color colorNull) {
			this.strTrue = strTrue;
			this.strFalse = strFalse;
			this.strNull = strNull;
			this.colorTrue = colorTrue;
			this.colorFalse = colorFalse;
			this.colorNull = colorNull;
			setValue(value);
			setBorder(BorderFactory.createEtchedBorder());
		}
	
		public void setValue(Boolean value) {
			this.value = value;
			updateBoxText();
		}
	
		public void updateBoxText() {
			if (value==null)
				setBoxText(strNull,colorNull);
			else if (value)
				setBoxText(strTrue,colorTrue);
			else
				setBoxText(strFalse,colorFalse);
		}
	
		public void setBoxText(String str, Color color) {
			setText(str);
			setForeground(color);
			//setOpaque(color!=null);
			//setBackground(color);
		}
		
		public <A> Predicate<A> passThrough(Predicate<A> isOK) {
			return val -> {
				boolean b = isOK.test(val);
				setValue(b);
				return b;
			};
		}
	}

	private static final boolean SHOW_THREADING = false;
	private static Long lastTimeMillis = null;
	private static void showMessageFromThread(String format, Object... args) {
		if (SHOW_THREADING) {
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
	
	static class FolderSettingsDialog extends StandardDialog {
		private static final long serialVersionUID = 4253868170530477053L;
		private static final int RMD = GridBagConstraints.REMAINDER;
		private static final Color COLOR_FILE_EXISTS     = Color.GREEN.darker();
		private static final Color COLOR_FILE_NOT_EXISTS = Color.RED;
		
		private final ModifiedTextField<File> txtImageViewer;
		private final ModifiedTextField<File> txtTextEditor;
		private final ModifiedTextField<File> txtSteamClientFolder;
		private JFileChooser folderChooser;

		public FolderSettingsDialog(Window parent, String title) {
			super(parent, title);
			
			folderChooser = new JFileChooser("./");
			folderChooser.setMultiSelectionEnabled(false);
			folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

			ToggleBox tglbxImageViewer       = createToggleBox(null, 100,5,  "file exists",   "file not exists", "?????", COLOR_FILE_EXISTS, COLOR_FILE_NOT_EXISTS, null);
			ToggleBox tglbxTextEditor        = createToggleBox(null, 100,5,  "file exists",   "file not exists", "?????", COLOR_FILE_EXISTS, COLOR_FILE_NOT_EXISTS, null);
			ToggleBox tglbxSteamClientFolder = createToggleBox(null, 100,5,"folder exists", "folder not exists", "?????", COLOR_FILE_EXISTS, COLOR_FILE_NOT_EXISTS, null);
			txtImageViewer       = createFileField(File::isFile     , tglbxImageViewer      , AppSettings.ValueKey.ImageViewer      );
			txtTextEditor        = createFileField(File::isFile     , tglbxTextEditor       , AppSettings.ValueKey.TextEditor       );
			txtSteamClientFolder = createFileField(File::isDirectory, tglbxSteamClientFolder, AppSettings.ValueKey.SteamClientFolder);
			JButton btnSetImageViewer       = createButton("...", true, e->selectFile(this,executableFileChooser, AppSettings.ValueKey.ImageViewer      , txtImageViewer      ));
			JButton btnSetTextEditor        = createButton("...", true, e->selectFile(this,executableFileChooser, AppSettings.ValueKey.TextEditor       , txtTextEditor       ));
			JButton btnSetSteamClientFolder = createButton("...", true, e->selectFile(this,folderChooser        , AppSettings.ValueKey.SteamClientFolder, txtSteamClientFolder));
			
			FolderListModel folderListModel = new FolderListModel(AppSettings.ValueKey.SteamLibraryFolders);
			JList<File> folderList = new JList<>(folderListModel);
			folderList.setCellRenderer(new FolderListRenderer());
			
			JPanel contentPane = new JPanel(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.BOTH;
			
			c.weightx = 0;
			c.weighty = 0;
			c.gridx = 0;
			c.gridheight = 1;
			c.gridy=0; c.gridwidth=1;   contentPane.add(createLabel("Image Viewer : ",JLabel.CENTER),c);
			c.gridy++; c.gridwidth=RMD; contentPane.add(createHorizontalLine(),c);
			c.gridy++; c.gridwidth=1;   contentPane.add(createLabel("Text Editor : ",JLabel.CENTER),c);
			c.gridy++; c.gridwidth=RMD; contentPane.add(createHorizontalLine(),c);
			c.gridy++; c.gridwidth=1;   contentPane.add(createLabel("Steam Client Folder : ",JLabel.CENTER),c);
			c.gridy++; c.gridwidth=RMD; contentPane.add(createHorizontalLine(),c);
			c.gridwidth=1;
			int nextRow = c.gridy+1;
			
			c.weightx = 1;
			c.weighty = 0;
			c.gridx = 1;
			c.gridy=0;  contentPane.add(txtImageViewer      ,c);
			c.gridy+=2; contentPane.add(txtTextEditor       ,c);
			c.gridy+=2; contentPane.add(txtSteamClientFolder,c);
			
			c.weightx = 0;
			c.weighty = 0;
			c.gridx = 2;
			c.gridy=0;  contentPane.add(btnSetImageViewer      ,c);
			c.gridy+=2; contentPane.add(btnSetTextEditor       ,c);
			c.gridy+=2; contentPane.add(btnSetSteamClientFolder,c);
			
			c.weightx = 0;
			c.weighty = 0;
			c.gridx = 3;
			c.gridy=0;  contentPane.add(tglbxImageViewer      ,c);
			c.gridy+=2; contentPane.add(tglbxTextEditor       ,c);
			c.gridy+=2; contentPane.add(tglbxSteamClientFolder,c);
			
			c.gridwidth = 1;
			c.gridy = nextRow;
			c.gridx=0; c.weightx = 0; c.weighty = 0; c.gridheight =   1; contentPane.add(createLabel("Steam Library Folders : ",JLabel.CENTER),c);
			c.gridx++; c.weightx = 1; c.weighty = 1; c.gridheight = RMD; contentPane.add(new JScrollPane(folderList),c);
			c.gridx++; c.weightx = 0; c.weighty = 0; c.gridheight =   1;
			c.gridwidth = 2;
			contentPane.add(createButton("Add"   , true, e->folderListModel.add(selectFile(this,folderChooser))),c); c.gridy++;
			contentPane.add(createButton("Remove", true, e->folderListModel.remove(folderList.getSelectedIndices())),c); c.gridy++;
			c.weighty = 1;
			contentPane.add(new JLabel(),c);
			c.gridx=0; c.gridy = nextRow+1; c.gridheight = RMD; c.gridwidth = 1;
			contentPane.add(new JLabel(),c);
			
			
			contentPane.setPreferredSize(new Dimension(600,300));
			this.createGUI(contentPane,createButton("Close", true, e->{closeDialog();}));
		}

		private File selectFile(Component parent, JFileChooser fileChooser) {
			return selectFile(parent, null, fileChooser);
		}
		private File selectFile(Component parent, String title, JFileChooser fileChooser) {
			if (title!=null) fileChooser.setDialogTitle(title);
			if (fileChooser.showOpenDialog(parent)==JFileChooser.APPROVE_OPTION)
				return fileChooser.getSelectedFile();
			return null;
		}

		private void selectFile(Component parent, JFileChooser fileChooser, AppSettings.ValueKey key, ModifiedTextField<File> fileField) {
			selectFile(parent, null, fileChooser, key, fileField);
		}
		private void selectFile(Component parent, String title, JFileChooser fileChooser, AppSettings.ValueKey key, ModifiedTextField<File> fileField) {
			File currentValue = settings.getFile(key, null);
			if (currentValue!=null && currentValue.exists()) {
				fileChooser.setCurrentDirectory(currentValue.getParentFile());
				fileChooser.setSelectedFile(currentValue);
			}
			File selectedFile = selectFile(parent, title, fileChooser);
			if (selectedFile!=null) {
				settings.putFile(key, selectedFile);
				//fileField.setText(selectedFile.getAbsolutePath());
				fileField.setValue(selectedFile, File::getAbsolutePath);
			}
		}

		private ModifiedTextField<File> createFileField(Predicate<File> isOK, ToggleBox tglbx, AppSettings.ValueKey key) {
			File initialValue = settings.getFile(key, null);
			String initialValueStr = initialValue==null ? "" : initialValue.getAbsolutePath();
			tglbx.setValue(initialValue==null ? null : isOK.test(initialValue));
			return createModifiedTextField(initialValueStr,File::new,tglbx.passThrough(isOK),file->settings.putFile(key, file));
		}

		private static class FolderListRenderer implements ListCellRenderer<File> {
			private static final Border BORDER_NOT_FOCUSED = BorderFactory.createEmptyBorder(1,1,1,1);
			private static final Border BORDER_FOCUSED = BorderFactory.createDashedBorder(Color.BLACK);
			private static final int DEFAULT_HEIGHT = 20;
			private final JPanel rendererComp;
			private final JLabel pathLabel;
			private final JLabel statusLabel;
			private final JLabel status2Label;

			FolderListRenderer() {
				rendererComp = new JPanel();
				rendererComp.setLayout(new BoxLayout(rendererComp,BoxLayout.X_AXIS));
				rendererComp.add(pathLabel = new JLabel());
				rendererComp.add(statusLabel = new JLabel());
				rendererComp.add(status2Label = new JLabel());
				//rendererComp.setPreferredSize(new Dimension(300,DEFAULT_HEIGHT));
			}
			
			@Override
			public Component getListCellRendererComponent(JList<? extends File> list, File value, int index, boolean isSelected, boolean cellHasFocus) {
				boolean exists = value!=null && value.isDirectory();
				boolean hasSteamApps = value!=null && new File(value,TreeNodes.KnownFolders.STEAMAPPS_SUBPATH).isDirectory();
				
				pathLabel   .setText(value==null ? "<null>" : value.getAbsolutePath());
				statusLabel .setText(exists ? "   folder exists" : "   folder not exists");
				status2Label.setText(exists && !hasSteamApps ? " but has no "+TreeNodes.KnownFolders.STEAMAPPS_SUBPATH+" sub folder" : "");
				rendererComp.setBorder(cellHasFocus ? BORDER_FOCUSED : BORDER_NOT_FOCUSED);
				
				if (isSelected) {
					rendererComp.setBackground(list.getSelectionBackground());
					pathLabel   .setForeground(list.getSelectionForeground());
					statusLabel .setForeground(list.getSelectionForeground());
					status2Label.setForeground(list.getSelectionForeground());
				} else {
					rendererComp.setBackground(list.getBackground());
					pathLabel   .setForeground(list.getForeground());
					statusLabel .setForeground(exists       ? COLOR_FILE_EXISTS : COLOR_FILE_NOT_EXISTS);
					status2Label.setForeground(hasSteamApps ? COLOR_FILE_EXISTS : COLOR_FILE_NOT_EXISTS);
				}
				
				int prefWidth = 5;
				Dimension prefSize;
				prefSize = pathLabel   .getPreferredSize(); prefWidth += prefSize==null ? 0 : prefSize.width;
				prefSize = statusLabel .getPreferredSize(); prefWidth += prefSize==null ? 0 : prefSize.width;
				prefSize = status2Label.getPreferredSize(); prefWidth += prefSize==null ? 0 : prefSize.width;
				rendererComp.setPreferredSize(new Dimension(prefWidth, DEFAULT_HEIGHT));
				return rendererComp;
			}
		
		}

		private class FolderListModel implements ListModel<File> {
		
			private final AppSettings.ValueKey appSettingsKey;
			private final Vector<ListDataListener> listDataListeners;
			private final Vector<File> folders;

			public FolderListModel(AppSettings.ValueKey appSettingsKey) {
				this.appSettingsKey = appSettingsKey;
				listDataListeners = new Vector<>();
				folders = new Vector<>();
				
				File[] files = settings.getFiles(this.appSettingsKey);
				if (files!=null)
					folders.addAll(Arrays.asList(files));
			}
			@Override public void    addListDataListener(ListDataListener l) { listDataListeners.   add(l); }
			@Override public void removeListDataListener(ListDataListener l) { listDataListeners.remove(l); }
			
			void fireIntervalAddedEvent(Object source, int index0, int index1) {
				ListDataEvent e = new ListDataEvent(source, ListDataEvent.INTERVAL_ADDED, index0, index1);
				listDataListeners.forEach(l->l.intervalAdded(e));
			}
			@SuppressWarnings("unused")
			void fireIntervalRemovedEvent(Object source, int index0, int index1) {
				ListDataEvent e = new ListDataEvent(source, ListDataEvent.INTERVAL_REMOVED, index0, index1);
				listDataListeners.forEach(l->l.intervalRemoved(e));
			}
			void fireContentsChangedEvent(Object source, int index0, int index1) {
				ListDataEvent e = new ListDataEvent(source, ListDataEvent.CONTENTS_CHANGED, index0, index1);
				listDataListeners.forEach(l->l.contentsChanged(e));
			}

			public void remove(int[] indices) {
				if (indices==null || indices.length==0) return;
				int oldSize = folders.size();
				for (int i=indices.length-1; i>=0; i--)
					folders.remove(indices[i]);
				fireContentsChangedEvent(this, 0, oldSize);
				settings.putFiles(appSettingsKey, folders);
			}

			public void add(File file) {
				if (file==null) return;
				for (File folder:folders)
					if (folder.equals(file)) return;
				folders.add(file);
				fireIntervalAddedEvent(this, folders.size()-1, folders.size()-1);
				settings.putFiles(appSettingsKey, folders);
			}

			@Override public int getSize() { return folders.size(); }
		
			@Override
			public File getElementAt(int index) {
				if (0<=index && index<folders.size())
					return folders.get(index);
				return null;
			}
		}
		
	}
	
	static class ExternalViewerInfo {
		private final String viewerName;
		private final AppSettings.ValueKey viewerKey;
		ExternalViewerInfo(String viewerName, AppSettings.ValueKey viewerKey) {
			this.viewerName = viewerName;
			this.viewerKey = viewerKey;
			if (this.viewerName==null) throw new IllegalArgumentException();
			if (this.viewerKey ==null) throw new IllegalArgumentException();
		}
		File getExecutable(Component parent) {
			if (settings.contains(viewerKey))
				return settings.getFile(viewerKey);
			else
				return chooseExecutable(parent);
		}
		File chooseExecutable(Component parent) {
			executableFileChooser.setDialogTitle("Select Executable of "+viewerName);
			if (settings.contains(viewerKey)) {
				File viewer = settings.getFile(viewerKey);
				executableFileChooser.setSelectedFile(viewer);
			}
			if (executableFileChooser.showOpenDialog(parent)==JFileChooser.APPROVE_OPTION) {
				File viewer = executableFileChooser.getSelectedFile();
				settings.putFile(viewerKey, viewer);
				return viewer;
			} else
				return null;
		}
	}
	
	static class AbstractComponentContextMenu extends JPopupMenu {
		private static final long serialVersionUID = -7319873585613172787L;

		AbstractComponentContextMenu() {}
		AbstractComponentContextMenu(Component invoker) { addTo(invoker); }

		void addTo(Component invoker) {
			invoker.addMouseListener(new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent e) {
					if (e.getButton()==MouseEvent.BUTTON3) {
						show(invoker, e.getX(),e.getY());
					}
				}
			});
		}
	}

	static class MainTreeContextMenue extends JPopupMenu {
		private static final long serialVersionUID = -3729823093931172004L;

		private final JMenuItem miCopyPath;
		private final JMenuItem miExtViewer;
		
		private Object clickedNode = null;
		private LabeledFile clickedFile = null;
		private ExternalViewerInfo clickedExternalViewerInfo = null;
		
		MainTreeContextMenue(JTree tree) {
			
			add(miCopyPath = createMenuItem("Copy Path to Clipboard", true, e->{
				ClipboardTools.copyToClipBoard(clickedFile.file.getAbsolutePath());
			}));
			add(miExtViewer = createMenuItem("Open in External Viewer", true, e->{
				File viewer = clickedExternalViewerInfo.getExecutable(tree);
				if (viewer==null) return;
				
				try {
					Runtime.getRuntime().exec(new String[] { viewer.getAbsolutePath(), clickedFile.file.getAbsolutePath() });
				} catch (IOException e1) {
					System.err.println("Exception occured while opening selected file in an external viewer:");
					System.err.println("    selected file: "+clickedFile.file.getAbsolutePath());
					System.err.println("    external viewer: "+viewer.getAbsolutePath());
					e1.printStackTrace();
				}
			}));
			
			tree.addMouseListener(new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent e) {
					if (e.getButton()==MouseEvent.BUTTON3) {
						TreePath path = tree.getPathForLocation(e.getX(), e.getY());
						if (path!=null) {
							clickedNode = path.getLastPathComponent();
							prepareMenueItems();
							show(tree, e.getX(), e.getY());
						}
					}
				}
			});
		}

		protected void prepareMenueItems() {
			clickedFile               = clickedNode instanceof FileBasedNode      ? ((FileBasedNode     ) clickedNode).getFile()               : null;
			clickedExternalViewerInfo = clickedNode instanceof ExternViewableNode ? ((ExternViewableNode) clickedNode).getExternalViewerInfo() : null;
			
			miCopyPath .setEnabled(clickedFile!=null);
			miExtViewer.setEnabled(clickedFile!=null && clickedExternalViewerInfo!=null);
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
		private boolean isWordWrapActive;
		
		TextOutput() {
			isWordWrapActive = true;
			view = new JTextArea();
			view.setEditable(false);
			scrollPane = new JScrollPane(view);
			updateWordWrap();
			new ContextMenu();
		}
		
		private void updateWordWrap() {
			//System.out.printf("TextOutput[%08X].updateWordWrap [isWordWrapActive:%s]%n", hashCode(), isWordWrapActive);
			view.setLineWrap(isWordWrapActive);
			view.setWrapStyleWord(true);
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
		
		private class ContextMenu extends AbstractComponentContextMenu {
			private static final long serialVersionUID = 476878064035243596L;
			ContextMenu() {
				addTo(view);
				JCheckBoxMenuItem chkbxWordWrap = createCheckBoxMenuItem("Word Wrap", isWordWrapActive, true, b->{ isWordWrapActive=b; updateWordWrap(); });
				add(chkbxWordWrap);
				addPopupMenuListener(new PopupMenuListener() {
					@Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
						chkbxWordWrap.setSelected(isWordWrapActive);
					}
					
					@Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
					@Override public void popupMenuCanceled(PopupMenuEvent e) {}
				});
			}
			
		}
	}

	static class DataTreeOutput extends FileContentOutput {
		private JTree view;
		private JScrollPane scrollPane;
		private TreeRoot treeRoot;

		DataTreeOutput() {
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

	static class ExtendedOutput extends MultiOutput {
		
		private final ContentType type;
		private final Component mainComp;
		private ContentLoadWorker runningContentLoadWorker;
		
		private final HexTableOutput hexView;
		private final TextOutput plainText;
		private final DataTreeOutput dataTree;
		
		ExtendedOutput(BaseTreeNode.ContentType type) {
			this.type = type;
			
			switch (type) {
			case Bytes:
				hexView    = new HexTableOutput();
				plainText  = null;
				dataTree = null;
				mainComp   = hexView.getMainComponent();
				break;
			
			case PlainText:
				hexView    = null;
				plainText  = new TextOutput();
				dataTree = null;
				mainComp   = plainText.getMainComponent();
				break;
			
			case ExtendedText:
				add("Hex Table" , hexView   = new HexTableOutput());
				add("Plain Text", plainText = new TextOutput    ());
				dataTree = null;
				setActiveTab(1);
				mainComp  = super.getMainComponent();
				break;
				
			case ParsedText:
				add("Hex Table" , hexView   = new HexTableOutput  ());
				add("Plain Text", plainText = new TextOutput      ());
				add("Data Tree" , dataTree  = new DataTreeOutput());
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
			if (type!=ContentType.Bytes || hexView==null || plainText!=null || dataTree!=null) throw new IllegalStateException();
			setOutput(source, ContentLoadWorker::new);
		}

		void setSource(TextContentSource source) {
			if (type!=ContentType.PlainText || hexView!=null || plainText==null || dataTree!=null) throw new IllegalStateException();
			setOutput(source, ContentLoadWorker::new);
		}
		
		void setSource(ExtendedTextContentSource source) {
			if (type!=ContentType.ExtendedText || hexView==null || plainText==null || dataTree!=null) throw new IllegalStateException();
			setOutput(source, ContentLoadWorker::new);
			setActiveTab(1);
		}

		void setSource(ParsedTextContentSource source) {
			if (type!=ContentType.ParsedText || hexView==null || plainText==null || dataTree==null) throw new IllegalStateException();
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
			if (dataTree!=null) dataTree.showLoadingMsg();
			
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
				dataTree.setRoot(treeRoot);
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
		void showContextMenu(JTree invoker, int x, int y, Object clickedTreeNode);
	}
	
	static abstract class AbstractTreeContextMenu extends JPopupMenu implements TreeContextMenuHandler {
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
		@Override public void showContextMenu(JTree invoker, int x, int y, Object clickedTreeNode) {
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
				BaseTreeNode<?,?> baseTreeNode = (BaseTreeNode<?,?>) value;
				Icon icon = baseTreeNode.getIcon();
				if (icon!=null) setIcon(icon);
			}
//			setIcon(icon);
			return component;
		}
	}

	static abstract class BaseTreeNode<ParentNodeType extends TreeNode, ChildNodeType extends TreeNode> implements TreeNode {
		
		enum ContentType { PlainText, Bytes, ExtendedText, ParsedText, Image, }
		
		protected final ParentNodeType parent;
		protected final String title;
		protected final boolean allowsChildren;
		protected final boolean isLeaf;
		protected Vector<? extends ChildNodeType> children;
		protected final Icon icon;

		protected BaseTreeNode(ParentNodeType parent, String title, boolean allowsChildren, boolean isLeaf) {
			this(parent, title, allowsChildren, isLeaf, (Icon)null);
		}
		protected BaseTreeNode(ParentNodeType parent, String title, boolean allowsChildren, boolean isLeaf, TreeIcons icon) {
			this(parent, title, allowsChildren, isLeaf, TreeNodes.TreeIconsIS.getCachedIcon(icon));
		}
		protected BaseTreeNode(ParentNodeType parent, String title, boolean allowsChildren, boolean isLeaf, Icon icon) {
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

		protected abstract Vector<? extends ChildNodeType> createChildren();
		@Override public String toString() { return title; }

		@Override public ParentNodeType getParent() { return parent; }
		@Override public boolean getAllowsChildren() { return allowsChildren; }
		@Override public boolean isLeaf() { return isLeaf; }
		
		@Override public int getChildCount() {
			checkChildren("getChildCount()");
			return children.size();
		}

		@Override public ChildNodeType getChildAt(int childIndex) {
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

		protected void checkChildren(String methodeLabel) {
//			if (!allowsChildren) throw new IllegalStateException(String.format("TreeNode.%s from \"not allows children\" TreeNode", methodeLabel));
			if (!allowsChildren) children=new Vector<>();
			if (children==null) children=createChildren();
		}
		
		static class DummyTextNode extends BaseTreeNode<TreeNode,DummyTextNode> {

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
