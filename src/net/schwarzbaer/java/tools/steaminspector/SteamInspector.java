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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.AbstractButton;
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
import javax.swing.JRadioButtonMenuItem;
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
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.TreeIcons;
import net.schwarzbaer.system.ClipboardTools;
import net.schwarzbaer.system.Settings;

class SteamInspector {
	
	public static void main(String[] args) {
		new SteamInspector().createGUI();
	}

	private static final AppSettings settings;
	private static final JFileChooser executableFileChooser;
	
	static {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		TreeNodes.loadIcons();
		TreeNodes.interestingNodes.readfile();
		
		settings = new AppSettings();
		executableFileChooser = new JFileChooser("./");
		executableFileChooser.setMultiSelectionEnabled(false);
		executableFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		executableFileChooser.addChoosableFileFilter(new FileNameExtensionFilter("Executables (*.exe)", "exe"));
	}

	static final String INTERESTING_NODES_INI = "SteamInspector.InterestingNodes.ini";
	static final String KNOWN_GAME_TITLES_INI = "SteamInspector.KnownGameTitles.ini";
	static final File   FOLDER_TEST_FILES = new File("./test");
//	private static final File FOLDER_STEAMLIBRARY_STEAMAPPS      = new File("C:\\__Games\\SteamLibrary\\steamapps\\");
//	private static final File FOLDER_STEAM_USERDATA              = new File("C:\\Program Files (x86)\\Steam\\userdata");
//	private static final File FOLDER_STEAM_APPCACHE              = new File("C:\\Program Files (x86)\\Steam\\appcache");
//	private static final File FOLDER_STEAM_APPCACHE_LIBRARYCACHE = new File("C:\\Program Files (x86)\\Steam\\appcache\\librarycache");
//	private static final File FOLDER_STEAM_STEAM_GAMES           = new File("C:\\Program Files (x86)\\Steam\\steam\\games");
	// C:\Program Files (x86)\Steam\appcache\librarycache
	//        425580_icon.jpg 
	//        425580_header.jpg 
	//        425580_library_600x900.jpg 
	//        425580_library_hero.jpg 
	//        425580_library_hero_blur.jpg 
	//        425580_logo.png 
	// eb32e3c266a74c7d51835ebf7c866bf2dbf59b47.ico    ||   C:\Program Files (x86)\Steam\steam\games
	
	static class KnownFolders {
		
		static String STEAMAPPS_SUBPATH = "steamapps";
		
		enum SteamClientSubFolders {
			USERDATA             ("userdata"),
			APPCACHE             ("appcache"),
			APPCACHE_LIBRARYCACHE("appcache\\librarycache"),
			STEAM_GAMES          ("steam\\games"),
			;
			private final String path;
			SteamClientSubFolders(String path) { this.path = path; }
		}
		
		static void forEachSteamAppsFolder(BiConsumer<Integer,File> action) {
			if (action==null) return;
			
			int inc = 0;
			File steamClientFolder = getSteamClientFolder();
			if (steamClientFolder!=null) {
				action.accept(0, new File(steamClientFolder,STEAMAPPS_SUBPATH));
				inc = 1;
			}
			
			File[] folders = getSteamLibraryFolders();
			if (folders!=null)
				for (int i=0; i<folders.length; i++)
					action.accept(i+inc, new File(folders[i],STEAMAPPS_SUBPATH));
		}

		static File getSteamClientSubFolder(SteamClientSubFolders subFolder) {
			if (subFolder==null) return null;
			File steamClientFolder = getSteamClientFolder();
			if (steamClientFolder==null) return null;
			return new File(steamClientFolder,subFolder.path);
		}

		static File[] getSteamLibraryFolders() {
			AppSettings.ValueKey key = AppSettings.ValueKey.SteamLibraryFolders;
			return settings.getFiles(key);
		}

		static File getSteamClientFolder() {
			AppSettings.ValueKey folderKey = AppSettings.ValueKey.SteamClientFolder;
			return settings.getFile(folderKey, null);
		}
	}
	
	private StandardMainWindow mainWindow = null;
	private JTree tree = null;
	private DefaultTreeModel treeModel = null;
	private TreeType selectedTreeType = null;
	private JPanel fileContentPanel = null;
	private final CombinedOutput hexTableOutput;
	private final CombinedOutput plainTextOutput;
	private final CombinedOutput extendedTextOutput;
	private final CombinedOutput parsedTextOutput;
	private final CombinedOutput treeOutput;
	private final ImageNTextOutput imageNTextOutput;
	private final ImageOutput imageOutput;
	private final OutputDummy outputDummy;
	private FileContentOutput lastFileContentOutput;
	
	private SteamInspector() {
		hexTableOutput     = new CombinedOutput(BaseTreeNode.ContentType.Bytes);
		plainTextOutput    = new CombinedOutput(BaseTreeNode.ContentType.PlainText);
		extendedTextOutput = new CombinedOutput(BaseTreeNode.ContentType.ByteBasedText);
		parsedTextOutput   = new CombinedOutput(BaseTreeNode.ContentType.ParsedByteBasedText);
		treeOutput         = new CombinedOutput(BaseTreeNode.ContentType.DataTree);
		imageNTextOutput = new ImageNTextOutput();
		imageOutput = new ImageOutput();
		outputDummy = new OutputDummy();
		lastFileContentOutput = outputDummy;
	}

	private static class AppSettings extends Settings<AppSettings.ValueGroup,AppSettings.ValueKey> {
		private enum ValueKey {
			WindowX, WindowY, WindowWidth, WindowHeight, TextEditor, ImageViewer, Browser, SteamClientFolder, SteamLibraryFolders, SelectedTreeType, ZipViewer,
		}

		private enum ValueGroup implements Settings.GroupKeys<ValueKey> {
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
	
