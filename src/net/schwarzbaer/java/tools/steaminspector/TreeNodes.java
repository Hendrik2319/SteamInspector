package net.schwarzbaer.java.tools.steaminspector;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.Vector;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import net.schwarzbaer.gui.IconSource;
import net.schwarzbaer.gui.IconSource.CachedIcons;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Array;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Object;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.TraverseException;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.Value;
import net.schwarzbaer.java.lib.jsonparser.JSON_Parser;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.AbstractTreeContextMenu;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.AppSettings.ValueKey;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BaseTreeNode;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ByteFileSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ExtendedTextFileSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ExternalViewerInfo;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ImageContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ImageNTextContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ParsedTextFileSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TextContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TreeContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TreeRoot;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.AppManifest;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.Game;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.GameImages;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.Player;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.Player.AchievementProgress;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.Player.FriendList;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.Player.FriendList.Friend;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.Player.GameStateInfo;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.Player.GameStateInfo.Achievements;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.ScreenShot;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.ScreenShotLists;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.FileSystem.FolderNode;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.FileSystem.ImageFile;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.PlayersNGames.GameChangeListeners.GameChangeListener;
import net.schwarzbaer.java.tools.steaminspector.VDFParser.VDFTreeNode;
import net.schwarzbaer.system.ClipboardTools;

class TreeNodes {
	
	private static class NV extends JSON_Data.NamedValueExtra.Dummy{}
	private static class V  extends JSON_Data.ValueExtra.Dummy{}

	enum TreeIcons { GeneralFile, TextFile, ImageFile, AudioFile, VDFFile, AppManifest, JSONFile, Badge, Achievement, Folder, RootFolder_Simple, RootFolder }
	static CachedIcons<TreeIcons> TreeIconsIS;
	
	enum JsonTreeIcons { Object, Array, String, Number, Boolean }
	static CachedIcons<JsonTreeIcons> JsonTreeIconsIS;
	