	private enum TreeType {
		FilesNFolders("Discovered Folders, Some Simple Extracts", TreeNodes.FileSystem.Root::new, false),
		GamesNPlayers("Discovered Players & Games", ()-> {
			Data.loadData();
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
		tree = new JTree(treeModel = treeRoot==null ? null : new DefaultTreeModel(treeRoot));
		tree.setRootVisible(isRootVisible);
		tree.setCellRenderer(new BaseTreeNodeRenderer());
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.addTreeSelectionListener(e->{
			TreePath path = e.getPath();
			if (path==null) return;
			showContent(path.getLastPathComponent());
		});
		new MainTreeContextMenu(tree,()->treeModel);
		
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
			tree.setModel(treeModel = null);
			tree.setRootVisible(selectedTreeType==null ? true : selectedTreeType.isRootVisible);
		} else {
			tree.setModel(treeModel = new DefaultTreeModel(selectedTreeType.createRoot.get()));
			tree.setRootVisible(selectedTreeType.isRootVisible);
		}
	}

	private JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();
		
		JMenu settingsMenu = menuBar.add(new JMenu("Settings"));
		for (ExternalViewerInfo evi:ExternalViewerInfo.values())
			settingsMenu.add(createMenuItem("Set Path to "+evi.viewerName+" ...", true, e->evi.chooseExecutable(mainWindow)));
		settingsMenu.addSeparator();
		settingsMenu.add(createMenuItem("Set All Paths ...", true, e->new FolderSettingsDialog(mainWindow, "Define Paths").showDialog()));
		
		JMenu debugMenu = menuBar.add(new JMenu("Debug"));
		debugMenu.add(createMenuItem("Show Interesting Nodes", true, e->TreeNodes.interestingNodes.showTo(System.out)));
		
		return menuBar;
	}