	private static final String KNOWN_GAME_TITLES_INI = "SteamInspector.KnownGameTitles.ini";
	private static final File FOLDER_TEST_FILES                  = new File("./test");
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
			ValueKey key = SteamInspector.AppSettings.ValueKey.SteamLibraryFolders;
			return SteamInspector.settings.getFiles(key);
		}

		static File getSteamClientFolder() {
			ValueKey folderKey = SteamInspector.AppSettings.ValueKey.SteamClientFolder;
			return SteamInspector.settings.getFile(folderKey, null);
		}
	}
	
	static void loadIcons() {
		TreeIconsIS     = IconSource.createCachedIcons(16, 16, "/images/TreeIcons.png"    , TreeIcons.values());
		JsonTreeIconsIS = IconSource.createCachedIcons(16, 16, "/images/JsonTreeIcons.png", JsonTreeIcons.values());
	}
	
	private static boolean fileNameEndsWith(File file, String... suffixes) {
		String name = file.getName().toLowerCase();
		for (String suffix:suffixes)
			if (name.endsWith(suffix))
				return true;
		return false;
	}

	private static boolean isImageFile(File file) {
		return file.isFile() && fileNameEndsWith(file,".jpg",".jpeg",".png",".bmp",".ico",".tga");
	}
	
	private static String getTimeStr(long millis) {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("CET"), Locale.GERMANY);
		cal.setTimeInMillis(millis);
		return String.format(Locale.ENGLISH, "%1$tA, %1$te. %1$tb %1$tY, %1$tT [%1$tZ:%1$tz]", cal);
	}
	
	private static String getSizeStr(File file) {
		long length = file==null ? 0 : file.length();
		return getSizeStr(length);
	}

	static String getSizeStr(long length) {
		if (length               <1100) return String.format(Locale.ENGLISH, "%d B"    , length);
		if (length/1024          <1100) return String.format(Locale.ENGLISH, "%1.1f kB", length/1024f);
		if (length/1024/1024     <1100) return String.format(Locale.ENGLISH, "%1.1f MB", length/1024f/1024f);
		if (length/1024/1024/1024<1100) return String.format(Locale.ENGLISH, "%1.1f GB", length/1024f/1024f/1024f);
		return "["+length+"]";
	}
	
	private static File[] getFilesAndFolders(File folder) {
		File[] files = folder.listFiles((FileFilter) file -> {
			String name = file.getName();
			if (file.isDirectory())
				return !name.equals(".") && !name.equals("..");
			return file.isFile();
		});
		return files;
	}
	
	private static Integer parseNumber(String name) {
		try {
			int n = Integer.parseInt(name);
			if (name.equals(Integer.toString(n))) return n;
		}
		catch (NumberFormatException e) {}
		return null;
	}
	
	private static Long parseLongNumber(String name) {
		try {
			long n = Long.parseLong(name);
			if (name.equals(Long.toString(n))) return n;
		}
		catch (NumberFormatException e) {}
		return null;
	}

	static BufferedImage createImageOfMessage(String message, int width, int height, Color textColor) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		JTextArea label = new JTextArea(message);
		label.setBorder(BorderFactory.createDashedBorder(Color.BLACK));
		label.setLineWrap(true);
		label.setWrapStyleWord(true);
		label.setSize(width, height);
		label.setForeground(textColor);
		label.paint(image.getGraphics());
		return image;
	}

	private static BufferedImage readImageFromURL(String url, String itemLabel) {
		if (url==null) return createImageOfMessage("No URL.",200,25,Color.RED);
		try {
			return readImageFromURL(new URL(url));
		} catch (MalformedURLException e) {
			System.err.printf("MalformedURLException while reading %s:%n    URL: \"%s\"%n    Exception: %s%n", itemLabel, url, e.getMessage());
		} catch (IOException e) {
			System.err.printf("IOException while reading %s:%n    URL: \"%s\"%n    Exception: %s%n", itemLabel, url, e.getMessage());
		}
		return createImageOfMessage(String.format("Can't read %s.",itemLabel),200,25,Color.RED);
	}

	private static BufferedImage readImageFromURL(URL url) throws IOException {
		URLConnection conn = url.openConnection();
		conn.setRequestProperty("User-Agent", "KlaUS");
		conn.setDoInput(true);
		conn.connect();
		return ImageIO.read(conn.getInputStream());
	}

	private static class HashMatrix<KeyType1,KeyType2,ValueType> {
		
		private final HashMap<KeyType1,HashMap<KeyType2,ValueType>> matrix;
		private final HashSet<KeyType1> keySet1;
		private final HashSet<KeyType2> keySet2;
		
		HashMatrix() {
			matrix = new HashMap<>();
			keySet1 = new HashSet<>();
			keySet2 = new HashSet<>();
		}
		
		void put(KeyType1 key1, KeyType2 key2, ValueType value) {
			HashMap<KeyType2, ValueType> map = matrix.get(key1);
			if (map==null) matrix.put(key1, map = new HashMap<>());
			map.put(key2, value);
			keySet1.add(key1);
			keySet2.add(key2);
		}
		
		HashMap<KeyType2, ValueType> getMapCopy(KeyType1 key1) {
			HashMap<KeyType2, ValueType> map = matrix.get(key1);
			if (map==null) return null;
			return new HashMap<>(map);
		}
		
		Collection<ValueType> getCollection(KeyType1 key1) {
			HashMap<KeyType2, ValueType> map = matrix.get(key1);
			return map.values();
		}
		
		ValueType get(KeyType1 key1, KeyType2 key2) {
			HashMap<KeyType2, ValueType> map = matrix.get(key1);
			if (map==null) return null;
			return map.get(key2);
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
	
	private static class FileNameNExt implements Comparable<FileNameNExt>{
		final String name;
		final String extension;
		private FileNameNExt(String name, String extension) {
			this.name = name;
			this.extension = extension;
		}
		static FileNameNExt create(String filename) {
			int pos = filename.lastIndexOf('.');
			if (pos<0) return new FileNameNExt(filename, null);
			return new FileNameNExt(filename.substring(0, pos), filename.substring(pos+1));
		}
		@Override
		public int compareTo(FileNameNExt other) {
			int comparedNames = this.name.compareToIgnoreCase(other.name);
			if (comparedNames!=0) return comparedNames;
			if (this .extension==null && other.extension==null) return 0;
			if (this .extension==null) return -1;
			if (other.extension==null) return +1;
			return this.extension.compareToIgnoreCase(other.extension);
		}
	}

	interface URLBasedNode {
		String getURL();
	}

	interface FileBasedNode {
		LabeledFile getFile();
	}

	interface FileCreatingNode {
		FilePromise getFilePromise();
	}
	
	interface ExternViewableNode {
		ExternalViewerInfo getExternalViewerInfo();
	}
	
	interface TreeNodeII extends TreeNode  {
		Iterable<? extends TreeNode> getChildren();
	}
	
	interface DataTreeNode extends TreeNodeII {
		default String getFullInfo() {
			String str = "";
			str += !hasName()  ? String.format("Name : none%n")  : String.format("Name : \"%s\"%n", getName());
			str += !hasValue() ? String.format("Value : none%n") : String.format("Value : %s%n", getValueStr());
			str += String.format("Path : %s%n", getPath());
			str += String.format("AccessCall : %s%n", getAccessCall());
			return str;
		}
		String getName();
		String getValueStr();
		String getPath();
		String getAccessCall();
		boolean hasName();
		boolean hasValue();
		
	}

	private static class DataTreeNodeContextMenu extends AbstractTreeContextMenu {
		private static final long serialVersionUID = 7620430144231207201L;
		
		private final JMenuItem miName;
		private final JMenuItem miValue;
		private final JMenuItem miPath;
		private final JMenuItem miAccessCall;
		private final JMenuItem miFullInfo;
		private final JMenuItem miCollapseChildren;
		private DataTreeNode clickedTreeNode;
		private TreePath clickedTreePath;
		private JTree tree;


		
		DataTreeNodeContextMenu() {
			clickedTreeNode = null;
			clickedTreePath = null;
			tree = null;
			add(miName       = SteamInspector.createMenuItem("Copy Name"       , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getName())));
			add(miValue      = SteamInspector.createMenuItem("Copy Value"      , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getValueStr())));
			add(miPath       = SteamInspector.createMenuItem("Copy Path"       , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getPath())));
			add(miAccessCall = SteamInspector.createMenuItem("Copy Access Call", true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getAccessCall())));
			add(miFullInfo   = SteamInspector.createMenuItem("Copy Full Info"  , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getFullInfo())));
			addSeparator();
			add(SteamInspector.createMenuItem("Expand Full Tree", true, e->{
				if (tree!=null)
					for (int i=0; i<tree.getRowCount(); i++)
						tree.expandRow(i);
			}));
			add(miCollapseChildren = SteamInspector.createMenuItem("Collapse Children", true, e->{
				if (tree!=null && clickedTreeNode!=null && clickedTreePath!=null) {
					Iterable<? extends TreeNode> children = clickedTreeNode.getChildren();
					if (children!=null) {
						tree.expandPath(clickedTreePath);
						for (TreeNode child:children)
							tree.collapsePath(clickedTreePath.pathByAddingChild(child));
					}
				}
			}));
		}
		
		@Override
		public void showContextMenu(JTree tree, int x, int y, TreePath clickedTreePath, Object clickedTreeNode) {
			this.tree = tree;
			this.clickedTreePath = clickedTreePath;
			this.clickedTreeNode = null;
			if (clickedTreeNode instanceof DataTreeNode)
				this.clickedTreeNode = (DataTreeNode) clickedTreeNode;
			
			miName      .setEnabled(this.clickedTreeNode!=null && this.clickedTreeNode.hasName());
			miValue     .setEnabled(this.clickedTreeNode!=null && this.clickedTreeNode.hasValue());
			miPath      .setEnabled(this.clickedTreeNode!=null);
			miAccessCall.setEnabled(this.clickedTreeNode!=null);
			miFullInfo  .setEnabled(this.clickedTreeNode!=null);
			miCollapseChildren.setEnabled(this.clickedTreeNode!=null);
			
			show(tree, x, y);
		}
	}

	private static class GroupingNode<ValueType> extends BaseTreeNode<TreeNode,TreeNode> implements FileBasedNode, FileCreatingNode, ExternViewableNode {
		
		private final Collection<ValueType> values;
		private final Comparator<ValueType> sortOrder;
		private final NodeCreator1<ValueType> createChildNode;
		private ExternalViewerInfo externalViewerInfo;
		private LabeledFile file;
		private FilePromise getFile;
		
		static <IT,VT> GroupingNode<Map.Entry<IT,VT>> create(TreeNode parent, String title, HashMap<IT,VT> values, Comparator<VT> sortOrder, NodeCreator1<VT> createChildNode) {
			return create(parent, title, values, sortOrder, createChildNode, null);
		}
		static <IT,VT> GroupingNode<Map.Entry<IT,VT>> create(TreeNode parent, String title, HashMap<IT,VT> values, Comparator<VT> sortOrder, NodeCreator1<VT> createChildNode, Icon icon) {
			return new GroupingNode<Map.Entry<IT,VT>>(parent, title, values.entrySet(), Comparator.comparing(Map.Entry<IT, VT>::getValue,sortOrder), (p,e)->createChildNode.create(p,e.getValue()), icon);
		}
		static <IT,VT> GroupingNode<Map.Entry<IT,VT>> create(TreeNode parent, String title, HashMap<IT,VT> values, Comparator<Map.Entry<IT,VT>> sortOrder, NodeCreator2<IT,VT> createChildNode) {
			return create(parent, title, values, sortOrder, createChildNode, null);
		}
		static <IT,VT> GroupingNode<Map.Entry<IT,VT>> create(TreeNode parent, String title, HashMap<IT,VT> values, Comparator<Map.Entry<IT,VT>> sortOrder, NodeCreator2<IT,VT> createChildNode, Icon icon) {
			return new GroupingNode<Map.Entry<IT,VT>>(parent, title, values.entrySet(), sortOrder, (p,e)->createChildNode.create(p,e.getKey(),e.getValue()), icon);
		}
		static <VT> GroupingNode<VT> create(TreeNode parent, String title, Collection<VT> values, Comparator<VT> sortOrder, NodeCreator1<VT> createChildNode) {
			return new GroupingNode<>(parent, title, values, sortOrder, createChildNode, null);
		}
		static <VT> GroupingNode<VT> create(TreeNode parent, String title, Collection<VT> values, Comparator<VT> sortOrder, NodeCreator1<VT> createChildNode, Icon icon) {
			return new GroupingNode<>(parent, title, values, sortOrder, createChildNode, icon);
		}
		GroupingNode(TreeNode parent, String title, Collection<ValueType> values, Comparator<ValueType> sortOrder, NodeCreator1<ValueType> createChildNode, Icon icon) {
			super(parent, title, true, false, icon);
			this.values = values;
			this.sortOrder = sortOrder;
			this.createChildNode = createChildNode;
			file = null;
			getFile = null;
			externalViewerInfo = null;
		}
	
		public void setFileSource(File file, ExternalViewerInfo externalViewerInfo) {
			setFileSource(new LabeledFile(file), externalViewerInfo);
		}
		public void setFileSource(LabeledFile file, ExternalViewerInfo externalViewerInfo) {
			this.file = file;
			this.externalViewerInfo = externalViewerInfo;
			if (getFile!=null) throw new IllegalStateException();
			if (file==null) throw new IllegalArgumentException();
		}
		public void setFileSource(FilePromise getFile, ExternalViewerInfo externalViewerInfo) {
			this.getFile = getFile;
			this.externalViewerInfo = externalViewerInfo;
			if (getFile==null) throw new IllegalArgumentException();
			if (file!=null) throw new IllegalStateException();
		}
		
		@Override public LabeledFile getFile() { return file; }
		@Override public FilePromise getFilePromise() { return getFile; }
		@Override public ExternalViewerInfo getExternalViewerInfo() {
			return externalViewerInfo;
		}
	
		@Override
		protected Vector<? extends TreeNode> createChildren() {
			Vector<ValueType> vector = new Vector<>(values);
			if (sortOrder!=null) vector.sort(sortOrder);
			Vector<TreeNode> children = new Vector<>();
			vector.forEach(value->{
				TreeNode treeNode = createChildNode.create(this,value);
				if (treeNode!=null) children.add(treeNode);
			});
			return children;
		}

		interface NodeCreator1<ValueType> {
			TreeNode create(TreeNode parent, ValueType value);
		}
		
		interface NodeCreator2<IDType, ValueType> {
			TreeNode create(TreeNode parent, IDType id, ValueType value);
		}
	}
	
	static class ImageUrlNode extends SimpleTextNode implements ImageContentSource, ExternViewableNode, URLBasedNode {
		
		private final String url;
		
		ImageUrlNode(TreeNode parent, String label, String  url) {
			super(parent, TreeIconsIS.getCachedIcon(TreeIcons.ImageFile), "%s: "+"\"%s\"", label, url);
			this.url = url;
		}

		@Override public String getURL() { return url; }
		@Override public ExternalViewerInfo getExternalViewerInfo() { return ExternalViewerInfo.Browser; }

		@Override ContentType getContentType() { return ContentType.Image; }
		@Override public BufferedImage getContentAsImage() { return readImageFromURL(url,"image"); }
	}
	
	static class PrimitiveValueNode extends SimpleTextNode {
		PrimitiveValueNode(TreeNode parent, String label, boolean value) { super(parent, "%s: "+  "%s"  , label, value); }
		PrimitiveValueNode(TreeNode parent, String label, int     value) { super(parent, "%s: "+  "%d"  , label, value); }
		PrimitiveValueNode(TreeNode parent, String label, long    value) { super(parent, "%s: "+  "%d"  , label, value); }
		PrimitiveValueNode(TreeNode parent, String label, double  value) { super(parent, "%s: "+"%1.4f" , label, value); }
		PrimitiveValueNode(TreeNode parent, String label, String  value) { super(parent, "%s: "+"\"%s\"", label, value); }
	}
	
	static class SimpleTextNode extends BaseTreeNode<TreeNode,SimpleTextNode> {

		SimpleTextNode(TreeNode parent,                           String format, Object...args) { this(parent, null, Locale.ENGLISH, format, args); }
		SimpleTextNode(TreeNode parent,            Locale locale, String format, Object...args) { this(parent, null, locale, format, args); }
		SimpleTextNode(TreeNode parent, Icon icon,                String format, Object...args) { this(parent, icon, Locale.ENGLISH, format, args); }
		SimpleTextNode(TreeNode parent, Icon icon, Locale locale, String format, Object...args) { super(parent, String.format(locale, format, args), false, true, icon); }
		@Override protected Vector<? extends SimpleTextNode> createChildren() { return new Vector<>(); }
		
		static TreeRoot createSingleTextLineTree(String format, Object...args) {
			return new TreeRoot( new SimpleTextNode(null, format, args), true, true );
		}
	}

	private static class TextContentNode extends BaseTreeNode<TreeNode,TreeNode> implements TextContentSource {
		
		final String content;

		@SuppressWarnings("unused")
		TextContentNode(TreeNode parent, String title, String content) {
			this(parent, title, content, (Icon)null);
		}
		TextContentNode(TreeNode parent, String title, String content, TreeIcons icon) {
			super(parent, title, false, true, icon);
			this.content = content;
		}
		TextContentNode(TreeNode parent, String title, String content, Icon icon) {
			super(parent, title, false, true, icon);
			this.content = content;
		}
		@Override ContentType getContentType() { return ContentType.PlainText; }
		@Override public String getContentAsText() { return content; }
		@Override protected Vector<? extends TreeNode> createChildren() { return new Vector<>(); }
	}

	private static class RawVDFDataNode extends BaseTreeNode<TreeNode,TreeNode> implements TreeContentSource {
		
		private final VDFTreeNode rawData;

		RawVDFDataNode(TreeNode parent, String title, VDFTreeNode rawData) {
			super(parent, title, false, true);
			this.rawData = rawData;
		}
		@Override ContentType getContentType() { return ContentType.DataTree; }
		@Override public TreeRoot getContentAsTree() { return new TreeRoot(rawData, true, true, new DataTreeNodeContextMenu()); }
		@Override protected Vector<? extends TreeNode> createChildren() { return null; }
	}

	@SuppressWarnings("unused")
	private static class RawJsonDataNode extends BaseTreeNode<TreeNode,TreeNode> implements TreeContentSource, FileBasedNode, ExternViewableNode {
	
		private final File file;
		private final JSON_Data.Value <NV,V> rawValue;
	
		RawJsonDataNode(TreeNode parent, String title, JSON_Data.Value<NV,V> rawValue) {
			this(parent, title, rawValue, null, null);
		}
		RawJsonDataNode(TreeNode parent, String title, JSON_Data.Value<NV,V> rawValue, Icon icon) {
			this(parent, title, rawValue, null, icon);
		}
		RawJsonDataNode(TreeNode parent, String title, JSON_Data.Value<NV,V> rawValue, File file) {
			this(parent, title, rawValue, file, null);
		}
		RawJsonDataNode(TreeNode parent, String title, JSON_Data.Value<NV,V> rawValue, File file, Icon icon) {
			super(parent, title, false, true, icon!=null ? icon : TreeIconsIS.getCachedIcon(TreeIcons.JSONFile));
			this.file = file;
			this.rawValue = rawValue;
		}
		
		@Override protected Vector<? extends TreeNode> createChildren() { return null; }
		@Override ContentType getContentType() { return ContentType.DataTree; }
		
		@Override public TreeRoot getContentAsTree() {
			if (rawValue!=null) return FileSystem.JSON_File.JSON_TreeNode.create(rawValue, false);
			return SimpleTextNode.createSingleTextLineTree("RawJsonDataNode(<null>)");
		}
		
		@Override
		public LabeledFile getFile() {
			return file==null ? null : new LabeledFile(file);
		}
		@Override
		public ExternalViewerInfo getExternalViewerInfo() {
			return file==null ? null : ExternalViewerInfo.TextEditor;
		}
	}

	private static class ParseException extends Exception {
		private static final long serialVersionUID = -7150324499542307039L;
		ParseException(String format, Object...args) {
			super(String.format(Locale.ENGLISH, format, args));
		}
	}

	@SuppressWarnings("unused")
	private static class DevHelper {
		
		static class ExtHashMap<TypeType> extends HashMap<String,HashSet<TypeType>> {
			private static final long serialVersionUID = -3042424737957471534L;
			ExtHashMap<TypeType> add(String name, TypeType type) {
				HashSet<TypeType> hashSet = get(name);
				if (hashSet==null) put(name,hashSet = new HashSet<>());
				hashSet.add(type);
				return this;
			}
			boolean contains(String name, TypeType type) {
				HashSet<TypeType> hashSet = get(name);
				return hashSet!=null && hashSet.contains(type);
			}
		}
		static class KnownJsonValues extends ExtHashMap<JSON_Data.Value.Type> {
			private static final long serialVersionUID = 875837641187739890L;
			@Override KnownJsonValues add(String name, JSON_Data.Value.Type type) { super.add(name, type); return this; }
		}
		static class KnownVdfValues extends ExtHashMap<VDFTreeNode.Type> {
			private static final long serialVersionUID = -8137083046811709725L;
			@Override KnownVdfValues add(String name, VDFTreeNode.Type type) { super.add(name, type); return this; }
		}
		
		static void scanUnexpectedValues(JSON_Object<NV,V> object, KnownJsonValues knownValues, String prefixStr) {
			for (JSON_Data.NamedValue<NV,V> nvalue:object)
				if (!knownValues.contains(nvalue.name, nvalue.value.type))
					//DevHelper.unknownValues.add(prefixStr+"."+nvalue.name+" = "+nvalue.value.type+"...");
					DevHelper.unknownValues.add(prefixStr,nvalue.name,nvalue.value.type);
		}
		static void scanUnexpectedValues(VDFTreeNode node, KnownVdfValues knownValues, String prefixStr) {
			node.forEach((subNode,t,n,v) -> {
				if (!knownValues.contains(n,t))
					DevHelper.unknownValues.add(prefixStr, n, t);
			});
		}

		static final UnknownValues unknownValues = new UnknownValues();
		static class UnknownValues extends HashSet<String> {
			private static final long serialVersionUID = 7229990445347378652L;
			
			void add(String baseLabel, String name, VDFTreeNode.Type type) {
				if (name==null) add(String.format("[VDF]%s:%s"   , baseLabel,       type==null?"<null>":type));
				else            add(String.format("[VDF]%s.%s:%s", baseLabel, name, type==null?"<null>":type));
			}
			void add(String baseLabel, String name, JSON_Data.Value.Type type) {
				if (name==null) add(String.format("[JSON]%s:%s"   , baseLabel,       type==null?"<null>":type));
				else            add(String.format("[JSON]%s.%s:%s", baseLabel, name, type==null?"<null>":type));
			}
			void show(PrintStream out) {
				if (isEmpty()) return;
				Vector<String> vec = new Vector<>(this);
				out.printf("Unknown Labels: [%d]%n", vec.size());
				vec.sort(null);
				for (String str:vec)
					out.printf("   \"%s\"%n", str);
			}
		}
		
		static void scanVdfStructure(VDFTreeNode node, String nodeLabel) {
			node.forEach((node1,t,n,v) -> {
				unknownValues.add(nodeLabel, n, t);
				if (t==VDFTreeNode.Type.Array)
					scanVdfStructure(node1, nodeLabel+"."+n);
			});
		}
		
		static void scanJsonStructure(JSON_Data.Value<NV, V> value, String valueLabel) {
			if (value==null) { unknownValues.add(valueLabel+" = <null>"); return; }
			unknownValues.add(valueLabel+":"+value.type);
			switch (value.type) {
			case Bool: case Float: case Integer: case Null: case String: break;
			case Object:
				JSON_Data.ObjectValue<NV,V> objectValue = value.castToObjectValue();
				if (objectValue==null)
					unknownValues.add(valueLabel+":"+value.type+" is not instance of JSON_Data.ObjectValue");
				else 
					scanJsonStructure(objectValue.value, valueLabel);
				break;
			case Array:
				JSON_Data.ArrayValue<NV,V> arrayValue = value.castToArrayValue();
				if (arrayValue==null)
					unknownValues.add(valueLabel+":"+value.type+" is not instance of JSON_Data.ArrayValue");
				else
					scanJsonStructure(arrayValue.value, valueLabel);
				break;
			}
		}

		static void scanJsonStructure(JSON_Object<NV,V> object, String valueLabel) {
			if (object==null)
				unknownValues.add(valueLabel+" (JSON_Object == <null>)");
			else
				for (JSON_Data.NamedValue<NV, V> nval:object)
					scanJsonStructure(nval.value, valueLabel+"."+(nval.name==null?"<null>":nval.name));
		}

		static void scanJsonStructure(JSON_Array<NV,V> array, String valueLabel) {
			if (array==null)
				unknownValues.add(valueLabel+" (JSON_Array == <null>)");
			else
				for (JSON_Data.Value<NV,V> val:array)
					scanJsonStructure(val, valueLabel+"[]");
		}

		static void scanJsonStructure_OAO( JSON_Data.Value<NV,V> baseValue, String baseValueLabel, String subArrayName, Vector<String> knownValueNames, Vector<String> knownSubArrayValueNames, String errorPrefix, File file) {
			JSON_Object<NV,V> object = null;
			try { object = JSON_Data.getObjectValue(baseValue, errorPrefix); }
			catch (TraverseException e) { Data.showException(e, file); }
			if (object!=null) {
				for (JSON_Data.NamedValue<NV,V> nvalue:object) {
					String valueStr = nvalue.value.type+"...";
					if (!knownValueNames.contains(nvalue.name)) valueStr = nvalue.value.toString();
					DevHelper.unknownValues.add(baseValueLabel+"."+nvalue.name+" = "+valueStr);
					if (subArrayName.equals(nvalue.name)) {
						JSON_Array<NV,V> array = null;
						try { array = JSON_Data.getArrayValue(nvalue.value, errorPrefix+"."+subArrayName); }
						catch (TraverseException e) { Data.showException(e, file); }
						if (array!=null) {
							for (int i=0; i<array.size(); i++) {
								JSON_Object<NV, V> object1 = null;
								try { object1 = JSON_Data.getObjectValue(array.get(i), errorPrefix+"."+subArrayName+"["+i+"]"); }
								catch (TraverseException e) { Data.showException(e, file); }
								if (object1!=null) {
									for (JSON_Data.NamedValue<NV, V> nvalue1:object1) {
										valueStr = nvalue1.value.type+"...";
										if (!knownSubArrayValueNames.contains(nvalue1.name)) valueStr = nvalue1.value.toString();
										DevHelper.unknownValues.add(baseValueLabel+"."+"rgCards."+nvalue1.name+" = "+valueStr);
									}
								}
							}
						}
					}
				}
			}
		}

		static Vector<String> strList(String...strings) {
			return new Vector<>(Arrays.asList(strings));
		}
	}

	protected static class Data {

		static void showException(JSON_Data.TraverseException e, File file) { showException("JSON_Data.TraverseException", e, file); }
		static void showException(JSON_Parser.ParseException  e, File file) { showException("JSON_Parser.ParseException", e, file); }
		static void showException(VDFParser.ParseException    e, File file) { showException("VDFParser.ParseException", e, file); }
		static void showException(TreeNodes.ParseException    e, File file) { showException("TreeNodes.ParseException", e, file); }

		static void showException(String prefix, Throwable e, File file) {
			String str = String.format("%s: %s%n", prefix, e.getMessage());
			if (file!=null) str += String.format("   in File \"%s\"%n", file.getAbsolutePath());
			System.err.print(str);
		}
		
		static final KnownGameTitles knownGameTitles = new KnownGameTitles();
		static class KnownGameTitles extends HashMap<Integer,String> {
			private static final long serialVersionUID = 2599578502526459790L;
			
			void readFromFile() {
				try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(KNOWN_GAME_TITLES_INI), StandardCharsets.UTF_8))) {
					
					clear();
					String line;
					while ( (line=in.readLine())!=null ) {
						int pos = line.indexOf('=');
						if (pos>0) {
							String gameIDStr = line.substring(0, pos);
							String gameTitle = line.substring(pos+1);
							Integer gameID = parseNumber(gameIDStr);
							if (gameID!=null)
								put(gameID,gameTitle);
						}
					}
					
				} catch (FileNotFoundException e) {
				} catch (IOException e) {
					System.err.printf("IOException while reading KnownGameTitles from file: %s%n", KNOWN_GAME_TITLES_INI);
					e.printStackTrace();
				}
			}
			void writeToFile() {
				try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(KNOWN_GAME_TITLES_INI), StandardCharsets.UTF_8))) {
					
					Vector<Integer> gameIDs = new Vector<>(this.keySet());
					gameIDs.sort(null);
					for (Integer gameID:gameIDs) {
						String title = get(gameID);
						if (gameID!=null && title!=null)
							out.printf("%s=%s%n", gameID, title);
					}
					
				} catch (FileNotFoundException e) {}
			}
		}

		static class Player {
			
			final long playerID;
			final File folder;
			final File configFolder;
			final File gameStateFolder;
			final File localconfigFile;
			final HashMap<Integer, File> steamCloudFolders;
			final ScreenShotLists screenShots;
			final VDFTreeNode localconfig;
			final HashMap<Integer,GameStateInfo> gameStateInfos;
			final AchievementProgress achievementProgress;
			final FriendList friends;

			Player(long playerID, File folder) {
				this.playerID = playerID;
				this.folder = folder;
				
				File[] gameNumberFolders = folder.listFiles(file->file.isDirectory() && parseNumber(file.getName())!=null);
				steamCloudFolders = new HashMap<Integer,File>();
				for (File subFolder:gameNumberFolders) {
					String name = subFolder.getName();
					if (!name.equals("7") && !name.equals("760")) {
						Integer gameID = parseNumber(name);
						steamCloudFolders.put(gameID, subFolder);
					}
				}
				File subFolder;
				
				// Folders
				subFolder = new File(folder,"760");
				screenShots = !subFolder.isDirectory() ? null : new ScreenShotLists(subFolder);
				
				subFolder = new File(folder,"config");
				if (subFolder.isDirectory()) {
					configFolder = subFolder;
				} else
					configFolder = null;
				
				File localconfigFile = null;
				if (configFolder!=null)
					localconfigFile = new File(configFolder,"localconfig.vdf");
				
				// localconfig
				VDFParser.Data localconfigData = null;
				if (localconfigFile!=null && localconfigFile.isFile()) {
					try { localconfigData = VDFParser.parse(localconfigFile,StandardCharsets.UTF_8); }
					catch (VDFParser.ParseException e) { showException(e, localconfigFile); }
				}
				if (localconfigData!=null) {
					this.localconfig = localconfigData.createVDFTreeNode();
					this.localconfigFile = localconfigFile;
				} else {
					this.localconfig = null;
					this.localconfigFile = null;
				}
				
				FriendList preFriends = null;
				if (localconfig!=null) {
					VDFTreeNode friendsNode = localconfig.getSubNode("UserLocalConfigStore","friends");
					if (friendsNode!=null) {
						try { preFriends = FriendList.parse(friendsNode,playerID); }
						catch (ParseException e) { showException(e, localconfigFile); }
						if (preFriends==null)
							preFriends = new FriendList(friendsNode);
					}
				}
				friends = preFriends;
				
				gameStateInfos = new HashMap<>();
				AchievementProgress preAchievementProgress = null;
				if (configFolder!=null) {
					File gameStateFolder = new File(configFolder,"librarycache");
					if (gameStateFolder.isDirectory()) {
						this.gameStateFolder = gameStateFolder;
						File[] files = gameStateFolder.listFiles(file->file.isFile());
						for (File file:files) {
							FileNameNExt fileNameNExt = FileNameNExt.create(file.getName());
							if (fileNameNExt.extension!=null && fileNameNExt.extension.equalsIgnoreCase("json")) {
								Integer gameID;
								if (fileNameNExt.name.equalsIgnoreCase("achievement_progress")) {
									// \config\librarycache\achievement_progress.json
									JSON_Data.Value<NV, V> result = null;
									try { result = new JSON_Parser<NV,V>(file,null).parse_withParseException(); }
									catch (JSON_Parser.ParseException e) { showException(e, file); }
									if (result!=null) {
										try {
											preAchievementProgress = new AchievementProgress(file,JSON_Data.getObjectValue(result, "AchievementProgress"));
										} catch (TraverseException e) {
											showException(e, file);
											preAchievementProgress = new AchievementProgress(file,result);
										}
									}
									
								} else if ((gameID=parseNumber(fileNameNExt.name))!=null) {
									// \config\librarycache\1465680.json
									JSON_Data.Value<NV, V> result = null;
									try { result = new JSON_Parser<NV,V>(file,null).parse_withParseException(); }
									catch (JSON_Parser.ParseException e) { showException(e, file); }
									if (result!=null) {
										try {
											gameStateInfos.put(gameID, new GameStateInfo(file,JSON_Data.getArrayValue(result, "GameStateInfos")));
										} catch (TraverseException e) {
											showException(e, file);
											gameStateInfos.put(gameID, new GameStateInfo(file,result));
										}
									}
								}
							}
						}
					} else
						this.gameStateFolder = null;
				} else
					this.gameStateFolder = null;
				achievementProgress = preAchievementProgress;
				
			}

			public String getName() {
				if (localconfig != null) {
					VDFTreeNode nameNode = localconfig.getSubNode("UserLocalConfigStore","friends","PersonaName");
					if (nameNode!=null && nameNode.value!=null && !nameNode.value.isEmpty())
						return nameNode.value;
				}
				return "Player "+playerID;
			}
			
			static class FriendList {
				
				//static final HashSet<String> unknownValueNames = new HashSet<>();
				
				final VDFTreeNode rawData;
				final Vector<Friend> friends;
				final HashMap<String,String> values;
				
				FriendList() {
					rawData = null;
					friends = new Vector<>();
					values = new HashMap<>();
				}
				
				FriendList(VDFTreeNode rawData) {
					this.rawData = rawData;
					friends = null;
					values = null;
				}

				public static FriendList parse(VDFTreeNode friendsNode, long playerID) throws ParseException {
					if (friendsNode==null) throw new ParseException("FriendList: base VDFTreeNode is NULL");
					if (friendsNode.type!=VDFTreeNode.Type.Array) throw new ParseException("FriendList: base VDFTreeNode is not an Array");
					FriendList friendList = new FriendList();
					friendsNode.forEach((subNode, type, name, value)->{
						switch (type) {
						case Root: System.err.printf("FriendList[Player %d]: Root node as sub node of base VDFTreeNode%n", playerID); break;

						case Array: // Friend
							friendList.friends.add(new Friend(name,subNode));
							break;
							
						case String: // simple value
							friendList.values.put(name, value);
							break;
						}
					});
					return friendList;
				}

				static class Friend {
					private static final DevHelper.KnownVdfValues KNOWN_VDF_VALUES = new DevHelper.KnownVdfValues()
							.add("name"  , VDFTreeNode.Type.String)
							.add("tag"   , VDFTreeNode.Type.String)
							.add("avatar", VDFTreeNode.Type.String)
							.add("NameHistory", VDFTreeNode.Type.Array);
					
					final VDFTreeNode rawData;
					final String idStr;
					final Long id;
					final String name;
					final String tag;
					final String avatar;
					final HashMap<Integer, String> nameHistory;

					public Friend(String idStr, VDFTreeNode node) {
						this.idStr = idStr;
						this.id = parseLongNumber(idStr);
						this.rawData = null;
						//this.rawData = node;
						//DevHelper.scanVdfStructure(node,"Friend");
						
						name   = node.getString("name"  );
						tag    = node.getString("tag"   );
						avatar = node.getString("avatar");
						VDFTreeNode arrayNode = node.getArray("NameHistory");
						if (arrayNode!=null) {
							nameHistory = new HashMap<Integer,String>();
							arrayNode.forEach((subNode,t,n,v) -> {
								if (t==VDFTreeNode.Type.String) {
									Integer index = parseNumber(n);
									if (index!=null && v!=null) {
										nameHistory.put(index,v);
										return;
									}
								}
								DevHelper.unknownValues.add("Friend.NameHistory", n, t);
							});
						} else
							nameHistory = null;
						
						DevHelper.scanUnexpectedValues(node, KNOWN_VDF_VALUES, "Friend");
					}
				}
			}
			
			static class AchievementProgress {
				private static final DevHelper.KnownJsonValues KNOWN_JSON_VALUES = new DevHelper.KnownJsonValues()
						.add("nVersion", JSON_Data.Value.Type.Integer)
						.add("mapCache", JSON_Data.Value.Type.Object);

				final File file;
				final JSON_Data.Value<NV, V> rawData;
				final boolean hasParsedData;
				final JSON_Object<NV, V> sourceData;
				final Long version;
				final HashMap<Integer,GameStatus> gameStates;
				final Vector<GameStatus> gameStates_withoutID;

				AchievementProgress(File file, JSON_Data.Value<NV, V> rawData) {
					this.file = file;
					this.rawData = rawData;
					hasParsedData = false;
					sourceData = null;
					version    = null;
					gameStates = null;
					gameStates_withoutID = null;
				}
				AchievementProgress(File file, JSON_Object<NV,V> object) throws TraverseException {
					this.file = file;
					rawData = null;
					hasParsedData = true;
					sourceData = object;
					//DevHelper.scanJsonStructure(object, "AchievementProgress");
					String prefixStr = "AchievementProgress";
					if (object==null) throw new TraverseException("%s == <NULL>", prefixStr);
					version                    = JSON_Data.getIntegerValue(object, "nVersion", prefixStr);
					JSON_Object<NV,V> mapCache = JSON_Data.getObjectValue (object, "mapCache", prefixStr);
					gameStates = new HashMap<>();
					gameStates_withoutID = new Vector<GameStatus>();
					for (JSON_Data.NamedValue<NV,V> nv:mapCache) {
						GameStatus gameStatus;
						try {
							gameStatus = new GameStatus(nv.name,nv.value);
						} catch (TraverseException e) {
							showException(e, file);
							gameStatus = new GameStatus(nv);
						}
						
						Integer gameID = parseNumber(nv.name);
						if (gameID==null && gameStatus.hasParsedData)
							gameID = (int) gameStatus.appID;
						
						if (gameID!=null)
							gameStates.put(gameID, gameStatus);
						else
							gameStates_withoutID.add(gameStatus);
					}
					DevHelper.scanUnexpectedValues(object, KNOWN_JSON_VALUES,prefixStr+".GameStatus");
				}
				
				static class GameStatus {
					private static final DevHelper.KnownJsonValues KNOWN_JSON_VALUES = new DevHelper.KnownJsonValues()
							.add("all_unlocked", JSON_Data.Value.Type.Bool)
							.add("appid"       , JSON_Data.Value.Type.Integer)
							.add("cache_time"  , JSON_Data.Value.Type.Integer)
							.add("total"       , JSON_Data.Value.Type.Integer)
							.add("unlocked"    , JSON_Data.Value.Type.Integer)
							.add("percentage"  , JSON_Data.Value.Type.Integer)
							.add("percentage"  , JSON_Data.Value.Type.Float);
					// "AchievementProgress.GameStatus.all_unlocked:Bool"
					// "AchievementProgress.GameStatus.appid:Integer"
					// "AchievementProgress.GameStatus.cache_time:Integer"
					// "AchievementProgress.GameStatus.percentage:Float"
					// "AchievementProgress.GameStatus.percentage:Integer"
					// "AchievementProgress.GameStatus.total:Integer"
					// "AchievementProgress.GameStatus.unlocked:Integer"
					// "AchievementProgress.GameStatus:Object"
					
					final JSON_Data.Value<NV, V> rawData;
					final String name;
					final boolean hasParsedData;
					
					final boolean allUnlocked;
					final long    appID;
					final long    cacheTime;
					final long    total;
					final long    unlocked;
					final double  percentage;

					GameStatus(JSON_Data.NamedValue<NV, V> rawData) {
						this.rawData = rawData.value;
						this.name    = rawData.name;
						hasParsedData = false;
						allUnlocked = false;
						appID       = -1;
						cacheTime   = -1;
						total       = -1;
						unlocked    = -1;
						percentage  = Double.NaN;
					}

					GameStatus(String name, JSON_Data.Value<NV, V> value) throws TraverseException {
						this.rawData = null;
						this.name    = name;
						hasParsedData = true;
						// DevHelper.scanJsonStructure(value, "AchievementProgress.GameStatus");
						String prefixStr = "AchievementProgress.GameStatus["+name+"]";
						JSON_Object<NV,V> object = JSON_Data.getObjectValue(value, prefixStr);
						allUnlocked = JSON_Data.getBoolValue   (object, "all_unlocked", prefixStr);
						appID       = JSON_Data.getIntegerValue(object, "appid"       , prefixStr);
						cacheTime   = JSON_Data.getIntegerValue(object, "cache_time"  , prefixStr);
						total       = JSON_Data.getIntegerValue(object, "total"       , prefixStr);
						unlocked    = JSON_Data.getIntegerValue(object, "unlocked"    , prefixStr);
						percentage  = JSON_Data.getNumber(object, "percentage"  , prefixStr);
						DevHelper.scanUnexpectedValues(object, KNOWN_JSON_VALUES,"AchievementProgress.GameStatus");
					}

					int getGameID() { return (int) appID; }
				}
			}
			
			static class GameStateInfo {

				final File file;
				final JSON_Data.Value<NV, V> rawData;
				final Vector<Block> blocks;
				final JSON_Array<NV, V> sourceData;
				final String fullDesc;
				final String shortDesc;
				final Badge badge;
				final Achievements achievements;

				public GameStateInfo(File file, JSON_Data.Value<NV, V> rawData) {
					this.file = file;
					this.rawData = rawData;
					sourceData   = null;
					blocks       = null;
					fullDesc     = null;
					shortDesc    = null;
					badge        = null;
					achievements = null;
				}

				public GameStateInfo(File file, JSON_Array<NV, V> array) throws TraverseException {
					this.file = file;
					this.rawData = null;
					this.sourceData = array;
					
					if (array==null) throw new TraverseException("GameStateInfo isn't a JSON_Array");
					
					blocks = new Vector<>();
					for (int i=0; i<array.size(); i++) {
						JSON_Data.Value<NV,V> value = array.get(i);
						try {
							blocks.add(new Block(i,value));
						} catch (TraverseException e) {
							showException(e, file);
							blocks.add(Block.createRawData(i,value));
						}
					}
					
					String preFullDesc  = null;
					String preShortDesc = null;
					Badge  preBadge     = null;
					Achievements preAchievements = null;
					for (Block block:blocks) {
						String dataValueStr = String.format("GameStateInfo.Block[%d].dataValue", block.blockIndex);
						//DevHelper.scanJsonStructure(block.dataValue,String.format("GameStateInfo.Block[\"%s\",V%d].dataValue", block.label, block.version));
						JSON_Object<NV, V> object;
						switch (block.label) {
						case "achievements":
							//DevHelper.scanJsonStructure(block.dataValue,String.format("GameStateInfo.Block[\"%s\",V%d].dataValue", block.label, block.version));
							try {
								object = JSON_Data.getObjectValue(block.dataValue, dataValueStr);
								preAchievements = new GameStateInfo.Achievements(object, dataValueStr, file);
							} catch (TraverseException e) {
								showException(e, file);
								preAchievements = new GameStateInfo.Achievements(block.dataValue);
							}
							break;
							
						case "badge":
							//DevHelper.scanJsonStructure_OAO(
							//	block.dataValue, "GameStateInfo.Block[\"badge\"]", "rgCards",
							//	DevHelper.strList("strIconURL","strName","strNextLevelName"),
							//	DevHelper.strList("strTitle","strName","strMarketHash","strImgURL","strArtworkURL"),
							//	dataValueStr, file
							//);
							try {
								object = JSON_Data.getObjectValue(block.dataValue, dataValueStr);
								preBadge = new GameStateInfo.Badge(object, dataValueStr, file);
							} catch (TraverseException e) {
								showException(e, file);
								preBadge = new GameStateInfo.Badge(block.dataValue);
							}
							break;
							
						case "descriptions":
							object = null;
							try { object = JSON_Data.getObjectValue(block.dataValue, dataValueStr); }
							catch (TraverseException e) { showException(e, file); }
							if (object!=null) {
								try { preFullDesc  = JSON_Data.getStringValue(object, "strFullDescription", dataValueStr); }
								catch (TraverseException e) { showException(e, file); }
								try { preShortDesc = JSON_Data.getStringValue(object, "strSnippet", dataValueStr); }
								catch (TraverseException e) { showException(e, file); }
							}
							break;
						}
					}
					fullDesc     = preFullDesc;
					shortDesc    = preShortDesc;
					badge        = preBadge;
					achievements = preAchievements;
				}
				
				static class Achievements {

					private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues()
							.add("nAchieved"        , JSON_Data.Value.Type.Integer)
							.add("nTotal"           , JSON_Data.Value.Type.Integer)
							.add("vecAchievedHidden", JSON_Data.Value.Type.Array  )
							.add("vecUnachieved"    , JSON_Data.Value.Type.Array  )
							.add("vecHighlight"     , JSON_Data.Value.Type.Array  );
				
					final Value<NV, V> rawData;
					final boolean hasParsedData;
					final long achieved;
					final long total;
					final Vector<Achievement> achievedHidden;
					final Vector<Achievement> unachieved;
					final Vector<Achievement> highlight;
					
					Achievements(JSON_Data.Value<NV, V> rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						achieved = -1;
						total    = -1;
						achievedHidden = null;
						unachieved = null;
						highlight = null;
					}

					Achievements(JSON_Object<NV, V> object, String dataValueStr, File file) throws TraverseException {
						this.rawData = null;
						hasParsedData = true;
						
						achieved                         = JSON_Data.getIntegerValue(object, "nAchieved"        , dataValueStr);
						total                            = JSON_Data.getIntegerValue(object, "nTotal"           , dataValueStr);
						JSON_Array<NV, V> unachieved     = JSON_Data.getArrayValue  (object, "vecUnachieved"    , dataValueStr);
						JSON_Array<NV, V> highlight      = JSON_Data.getArrayValue  (object, "vecHighlight"     , dataValueStr);
						JSON_Array<NV, V> achievedHidden = JSON_Data.getValue(object, "vecAchievedHidden", true, JSON_Data.Value.Type.Array, JSON_Data.Value::castToArrayValue, false, dataValueStr);
						
						this.achievedHidden = parseArray(achievedHidden, dataValueStr+"."+"vecAchievedHidden"+"[]", file);
						this.unachieved     = parseArray(unachieved    , dataValueStr+"."+"vecUnachieved"    +"[]", file);
						this.highlight      = parseArray(highlight     , dataValueStr+"."+"vecHighlight"     +"[]", file);
						
						DevHelper.scanUnexpectedValues(object, KNOWN_VALUES, "GameStateInfo.Achievements");
					}

					private Vector<Achievement> parseArray(JSON_Array<NV, V> rawArray, String debugOutputPrefixStr, File file) {
						if (rawArray==null) return null;
						Vector<Achievement> array = new Vector<>();
						for (JSON_Data.Value<NV, V> value:rawArray) {
							try {
								array.add(new Achievement(value,debugOutputPrefixStr));
							} catch (TraverseException e) {
								showException(e, file); 
								array.add(new Achievement(value));
							}
						}
						return array;
					}
					
					public String getTreeNodeExtraInfo() {
						if (hasParsedData && achieved!=0 && total!=0)
							return String.format("A:%d/%d", achieved, total);
						return "";
					}

					static class Achievement {

						private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues()
								.add("bAchieved"     , JSON_Data.Value.Type.Bool   )
								.add("flAchieved"    , JSON_Data.Value.Type.Float  )
								.add("flAchieved"    , JSON_Data.Value.Type.Integer)
								.add("rtUnlocked"    , JSON_Data.Value.Type.Integer)
								.add("strDescription", JSON_Data.Value.Type.String )
								.add("strID"         , JSON_Data.Value.Type.String )
								.add("strImage"      , JSON_Data.Value.Type.String )
								.add("strName"       , JSON_Data.Value.Type.String );
						
						final Value<NV, V> rawData;
						final boolean hasParsedData;
						final boolean isAchieved;
						final Double achievedRatio;
						final long unlocked;
						final String description;
						final String id;
						final String image;
						final String name;


						public Achievement(Value<NV, V> rawData) {
							this.rawData = rawData;
							hasParsedData = false;
							isAchieved    = false;
							achievedRatio = null;
							unlocked      = -1;
							description   = null;
							id            = null;
							image         = null;
							name          = null;
						}

						public Achievement(JSON_Data.Value<NV, V> value, String debugOutputPrefixStr) throws TraverseException {
							rawData = null;
							hasParsedData = true;
							//DevHelper.scanJsonStructure(value,"GameStateInfo.Achievements.Achievement");
							
							JSON_Object<NV, V> object = JSON_Data.getObjectValue(value, debugOutputPrefixStr);
							isAchieved    = JSON_Data.getBoolValue   (object, "bAchieved"     , debugOutputPrefixStr);
							unlocked      = JSON_Data.getIntegerValue(object, "rtUnlocked"    , debugOutputPrefixStr);
							description   = JSON_Data.getStringValue (object, "strDescription", debugOutputPrefixStr);
							id            = JSON_Data.getStringValue (object, "strID"         , debugOutputPrefixStr);
							image         = JSON_Data.getStringValue (object, "strImage"      , debugOutputPrefixStr);
							name          = JSON_Data.getStringValue (object, "strName"       , debugOutputPrefixStr);
							achievedRatio = JSON_Data.getNumber(object, "flAchieved", true, debugOutputPrefixStr);
							
							DevHelper.scanUnexpectedValues(object, KNOWN_VALUES, "GameStateInfo.Achievements.Achievement");
						}
						
					}
				}

				static class Badge {
					
					private static final DevHelper.KnownJsonValues KNOWN_JSON_VALUES = new DevHelper.KnownJsonValues()
							.add("strName"         , JSON_Data.Value.Type.String)
							.add("bHasBadgeData"   , JSON_Data.Value.Type.Bool)
							.add("bMaxed"          , JSON_Data.Value.Type.Null)
							.add("nMaxLevel"       , JSON_Data.Value.Type.Integer)
							.add("nLevel"          , JSON_Data.Value.Type.Integer)
							.add("nXP"             , JSON_Data.Value.Type.Integer)
							.add("strNextLevelName", JSON_Data.Value.Type.String)
							.add("nNextLevelXP"    , JSON_Data.Value.Type.Integer)
							.add("strIconURL"      , JSON_Data.Value.Type.String)
							.add("rgCards"         , JSON_Data.Value.Type.Array);
				
					final JSON_Data.Value<NV, V> rawData;
					final boolean hasParsedData;
					final Vector<TradingCard> tradingCards;

					final String  name;
					final boolean hasBadgeData;
					final long    maxLevel;
					final long    currentLevel;
					final long    currentXP;
					final String  nextLevelName;
					final long    nextLevelXP;
					final String  iconURL;


					Badge(JSON_Data.Value<NV, V> rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						tradingCards  = null;
						name          = null;
						hasBadgeData  = false;
						maxLevel      = -1;
						currentLevel  = -1;
						currentXP     = -1;
						nextLevelName = null;
						nextLevelXP   = -1;
						iconURL       = null;
					}

					Badge(JSON_Object<NV, V> object, String dataValueStr, File file) throws TraverseException {
						rawData = null;
						hasParsedData = true;
						
						name          = JSON_Data.getStringValue (object, "strName"         , dataValueStr);
						hasBadgeData  = JSON_Data.getBoolValue   (object, "bHasBadgeData"   , dataValueStr);
						maxLevel      = JSON_Data.getIntegerValue(object, "nMaxLevel"       , dataValueStr);
						currentLevel  = JSON_Data.getIntegerValue(object, "nLevel"          , dataValueStr);
						currentXP     = JSON_Data.getIntegerValue(object, "nXP"             , dataValueStr);
						nextLevelName = JSON_Data.getStringValue (object, "strNextLevelName", dataValueStr);
						nextLevelXP   = JSON_Data.getIntegerValue(object, "nNextLevelXP"    , dataValueStr);
						iconURL       = JSON_Data.getStringValue (object, "strIconURL"      , dataValueStr);
						
						JSON_Array<NV,V> array = JSON_Data.getArrayValue(object, "rgCards", dataValueStr);
						tradingCards = new Vector<>();
						for (int i=0; i<array.size(); i++) {
							try {
								String prefixStr = dataValueStr+".rgCards["+i+"]";
								JSON_Object<NV,V> objectTC = JSON_Data.getObjectValue(array.get(i), prefixStr);
								TradingCard tradingCard = new TradingCard(objectTC, prefixStr);
								tradingCards.add(tradingCard);
							} catch (TraverseException e) {
								showException(e, file);
								tradingCards.add(new TradingCard(array.get(i)));
							}
						}
						
						// unexpected values
						DevHelper.scanUnexpectedValues(object, KNOWN_JSON_VALUES,"GameStateInfo.Badge");
					}

					String getTreeNodeExtraInfo() {
						if (!hasParsedData) return "";
						
						String str = "";
						if (currentLevel!=0)
							str += (str.isEmpty()?"":", ") + "B:"+currentLevel;
						
						int tcCount = 0;
						for (TradingCard tc:tradingCards) tcCount += tc.owned;
						if (tcCount>0)
							str += (str.isEmpty()?"":", ") + "TC:"+tcCount;
						
						return str;
					}

					File createTempTradingCardsOverviewHTML() {
						Path htmlPath;
						try { htmlPath = Files.createTempFile(null, ".html"); }
						catch (IOException e) {
							System.err.printf("IOException while createing a temporary file (*.html): %s%n", e.getMessage());
							return null;
						}
						System.out.printf("Create TradingCards Overview HTML: %s%n", htmlPath);
						File htmlFile = htmlPath.toFile();
						try (
							PrintWriter htmlOut = new PrintWriter(new OutputStreamWriter(new FileOutputStream(htmlFile), StandardCharsets.UTF_8));
							BufferedReader templateIn = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/html/templateTradingCardsOverview.html"), StandardCharsets.UTF_8)); 
						) {
							
							String line;
							while ( (line=templateIn.readLine())!=null ) {
								if (line.trim().equals("// write url array here"))
									writeTradingCardsOverviewHTML(htmlOut);
								else
									htmlOut.println(line);
							}
							
						} catch (FileNotFoundException e) {
							System.err.printf("FileNotFoundException while writing in a temporary file (\"%s\"): %s%n", htmlFile, e.getMessage());
						} catch (IOException e) {
							System.err.printf("IOException while writing in a temporary file (\"%s\"): %s%n", htmlFile, e.getMessage());
						}
						return htmlFile;
					}

					private void writeTradingCardsOverviewHTML(PrintWriter htmlOut) {
						if (hasParsedData)
							for (TradingCard tc:tradingCards) {
								htmlOut.printf("		new TradingCard(%s,%s,%s,%s),%n", toString(tc.name), tc.owned, toString(tc.imageURL), toString(tc.artworkURL));
							}
					}

					private String toString(String str) {
						if (str==null) return "null";
						return "\""+str+"\"";
					}

					static class TradingCard {
						
						private static final DevHelper.KnownJsonValues KNOWN_JSON_VALUES = new DevHelper.KnownJsonValues()
								.add("strName"      , JSON_Data.Value.Type.String)
								.add("strTitle"     , JSON_Data.Value.Type.String)
								.add("nOwned"       , JSON_Data.Value.Type.Integer)
								.add("strArtworkURL", JSON_Data.Value.Type.String)
								.add("strImgURL"    , JSON_Data.Value.Type.String)
								.add("strMarketHash", JSON_Data.Value.Type.String);

						final JSON_Data.Value<NV, V> rawData;
						final boolean hasParsedData;
						final String name;
						final String title;
						final long   owned;
						final String artworkURL;
						final String imageURL;
						final String marketHash;

						
						public TradingCard(JSON_Data.Value<NV, V> rawData) {
							this.rawData = rawData;
							hasParsedData = false;
							name  = null;
							title = null;
							owned = -1;
							artworkURL = null;
							imageURL   = null;
							marketHash = null;
						}

						public TradingCard(JSON_Object<NV, V> object, String debugOutputPrefixStr) throws TraverseException {
							rawData = null;
							hasParsedData = true;
							
							name       = JSON_Data.getStringValue (object, "strName"      , debugOutputPrefixStr);
							title      = JSON_Data.getStringValue (object, "strTitle"     , debugOutputPrefixStr);
							owned      = JSON_Data.getIntegerValue(object, "nOwned"       , debugOutputPrefixStr);
							artworkURL = JSON_Data.getStringValue (object, "strArtworkURL", debugOutputPrefixStr);
							imageURL   = JSON_Data.getStringValue (object, "strImgURL"    , debugOutputPrefixStr);
							marketHash = JSON_Data.getStringValue (object, "strMarketHash", debugOutputPrefixStr);
							
							DevHelper.scanUnexpectedValues(object, KNOWN_JSON_VALUES,"GameStateInfo.Badge.SteamCard");
						}
					}
				}

				static class Block {

					final JSON_Data.Value<NV, V> rawData;
					final boolean hasParsedData;
					final int blockIndex;
					final String label;
					final long version;
					final JSON_Data.Value<NV, V> dataValue;

					static Block createRawData(int blockIndex, JSON_Data.Value<NV, V> value) {
						try { return new Block(blockIndex, value, true); }
						catch (TraverseException e) { return null; }
					}

					Block(int blockIndex, JSON_Data.Value<NV, V> value) throws TraverseException {
						this(blockIndex, value, false);
					}
					Block(int blockIndex, JSON_Data.Value<NV, V> value, boolean asRawData) throws TraverseException {
						if (asRawData) {
							this.rawData = value;
							this.hasParsedData = false;
							this.blockIndex = blockIndex;
							this.label = null;
							this.version = -1;
							this.dataValue = null;
							
						} else {
							this.rawData = null;
							this.hasParsedData = true;
							this.blockIndex = blockIndex;
							
							String blockStr     = "GameStateInfo.Block["+blockIndex+"]";
							String labelStr     = blockStr+".value[0:label]";
							String blockdataStr = blockStr+".value[1:blockdata]";
							
							JSON_Array<NV, V> array = JSON_Data.getArrayValue(value, blockStr);
							if (array.size()!=2) throw new TraverseException("%s.value.length(==%d) != 2", blockStr, array.size());
							
												  label = JSON_Data.getStringValue(array.get(0), labelStr);
							JSON_Object<NV,V> blockdata = JSON_Data.getObjectValue(array.get(1), blockdataStr);
							if (blockdata.size()>2)  throw new TraverseException("%s JSON_Object.length(==%d) > 2: Too much values", blockdataStr, blockdata.size());
							if (blockdata.isEmpty()) throw new TraverseException("%s JSON_Object is empty: Too few values", blockdataStr);
							version   = JSON_Data.getIntegerValue(blockdata,"version", blockdataStr+".version");
							dataValue = blockdata.getValue("data");
							if (dataValue==null && blockdata.size()>1) throw new TraverseException("%s.data not found, but there are other values", blockdataStr);
						}
					}
				}
			}
		}

		static class AppManifest {
			
			private static final String prefix = "appmanifest_";
			private static final String suffix = ".acf";
			
			@SuppressWarnings("unused")
			private final int appID;
			private final File file;
			private final VDFTreeNode vdfTree;
			
			AppManifest(int appID, File file) {
				this.appID = appID;
				this.file = file;
				
				if (this.file!=null) {
					
					VDFParser.Data vdfData = null;
					try { vdfData = VDFParser.parse(this.file,StandardCharsets.UTF_8); }
					catch (VDFParser.ParseException e) {}
					
					vdfTree = vdfData!=null ? vdfData.createVDFTreeNode() : null;
					
				} else
					vdfTree = null;
			}
		
			static Integer getAppIDFromFile(File file) {
				// appmanifest_275850.acf 
				if (!file.isFile()) return null;
				String name = file.getName();
				if (!name.startsWith(prefix)) return null;
				if (!name.endsWith(suffix)) return null;
				String idStr = name.substring(prefix.length(), name.length()-suffix.length());
				try { return Integer.parseInt(idStr); }
				catch (NumberFormatException e) { return null; }
			}

			String getGameTitle() {
				if (vdfTree!=null) {
					VDFTreeNode appNameNode = vdfTree.getSubNode("AppState","name");
					if (appNameNode!=null) return appNameNode.value;
				}
				return null;
			}
		}
		
		static class Game implements Comparable<Game>{
			
			private final int appID;
			private final String title;
			private final AppManifest appManifest;
			private final HashMap<String, File> imageFiles;
			private final HashMap<Long, File> steamCloudFolders;
			private final HashMap<Long, ScreenShotLists.ScreenShotList> screenShots;
			private final HashMap<Long, GameStateInfo>  gameStateInfos;
			private final HashMap<Long, AchievementProgress.GameStatus>  achievementProgress;
			
			Game(int appID, AppManifest appManifest, HashMap<String, File> imageFiles, HashMap<Long, Player> players) {
				this.appID = appID;
				this.appManifest = appManifest;
				this.imageFiles = imageFiles;
				title = appManifest==null ? null : appManifest.getGameTitle();
				
				steamCloudFolders = new HashMap<>();
				screenShots = new HashMap<>();
				gameStateInfos = new HashMap<>();
				achievementProgress = new HashMap<>();
				players.forEach((playerID,player)->{
					
					File steamCloudFolder = player.steamCloudFolders.get(appID);
					if (steamCloudFolder!=null)
						steamCloudFolders.put(playerID, steamCloudFolder);
					
					if (player.screenShots!=null) {
						ScreenShotLists.ScreenShotList screenShots = player.screenShots.get(appID);
						if (screenShots!=null && !screenShots.isEmpty())
							this.screenShots.put(playerID, screenShots);
					}
					GameStateInfo gameStateInfo = player.gameStateInfos.get(appID);
					if (gameStateInfo!=null)
						gameStateInfos.put(playerID, gameStateInfo);
					
					if (player.achievementProgress!=null && player.achievementProgress.gameStates!=null) {
						AchievementProgress.GameStatus gameStatus = player.achievementProgress.gameStates.get(appID);
						if (gameStatus!=null)
							achievementProgress.put(playerID, gameStatus);
					}
				});
			}

			boolean hasATitle() {
				return title!=null;
			}

			String getTitle() {
				if (title!=null) return title+" ["+appID+"]";
				String storedTitle = knownGameTitles.get(appID);
				if (storedTitle!=null) return storedTitle+" ["+appID+"]";
				return "Game "+appID;
			}

			Icon getIcon() {
				if (imageFiles!=null) {
					File iconImageFile = imageFiles.get("icon");
					if (iconImageFile!=null) {
						try {
							BufferedImage image = ImageIO.read(iconImageFile);
							return IconSource.getScaledIcon(image, 16, 16);
						} catch (IOException e) {}
					}
				}
				return null;
			}

			@Override
			public int compareTo(Game other) {
				if (other==null) return -1;
				if (this.title!=null && other.title==null) return -1;
				if (this.title==null && other.title!=null) return +1;
				if (this.title!=null && other.title!=null) {
					int n = this.title.compareTo(other.title);
					if (n!=0) return n;
				}
				return this.appID-other.appID;
			}
		}
	
		static class GameImages {
			
			@SuppressWarnings("unused")
			private final File folder;
			private final Vector<File> otherFiles;
			private final Vector<File> imageFiles;
			private final HashMatrix<Integer, String, File> appImages;
		
			GameImages(File folder) {
				this.folder = folder;
				File[] files = getFilesAndFolders(folder);
				
				otherFiles = new Vector<>();
				imageFiles = new Vector<>();
				appImages = new HashMatrix<>();
				
				for (File file:files) {
					if (file.isDirectory()) {
						otherFiles.add(file);
						
					} else if (ImageFile.is(file)) {
						ImageFileName ifn = ImageFileName.parse(file.getName());
						if (ifn==null || ifn.label==null || ifn.number==null)
							imageFiles.add(file);
						else
							appImages.put(ifn.number, ifn.label, file);
						
					} else
						otherFiles.add(file);
				}
			}
			
			public Collection<? extends Integer> getGameIDs() {
				return appImages.keySet1;
			}

			public Vector<Integer> getSortedGameIDs() {
				Vector<Integer> keySet1 = new Vector<>(getGameIDs());
				keySet1.sort(null);
				return keySet1;
			}
		
			public Vector<String> getSortedImageTypes() {
				Vector<String>  keySet2 = new Vector<>(appImages.keySet2);
				keySet2.sort(null);
				return keySet2;
			}
		
			public File getImageFile(Integer gameID, String ImageType) { return appImages.get(gameID, ImageType); }
			public File getImageFile(String ImageType, Integer gameID) { return appImages.get(gameID, ImageType); }
		
			public HashMap<String, File> getImageFileMap(Integer gameID) {
				return appImages.getMapCopy(gameID);
			}
			
			public File[] getImageFileArrays(Integer gameID) {
				Collection<File> files = appImages.getCollection(gameID);
				return files.toArray(new File[files.size()]);
			}

			static class ImageFileName {
			
				private final Integer number;
				private final String label;
			
				public ImageFileName(Integer number, String label) {
					this.number = number;
					this.label = label;
				}
			
				static ImageFileName parse(String name) {
					// 1000410_library_600x900.jpg
					int pos = name.lastIndexOf('.');
					if (pos>=0) name = name.substring(0, pos);
					pos = name.indexOf('_');
					if (pos<0) return null;
					String numberStr = name.substring(0, pos);
					String labelStr  = name.substring(pos+1);
					Integer number = parseNumber(numberStr);
					if (number==null) return null;
					return new ImageFileName(number,labelStr);
				}
				
			}
		}
		static class ScreenShotLists extends HashMap<Integer,ScreenShotLists.ScreenShotList>{
			private static final long serialVersionUID = -428703055699412094L;
			
			final File folder;
		
			ScreenShotLists(File folder) { 
				File subFolder = new File(folder,"remote");
				if (subFolder.isDirectory()) {
					this.folder = subFolder;
					File[] folders = subFolder.listFiles(file->file.isDirectory() && parseNumber(file.getName())!=null);
					for (File gameFolder:folders) {
						Integer gameID = parseNumber(gameFolder.getName());
						File imagesFolder = new File(gameFolder,"screenshots");
						if (imagesFolder.isDirectory()) {
							File thumbnailsFolder = new File(imagesFolder,"thumbnails");
							if (!thumbnailsFolder.isDirectory()) thumbnailsFolder = null;
							put(gameID, new ScreenShotList(imagesFolder,thumbnailsFolder));
						}
					}
				} else
					this.folder = null;
			}
			
			static class ScreenShotList extends Vector<ScreenShot> {
				private static final long serialVersionUID = 8285684141839919150L;
				
				final File imagesFolder;
				final File thumbnailsFolder;
				
				public ScreenShotList(File imagesFolder, File thumbnailsFolder) {
					this.imagesFolder = imagesFolder;
					this.thumbnailsFolder = thumbnailsFolder;
					File[] imageFiles = imagesFolder.listFiles(TreeNodes::isImageFile);
					for (File image:imageFiles) {
						File thumbnail = null;
						if (thumbnailsFolder!=null)
							thumbnail = new File(thumbnailsFolder,image.getName());
						add(new ScreenShot(image,thumbnail));
					}
				}
			}
		}
		static class ScreenShot implements Comparable<ScreenShot> {
			final File image;
			final File thumbnail;
			ScreenShot(File image, File thumbnail) {
				this.image = image;
				this.thumbnail = thumbnail;
				if (image==null || !image.isFile())
					throw new IllegalArgumentException();
			}
			@Override public int compareTo(ScreenShot other) {
				if (other==null) return -1;
				return this.image.getAbsolutePath().compareTo(other.image.getAbsolutePath());
			}
		}
	}
	
	static class PlayersNGames {
		
		static final GameChangeListeners gameChangeListeners = new GameChangeListeners();
		static class GameChangeListeners extends HashMap<Integer,Vector<GameChangeListeners.GameChangeListener>> {
			private static final long serialVersionUID = 374860814407569282L;
			
			private final HashMap<TreeNode,Integer> registeredTreeNodes = new HashMap<>();
			
			@Override
			public void clear() {
				registeredTreeNodes.clear();
				super.clear();
			}
			
			Integer getRegisteredGameID(TreeNode node) {
				return registeredTreeNodes.get(node);
			}
			
			boolean isRegistered(TreeNode node) {
				return registeredTreeNodes.keySet().contains(node);
			}

			void add(Integer gameID, GameChangeListener listener) {
				if (gameID==null) return;
				if (listener==null) throw new IllegalArgumentException();
				Vector<GameChangeListener> vector = get(gameID);
				if (vector==null) put(gameID,vector = new Vector<>());
				vector.add(listener);
				TreeNode treeNode = listener.getTreeNode();
				if (treeNode!=null) registeredTreeNodes.put(treeNode, gameID);
			}
			
			void gameTitleWasChanged(DefaultTreeModel treeModel, Integer gameID) {
				if (gameID==null) throw new IllegalArgumentException();
				if (treeModel==null) throw new IllegalArgumentException();
				Vector<GameChangeListener> vector = get(gameID);
				if (vector!=null) {
					for (GameChangeListener l:vector) {
						l.gameTitleWasChanged();
						treeModel.nodeChanged(l.getTreeNode());
					}
				}
			}
			
			interface GameChangeListener {
				TreeNode getTreeNode();
				void gameTitleWasChanged();
			}
		}
		
		static final HashMap<Integer,Game> games = new HashMap<>();
		static final HashMap<Long,Player> players = new HashMap<>();

		static void loadData() {
			DevHelper.unknownValues.clear();
			Data.knownGameTitles.readFromFile();
			
			File folder = KnownFolders.getSteamClientSubFolder(KnownFolders.SteamClientSubFolders.APPCACHE_LIBRARYCACHE);
			GameImages gameImages = null;
			if (folder!=null && folder.isDirectory())
				gameImages = new GameImages(folder);
			
			HashMap<Integer,AppManifest> appManifests = new HashMap<>();
			KnownFolders.forEachSteamAppsFolder((i,f)->{
				if (f!=null && f.isDirectory()) {
					File[] files = f.listFiles(file->AppManifest.getAppIDFromFile(file)!=null);
					for (File file:files) {
						Integer appID = AppManifest.getAppIDFromFile(file);
						appManifests.put(appID, new AppManifest(appID,file));
					}
				}
			});
			
			players.clear();
			folder = KnownFolders.getSteamClientSubFolder(KnownFolders.SteamClientSubFolders.USERDATA);
			if (folder!=null) {
				File[] files = folder.listFiles(file->file.isDirectory() && parseLongNumber(file.getName())!=null);
				for (File playerFolder:files) {
					Long playerID = parseLongNumber(playerFolder.getName());
					if (playerID==null) throw new IllegalStateException();
					players.put(playerID, new Player(playerID,playerFolder));
				}
			}
			
			// collect all AppIDs
			HashSet<Integer> idSet = new HashSet<>();
			idSet.addAll(appManifests.keySet());
			if (gameImages!=null)
				idSet.addAll(gameImages.getGameIDs());
			players.forEach((playerID,player)->{
				idSet.addAll(player.steamCloudFolders.keySet());
				if (player.screenShots!=null)
					idSet.addAll(player.screenShots.keySet());
			});
			
			
			games.clear();
			for (Integer appID:idSet) {
				HashMap<String, File> imageFiles = gameImages==null ? null : gameImages.getImageFileMap(appID);
				AppManifest appManifest = appManifests.get(appID);
				games.put(appID, new Game(appID, appManifest, imageFiles, players));
			}
			
			for (Game game:games.values())
				if (game.title!=null)
					Data.knownGameTitles.put(game.appID, game.title);
			Data.knownGameTitles.writeToFile();
			
			DevHelper.unknownValues.show(System.err);
		}

		private static String getPlayerName(Long playerID) {
			if (playerID==null) return "Player ???";
			Player game = players.get(playerID);
			if (game==null) return "Player "+playerID;
			return game.getName();
		}

		private static Icon getGameIcon(Integer gameID, TreeIcons defaultIcon) {
			if (gameID==null) return TreeIconsIS.getCachedIcon(defaultIcon);
			Game game = games.get(gameID);
			if (game==null) return TreeIconsIS.getCachedIcon(defaultIcon);
			return game.getIcon();
		}

		private static String getGameTitle(Integer gameID) {
			if (gameID==null) return "Game ???";
			Game game = games.get(gameID);
			if (game==null) return "Game "+gameID;
			return game.getTitle();
		}

		@SuppressWarnings("unused")
		private static boolean hasGameATitle(Integer gameID) {
			if (gameID==null) return false;
			Game game = games.get(gameID);
			if (game==null) return false;
			return game.hasATitle();
		}
		
		private static Comparator<Integer> createGameIdOrder() {
			//return Comparator.<Integer,Game>comparing(games::get,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Comparator.naturalOrder());
			
			Function<Integer,String> getTitle = gameID->{ Game game = games.get(gameID); return game==null ? null : game.title; };
			return Comparator.<Integer,String>comparing(getTitle,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Comparator.naturalOrder());
			
			//return Comparator.<Integer,Boolean>comparing(id->!hasGameATitle(id)).thenComparing(Comparator.naturalOrder());
		}
		private static <ValueType> Comparator<Map.Entry<Integer,ValueType>> createGameIdKeyOrder() {
			return Comparator.comparing(Map.Entry<Integer,ValueType>::getKey, createGameIdOrder());
		}
		private static <ValueType> Comparator<Map.Entry<Long,ValueType>> createPlayerIdKeyOrder() {
			return Comparator.comparing(Map.Entry<Long,ValueType>::getKey);
		}

		private static TreeNode createGameScreenShotsNode(TreeNode parent, Long id, ScreenShotLists.ScreenShotList screenShots) {
			return createGameScreenShotsNode(parent, "by "+getPlayerName(id), screenShots, null);
		}
		private static TreeNode createGameScreenShotsNode(TreeNode parent, Integer gameID, ScreenShotLists.ScreenShotList screenShots) {
			GroupingNode<ScreenShot> node = createGameScreenShotsNode(parent, getGameTitle(gameID), screenShots, getGameIcon(gameID,TreeIcons.ImageFile));
			gameChangeListeners.add(gameID, new GameChangeListener() {
				@Override public TreeNode getTreeNode() { return node; }
				@Override public void gameTitleWasChanged() { node.setTitle(getGameTitle(gameID)); }
			});
			return node;
		}
		private static GroupingNode<ScreenShot> createGameScreenShotsNode(TreeNode parent, String title, ScreenShotLists.ScreenShotList screenShots, Icon icon) {
			GroupingNode<ScreenShot> groupingNode = GroupingNode.create(parent, title, screenShots, Comparator.naturalOrder(), ScreenShotNode::new, icon);
			groupingNode.setFileSource(screenShots.imagesFolder, null);
			return groupingNode;
		}

		static class Root extends BaseTreeNode<TreeNode,TreeNode> {
			Root() {
				super(null, "PlayersNGames.Root", true, false);
			}
			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				children.add(GroupingNode.create(this, "Games"  , games  , Comparator.<Game>naturalOrder()                      , GameNode  ::new));
				children.add(GroupingNode.create(this, "Players", players, Comparator.comparing(Map.Entry<Long, Player>::getKey), PlayerNode::new));
				return children;
			}
		}
		
		static class GameNode extends BaseTreeNode<TreeNode,TreeNode> {
		
			private final Game game;
		
			protected GameNode(TreeNode parent, Game game) {
				super(parent, game.getTitle(), true, false, game.getIcon());
				this.game = game;
				//if (this.game.title==null)
					gameChangeListeners.add(this.game.appID, new GameChangeListener() {
						@Override public TreeNode getTreeNode() { return GameNode.this; }
						@Override public void gameTitleWasChanged() { setTitle(GameNode.this.game.getTitle()); }
					});
			}

			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				
				if (game.appManifest!=null) {
					children.add(new FileSystem.AppManifestNode(this, game.appManifest.file));
				}
				if (!game.gameStateInfos.isEmpty()) {
					children.add(GroupingNode.create(this, "Game Status Infos", game.gameStateInfos, createPlayerIdKeyOrder(), GameStateInfoNode::new));
				}
				if (!game.achievementProgress.isEmpty()) {
					children.add(GroupingNode.create(this, "Achievement Progress", game.achievementProgress, createPlayerIdKeyOrder(), AchievementProgressNode.GameStatusNode::new));
				}
				if (game.imageFiles!=null && !game.imageFiles.isEmpty()) {
					children.add(new FileSystem.FolderNode(this, "Images", game.imageFiles.values(), TreeIcons.ImageFile));
				}
				if (game.screenShots!=null && !game.screenShots.isEmpty()) {
					children.add(GroupingNode.create(this, "ScreenShots", game.screenShots, createPlayerIdKeyOrder(), PlayersNGames::createGameScreenShotsNode, TreeIconsIS.getCachedIcon(TreeIcons.ImageFile)));
					//children.add(new ScreenShotsNode<>(this,game.screenShots,id->String.format("by %s", getPlayerName(id)),Comparator.<Long>naturalOrder()));
				}
				if (game.steamCloudFolders!=null && !game.steamCloudFolders.isEmpty()) {
					HashMap<File,String> folderLabels = new HashMap<>();
					game.steamCloudFolders.forEach((playerID,folder)->folderLabels.put(folder, String.format("by %s", getPlayerName(playerID))));
					
					Stream<Map.Entry<Long, File>> sorted = game.steamCloudFolders.entrySet().stream().sorted(createPlayerIdKeyOrder());
					File[] files = sorted.map(Map.Entry<Long,File>::getValue).toArray(File[]::new);
					//Collection<File> files = game.gameDataFolders.values();
					
					children.add(new FileSystem.FolderNode(this, "SteamCloud Shared Data", files, true, folderLabels::get, TreeIcons.Folder));
				}
				
				return children;
			}
		}

		static class PlayerNode extends BaseTreeNode<TreeNode,TreeNode> implements FileBasedNode {

			//private long playerID;
			private Player player;

			public PlayerNode(TreeNode parent, Long playerID, Player player) {
				super(parent, player.getName()+"  ["+playerID+"]", true, false);
				//this.playerID = playerID;
				this.player = player;
			}

			@Override
			public LabeledFile getFile() {
				return new LabeledFile(player.folder);
			}

			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				
				GroupingNode<?> groupingNode;
				if (player.steamCloudFolders!=null && !player.steamCloudFolders.isEmpty()) {
					children.add(groupingNode = GroupingNode.create(this, "SteamCloud Shared Data", player.steamCloudFolders, createGameIdKeyOrder(), this::createSteamCloudFolderNode));
					groupingNode.setFileSource(player.folder, null);
				}
				if (player.screenShots!=null && !player.screenShots.isEmpty()) {
					children.add(groupingNode = GroupingNode.create(this, "ScreenShots", player.screenShots, createGameIdKeyOrder(), PlayersNGames::createGameScreenShotsNode, TreeIconsIS.getCachedIcon(TreeIcons.ImageFile)));
					groupingNode.setFileSource(player.screenShots.folder, null);
					//children.add(new ScreenShotsNode<Integer>(this,player.screenShots,id->getGameTitle(id),id->getGameIcon(id,TreeIcons.ImageFile),gameIdOrder));
				}
				if (player.configFolder!=null) {
					children.add(new FileSystem.FolderNode(this, "Config Folder", player.configFolder));
				}
				if (player.friends!=null) {
					children.add(new FriendListNode(this, player.friends, player.localconfigFile));
				}
				if (player.achievementProgress!=null) {
					children.add(new AchievementProgressNode(this, player.achievementProgress));
				}
				if (!player.gameStateInfos.isEmpty()) {
					children.add(groupingNode = GroupingNode.create(this, "Game Status Infos", player.gameStateInfos, createGameIdKeyOrder(), GameStateInfoNode::new));
					groupingNode.setFileSource(player.gameStateFolder, null);
				}
				
				return children;
			}

			private FolderNode createSteamCloudFolderNode(TreeNode parent, Integer gameID, File file) {
				FileSystem.FolderNode node = new FileSystem.FolderNode(parent, getGameTitle(gameID), file, getGameIcon(gameID, TreeIcons.Folder));
				gameChangeListeners.add(gameID, new GameChangeListener() {
					@Override public TreeNode getTreeNode() { return node; }
					@Override public void gameTitleWasChanged() { node.setTitle(getGameTitle(gameID)); }
				});
				return node;
			}
		}
		
		static class FriendListNode extends BaseTreeNode<TreeNode,TreeNode> implements FileBasedNode, ExternViewableNode, TextContentSource {

			private final FriendList data;
			private final File localconfigFile;

			public FriendListNode(TreeNode parent, FriendList friendList, File localconfigFile) {
				super(parent,"Friends",true,false);
				this.data = friendList;
				this.localconfigFile = localconfigFile;
			}

			@Override public ExternalViewerInfo getExternalViewerInfo() { return localconfigFile==null ? null : ExternalViewerInfo.TextEditor; }
			@Override public LabeledFile getFile() { return localconfigFile==null ? null : new LabeledFile(localconfigFile); }

			@Override ContentType getContentType() { return ContentType.PlainText; }
			@Override public String getContentAsText() {
				Vector<String> valueKeys = new Vector<>(data.values.keySet());
				valueKeys.sort(null);
				Iterator<String> iterator = valueKeys
						.stream()
						.map(key->String.format("%s: \"%s\"%n", key, data.values.get(key)))
						.iterator();
				return String.join("", (Iterable<String>)()->iterator);
			}
			
			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				if (data.rawData!=null)
					children.add( new RawVDFDataNode(this, "Raw VDF Data", data.rawData) );
				if (data.values!=null) {
				//	children.add(
				//		GroupingNode.create(
				//			this, "Values", data.values,
				//			Comparator.comparing(Map.Entry<String,String>::getKey),
				//			(parent, id, value) -> new PrimitiveValueNode(parent, id, value)
				//		)
				//	);
				}
				if (data.friends!=null) {
					Vector<Friend> vector = new Vector<>(data.friends);
					vector.sort(Comparator.<Friend,Long>comparing(friend->friend.id,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(friend->friend.idStr));
					for (Friend friend:vector)
						children.add( new FriendNode(this, friend) );
					//children.add(
					//	GroupingNode.create(
					//		this, "Friends", data.friends,
					//		Comparator.<Friend,Long>comparing(friend->friend.id,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(friend->friend.idStr),
					//		FriendNode::new
					//	)
					//);
				}
				return children;
			}
			
			static class FriendNode extends BaseTreeNode<TreeNode,TreeNode> {
				
				private final Friend friend;
				
				FriendNode(TreeNode parent, Friend friend) {
					super(parent,generateTitle(friend),true,false);
					this.friend = friend;
				}

				private static String generateTitle(Friend friend) {
					if (friend==null) return "(NULL) Friend ";
					String str = "Friend";
					if (friend.id  !=null) str += String.format(" %016X", friend.id);
					else                   str += String.format(" [%s]", friend.idStr);
					if (friend.name!=null) str += String.format(" \"%s\"", friend.name);
					if (friend.tag !=null) str += String.format(" (Tag:%s)", friend.tag);
					return str;
				}

				@Override
				protected Vector<? extends TreeNode> createChildren() {
					Vector<TreeNode> children = new Vector<>();
					if (friend.rawData!=null) children.add( new RawVDFDataNode(this, "Raw VDF Data", friend.rawData) );
					if (friend.avatar !=null) children.add( new PrimitiveValueNode(this, "avatar", friend.avatar) );
					if (friend.nameHistory!=null) {
						Comparator<Map.Entry<Integer,String>> sortOrder = Comparator.comparing(Map.Entry<Integer,String>::getKey).thenComparing(Map.Entry<Integer,String>::getValue);
						children.add( GroupingNode.create(this, "Name History", friend.nameHistory, sortOrder, (p,id,val)->new SimpleTextNode(p, "[%d] \"%s\"", id, val)) );
					}
					return children;
				}
			}
		}
		
		static class AchievementProgressNode extends BaseTreeNode<TreeNode,TreeNode> implements FileBasedNode, ExternViewableNode, TreeContentSource {
			
			private final AchievementProgress data;

			AchievementProgressNode(TreeNode parent, AchievementProgress data) {
				super(parent,generateTitle(data),true,false,TreeIcons.Achievement);
				this.data = data;
			}

			private static String generateTitle(AchievementProgress data) {
				String str = "Achievement Progress";
				if (data!=null) {
					if (data.version!=null) str += " V"+data.version;
				}
				return str;
			}

			@Override ContentType getContentType() { return data.sourceData!=null ? ContentType.DataTree : null; }
			@Override public TreeRoot getContentAsTree() {
				if (data.sourceData!=null) return FileSystem.JSON_File.JSON_TreeNode.create(data.sourceData, false);
				return null;
			}

			@Override public LabeledFile getFile() { return new LabeledFile(data.file); }
			@Override public ExternalViewerInfo getExternalViewerInfo() { return ExternalViewerInfo.TextEditor; }

			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				if (data.rawData!=null)
					children.add( new RawJsonDataNode(this, "Raw JSON Data", data.rawData, data.file) );
				if (data.gameStates!=null) {
					Vector<Integer> vec = new Vector<>(data.gameStates.keySet());
					vec.sort(createGameIdOrder());
					for (Integer gameID:vec)
						children.add(new GameStatusNode(this,gameID,data.gameStates.get(gameID)));
				}
				if (data.gameStates_withoutID!=null) {
					for (AchievementProgress.GameStatus gameStatus:data.gameStates_withoutID)
						children.add(new GameStatusNode(this,(Integer)null,gameStatus));
					
				}
				return children;
			}
			
			static class GameStatusNode extends BaseTreeNode<TreeNode,TreeNode> implements TextContentSource, TreeContentSource {

				private final AchievementProgress.GameStatus gameStatus;

				public GameStatusNode(TreeNode parent, Long playerID, AchievementProgress.GameStatus gameStatus) {
					super(parent,"by "+getPlayerName(playerID),true,false);
					this.gameStatus = gameStatus;
				}
				public GameStatusNode(TreeNode parent, Integer gameID, AchievementProgress.GameStatus gameStatus) {
					super(parent,getGameTitle(gameID),true,false,getGameIcon(gameID, TreeIcons.Folder));
					if (gameStatus!=null) {
						gameChangeListeners.add(gameID, new GameChangeListener() {
							@Override public TreeNode getTreeNode() { return GameStatusNode.this; }
							@Override public void gameTitleWasChanged() { setTitle(getGameTitle(gameID)); }
						});
					}
					this.gameStatus = gameStatus;
				}

				@Override ContentType getContentType() {
					if (gameStatus!=null) {
						if (gameStatus.hasParsedData) return ContentType.PlainText;
						if (gameStatus.rawData!=null) return ContentType.DataTree;
					}
					
					return null;
				}
				@Override public String getContentAsText() {
					if (gameStatus==null || !gameStatus.hasParsedData) return null;
					String str = "";
					str += String.format(Locale.ENGLISH, "Unlocked: %d/%d (%f%%)%n", gameStatus.unlocked, gameStatus.total, gameStatus.total==0 ? Double.NaN : gameStatus.unlocked/(double)gameStatus.total*100);
					str += String.format(Locale.ENGLISH, "Percentage: %f%n"  , gameStatus.percentage);
					str += String.format(Locale.ENGLISH, "All Unlocked: %s%n", gameStatus.allUnlocked);
					str += String.format(Locale.ENGLISH, "Cache Time: %d (%s)%n", gameStatus.cacheTime, getTimeStr(gameStatus.cacheTime*1000));
					return str;
				}
				@Override public TreeRoot getContentAsTree() {
					if (gameStatus==null || gameStatus.rawData==null) return null;
					return FileSystem.JSON_File.JSON_TreeNode.create(gameStatus.rawData, false);
				}

				@Override
				protected Vector<? extends TreeNode> createChildren() {
					Vector<TreeNode> children = new Vector<>();
					if (gameStatus!=null) {
						if (gameStatus.rawData!=null)
							children.add(new RawJsonDataNode   (this, "Raw JSON Data", gameStatus.rawData) );
						if (gameStatus.hasParsedData) {
							children.add(new SimpleTextNode    (this, "Unlocked: %d/%d (%f%%)", gameStatus.unlocked, gameStatus.total, gameStatus.total==0 ? Double.NaN : gameStatus.unlocked/(double)gameStatus.total*100) );
							children.add(new PrimitiveValueNode(this, "Percentage"  , gameStatus.percentage));
							children.add(new PrimitiveValueNode(this, "All Unlocked", gameStatus.allUnlocked));
							children.add(new SimpleTextNode    (this, "Cache Time: %d (%s)", gameStatus.cacheTime, getTimeStr(gameStatus.cacheTime*1000)));
						}
					}
					return children;
				}
			}
		}
		
		static class GameStateInfoNode extends BaseTreeNode<TreeNode,TreeNode> implements FileBasedNode, ExternViewableNode, TreeContentSource {
			
			private final GameStateInfo data;

			GameStateInfoNode(TreeNode parent, Long playerID, GameStateInfo gameStateInfo) {
				super(parent, "by "+getPlayerName(playerID)+generateExtraInfo(gameStateInfo), true, false);
				this.data = gameStateInfo;
			}
			GameStateInfoNode(TreeNode parent, Integer gameID, GameStateInfo gameStateInfo) {
				super(parent, getGameTitle(gameID)+generateExtraInfo(gameStateInfo), true, false, generateMergedIcon( getGameIcon(gameID, TreeIcons.Folder), gameStateInfo ));
				gameChangeListeners.add(gameID, new GameChangeListener() {
					@Override public TreeNode getTreeNode() { return GameStateInfoNode.this; }
					@Override public void gameTitleWasChanged() { setTitle(getGameTitle(gameID)+generateExtraInfo(gameStateInfo)); }
				});
				this.data = gameStateInfo;
			}

			private static Icon generateMergedIcon(Icon baseIcon, GameStateInfo gameStateInfo) {
				if (baseIcon==null) baseIcon = TreeIconsIS.getCachedIcon(TreeIcons.Folder);
				return IconSource.setSideBySide(
					true, 1, baseIcon,
					gameStateInfo.fullDesc    ==null ? IconSource.createEmptyIcon(16,16) : TreeIconsIS.getCachedIcon(TreeIcons.TextFile),
					gameStateInfo.badge       ==null ? IconSource.createEmptyIcon(16,16) : TreeIconsIS.getCachedIcon(TreeIcons.Badge),
					gameStateInfo.achievements==null ? IconSource.createEmptyIcon(16,16) : TreeIconsIS.getCachedIcon(TreeIcons.Achievement)
				);
			}
			
			private static String generateExtraInfo(GameStateInfo gameStateInfo) {
				String str = "";
				if (gameStateInfo.badge!=null) {
					String str1 = gameStateInfo.badge.getTreeNodeExtraInfo();
					if (str1!=null && !str1.isEmpty())
						str += (str.isEmpty()?"":", ") + str1;
				}
				if (gameStateInfo.achievements!=null) {
					String str1 = gameStateInfo.achievements.getTreeNodeExtraInfo();
					if (str1!=null && !str1.isEmpty())
						str += (str.isEmpty()?"":", ") + str1;
				}
				return str.isEmpty() ? "" : " ("+str+")";
			}
			
			@Override public LabeledFile getFile() { return new LabeledFile(data.file); }
			@Override public ExternalViewerInfo getExternalViewerInfo() { return ExternalViewerInfo.TextEditor; }

			@Override ContentType getContentType() { return ContentType.DataTree; }
			@Override public TreeRoot getContentAsTree() {
				if (data.sourceData==null) return null;
				return FileSystem.JSON_File.JSON_TreeNode.create(data.sourceData, false);
			}
			
			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				GroupingNode<GameStateInfo.Block> groupingNode;
				if (data.fullDesc!=null) {
					children.add(new TextContentNode(this, "Full Game Description", data.fullDesc, TreeIcons.TextFile));
				}
				if (data.shortDesc!=null) {
					children.add(new TextContentNode(this, "Short Game Description", data.shortDesc, TreeIcons.TextFile));
				}
				if (data.badge!=null) {
					children.add(new BadgeNode(this, data.badge));
				}
				if (data.achievements!=null) {
					children.add(new AchievementsNode(this, data.achievements));
				}
				if (data.blocks!=null) {
					children.add(groupingNode = GroupingNode.create(this, "Raw Blocks", data.blocks, null, BlockNode::new));
					groupingNode.setFileSource(data.file,ExternalViewerInfo.TextEditor);
				}
				if (data.rawData!=null)
					children.add( new RawJsonDataNode(this, "Raw JSON Data", data.rawData, data.file) );
				return children;
			}

			static class AchievementsNode extends BaseTreeNode<TreeNode,TreeNode> {

				private final Achievements achievements;

				public AchievementsNode(TreeNode parent, GameStateInfo.Achievements achievements) {
					super(parent, generateTitle(achievements), true, false, TreeIcons.Achievement);
					this.achievements = achievements;
				}
				
				private static String generateTitle(GameStateInfo.Achievements achievements) {
					if (achievements==null) return "(NULL) Achievements";
					String str = "Achievements";
					if (achievements.hasParsedData)
						str += String.format(" (achieved: %d/%d)", achievements.achieved, achievements.total);
					else if (achievements.rawData!=null)
						str += " [ Raw Data ]";
					return str;
				}
				
				@Override protected Vector<? extends TreeNode> createChildren() {
					Vector<TreeNode> children = new Vector<>();
					if (achievements.rawData!=null)
						children.add(new RawJsonDataNode   (this, "raw data"   , achievements.rawData   ));
					if (achievements.hasParsedData) {
						if (achievements.achievedHidden!=null) children.add(GroupingNode.create(this, "Achieved (hidden)", achievements.achievedHidden, null, AchievementNode::new));
						if (achievements.highlight     !=null) children.add(GroupingNode.create(this, "Highlight"        , achievements.highlight     , null, AchievementNode::new));
						if (achievements.unachieved    !=null) children.add(GroupingNode.create(this, "Unachieved"       , achievements.unachieved    , null, AchievementNode::new));
					}
					return children;
				}

				static class AchievementNode extends BaseTreeNode<TreeNode,TreeNode> implements ImageNTextContentSource, TreeContentSource {
					
					private GameStateInfo.Achievements.Achievement achievement;
					
					AchievementNode(TreeNode parent, GameStateInfo.Achievements.Achievement achievement) {
						super(parent, generateTitle(achievement), true, false, TreeIcons.Achievement);
						this.achievement = achievement;
					}
					
					private static String generateTitle(GameStateInfo.Achievements.Achievement achievement) {
						if (achievement==null) return "(NULL) Achievement";
						String str = "Achievement";
						if (achievement.hasParsedData) {
							str = achievement.name;
							if (achievement.isAchieved) str = "[+] "+str;
						} else if (achievement.rawData!=null)
							str += " [ Raw Data ]";
						return str;
					}
					
					@Override ContentType getContentType() {
						if (achievement.hasParsedData) return ContentType.ImageNText;
						if (achievement.rawData!=null) return ContentType.DataTree;
						return null;
					}

					@Override
					public BufferedImage getContentAsImage() {
						return readImageFromURL(achievement.image,"achievement image");
					}

					@Override
					public String getContentAsText() {
						if (achievement.hasParsedData) {
							String str = "";
							str += String.format(Locale.ENGLISH, "%s: "+"\"%s\"" +"%n" , "Name"       , achievement.name       );
							str += String.format(Locale.ENGLISH, "%s: "+  "%s"   +"%n" , "Achieved"   , achievement.isAchieved);
							str += String.format(Locale.ENGLISH, "%s: "+"\"%s\"" +"%n" , "Description", achievement.description);
							str += String.format(Locale.ENGLISH, "%s: "+"\"%s\"" +"%n" , "ID"         , achievement.id         );
							str += String.format(Locale.ENGLISH, "%s: "+"\"%s\"" +"%n" , "Image"      , achievement.image      );
							str += String.format(Locale.ENGLISH, "%s: "+"%d (%s)"+"%n", "Unlocked"   , achievement.unlocked, achievement.unlocked==0 ? "not yet" : getTimeStr(achievement.unlocked*1000));
							if (achievement.achievedRatio!=null)
								str += String.format(Locale.ENGLISH, "%s: %f%n" , "Achieved Ratio", achievement.achievedRatio);
							return str;
						}
						return null;
					}

					@Override
					public TreeRoot getContentAsTree() {
						if (achievement.rawData==null) return null;
						return FileSystem.JSON_File.JSON_TreeNode.create(achievement.rawData, false);
					}

					@Override protected Vector<? extends TreeNode> createChildren() {
						Vector<TreeNode> children = new Vector<>();
						if (achievement.rawData!=null)
							children.add(new RawJsonDataNode   (this, "raw data"   , achievement.rawData    ));
						if (achievement.hasParsedData) {
							children.add(new PrimitiveValueNode(this, "Description", achievement.description));
							children.add(new PrimitiveValueNode(this, "ID"         , achievement.id         ));
							children.add(new SimpleTextNode    (this, "Unlocked: %d (%s)", achievement.unlocked, achievement.unlocked==0 ? "not yet" : getTimeStr(achievement.unlocked*1000)));
							if (achievement.achievedRatio!=null)
								children.add(new PrimitiveValueNode(this, "Achieved Ratio", achievement.achievedRatio));
							children.add(new ImageUrlNode      (this, "Image"      , achievement.image      ));
						}
						return children;
					}
				}
				
			}

			static class BadgeNode extends BaseTreeNode<TreeNode,TreeNode> implements ImageContentSource, ExternViewableNode, URLBasedNode {

				private final GameStateInfo.Badge badge;

				public BadgeNode(TreeNode parent, GameStateInfo.Badge badge) {
					super(parent, generateTitle(badge), true, false, TreeIcons.Badge);
					this.badge = badge;
				}
				
				private static String generateTitle(GameStateInfo.Badge badge) {
					if (badge==null) return "(NULL) Badge";
					String str = "Badge";
					if (badge.hasParsedData) {
						str += String.format("\"%s\"", badge.name);
						str += String.format(" (Level: %d/%d, XP: %d)", badge.currentLevel, badge.maxLevel, badge.currentXP);
					}
					return str;
				}

				@Override public String getURL() { return badge.iconURL; }
				@Override public ExternalViewerInfo getExternalViewerInfo() { return ExternalViewerInfo.Browser; }

				@Override ContentType getContentType() { return ContentType.Image; }
				@Override public BufferedImage getContentAsImage() { return readImageFromURL(badge.iconURL,"badge icon"); }

				@Override protected Vector<? extends TreeNode> createChildren() {
					Vector<TreeNode> children = new Vector<>();
					GroupingNode<?> gnode;
					
					if (badge.rawData!=null)
						children.add(new RawJsonDataNode   (this, "raw data"       , badge.rawData      ));
					
					if (badge.hasParsedData) {
						children.add(new SimpleTextNode(this, "Next Level: \"%s\", XP: %d", badge.nextLevelName, badge.nextLevelXP));
						
						//children.add(new PrimitiveValueNode(this, "name"           , badge.name         ));
						children.add(new PrimitiveValueNode(this, "has badge data" , badge.hasBadgeData ));
						//children.add(new PrimitiveValueNode(this, "max level"      , badge.maxLevel     ));
						//children.add(new PrimitiveValueNode(this, "current level"  , badge.currentLevel ));
						//children.add(new PrimitiveValueNode(this, "current XP"     , badge.currentXP    ));
						//children.add(new PrimitiveValueNode(this, "next level name", badge.nextLevelName));
						//children.add(new PrimitiveValueNode(this, "next level XP"  , badge.nextLevelXP  ));
						children.add(new ImageUrlNode      (this, "icon URL"       , badge.iconURL      ));
						children.add(gnode = GroupingNode.create(this, "Trading Cards", badge.tradingCards, null, TradingCardNode::new));
						gnode.setFileSource(new FilePromise("HTML-Overview", ()->badge.createTempTradingCardsOverviewHTML()), ExternalViewerInfo.Browser);
					}
					return children;
				}
			}

			static class TradingCardNode extends BaseTreeNode<TreeNode,TreeNode> implements ImageContentSource, ExternViewableNode, URLBasedNode {
				
				private final GameStateInfo.Badge.TradingCard tradingCard;
				
				TradingCardNode(TreeNode parent, GameStateInfo.Badge.TradingCard tradingCard) {
					super(parent, generateTitle(tradingCard), true, false);
					this.tradingCard = tradingCard;
				}
				
				private static String generateTitle(GameStateInfo.Badge.TradingCard tradingCard) {
					if (tradingCard==null)
						return "(NULL) Trading Card";
					if (!tradingCard.hasParsedData)
						return "Trading Card"+(tradingCard.rawData!=null ? " [Raw Data]" : "");
					
					String str = String.format("Trading Card \"%s\"", tradingCard.name);
					if (tradingCard.owned!=0) str += String.format(" (%d)", tradingCard.owned);
					return str;
				}

				@Override public String getURL() { return tradingCard.imageURL; }
				@Override public ExternalViewerInfo getExternalViewerInfo() { return ExternalViewerInfo.Browser; }

				@Override ContentType getContentType() { return ContentType.Image; }
				@Override public BufferedImage getContentAsImage() { return readImageFromURL(tradingCard.imageURL,"trading card image"); }
			
				@Override protected Vector<? extends TreeNode> createChildren() {
					Vector<TreeNode> children = new Vector<>();
					if (tradingCard.rawData!=null)
						children.add(new RawJsonDataNode   (this, "raw data"   , tradingCard.rawData   ));
					if (tradingCard.hasParsedData) {
						children.add(new PrimitiveValueNode(this, "name"       , tradingCard.name      ));
						children.add(new PrimitiveValueNode(this, "title"      , tradingCard.title     ));
						children.add(new PrimitiveValueNode(this, "owned"      , tradingCard.owned     ));
						children.add(new ImageUrlNode      (this, "artwork URL", tradingCard.artworkURL));
						children.add(new ImageUrlNode      (this, "image URL"  , tradingCard.imageURL  ));
						children.add(new PrimitiveValueNode(this, "market hash", tradingCard.marketHash));
					}
					return children;
				}
			}

			static class BlockNode extends BaseTreeNode<TreeNode,TreeNode> {
				
				private final GameStateInfo.Block block;
			
				BlockNode(TreeNode parent, GameStateInfo.Block block) {
					super(parent, generateTitle(block), true, false);
					this.block = block;
				}
			
				private static String generateTitle(GameStateInfo.Block block) {
					if (block==null) return "Block ???";
					if (block.rawData!=null) return "Block "+block.blockIndex+" (RawData)";
					return "["+block.blockIndex+"] "+block.label;
				}
			
				@Override protected Vector<? extends TreeNode> createChildren() {
					Vector<TreeNode> children = new Vector<>();
					if (block.rawData!=null)
						children.add(new RawJsonDataNode(this, "raw data", block.rawData));
					else {
						children.add(new PrimitiveValueNode(this, "version", block.version));
						if (block.dataValue!=null)
							children.add(new RawJsonDataNode(this, "data", block.dataValue));
					}
					return children;
				}
			}
		}

		static class ScreenShotNode extends BaseTreeNode<TreeNode,TreeNode> implements FileBasedNode, ExternViewableNode, ImageContentSource {
			
			private final ScreenShot screenShot;
			ScreenShotNode(TreeNode parent, ScreenShot screenShot) {
				super(parent, screenShot.image.getName(), false, true, TreeIcons.ImageFile);
				this.screenShot = screenShot;
			}
		
			@Override ContentType getContentType() { return ContentType.Image; }
			@Override public BufferedImage getContentAsImage() {
				try {
					return ImageIO.read(screenShot.image);
				} catch (IOException e) {
					System.out.printf("IOException while reading image \"%s\": %s%n", screenShot.image.getAbsolutePath(), e.getMessage());
					return null;
				}
			}
		
			@Override public ExternalViewerInfo getExternalViewerInfo() { return ExternalViewerInfo.ImageViewer; }
			@Override public LabeledFile getFile() { return new LabeledFile(screenShot.image); }
			@Override protected Vector<? extends TreeNode> createChildren() { return null; }
		}
		
		/*
		static class ScreenShotsNode<IDType> extends BaseTreeNode<TreeNode,FileSystem.FolderNode> {

			private final HashMap<IDType, Vector<ScreenShot>> screenShots;
			private final Function<IDType, String> getSubFolderName;
			private final Function<IDType, Icon> getSubFolderIcon;
			private final Comparator<IDType> subFolderOrder;

			public ScreenShotsNode(TreeNode parent, HashMap<IDType, Vector<ScreenShot>> screenShots, Function<IDType,String> getSubFolderName) {
				this(parent, screenShots, getSubFolderName, null, null);
			}
			public ScreenShotsNode(TreeNode parent, HashMap<IDType, Vector<ScreenShot>> screenShots, Function<IDType,String> getSubFolderName, Comparator<IDType> subFolderOrder) {
				this(parent, screenShots, getSubFolderName, null, subFolderOrder);
			}
			public ScreenShotsNode(TreeNode parent, HashMap<IDType, Vector<ScreenShot>> screenShots, Function<IDType,String> getSubFolderName, Function<IDType,Icon> getSubFolderIcon) {
				this(parent, screenShots, getSubFolderName, getSubFolderIcon, null);
			}
			public ScreenShotsNode(TreeNode parent, HashMap<IDType, Vector<ScreenShot>> screenShots, Function<IDType,String> getSubFolderName, Function<IDType,Icon> getSubFolderIcon, Comparator<IDType> subFolderOrder) {
				super(parent, "ScreenShots", true, screenShots==null || screenShots.isEmpty());
				this.screenShots = screenShots;
				this.getSubFolderName = getSubFolderName;
				this.getSubFolderIcon = getSubFolderIcon;
				this.subFolderOrder = subFolderOrder;
			}

			@Override
			protected Vector<? extends FolderNode> createChildren() {
				Vector<FolderNode> children = new Vector<>();
				
				if (subFolderOrder!=null) {
					Vector<Map.Entry<IDType, Vector<ScreenShot>>> vec = new Vector<>(screenShots.entrySet());
					vec.sort(Comparator.comparing(Map.Entry<IDType, Vector<ScreenShot>>::getKey,subFolderOrder));
					vec.forEach(entry->{
						children.add(createScreenShotsFolderNode(entry.getKey(), entry.getValue()));
					});
					
				} else
					screenShots.forEach((id,screenShotsList)->{
						children.add(createScreenShotsFolderNode(id, screenShotsList));
					});
				
				return children;
			}

			private FileSystem.FolderNode createScreenShotsFolderNode(IDType id, Vector<ScreenShot> screenShotsList) {
				Comparator<ScreenShot> order = Comparator.<ScreenShot,String>comparing(scrnsht->scrnsht.image.getName());
				File[] files = screenShotsList.stream().sorted(order).map(scrnsht->scrnsht.image).toArray(File[]::new);
				String title = getSubFolderName.apply(id);
				Icon icon = getSubFolderIcon!=null ? getSubFolderIcon.apply(id) : null;
				if (icon==null) icon = TreeIconsIS.getCachedIcon(TreeIcons.ImageFile);
				FileSystem.FolderNode folderNode = new FileSystem.FolderNode(this, title, files, icon);
				return folderNode;
			}
		}
		*/
		
	}
	
	static class FileSystem {
		
		static class Root extends BaseTreeNode<TreeNode,TreeNode> {
		
			Root() { super(null, "FolderStructure.Root", true, false); }
		
			@Override
			protected Vector<TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				
				Vector<File> steamAppsFolder = new Vector<>(); 
				KnownFolders.forEachSteamAppsFolder((i,folder)->{
					if (folder!=null && folder.isDirectory())
						steamAppsFolder.add(folder);
				});
				if (!steamAppsFolder.isEmpty()) {
					children.add(new AppManifestsRoot(this,steamAppsFolder));
					children.add(new FolderRoot(this,"SteamApps Folder",steamAppsFolder,File::getAbsolutePath));
				}
				
				File folder;
				
				folder = KnownFolders.getSteamClientFolder();
				if (folder!=null && folder.isDirectory()) children.add(new FolderRoot(this,"SteamClient Folder",folder));
				
				folder = KnownFolders.getSteamClientSubFolder(KnownFolders.SteamClientSubFolders.USERDATA);
				if (folder!=null && folder.isDirectory()) children.add(new FolderRoot(this,"<UserData>",folder));
				
				folder = KnownFolders.getSteamClientSubFolder(KnownFolders.SteamClientSubFolders.APPCACHE);
				if (folder!=null && folder.isDirectory()) children.add(new FolderRoot(this,"<AppCache>",folder));
				
				folder = KnownFolders.getSteamClientSubFolder(KnownFolders.SteamClientSubFolders.APPCACHE_LIBRARYCACHE);
				if (folder!=null && folder.isDirectory()) {
					children.add(new FolderRoot(this,"<LibraryCache>",folder));
					children.add(new GameImagesRoot(this,folder));
				}
				
				folder = KnownFolders.getSteamClientSubFolder(KnownFolders.SteamClientSubFolders.STEAM_GAMES);
				if (folder!=null && folder.isDirectory()) children.add(new FolderRoot(this,"Game Icons (as Folder)",folder));
				
				folder = FOLDER_TEST_FILES;
				try { folder = folder.getCanonicalFile(); } catch (IOException e) {}
				if (folder.isDirectory()) children.add(new FolderRoot(this,"Test Files",folder));
				
				return children;
			}
		
		}

		static class GameImagesRoot extends BaseTreeNode<TreeNode,TreeNode> {
		
			private File folder;

			GameImagesRoot(TreeNode parent, File folder) {
				super(parent, "Game Images", true, false, TreeIcons.RootFolder);
				this.folder = folder;
			}

			@Override
			public String toString() {
				return String.format("%s [%s]", super.toString(), folder.getAbsolutePath());
			}

			@Override
			protected Vector<? extends TreeNode> createChildren() {
				
				GameImages gameImages = new GameImages(folder);
				
				Vector<Integer> keySet1 = gameImages.getSortedGameIDs();
				Vector<String>  keySet2 = gameImages.getSortedImageTypes();
				
				Vector<TreeNode> children = new Vector<>();
				children.add(new ImageGroup1<Integer, String>(this, "Images (by Game)" ,  true, keySet1, keySet2, gameImages::getImageFile));
				children.add(new ImageGroup1<String, Integer>(this, "Images (by Label)", false, keySet2, keySet1, gameImages::getImageFile));
				children.add(new FolderNode(this, "Other Images", gameImages.imageFiles, TreeIcons.Folder));
				children.add(new FolderNode(this, "Other Files", gameImages.otherFiles, TreeIcons.Folder));
				return children;
			}

			static class ImageGroup1<KeyType1,KeyType2> extends BaseTreeNode<TreeNode,TreeNode> {

				private final Collection<KeyType1> keys1;
				private final Collection<KeyType2> keys2;
				private final BiFunction<KeyType1, KeyType2, File> getFile;
				private final boolean showNullValues;

				protected ImageGroup1(TreeNode parent, String title, boolean showNullValues, Collection<KeyType1> keys1, Collection<KeyType2> keys2, BiFunction<KeyType1,KeyType2,File> getFile) {
					super(parent, title, true, false, TreeIcons.Folder);
					this.showNullValues = showNullValues;
					this.keys1 = keys1;
					this.keys2 = keys2;
					this.getFile = getFile;
				}

				@Override
				protected Vector<? extends TreeNode> createChildren() {
					Vector<TreeNode> children = new Vector<>();
					for (KeyType1 k1:keys1)
						children.add(new ImageGroup2<KeyType2>(this, k1.toString(), showNullValues, TreeIcons.Folder, keys2, k2->getFile.apply(k1,k2)));
					return children;
				}
			}

			static class ImageGroup2<KeyType> extends BaseTreeNode<TreeNode,TreeNode> {

				private final Collection<KeyType> keys;
				private final Function<KeyType, File> getFile;
				private final boolean showNullValues;

				protected ImageGroup2(TreeNode parent, String title, boolean showNullValues, TreeIcons icon, Collection<KeyType> keys, Function<KeyType,File> getFile) {
					super(parent, title, true, false, icon);
					this.showNullValues = showNullValues;
					this.keys = keys;
					this.getFile = getFile;
				}

				@Override
				protected Vector<? extends TreeNode> createChildren() {
					Vector<TreeNode> children = new Vector<>();
					for (KeyType key:keys) {
						File file = getFile.apply(key);
						if (file!=null)
							children.add(new ImageFile(this, file));
						else if (showNullValues)
							children.add(new SimpleTextNode(this, "no \"%s\"", key.toString()));
					}
					return children;
				}
			}
		}

		static class AppManifestsRoot extends BaseTreeNode<TreeNode,TreeNode> {
		
			private final File folder;
			private final Vector<File> folders;

			AppManifestsRoot(TreeNode parent, String title, File folder) {
				super(parent, title, folder!=null, folder==null, TreeIcons.Folder);
				this.folder = folder;
				this.folders = null;
			}
		
			public AppManifestsRoot(Root parent, Vector<File> folders) {
				super(parent, "AppManifests", folders!=null, folders==null || folders.isEmpty(), TreeIcons.RootFolder);
				this.folder = null;
				this.folders = folders;
			}

			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				
				if (folders!=null) {
					for (File folder:folders)
						children.add(new AppManifestsRoot(this, folder.getAbsolutePath(), folder));
					
				} else if (folder!=null) {
					File[] files = folder.listFiles((FileFilter) AppManifestNode::is);
					Arrays.sort(files,Comparator.comparing(AppManifest::getAppIDFromFile, Comparator.nullsLast(Comparator.naturalOrder())));
					for (File file:files)
						children.add(new AppManifestNode(this, file));
				}
				
				return children;
			}
			
		}

		static class FolderRoot extends FolderNode {
			private final String rootTitle;
			
			FolderRoot(TreeNode parent, String rootTitle, File folder) {
				this(parent, rootTitle, folder, TreeIcons.RootFolder_Simple);
			}
			FolderRoot(TreeNode parent, String rootTitle, File folder, TreeIcons icon) {
				super(parent, folder, icon);
				this.rootTitle = rootTitle;
			}
			FolderRoot(TreeNode parent, String rootTitle, Vector<File> folders) {
				this(parent, rootTitle, folders, null);
			}
			FolderRoot(TreeNode parent, String rootTitle, Vector<File> folders, Function<File,String> getNodeTitle) {
				super(parent, rootTitle, folders, getNodeTitle, TreeIcons.RootFolder_Simple);
				this.rootTitle = rootTitle;
			}
			@Override public String toString() {
				if (fileObj!=null) return String.format("%s [%s]", rootTitle, fileObj.getAbsolutePath());
				return rootTitle;
			}
		}

		static abstract class FileSystemNode extends BaseTreeNode<TreeNode,FileSystemNode> implements FileBasedNode {
			
			protected final File fileObj;
			
			protected FileSystemNode(TreeNode parent, File file, String title, boolean allowsChildren, boolean isLeaf) {
				this(parent, file, title, allowsChildren, isLeaf, (Icon)null);
			}
			protected FileSystemNode(TreeNode parent, File file, String title, boolean allowsChildren, boolean isLeaf, Icon icon) {
				super(parent, title, allowsChildren, isLeaf, icon);
				this.fileObj = file;
			}
			protected FileSystemNode(TreeNode parent, File file, String title, boolean allowsChildren, boolean isLeaf, TreeIcons icon) {
				super(parent, title, allowsChildren, isLeaf, icon);
				this.fileObj = file;
			}
			@Override public LabeledFile getFile() {
				return fileObj!=null ? new LabeledFile(fileObj) : null;
			}
		}

		static class FolderNode extends FileSystemNode {
			
			protected final File[] files;
			protected final boolean keepFileOrder;
			protected final Function<File, String> getNodeTitle;
			protected final Function<File, Icon> getNodeIcon;

			FolderNode(TreeNode parent, File folder) {
				this(parent, null, folder, TreeIcons.Folder);
			}
			FolderNode(TreeNode parent, String title, File folder) {
				this(parent, title, folder, TreeIcons.Folder);
			}
			FolderNode(TreeNode parent, File folder, TreeIcons icon) {
				this(parent, null, folder, icon);
			}
			FolderNode(TreeNode parent, File folder, Icon icon) {
				this(parent, null, folder, icon);
			}
			FolderNode(TreeNode parent, String title, File folder, TreeIcons icon) {
				this(parent, title, folder, TreeIconsIS.getCachedIcon(icon));
			}
			FolderNode(TreeNode parent, String title, File folder, Icon icon) {
				super(parent, folder, title==null ? folder.getName() : title, true, false, icon);
				this.files = null;
				this.keepFileOrder = false;
				this.getNodeTitle = null;
				this.getNodeIcon = null;
			}
			FolderNode(TreeNode parent, String title, Collection<File> files, TreeIcons icon) {
				this(parent, title, files, null, icon);
			}
			FolderNode(TreeNode parent, String title, Collection<File> files, Function<File,String> getNodeTitle, TreeIcons icon) {
				this(parent, title, files==null ? null : files.toArray(new File[files.size()]), false, getNodeTitle, icon);
			}
			FolderNode(TreeNode parent, String title, File[] files, Icon icon) {
				this(parent, title, files, false, null, icon);
			}
			FolderNode(TreeNode parent, String title, File[] files, TreeIcons icon) {
				this(parent, title, files, false, null, icon);
			}
			FolderNode(TreeNode parent, String title, File[] files, boolean keepFileOrder, Function<File,String> getNodeTitle, TreeIcons icon) {
				this(parent, title, files, keepFileOrder, getNodeTitle, TreeIconsIS.getCachedIcon(icon));
			}
			FolderNode(TreeNode parent, String title, File[] files, boolean keepFileOrder, Function<File,String> getNodeTitle, Icon icon) {
				this(parent, title, files, keepFileOrder, getNodeTitle, null, icon);
			}
			FolderNode(TreeNode parent, String title, File[] files, boolean keepFileOrder, Function<File,String> getNodeTitle, Function<File,Icon> getNodeIcon, TreeIcons icon) {
				this(parent, title, files, keepFileOrder, getNodeTitle, getNodeIcon, TreeIconsIS.getCachedIcon(icon));
			}
			FolderNode(TreeNode parent, String title, File[] files, boolean keepFileOrder, Function<File,String> getNodeTitle, Function<File,Icon> getNodeIcon, Icon icon) {
				super(parent, null, title, true, files==null || files.length==0, icon);
				this.files = files;
				this.keepFileOrder = keepFileOrder;
				this.getNodeTitle = getNodeTitle;
				this.getNodeIcon = getNodeIcon;
			}
		
			@Override
			protected Vector<? extends FileSystemNode> createChildren() {
				File[] files_ = files!=null ? files : getFilesAndFolders(fileObj);
				if (!keepFileOrder) sortFiles(files_);
				return createNodes(this,files_,getNodeTitle,getNodeIcon);
			}
			
			static void sortFiles(File[] files) {
				//Comparator<String> fileNameAsNumber = Comparator.comparing(FolderNode::parseNumber, Comparator.nullsLast(Comparator.naturalOrder()));
				//Comparator<String> fileNameComparator = fileNameAsNumber.thenComparing(String::toLowerCase);
				Comparator<FileNameNExt> splitted = Comparator.comparing((FileNameNExt sfn)->parseNumber(sfn.name), Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Comparator.naturalOrder());
				Comparator<String> fileNameComparator = Comparator.comparing(FileNameNExt::create, splitted);
				Comparator<File> fileComparator = Comparator.comparing(File::isFile).thenComparing(File::getName, fileNameComparator);
				Arrays.sort(files,fileComparator);
				
//				Arrays.sort(files, (f1,f2) -> {
//					//f1.isDirectory();
//					String name1 = f1.getName();
//					String name2 = f2.getName();
//					Integer n1 = parseNumber(name1);
//					Integer n2 = parseNumber(name2);
//					if (n1!=null && n2!=null) return n1.intValue() - n2.intValue();
//					if (n1==null && n2==null) return name1.compareToIgnoreCase(name2);
//					return n1!=null ? -1 : +1;
//				});
			}
			
			static Vector<? extends FileSystemNode> createNodes(TreeNode parent, File[] files, Function<File, String> getNodeTitle, Function<File, Icon> getNodeIcon) {
				Vector<FileSystemNode> children = new Vector<>();
				
				for (File file:files) {
					if (file.isDirectory()) {
						
						String title = getNodeTitle!=null ? getNodeTitle.apply(file) : null;
						
						Icon icon = getNodeIcon!=null ? getNodeIcon.apply(file) : null;
						if (icon==null) icon = TreeIconsIS.getCachedIcon(TreeIcons.Folder);
						
						children.add(new FolderNode(parent, title, file, icon));
						
					} else if (file.isFile()) {
						
						if (TextFile.is(file))
							children.add(new TextFile(parent, file));
						
						else if (VDF_File.is(file))
							children.add(new VDF_File(parent, file));
						
						else if (JSON_File.is(file))
							children.add(new JSON_File(parent, file));
						
						else if (AppManifestNode.is(file))
							children.add(new AppManifestNode(parent, file));
						
						else if (ImageFile.is(file))
							children.add(new ImageFile(parent, file));
							
						else
							children.add(new FileNode(parent, file));
					}
				}
				
				return children;
			}
		}

		static class FileNode extends FileSystemNode implements ByteFileSource, ExternViewableNode {
		
			protected byte[] byteContent;
			private final ExternalViewerInfo externalViewerInfo;
			
			FileNode(TreeNode parent, File file) {
				this(parent, file, TreeIcons.GeneralFile, null);
			}
			protected FileNode(TreeNode parent, File file, TreeIcons icon, ExternalViewerInfo externalViewerInfo) {
				super(parent, file, file.getName(), false, true, icon);
				this.externalViewerInfo = externalViewerInfo;
				this.byteContent = null;
				if (!fileObj.isFile())
					throw new IllegalStateException("Can't create a FileSystem.FileNode from nonexisting file or nonfile");
			}
			
			@Override public boolean isLarge() { return getFileSize()>400000; }
			@Override public long getFileSize() { return fileObj.length(); }
			
			static Icon getIconForFile(String filename) {
				return new JFileChooser().getIcon(new File(filename));
			}

			@Override
			public String toString() {
				return String.format("%s (%s)", fileObj.getName(), getSizeStr(fileObj));
			}

			@Override protected Vector<FileSystemNode> createChildren() {
				throw new UnsupportedOperationException("Call of FileSystem.FileNode.createChildren() is not supported.");
			}

			@Override public ExternalViewerInfo getExternalViewerInfo() {
				return externalViewerInfo;
			}
			
			@Override ContentType getContentType() {
				return ContentType.Bytes;
			}

			@Override public byte[] getContentAsBytes() {
				if (byteContent != null) return byteContent;
				return byteContent = readBytesUncached();
			}
			
			protected byte[] readBytesUncached() {
				try {
					return Files.readAllBytes(fileObj.toPath());
				} catch (IOException e) {
					return null;
				}
			}
		}

		static class ImageFile extends FileNode implements ImageContentSource {
			
			private BufferedImage imageContent;

			ImageFile(TreeNode parent, File file) {
				super(parent, file, TreeIcons.ImageFile, ExternalViewerInfo.ImageViewer);
				imageContent = null;
			}
			
			static boolean is(File file) {
				return isImageFile(file);
			}

			@Override ContentType getContentType() {
				return ContentType.Image;
			}

			@Override
			public BufferedImage getContentAsImage() {
				if (imageContent!=null) return imageContent;
				byte[] bytes = getContentAsBytes();
				if (bytes==null) return null;
				try (ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes)) {
					imageContent = ImageIO.read(byteStream);
					if (imageContent==null)
						imageContent = createImageOfMessage("Can't read image.",200,25,Color.RED);
				} catch (IOException e) {
					e.printStackTrace();
				}
				return imageContent;
			}
		}

		static class TextFile extends FileNode implements ExtendedTextFileSource {
			
			protected final Charset charset;
			protected String textContent;

			TextFile(TreeNode parent, File file) {
				this(parent, file, TreeIcons.TextFile, null);
			}

			protected TextFile(TreeNode parent, File file, TreeIcons icon, Charset charset) {
				super(parent, file, icon, ExternalViewerInfo.TextEditor);
				this.charset = charset;
				this.textContent = null;
			}

			static boolean is(File file) {
				return fileNameEndsWith(file, ".txt");
			}

			@Override ContentType getContentType() {
				return ContentType.ByteBasedText;
			}
		
			@Override public String getContentAsText() {
				if (textContent!=null) return textContent;
				byte[] bytes = getContentAsBytes();
				if (bytes==null) return "Can't read content";
				return textContent = charset!=null ? new String(bytes, charset) : new String(bytes);
			}
		}
		
		static class JSON_File extends TextFile implements ParsedTextFileSource {
			
			private static final DataTreeNodeContextMenu contextMenu = new DataTreeNodeContextMenu();
			private JSON_Data.Value<NV,V> parseResult;

			JSON_File(TreeNode parent, File file) {
				this(parent, file, TreeIcons.JSONFile);
			}
			protected JSON_File(TreeNode parent, File file, TreeIcons icon) {
				super(parent, file, icon, StandardCharsets.UTF_8);
				parseResult = null;
			}

			static boolean is(File file) {
				return fileNameEndsWith(file,".json");
			}
			
			@Override ContentType getContentType() {
				return ContentType.ParsedByteBasedText;
			}
			
			@Override
			public TreeRoot getContentAsTree() {
				if (parseResult==null) {
					String text = getContentAsText();
					if (text==null) return null;
					JSON_Parser<NV,V> parser = new JSON_Parser<>(text,null);
					try {
						parseResult = parser.parse_withParseException();
					} catch (JSON_Parser.ParseException e) {
						System.err.printf("ParseException: %s%n", e.getMessage());
						//e.printStackTrace();
						return SimpleTextNode.createSingleTextLineTree("Parse Error: %s", e.getMessage());
					}
					if (parseResult==null)
						// return null;
						return SimpleTextNode.createSingleTextLineTree("Parse Error: Parser returns <null>");
				}
				return JSON_TreeNode.create(parseResult,isLarge());
			}
			
			static class JSON_TreeNode<ValueType> extends BaseTreeNode<JSON_TreeNode<?>,JSON_TreeNode<?>> implements DataTreeNode {

				private final Vector<ValueType> childValues;
				private final Function<ValueType, String> getName;
				private final Function<ValueType, JSON_Data.Value<NV,V>> getValue;
				final String name;
				final JSON_Data.Value<NV,V> value;

				private JSON_TreeNode(JSON_TreeNode<?> parent, String title, JsonTreeIcons icon, String name, JSON_Data.Value<NV,V> value, Vector<ValueType> childValues, Function<ValueType,String> getName, Function<ValueType,JSON_Data.Value<NV,V>> getValue) {
					super(parent, title, childValues!=null, childValues==null || childValues.isEmpty(), icon==null ? null : JsonTreeIconsIS.getCachedIcon(icon));
					this.name = name;
					this.value = value;
					this.childValues = childValues;
					this.getName = getName;
					this.getValue = getValue;
					if (this.value==null) throw new IllegalArgumentException("JSON_TreeNode( ... , value == null, ... ) is not allowed");
				}

				@Override public String getPath() {
					if (parent==null) {
						if (name!=null)
							return name;
						return "<Root"+(value!=null ? value.type : "")+">";
					}
					JSON_Data.Value.Type parentType = parent.getValueType();
					String indexInParent = parentType==JSON_Data.Value.Type.Array ? "["+parent.getIndex(this)+"]" : "";
					String nameRef = name!=null ? "."+name : "";
					return parent.getPath()+indexInParent+nameRef;
				}
				
				JSON_Data.Value<NV,V> getSubNodeValue(Object... path) {
					try {
						return JSON_Data.getSubNode(value, path);
					} catch (JSON_Data.TraverseException e) {
						return null;
					}
				}

				@Override public String getAccessCall() {
					return String.format("<root>.getSubNode|Value(%s)", getAccessPath());
				}
				private String getAccessPath() {
					if (parent==null) return "";
					String path = parent.getAccessPath();
					if (!path.isEmpty()) path += ",";
					JSON_Data.Value.Type parentType = parent.getValueType();
					return path + (parentType==JSON_Data.Value.Type.Array ? parent.getIndex(this) : "\""+name+"\"");
				}

				@Override public String getName    () { return name; }
				@Override public String getValueStr() { return value.toString(); }
				private JSON_Data.Value.Type getValueType() { return value==null ? null : value.type; }
				@Override public boolean hasName () { return name !=null; }
				@Override public boolean hasValue() { return value!=null; }

				@Override
				protected Vector<? extends JSON_TreeNode<?>> createChildren() {
					if (childValues==null) return null;
					Vector<JSON_TreeNode<?>> childNodes = new Vector<>();
					for (ValueType value:childValues) childNodes.add(create(this,getName.apply(value),getValue.apply(value)));
					return childNodes;
				}
				
//				static TreeRoot create(JSON_Parser.Result<NV,V> parseResult, boolean isLarge) {
//					if (parseResult.object != null) return create(parseResult.object, isLarge);
//					if (parseResult.array  != null) return create(parseResult.array , isLarge);
//					return SimpleTextNode.createSingleTextLineTree("Parse Error: Parser returns neither an JSON array nor an JSON object");
//				}

				static TreeRoot create(JSON_Array<NV, V> array, boolean isLarge) {
					if (array == null) return null;
					return new TreeRoot(create(null,null,new JSON_Data.ArrayValue<NV,V>(array,null)),true,!isLarge,contextMenu);
				}

				static TreeRoot create(JSON_Object<NV, V> object, boolean isLarge) {
					if (object == null) return null;
					return new TreeRoot(create(null,null,new JSON_Data.ObjectValue<NV,V>(object,null)),true,!isLarge,contextMenu);
				}
				
				static TreeRoot create(JSON_Data.Value<NV,V> value, boolean isLarge) {
					return new TreeRoot(create(null,null,value),true,!isLarge,contextMenu);
				}
				
				private static JSON_TreeNode<?> create(JSON_TreeNode<?> parent, String name, JSON_Data.Value<NV,V> value) {
					String title = getTitle(name,value);
					JsonTreeIcons icon = getIcon(value.type);
					switch (value.type) {
					case Object: return new JSON_TreeNode<>(parent, title, icon, name, value, value.castToObjectValue().value, vt->vt.name, vt->vt.value);
					case Array : return new JSON_TreeNode<>(parent, title, icon, name, value, value.castToArrayValue ().value, vt->null, vt->vt);
					default    : return new JSON_TreeNode<>(parent, title, icon, name, value, null, null, null);
					}
				}
				
				private static JsonTreeIcons getIcon(JSON_Data.Value.Type type) {
					if (type==null) return null;
					switch(type) {
					case Object: return JsonTreeIcons.Object;
					case Array : return JsonTreeIcons.Array;
					case String: return JsonTreeIcons.String;
					case Bool  : return JsonTreeIcons.Boolean;
					case Null  : return null;
					case Float : case Integer:
						return JsonTreeIcons.Number;
					}
					return null;
				}
				
				private static String getTitle(String name, JSON_Data.Value<NV,V> value) {
					switch (value.type) {
					case Object : return getTitle(name, "{", value.castToObjectValue ().value.size(), "}");
					case Array  : return getTitle(name, "[", value.castToArrayValue  ().value.size(), "]");
					case Bool   : return getTitle(name, "" , value.castToBoolValue   ().value, "");
					case String : return getTitle(name, "" , value.castToStringValue ().value, "");
					case Integer: return getTitle(name, "" , value.castToIntegerValue().value, "");
					case Float  : return getTitle(name, "" , value.castToFloatValue  ().value, "");
					case Null   : return getTitle(name, "" , value.castToNullValue   ().value, "");
					}
					return null;
				}
				
				protected static String getTitle(String name, String openingBracket, Object value, String closingBracket) {
					if (name==null || name.isEmpty()) return String.format("%s%s%s", openingBracket, value, closingBracket);
					return String.format("%s : %s%s%s", name, openingBracket, value, closingBracket);
				}
			}
		}

		static class VDF_File extends TextFile implements ParsedTextFileSource {
			
			private static final DataTreeNodeContextMenu contextMenu = new DataTreeNodeContextMenu();
			private VDFParser.Data vdfData;

			VDF_File(TreeNode parent, File file) {
				this(parent, file, TreeIcons.VDFFile);
			}
			protected VDF_File(TreeNode parent, File file, TreeIcons icon) {
				super(parent, file, icon, StandardCharsets.UTF_8);
				vdfData = null;
			}

			static boolean is(File file) {
				return fileNameEndsWith(file,".vdf");
			}
			
			@Override ContentType getContentType() {
				return ContentType.ParsedByteBasedText;
			}
			
			@Override
			public TreeRoot getContentAsTree() {
				if (vdfData==null) {
					String text = getContentAsText();
					try {
						vdfData = VDFParser.parse(text);
					} catch (VDFParser.ParseException e) {
						System.err.printf("ParseException: %s%n", e.getMessage());
						//e.printStackTrace();
						return SimpleTextNode.createSingleTextLineTree("Parse Error: %s", e.getMessage());
					}
				}
				return vdfData==null ? null : vdfData.getTreeRoot(isLarge(),contextMenu);
			}
		}

		static class AppManifestNode extends VDF_File {
			
			private final int id;
		
			AppManifestNode(TreeNode parent, File file) {
				super(parent, file, TreeIcons.AppManifest);
				id = AppManifest.getAppIDFromFile(file);
			}
		
			static boolean is(File file) {
				return AppManifest.getAppIDFromFile(file) != null;
			}

			@Override public String toString() {
				return String.format("App %d (%s, %s)", id, fileObj==null ? "" : fileObj.getName(), getSizeStr(fileObj));
			}
		}
	}
}