	private void showContent(Object selectedNode) {
		boolean hideOutput = true;
		if (selectedNode instanceof BaseTreeNode) {
			BaseTreeNode<?,?> baseTreeNode = (BaseTreeNode<?,?>) selectedNode;
			BaseTreeNode.ContentType contentType = baseTreeNode.getContentType();
			if (contentType!=null) {
				switch (contentType) {
				
				case Bytes:
					if (baseTreeNode instanceof ByteContentSource) {
						ByteContentSource source = (ByteContentSource) baseTreeNode;
						if (allowLargeFile(baseTreeNode)) {
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
						if (allowLargeFile(baseTreeNode)) {
							plainTextOutput.setSource(source);
							changeFileContentOutput(plainTextOutput);
							hideOutput = false;
						}
					} else
						System.err.printf("TreeNode (\"%s\") has wrong ContentSource interface for \"%s\" ContentType %n", baseTreeNode, contentType);
					break;
					
				case ByteBasedText:
					if (baseTreeNode instanceof ByteBasedTextContentSource) {
						ByteBasedTextContentSource source = (ByteBasedTextContentSource) baseTreeNode;
						if (allowLargeFile(baseTreeNode)) {
							extendedTextOutput.setSource(source);
							changeFileContentOutput(extendedTextOutput);
							hideOutput = false;
						}
					} else
						System.err.printf("TreeNode (\"%s\") has wrong ContentSource interface for \"%s\" ContentType %n", baseTreeNode, contentType);
					break;
				
				case ParsedByteBasedText:
					if (baseTreeNode instanceof ParsedByteBasedTextContentSource) {
						ParsedByteBasedTextContentSource source = (ParsedByteBasedTextContentSource) baseTreeNode;
						if (allowLargeFile(baseTreeNode)) {
							parsedTextOutput.setSource(source);
							changeFileContentOutput(parsedTextOutput);
							hideOutput = false;
						}
					} else
						System.err.printf("TreeNode (\"%s\") has wrong ContentSource interface for \"%s\" ContentType %n", baseTreeNode, contentType);
					break;
					
				case DataTree:
					if (baseTreeNode instanceof TreeContentSource) {
						TreeContentSource source = (TreeContentSource) baseTreeNode;
						treeOutput.setSource(source);
						changeFileContentOutput(treeOutput);
						hideOutput = false;
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
					
				case ImageNText:
					if (baseTreeNode instanceof ImageNTextContentSource) {
						ImageNTextContentSource source = (ImageNTextContentSource) baseTreeNode;
						imageNTextOutput.setSource(source);
						changeFileContentOutput(imageNTextOutput);
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

	private boolean allowLargeFile(BaseTreeNode<?, ?> baseTreeNode) {
		if (baseTreeNode instanceof FileBasedSource) {
			FileBasedSource fileBasedSource = (FileBasedSource) baseTreeNode;
			return !fileBasedSource.isLarge() || userAllowsLargeFile(fileBasedSource);
		}
		return true;
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

	private static <BTN extends AbstractButton> BTN configure(BTN comp, boolean isEnabled, ButtonGroup bg, ActionListener al) {
		comp.setEnabled(isEnabled);
		if (bg!=null) bg.add(comp);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}
	private static <BTN extends AbstractButton> BTN configure(BTN comp, boolean isEnabled, ButtonGroup bg, Consumer<Boolean> setValue) {
		return configure(comp, isEnabled, bg, setValue==null ? null : (ActionListener)e->setValue.accept(comp.isSelected()));
	}

	static JRadioButton createRadioButton(String title, boolean isSelected, boolean isEnabled, ButtonGroup bg, Consumer<Boolean> setValue) {
		return configure(new JRadioButton(title, isSelected), isEnabled, bg, setValue);
	}
	static JButton createButton(String title, boolean enabled, ActionListener al) {
		return configure(new JButton(title), enabled, null, al);
	}
	static JCheckBoxMenuItem createCheckBoxMenuItem(String title, boolean isSelected, boolean isEnabled, Consumer<Boolean> setValue) {
		return configure(new JCheckBoxMenuItem(title, isSelected), isEnabled, null, setValue);
	}
	static JRadioButtonMenuItem createRadioButtonMenuItem(String title, boolean isSelected, boolean isEnabled, Consumer<Boolean> setValue) {
		return configure(new JRadioButtonMenuItem(title, isSelected), isEnabled, null, setValue);
	}
	static JMenuItem createMenuItem(String title, boolean isEnabled, ActionListener al) {
		return configure(new JMenuItem(title), isEnabled, null, al);
	}
	static JCheckBox createCheckBox(String title, boolean isSelected, boolean isEnabled, Consumer<Boolean> setValue) {
		return configure(new JCheckBox(title, isSelected), isEnabled, null, setValue);
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

	private static class ToggleBox extends JLabel {
		private static final long serialVersionUID = 8024197163969547939L;
		
		private Boolean value;
		private final String strTrue;
		private final String strFalse;
		private final String strNull;
		private final Color colorTrue;
		private final Color colorFalse;
		private final Color colorNull;
	
		ToggleBox(Boolean value, String strTrue, String strFalse, String strNull, Color colorTrue, Color colorFalse, Color colorNull) {
			this.strTrue = strTrue;
			this.strFalse = strFalse;
			this.strNull = strNull;
			this.colorTrue = colorTrue;
			this.colorFalse = colorFalse;
			this.colorNull = colorNull;
			setValue(value);
			setBorder(BorderFactory.createEtchedBorder());
		}
	
		void setValue(Boolean value) {
			this.value = value;
			updateBoxText();
		}
	
		void updateBoxText() {
			if (value==null)
				setBoxText(strNull,colorNull);
			else if (value)
				setBoxText(strTrue,colorTrue);
			else
				setBoxText(strFalse,colorFalse);
		}
	
		void setBoxText(String str, Color color) {
			setText(str);
			setForeground(color);
			//setOpaque(color!=null);
			//setBackground(color);
		}
		
		<A> Predicate<A> passThrough(Predicate<A> isOK) {
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
	
	private final static int HEXTABLE_PAGE_SIZE = 0x10000;

	static String toHexTable(byte[] bytes, int page) {
		StringBuilder text = new StringBuilder();
		//String text = "";
		
		if (bytes==null)
			text.append("Can't read content");
		else {
			int startAddress = page<0 ? 0                 :  page   *HEXTABLE_PAGE_SIZE;
			int   endAddress = page<0 ? Integer.MAX_VALUE : (page+1)*HEXTABLE_PAGE_SIZE;
			for (int lineStart=startAddress; lineStart<endAddress && lineStart<bytes.length; lineStart+=16) {
				StringBuilder hex = new StringBuilder();
				StringBuilder plain = new StringBuilder();
				for (int pos=0; pos<16; pos++) {
					if (pos==8)
						hex.append(" |");
					if (lineStart+pos<bytes.length) {
						byte b = bytes[lineStart+pos];
						hex.append(String.format(" %02X", b));
						char ch = (char) b;
						if (ch=='\t' || ch=='\n' || ch=='\r') ch='.';
						plain.append(ch);
					} else {
						hex.append(" --");
						plain.append(' ');
					}
				}
				
				text.append(String.format("%08X: %s  |  %s%n", lineStart, hex, plain));
			}
		}
		
		return text.toString();
	}

	private static class FolderSettingsDialog extends StandardDialog {
		private static final long serialVersionUID = 4253868170530477053L;
		private static final int RMD = GridBagConstraints.REMAINDER;
		private static final Color COLOR_FILE_EXISTS     = Color.GREEN.darker();
		private static final Color COLOR_FILE_NOT_EXISTS = Color.RED;
		
		private JFileChooser folderChooser;
		public FolderSettingsDialog(Window parent, String title) {
			super(parent, title);
			
			folderChooser = new JFileChooser("./");
			folderChooser.setMultiSelectionEnabled(false);
			folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			
			EnumMap<ExternalViewerInfo,ToggleBox              > viewerToggleBoxes = new EnumMap<>(ExternalViewerInfo.class);
			EnumMap<ExternalViewerInfo,ModifiedTextField<File>> viewerFileFields  = new EnumMap<>(ExternalViewerInfo.class);
			EnumMap<ExternalViewerInfo,JButton                > viewerSetButtons  = new EnumMap<>(ExternalViewerInfo.class);
			
			for (ExternalViewerInfo evi : ExternalViewerInfo.values())
				viewerToggleBoxes.put(evi,     createToggleBox(null, 100,5,  "file exists",   "file not exists", "?????", COLOR_FILE_EXISTS, COLOR_FILE_NOT_EXISTS, null));
			ToggleBox tglbxSteamClientFolder = createToggleBox(null, 100,5,"folder exists", "folder not exists", "?????", COLOR_FILE_EXISTS, COLOR_FILE_NOT_EXISTS, null);
			
			for (ExternalViewerInfo evi : ExternalViewerInfo.values())
				viewerFileFields.put(evi,                  createFileField(File::isFile     , viewerToggleBoxes.get(evi), evi.viewerKey                         ));
			ModifiedTextField<File> txtSteamClientFolder = createFileField(File::isDirectory, tglbxSteamClientFolder    , AppSettings.ValueKey.SteamClientFolder);
			
			for (ExternalViewerInfo evi : ExternalViewerInfo.values())
				viewerSetButtons.put(evi,     createButton("...", true, e->selectFile(this,executableFileChooser, evi.viewerKey                         , viewerFileFields.get(evi))));
			JButton btnSetSteamClientFolder = createButton("...", true, e->selectFile(this,folderChooser        , AppSettings.ValueKey.SteamClientFolder, txtSteamClientFolder     ));
			
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
			c.gridy=0;
			for (ExternalViewerInfo evi : ExternalViewerInfo.values()) {
				c.gridwidth=1  ; contentPane.add(createLabel(evi.viewerName+" : ",JLabel.CENTER),c); c.gridy++;
				c.gridwidth=RMD; contentPane.add(createHorizontalLine(),c); c.gridy++;
			}
			c.gridwidth=1;   contentPane.add(createLabel("Steam Client Folder : ",JLabel.CENTER),c); c.gridy++;
			c.gridwidth=RMD; contentPane.add(createHorizontalLine(),c);
			c.gridwidth=1;
			int nextRow = c.gridy+1;
			
			c.weightx = 1;
			c.weighty = 0;
			c.gridx = 1;
			c.gridy=0;
			for (ExternalViewerInfo evi : ExternalViewerInfo.values()) {
				contentPane.add(viewerFileFields.get(evi),c); c.gridy+=2;
			}
			contentPane.add(txtSteamClientFolder,c);
			
			c.weightx = 0;
			c.weighty = 0;
			c.gridx = 2;
			c.gridy=0;
			for (ExternalViewerInfo evi : ExternalViewerInfo.values()) {
				contentPane.add(viewerSetButtons.get(evi),c); c.gridy+=2;
			}
			contentPane.add(btnSetSteamClientFolder,c);
			
			c.weightx = 0;
			c.weighty = 0;
			c.gridx = 3;
			c.gridy=0;
			for (ExternalViewerInfo evi : ExternalViewerInfo.values()) {
				contentPane.add(viewerToggleBoxes.get(evi),c); c.gridy+=2;
			}
			contentPane.add(tglbxSteamClientFolder,c);
			
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
				boolean hasSteamApps = value!=null && new File(value,KnownFolders.STEAMAPPS_SUBPATH).isDirectory();
				
				pathLabel   .setText(value==null ? "<null>" : value.getAbsolutePath());
				statusLabel .setText(exists ? "   folder exists" : "   folder not exists");
				status2Label.setText(exists && !hasSteamApps ? " but has no "+KnownFolders.STEAMAPPS_SUBPATH+" sub folder" : "");
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

	static class LabeledFile {
		final String label;
		final File file;
		LabeledFile(File file) { this(null,file); }
		LabeledFile(String label, File file) {
			if (file==null) throw new IllegalArgumentException();
			this.label = label!=null ? label : String.format("%s\"%s\"", file.isDirectory() ? "Folder " : file.isFile() ? "File " : "", file.getName());
			this.file = file;
		}
		static LabeledFile create(File file) {
			return create(null, file);
		}
		static LabeledFile create(String label, File file) {
			if (file==null) return null;
			return new LabeledFile(label, file);
		}
	}

	static class LabeledUrl {
		final String label;
		final String url;
		LabeledUrl(String url) { this(null,url); }
		LabeledUrl(String label, String url) {
			if (url==null) throw new IllegalArgumentException();
			this.label = label!=null ? label : "";
			this.url = url;
		}
		static LabeledUrl create(String url) {
			return create(null, url);
		}
		static LabeledUrl create(String label, String url) {
			if (url==null) return null;
			return new LabeledUrl(label, url);
		}
	}

	static class FilePromise {
		final String label;
		final Supplier<File> createFile;
		FilePromise(String label, Supplier<File> createFile) {
			if (label     ==null) throw new IllegalArgumentException();
			if (createFile==null) throw new IllegalArgumentException();
			this.label = label;
			this.createFile = createFile;
		}
	}
	
	static class ExternViewableItem {
		
		final LabeledUrl  url;
		final LabeledFile file;
		final FilePromise filePromise;
		final ExternalViewerInfo viewerInfo;
		
		ExternViewableItem(LabeledUrl  url        , ExternalViewerInfo viewerInfo) { this( url, null,        null, viewerInfo); if (url        ==null) throw new IllegalArgumentException(); }
		ExternViewableItem(LabeledFile file       , ExternalViewerInfo viewerInfo) { this(null, file,        null, viewerInfo); if (file       ==null) throw new IllegalArgumentException();}
		ExternViewableItem(FilePromise filePromise, ExternalViewerInfo viewerInfo) { this(null, null, filePromise, viewerInfo); if (filePromise==null) throw new IllegalArgumentException();}
		
		private ExternViewableItem(LabeledUrl url, LabeledFile file, FilePromise filePromise, ExternalViewerInfo viewerInfo) {
			this.url = url;
			this.file = file;
			this.filePromise = filePromise;
			this.viewerInfo = viewerInfo;
			if (viewerInfo==null) throw new IllegalArgumentException();
		}
		
		ExternViewableItem createCopy(ExternalViewerInfo evi) {
			return new ExternViewableItem(url, file, filePromise, evi);
		}
		
		String getViewerName() {
			return viewerInfo.viewerName;
		}
		String getItemLabel() {
			if (url        !=null) return url.label;
			if (file       !=null) return file.label;
			if (filePromise!=null) return filePromise.label; 
			return "";
		}
		boolean areItemAndViewerCompatible() {
			for (ExternalViewerInfo.AddressType addressType:viewerInfo.acceptedAddressTypes)
				switch (addressType) {
				case URL   : if (   url !=null                                                   ) return true; break;
				case Folder: if (   file!=null && file.file.isDirectory()                        ) return true; break;
				case File  : if ( ( file!=null && file.file.isFile     () ) || filePromise!=null ) return true; break;
				}
			return false;
		}
		String getItemPath() {
			for (ExternalViewerInfo.AddressType addressType:viewerInfo.acceptedAddressTypes)
				switch (addressType) {
				case URL   : if (url !=null) return url.url; break;
				case Folder: if (file!=null && file.file.isDirectory()) return file.file.getAbsolutePath(); break;
				case File  : if (file!=null && file.file.isFile     ()) return file.file.getAbsolutePath();
					if (filePromise!=null) {
						File file = filePromise.createFile.get();
						if (file!=null) return file.getAbsolutePath();
					}
					break;
				}
			return null;
		}
	}
	
	enum ExternalViewerInfo {
		TextEditor  ( "Text Editor"    , AppSettings.ValueKey.TextEditor , AddressType.File),
		ImageViewer ( "Image Viewer"   , AppSettings.ValueKey.ImageViewer, AddressType.File),
		Browser     ( "Browser"        , AppSettings.ValueKey.Browser    , AddressType.URL, AddressType.File),
		ZipViewer   ( "Zip File Viewer", AppSettings.ValueKey.ZipViewer  , AddressType.File),
		;
		
		enum AddressType { Folder, File, URL }
		
		private final String viewerName;
		private final AppSettings.ValueKey viewerKey;
		private final EnumSet<AddressType> acceptedAddressTypes;
		
		ExternalViewerInfo(String viewerName, AppSettings.ValueKey viewerKey, AddressType... addressTypes) {
			this.viewerName = viewerName;
			this.viewerKey = viewerKey;
			this.acceptedAddressTypes = EnumSet.copyOf(Arrays.asList(addressTypes));
			if (this.viewerName==null) throw new IllegalArgumentException();
			if (this.viewerKey ==null) throw new IllegalArgumentException();
		}
		boolean accept(AddressType addressType) {
			return acceptedAddressTypes.contains(addressType);
		}
		
		ExternViewableItem createItem(LabeledUrl  url        ) { return url         == null ? null : new ExternViewableItem(url        , this); }
		ExternViewableItem createItem(LabeledFile file       ) { return file        == null ? null : new ExternViewableItem(file       , this); }
		ExternViewableItem createItem(FilePromise filePromise) { return filePromise == null ? null : new ExternViewableItem(filePromise, this); }
		
		File getExecutable(Component parent) {
			File file = settings.getFile(viewerKey);
			if (file!=null && file.isFile()) return file;
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
	
	private static class AbstractComponentContextMenu extends JPopupMenu {
		private static final long serialVersionUID = -7319873585613172787L;

		AbstractComponentContextMenu() {}
		@SuppressWarnings("unused")
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

	static class MainTreeContextMenu extends JPopupMenu {
		private static final long serialVersionUID = -3729823093931172004L;

		interface URLBasedNode {
			LabeledUrl getURL();
		}

		interface FileBasedNode {
			LabeledFile getFile();
		}

//		interface FileCreatingNode {
//			FilePromise getFilePromise();
//		}
		
		interface ExternViewableNode {
			ExternViewableItem getExternViewableItem();
		}
		
		interface FilterOption {}
		interface Filter {
			FilterOption[] getFilterOptions();
			boolean isFilterOptionSet(FilterOption option);
			void setFilterOption(FilterOption option, boolean active, DefaultTreeModel currentTreeModel);
			void clearFilter(DefaultTreeModel treeModel);
		}
		
		interface FilterableNode {
			Filter getFilter();
		}

		private final JTree tree;
		private final Supplier<DefaultTreeModel> getCurrentTreeModel;
		
		private final JMenuItem miCopyPath;
		private final JMenuItem miCopyURL;
		private final JMenuItem miExtViewer;
		private final JMenuItem miSetTitle;
		private final JMenuItem miCollapseChildren;
		private final JMenu     menuFilterChildren;
		private final ExtViewerChooseMenu extViewerChooseMenu;
		
		private TreePath           clickedPath   = null;
		private Object             clickedNode   = null;
		private LabeledUrl         clickedURL    = null;
		private LabeledFile        clickedFile   = null;
		private ExternViewableItem clickedEVI    = null;
		private Filter             clickedFilter = null;

		MainTreeContextMenu(JTree tree, Supplier<DefaultTreeModel> getCurrentTreeModel) {
			if (tree==null) throw new IllegalArgumentException();
			if (getCurrentTreeModel==null) throw new IllegalArgumentException();
			this.tree = tree;
			this.getCurrentTreeModel = getCurrentTreeModel;
			
			extViewerChooseMenu = new ExtViewerChooseMenu();
			
			add(miCopyPath  = createMenuItem("Copy Path to Clipboard" , true, e->ClipboardTools.copyToClipBoard(clickedFile.file.getAbsolutePath())));
			add(miCopyURL   = createMenuItem("Copy URL to Clipboard"  , true, e->ClipboardTools.copyToClipBoard(clickedURL.url)));
			add(miExtViewer = createMenuItem("Open in External Viewer", true, e->openAs(clickedEVI)));
			
			add(extViewerChooseMenu.createMenu());
			
			add(miSetTitle = createMenuItem("Set Title", true, e->{
				DefaultTreeModel treeModel = this.getCurrentTreeModel.get();
				if (treeModel==null) return;
				if (clickedNode instanceof TreeNode) {
					TreeNode treeNode = (TreeNode) clickedNode;
					Integer gameID = TreeNodes.PlayersNGames.gameChangeListeners.getRegisteredGameID(treeNode);
					if (gameID!=null) {
						String currentTitle = Data.knownGameTitles.get(gameID);
						String actionName = currentTitle==null ? "Set" : "Change";
						String newTitle = JOptionPane.showInputDialog(this, actionName+" Title of Game "+gameID, currentTitle);
						if (newTitle!=null) {
							Data.knownGameTitles.put(gameID, newTitle);
							Data.knownGameTitles.writeToFile();
							TreeNodes.PlayersNGames.gameChangeListeners.gameTitleWasChanged(treeModel, gameID);
						}
					}
				}
			}));
			
			addSeparator();
			add(miCollapseChildren = createMenuItem("Collapse Children", true, e->{
				if (clickedPath!=null && clickedNode instanceof TreeNodes.TreeNodeII) {
					TreeNodes.TreeNodeII treeNodeII = (TreeNodes.TreeNodeII) clickedNode;
					Iterable<? extends TreeNode> children = treeNodeII.getChildren();
					if (children!=null) {
						this.tree.expandPath(clickedPath);
						for (TreeNode child:children)
							this.tree.collapsePath(clickedPath.pathByAddingChild(child));
					}
				}
			}));
			add(menuFilterChildren = new JMenu("Filter Children"));
			
			this.tree.addMouseListener(new MouseAdapter() {

				@Override public void mouseClicked(MouseEvent e) {
					if (e.getButton()==MouseEvent.BUTTON3) {
						clickedPath = MainTreeContextMenu.this.tree.getPathForLocation(e.getX(), e.getY());
						if (clickedPath!=null)
							clickedNode = clickedPath.getLastPathComponent();
						prepareMenuItems();
						show(MainTreeContextMenu.this.tree, e.getX(), e.getY());
					}
				}
			});
		}

		private void openAs(ExternViewableItem viewableItem) {
			File viewer = viewableItem.viewerInfo.getExecutable(tree);
			if (viewer==null) return;
			
			String viewerPath = viewer.getAbsolutePath();
			String filePath = viewableItem.getItemPath();
			
			if (viewerPath!=null && filePath!=null)
				try {
					System.out.printf("Execute in Shell: \"%s\" \"%s\"%n", viewerPath, filePath);
					Runtime.getRuntime().exec(new String[] { viewerPath, filePath });
				} catch (IOException ex) {
					System.err.println("Exception occured while opening selected file in an external viewer:");
					System.err.printf ("    external viewer: \"%s\"%n", viewerPath);
					System.err.printf ("    parameter: \"%s\"%n", filePath);
					ex.printStackTrace();
				}
		}

		protected void prepareMenuItems() {
			clickedURL    = clickedNode instanceof URLBasedNode       ? ((URLBasedNode      ) clickedNode).getURL()                : null;
			clickedFile   = clickedNode instanceof FileBasedNode      ? ((FileBasedNode     ) clickedNode).getFile()               : null;
			clickedEVI    = clickedNode instanceof ExternViewableNode ? ((ExternViewableNode) clickedNode).getExternViewableItem() : null;
			clickedFilter = clickedNode instanceof FilterableNode     ? ((FilterableNode    ) clickedNode).getFilter()             : null;
			
			extViewerChooseMenu.prepareMenuItems();
			
			miCollapseChildren.setEnabled(clickedNode!=null);
			miCopyPath .setEnabled(clickedFile!=null);
			miCopyURL  .setEnabled(clickedURL !=null);
			miExtViewer.setEnabled(clickedEVI!=null && clickedEVI.areItemAndViewerCompatible());
			
			String pathLabel  = clickedFile!=null ? (clickedFile.file.isFile() ? "File Path" : clickedFile.file.isDirectory() ? "Folder Path" : "Path") : "Path";
			String urlLabel   = clickedURL!=null ? clickedURL.label+" URL" : "URL";
			String viewerName = clickedEVI==null ? "External Viewer" : clickedEVI.getViewerName();
			String fileLabel  = clickedEVI==null ? ""                : clickedEVI.getItemLabel();
			if (!fileLabel.isEmpty()) fileLabel+=" ";
			
			miCopyPath .setText(String.format("Copy %s to Clipboard", pathLabel));
			miCopyURL  .setText(String.format("Copy %s to Clipboard", urlLabel));
			miExtViewer.setText(String.format("Open %sin %s" , fileLabel, viewerName));
			
			if (clickedNode instanceof TreeNode) {
				TreeNode treeNode = (TreeNode) clickedNode;
				Integer gameID = TreeNodes.PlayersNGames.gameChangeListeners.getRegisteredGameID(treeNode);
				Data.Game game = Data.games.get(gameID);
				miSetTitle.setEnabled(gameID!=null && (game==null || !game.hasATitle()));
				if (gameID!=null) {
					String currentTitle = Data.knownGameTitles.get(gameID);
					String miTitle;
					if (currentTitle==null) miTitle = String.format("Set Title of Game %d", gameID);
					else miTitle = String.format("Change Title of Game %d (\"%s\")%s", gameID, currentTitle, game!=null && game.hasATitle() ? " <fixed by AppManifest>" : "");
					miSetTitle.setText(miTitle);
				} else
					miSetTitle.setText("Set Title of Game");
			}
			
			if (clickedFilter!=null) {
				menuFilterChildren.setEnabled(true);
				menuFilterChildren.removeAll();
				DefaultTreeModel treeModel = this.getCurrentTreeModel.get();
				if (treeModel==null) {
					menuFilterChildren.setEnabled(false);
				} else {
					menuFilterChildren.add(createMenuItem("Clear Filter", true, e->clickedFilter.clearFilter(treeModel)));
					menuFilterChildren.addSeparator();
					FilterOption[] filterOptions = clickedFilter.getFilterOptions();
					//System.out.println("Rebuild FilterMenu:");
					for (FilterOption opt:filterOptions) {
						//System.out.printf("   create CheckBoxMenuItem( isSelected=%s, title=\"%s\" )%n", clickedFilter.isFilterOptionSet(opt), opt);
						menuFilterChildren.add(
								createCheckBoxMenuItem(
										opt.toString(),
										clickedFilter.isFilterOptionSet(opt),
										true,
										b->clickedFilter.setFilterOption(opt,b,treeModel)
								)
						);
					}
				}
				
			} else {
				menuFilterChildren.setEnabled(false);
				menuFilterChildren.removeAll();
			}
		}

		private class ExtViewerChooseMenu {
		
			private JMenu menu;
			private final EnumMap<ExternalViewerInfo,JMenuItem> menuItems;
			private boolean setMenuActive;

			public ExtViewerChooseMenu() {
				menu = null;
				menuItems = new EnumMap<>(ExternalViewerInfo.class);
				setMenuActive = true;
			}

			public JMenu createMenu() {
				menu = new JMenu("Open in ...");
				menuItems.clear();
				for (ExternalViewerInfo evi:ExternalViewerInfo.values())
					menuItems.put(evi, menu.add(createMenuItem(evi.viewerName, true, e->openAs(clickedEVI.createCopy(evi)))));
				return menu;
			}
		
			public void prepareMenuItems() {
				String fileLabel = clickedEVI==null ? "" : clickedEVI.getItemLabel();
				if (!fileLabel.isEmpty()) fileLabel+=" ";
				menu.setText(String.format("Open %sin ..." , fileLabel));
				setMenuActive = false;
				menuItems.forEach((evi,mi)->{
					boolean b = clickedEVI!=null && clickedEVI.createCopy(evi).areItemAndViewerCompatible();
					if (b) setMenuActive = true;
					mi.setEnabled(b);
				});
				menu.setEnabled(setMenuActive);
			}
		
		}
		
	}
	
	private static abstract class FileContentOutput {

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
	
	private static class OutputDummy extends FileContentOutput {
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

	private static class ImageNTextOutput extends FileContentOutput {
		
		private final JSplitPane mainPanel;
		private final ImageOutput imageView;
		private final TextOutput textView;
		
		ImageNTextOutput() {
			imageView = new ImageOutput();
			textView  = new TextOutput ();
			mainPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, imageView.getMainComponent(), textView.getMainComponent());
		}
		@Override Component getMainComponent() { return mainPanel; }
		@Override void showLoadingMsg() { imageView.showLoadingMsg(); textView.showLoadingMsg(); }
		
		void setSource(ImageNTextContentSource source) {
			imageView.setSource(source);
			textView.setText(source.getContentAsText());
			textView.loadScrollPos();
		}
	}

	private static class ImageOutput extends FileContentOutput {
		
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
		@Override void showLoadingMsg() { setImage(TreeNodes.createImageOfMessage("loading image ...",200,25,Color.RED)); }
		
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

	private static class HexTableOutput extends TextOutput {
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
			nextPageBtn1.setEnabled(bytes!=null && (page+1)*HEXTABLE_PAGE_SIZE<bytes.length);
			nextPageBtn2.setEnabled(bytes!=null && (page+1)*HEXTABLE_PAGE_SIZE<bytes.length);
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
				showMessageFromThread("HexTableOutput.setPage result task started  (page:%d, chars:%d)", page, hexTable.length());
				setText(hexTable);
				showMessageFromThread("HexTableOutput.setPage setText finished     (page:%d, chars:%d)", page, hexTable.length());
				setScrollPos(0);
				loadScrollPos();
				enableButtons();
				showMessageFromThread("HexTableOutput.setPage result task finished (page:%d, chars:%d)", page, hexTable.length());
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
				showMessageFromThread("HexTableFormatter.doInBackground started  (page:%d, bytes:%d)", page, bytes.length);
				String hexTable = toHexTable(bytes, page);
				showMessageFromThread("HexTableFormatter.doInBackground finished (page:%d, chars:%d)%s", page, hexTable.length(), isObsolete?" isObsolete":"");
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
			
			
		}

	}

	private static class TextOutput extends FileContentOutput {
		
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

	private static class TreeOutput extends FileContentOutput {
		private final JTree view;
		private final JScrollPane scrollPane;
		private TreeRoot treeRoot;
		private DefaultTreeModel currentTreeModel;

		TreeOutput() {
			treeRoot = null;
			currentTreeModel = null;
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
						if (treeRoot==null || currentTreeModel==null) return;
						treeRoot.showContextMenu(view, currentTreeModel, e.getX(), e.getY(), path, path==null ? null : path.getLastPathComponent());
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
			if (this.treeRoot==null) {
				view.setModel(currentTreeModel = null);
			} else {
				if (this.treeRoot.beforeViewAction!=null)
					this.treeRoot.beforeViewAction.run();
				view.setModel(currentTreeModel = new DefaultTreeModel(this.treeRoot.node));
				view.setRootVisible(this.treeRoot.isRootVisible);
				if (this.treeRoot.expandAllRows) {
					showMessageFromThread("ParsedTreeOutput.setRoot expand full tree");
					for (int i=0; i<view.getRowCount(); i++)
						view.expandRow(i);
				}
			}
			showMessageFromThread("ParsedTreeOutput.setRoot finished");
		}
		@Override void showLoadingMsg() {
			setRoot(TreeNodes.SimpleLeafNode.createSingleTextLineTree("load content ..."));
		}
	}
	
	private static class MultiOutput extends FileContentOutput {
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

	private static class CombinedOutput extends MultiOutput {
		
		private final BaseTreeNode.ContentType type;
		private final Component mainComp;
		private ContentLoadWorker runningContentLoadWorker;
		
		private final HexTableOutput hexView;
		private final TextOutput plainText;
		private final TreeOutput dataTree;
		
		CombinedOutput(BaseTreeNode.ContentType type) {
			this.type = type;
			
			switch (type) {
			case Bytes:
				hexView    = new HexTableOutput();
				plainText  = null;
				dataTree   = null;
				mainComp   = hexView.getMainComponent();
				break;
			
			case PlainText:
				hexView    = null;
				plainText  = new TextOutput();
				dataTree   = null;
				mainComp   = plainText.getMainComponent();
				break;
			
			case DataTree:
				hexView    = null;
				plainText  = null;
				dataTree   = new TreeOutput();
				mainComp   = dataTree.getMainComponent();
				break;
			
			case ByteBasedText:
				add("Hex Table" , hexView   = new HexTableOutput());
				add("Plain Text", plainText = new TextOutput    ());
				dataTree = null;
				setActiveTab(1);
				mainComp  = super.getMainComponent();
				break;
				
			case ParsedByteBasedText:
				add("Hex Table" , hexView   = new HexTableOutput());
				add("Plain Text", plainText = new TextOutput    ());
				add("Data Tree" , dataTree  = new TreeOutput    ());
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

		void setSource(ByteContentSource source) {
			if (type!=BaseTreeNode.ContentType.Bytes || hexView==null || plainText!=null || dataTree!=null) throw new IllegalStateException();
			setOutput(source, ContentLoadWorker::new);
		}

		void setSource(TextContentSource source) {
			if (type!=BaseTreeNode.ContentType.PlainText || hexView!=null || plainText==null || dataTree!=null) throw new IllegalStateException();
			setOutput(source, ContentLoadWorker::new);
		}

		void setSource(TreeContentSource source) {
			if (type!=BaseTreeNode.ContentType.DataTree || hexView!=null || plainText!=null || dataTree==null) throw new IllegalStateException();
			setOutput(source, ContentLoadWorker::new);
		}
		
		void setSource(ByteBasedTextContentSource source) {
			if (type!=BaseTreeNode.ContentType.ByteBasedText || hexView==null || plainText==null || dataTree!=null) throw new IllegalStateException();
			setOutput(source, ContentLoadWorker::new);
			setActiveTab(1);
		}

		void setSource(ParsedByteBasedTextContentSource source) {
			if (type!=BaseTreeNode.ContentType.ParsedByteBasedText || hexView==null || plainText==null || dataTree==null) throw new IllegalStateException();
			setOutput(source, ContentLoadWorker::new);
			setActiveTab(2);
		}

		private <A> void setOutput(A source, Function<A,ContentLoadWorker> createWorker) {
			
			if (runningContentLoadWorker!=null && !runningContentLoadWorker.isDone() && !runningContentLoadWorker.isCancelled()) {
				runningContentLoadWorker.setObsolete(true);
				runningContentLoadWorker.cancel(true);
			}
			
			if (hexView  !=null) hexView  .showLoadingMsg();
			if (plainText!=null) plainText.showLoadingMsg();
			if (dataTree !=null) dataTree .showLoadingMsg();
			
			runningContentLoadWorker = createWorker.apply(source);
			runningContentLoadWorker.execute();
		}
		
		private class ContentLoadWorker extends SwingWorker<List<PostponedTask>,PostponedTask> {
			
			private final ByteContentSource bytesSource;
			private final TextContentSource  textSource;
			private final TreeContentSource  treeSource;
			private boolean isObsolete;
		
			ContentLoadWorker(               ByteContentSource source) { this(source,  null,  null); }
			ContentLoadWorker(               TextContentSource source) { this(  null,source,  null); }
			ContentLoadWorker(               TreeContentSource source) { this(  null,  null,source); }
			ContentLoadWorker(      ByteBasedTextContentSource source) { this(source,source,  null); }
			ContentLoadWorker(ParsedByteBasedTextContentSource source) { this(source,source,source); }
			
			private ContentLoadWorker(ByteContentSource bytesSource, TextContentSource textSource, TreeContentSource treeSource) {
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
				if (bytes==null) bytes = new byte[0];
				showMessageFromThread("ContentLoadWorker.setHexTable started (bytes:%d)", bytes.length);
				hexView.setHexTableOutput(bytes);
				showMessageFromThread("ContentLoadWorker.setHexTable finished");
			}
			private void setPlainText(String text) {
				if (text==null) text="";
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
				if ( textSource!=null) publish(setPlainText  = new PostponedTask("setPlainText ",()->setPlainText (text    )));  if (isObsolete) return null;
				if ( treeSource!=null) publish(setParsedTree = new PostponedTask("setParsedTree",()->setParsedTree(treeNode)));  if (isObsolete) return null;
				if (bytesSource!=null) publish(setHexView    = new PostponedTask("setHexTable  ",()->setHexTable  (bytes   )));  if (isObsolete) return null;
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
	
	interface ByteContentSource {
		byte[] getContentAsBytes();
	}
	interface TextContentSource {
		String getContentAsText();
	}
	interface TreeContentSource {
		TreeRoot getContentAsTree();
	}
	interface ImageContentSource {
		BufferedImage getContentAsImage();
	}
	interface ImageNTextContentSource extends ImageContentSource, TextContentSource {}
	
	interface ByteBasedTextContentSource extends ByteContentSource, TextContentSource {}
	interface ParsedByteBasedTextContentSource extends ByteBasedTextContentSource, TreeContentSource {}
	
	interface ByteFileSource extends ByteContentSource, FileBasedSource {}
	interface TextFileSource extends TextContentSource, FileBasedSource {}
	interface ByteBasedTextFileSource extends ByteBasedTextContentSource, FileBasedSource {}
	interface ParsedByteBasedTextFileSource extends ParsedByteBasedTextContentSource, FileBasedSource {}
	
	interface TreeContextMenuHandler {
		void showContextMenu(JTree invoker, DefaultTreeModel currentTreeModel, int x, int y, TreePath clickedTreePath, Object clickedTreeNode);
	}
	
	static abstract class AbstractTreeContextMenu extends JPopupMenu implements TreeContextMenuHandler {
		private static final long serialVersionUID = -7162801786069506030L;
	}
	
	static class TreeRoot implements TreeContextMenuHandler {
		final TreeNode node;
		final boolean isRootVisible;
		final boolean expandAllRows;
		final TreeContextMenuHandler tcmh;
		final Runnable beforeViewAction;
		
		TreeRoot(TreeNode node, boolean isRootVisible, boolean expandAllRows) {
			this(node, isRootVisible, expandAllRows, null, null);
		}
		TreeRoot(TreeNode node, boolean isRootVisible, boolean expandAllRows, TreeContextMenuHandler tcmh) {
			this(node, isRootVisible, expandAllRows, tcmh, null);
		}
		TreeRoot(TreeNode node, boolean isRootVisible, boolean expandAllRows, TreeContextMenuHandler tcmh, Runnable beforeViewAction) {
			this.node = node;
			this.isRootVisible = isRootVisible;
			this.expandAllRows = expandAllRows;
			this.tcmh = tcmh;
			this.beforeViewAction = beforeViewAction;
		}
		@Override public void showContextMenu(JTree invoker, DefaultTreeModel currentTreeModel, int x, int y, TreePath clickedTreePath, Object clickedTreeNode) {
			if (tcmh!=null)
				tcmh.showContextMenu(invoker, currentTreeModel, x, y, clickedTreePath, clickedTreeNode);
		}
	}
	
	private final static class BaseTreeNodeRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = -7291286788678796516L;
//		private Icon icon;
		
		BaseTreeNodeRenderer() {
			setLeafIcon(TreeIcons.DefaultLeafIcon.getIcon());
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
				if (!isSelected) {
					Color textColor = baseTreeNode.getTextColor();
					if (textColor == null) textColor = tree.getForeground();
					setForeground(textColor);
				}
				
			}
//			setIcon(icon);
			return component;
		}
	}

	static abstract class BaseTreeNode<ParentNodeType extends TreeNode, ChildNodeType extends TreeNode> implements TreeNodes.TreeNodeII {
		
		enum ContentType { PlainText, Bytes, ByteBasedText, ParsedByteBasedText, Image, DataTree, ImageNText }
		
		protected final ParentNodeType parent;
		private   String title;
		private   final boolean allowsChildren;
		private   final boolean isLeaf;
		protected Vector<? extends ChildNodeType> children;
		private   final Icon icon;

		protected BaseTreeNode(ParentNodeType parent, String title, boolean allowsChildren, boolean isLeaf) {
			this(parent, title, allowsChildren, isLeaf, (Icon)null);
		}
		protected BaseTreeNode(ParentNodeType parent, String title, boolean allowsChildren, boolean isLeaf, TreeNodes.TreeIcons icon) {
			this(parent, title, allowsChildren, isLeaf, icon==null ? null : icon.getIcon());
		}
		protected BaseTreeNode(ParentNodeType parent, String title, boolean allowsChildren, boolean isLeaf, Icon icon) {
			this.parent = parent;
			this.title = title;
			this.allowsChildren = allowsChildren;
			this.isLeaf = isLeaf;
			this.icon = icon;
			children = null;
		}

		ContentType getContentType() { return null; }
		Icon getIcon() { return icon; }
		Color getTextColor() { return null; }

		protected abstract Vector<? extends ChildNodeType> createChildren();

		public void setTitle(String title) { this.title = title; }
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
		@Override public Iterable<? extends TreeNode> getChildren() {
			checkChildren("getChildren()");
			return children;
		}

		protected void checkChildren(String methodeLabel) {
//			if (!allowsChildren) throw new IllegalStateException(String.format("TreeNode.%s from \"not allows children\" TreeNode", methodeLabel));
			if (!allowsChildren) children=new Vector<>();
			if (children==null) {
				children=createChildren();
				doAfterCreateChildren();
			}
		}
		protected void doAfterCreateChildren() {}
		
		public void rebuildChildren(DefaultTreeModel currentTreeModel) {
			if (currentTreeModel==null) throw new IllegalArgumentException("rebuildChildren( currentTreeModel == null ) is not allowed");
			children = null;
			checkChildren("rebuildChildren()");
			currentTreeModel.nodeStructureChanged(this);
		}
	}
}
