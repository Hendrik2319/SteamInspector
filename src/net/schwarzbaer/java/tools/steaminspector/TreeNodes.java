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
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.Vector;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import net.schwarzbaer.gui.IconSource;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data;
import net.schwarzbaer.java.lib.jsonparser.JSON_Parser;
import net.schwarzbaer.java.tools.steaminspector.Data.AppManifest;
import net.schwarzbaer.java.tools.steaminspector.Data.Base64String;
import net.schwarzbaer.java.tools.steaminspector.Data.Game;
import net.schwarzbaer.java.tools.steaminspector.Data.GameImages;
import net.schwarzbaer.java.tools.steaminspector.Data.NV;
import net.schwarzbaer.java.tools.steaminspector.Data.Player;
import net.schwarzbaer.java.tools.steaminspector.Data.Player.AchievementProgress;
import net.schwarzbaer.java.tools.steaminspector.Data.Player.AchievementProgress.AchievementProgressInGame;
import net.schwarzbaer.java.tools.steaminspector.Data.Player.GameInfos;
import net.schwarzbaer.java.tools.steaminspector.Data.Player.GameInfos.CommunityItems.CommunityItem;
import net.schwarzbaer.java.tools.steaminspector.Data.Player.GameInfos.GameInfosFilterOptions;
import net.schwarzbaer.java.tools.steaminspector.Data.Player.LocalConfig.FriendList;
import net.schwarzbaer.java.tools.steaminspector.Data.Player.LocalConfig.SoftwareValveSteamApps;
import net.schwarzbaer.java.tools.steaminspector.Data.ScreenShot;
import net.schwarzbaer.java.tools.steaminspector.Data.ScreenShotLists;
import net.schwarzbaer.java.tools.steaminspector.Data.SteamId;
import net.schwarzbaer.java.tools.steaminspector.Data.V;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.AbstractTreeContextMenu;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BaseTreeNode;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ByteBasedTextFileSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ByteFileSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ExternViewableItem;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ExternalViewerInfo;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.FilePromise;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ImageContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ImageNTextContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.LabeledFile;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.LabeledUrl;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.MainTreeContextMenu.ExternViewableNode;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.MainTreeContextMenu.FileBasedNode;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.MainTreeContextMenu.Filter;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.MainTreeContextMenu.FilterOption;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.MainTreeContextMenu.FilterableNode;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.MainTreeContextMenu.SortOption;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.MainTreeContextMenu.SortableNode;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.MainTreeContextMenu.Sorter;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.MainTreeContextMenu.URLBasedNode;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ParsedByteBasedTextFileSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TextContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TreeContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TreeRoot;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.FileSystem.FolderNode;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.PlayersNGames.GameChangeListeners.GameChangeListener;
import net.schwarzbaer.java.tools.steaminspector.VDFParser.VDFParseException;
import net.schwarzbaer.java.tools.steaminspector.VDFParser.VDFTreeNode;
import net.schwarzbaer.system.ClipboardTools;

class TreeNodes {
	
	
	private static IconSource.CachedIcons<TreeIcons> TreeIconsIS;
	enum TreeIcons {
		DefaultLeafIcon, GeneralFile, TextFile, ImageFile, AudioFile, VDFFile, AppManifest, JSONFile, Badge, Achievement, Facebook, Twitch, Twitter, YouTube, Folder, RootFolder_Simple, RootFolder;
		Icon getIcon() { return TreeIconsIS.getCachedIcon(this); }
	}
	
	private static IconSource.CachedIcons<JsonTreeIcons> JsonTreeIconsIS;
	enum JsonTreeIcons {
		Object, Array, String, Number, Boolean, Null;
		Icon getIcon() { return JsonTreeIconsIS.getCachedIcon(this); }
	}
	
	private static IconSource.CachedIcons<VdfTreeIcons> VdfTreeIconsIS;
	enum VdfTreeIcons {
		Array, String;
		Icon getIcon() { return VdfTreeIconsIS.getCachedIcon(this); }
	}
	
	static void loadIcons() {
		TreeIconsIS     = IconSource.createCachedIcons(16, 16, "/images/TreeIcons.png"    , TreeIcons.values());
		JsonTreeIconsIS = IconSource.createCachedIcons(16, 16, "/images/JsonTreeIcons.png", JsonTreeIcons.values());
		VdfTreeIconsIS  = IconSource.createCachedIcons(16, 16, "/images/VdfTreeIcons.png" , VdfTreeIcons.values());
	}
	
	private static boolean fileNameEndsWith(File file, String... suffixes) {
		String name = file.getName().toLowerCase();
		for (String suffix:suffixes)
			if (name.endsWith(suffix))
				return true;
		return false;
	}

	static boolean isImageFile(File file) {
		return file.isFile() && fileNameEndsWith(file,".jpg",".jpeg",".png",".bmp",".ico",".tga");
	}
	
	static String getTimeStr(long millis) {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("CET"), Locale.GERMANY);
		cal.setTimeInMillis(millis);
		return String.format(Locale.ENGLISH, "%1$tA, %1$te. %1$tb %1$tY, %1$tT [%1$tZ:%1$tz]", cal);
	}
	
	static String getSizeStr(File file) {
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
	
	static File[] getFilesAndFolders(File folder) {
		File[] files = folder.listFiles((FileFilter) file -> {
			String name = file.getName();
			if (file.isDirectory())
				return !name.equals(".") && !name.equals("..");
			return file.isFile();
		});
		return files;
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

	private static BufferedImage createImageFromBase64(String base64Data) {
		byte[] bytes;
		String fixedStr = base64Data.replace('_','/').replace('-','+');
		//while ( (fixedStr.length()&0x3)!=0 ) fixedStr += '='; // Base64.Decoder does padding
		try {
			bytes = Base64.getDecoder().decode(fixedStr);
		} catch (IllegalArgumentException e) {
			return createImageOfMessage(e.getMessage(),150,150,Color.RED);
		}
		ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
		try {
			BufferedImage image = ImageIO.read(stream);
			if (image==null) 
				return createImageOfMessage("Base64 data doesn't encode an image.",150,150,Color.RED);
			return image;
		} catch (IOException e) {
			return createImageOfMessage("Can't read image from decoded Base64 data.",150,150,Color.RED);
		}
	}

	static String getTreeIDStr(Class<?> hostClass, String suffix) {
		if (hostClass==null) return null;
		return String.format("%s<%s>", hostClass.getCanonicalName(), suffix);
	}
	
	static String getRawDataTreeIDStr(Class<?> rawDataHostClass) {
		return getTreeIDStr(rawDataHostClass, "RawData");
	}

	@SuppressWarnings("unused")
	private static void log_ln(Object source, String format, Object... args) {
		System.out.printf("[%s] \"%s\": %s%n", source.getClass().getSimpleName(), source.toString(), String.format(Locale.ENGLISH, format, args));
	}
	
	static class NodeColorizer {
		
		interface ColorizableNode {
			boolean wasProcessed();
			boolean hasUnprocessedChildren();
			Boolean isInteresting();
		}
		
		enum ColorSetting {
			Normal (null, new Color(0xC000C0), new Color(0x00B000), new Color(0x14C1FF), new Color(0xC0C0C0)),
			WarnNew(new Color(0xFF0000), new Color(0xC000C0), null, new Color(0x14C1FF), new Color(0xC0C0C0)),
			;
			final Color notProcessed;
			final Color partiallyProcessed;
			final Color fullyProcessed;
			final Color isInteresting;
			final Color isNotInteresting;
			private ColorSetting(Color notProcessed, Color partiallyProcessed, Color fullyProcessed, Color isInteresting, Color isNotInteresting) {
				this.notProcessed = notProcessed;
				this.partiallyProcessed = partiallyProcessed;
				this.fullyProcessed = fullyProcessed;
				this.isInteresting = isInteresting;
				this.isNotInteresting = isNotInteresting;
			}
		}

		static Color getTextColor        (ColorizableNode        node ) { return getTextColor(node , ColorSetting.Normal ); }
		static Color getTextColor_WarnNew(ColorizableNode        node ) { return getTextColor(node , ColorSetting.WarnNew); }
		static Color getTextColor        (JSON_Data.Value<NV, V> value) { return getTextColor(value, ColorSetting.Normal ); }
		static Color getTextColor_WarnNew(JSON_Data.Value<NV, V> value) { return getTextColor(value, ColorSetting.WarnNew); }
		
		static Color getTextColor(ColorizableNode node, ColorSetting colorSetting) {
			if (node==null) return getTextColor(true, false, null, colorSetting);
			return getTextColor(node.wasProcessed(), node.hasUnprocessedChildren(), node.isInteresting(), colorSetting);
		}
		static Color getTextColor(JSON_Data.Value<NV, V> value, ColorSetting colorSetting) {
			if (value==null) return getTextColor(true, false, null, colorSetting);
			return getTextColor(value.extra.wasProcessed, value.extra.hasUnprocessedChildren(), null, colorSetting);
		}
		
		static Color getTextColor(boolean wasProcessed, boolean hasUnprocessedChildren, Boolean isInteresting, ColorSetting colorSetting) {
			if (colorSetting==null)
				return null;
			if (wasProcessed) {
				if (hasUnprocessedChildren)
					return colorSetting.partiallyProcessed;
				return colorSetting.fullyProcessed;
			}
			if (isInteresting!=null) {
				if (isInteresting)
					return colorSetting.isInteresting;
				return colorSetting.isNotInteresting;
			}
			return colorSetting.notProcessed;
		}
	}
	
	static class ValueListOutput extends Vector<ValueListOutput.Entry> {
		private static final long serialVersionUID = -5898390765518030500L;

		void add(int indentLevel, String label, int     value) { add(indentLevel, label, "%d", value); }
		void add(int indentLevel, String label, long    value) { add(indentLevel, label, "%d", value); }
		void add(int indentLevel, String label, float   value) { add(indentLevel, label, "%f", value); }
		void add(int indentLevel, String label, double  value) { add(indentLevel, label, "%f", value); }
		void add(int indentLevel, String label, boolean value) { add(indentLevel, label, "%s", value); }
		void add(int indentLevel, String label, Integer value) { if (value==null) add(indentLevel, label, "<null> (%s)", "Integer"); else add(indentLevel, label, "%d", value); }
		void add(int indentLevel, String label, Long    value) { if (value==null) add(indentLevel, label, "<null> (%s)", "Long"   ); else add(indentLevel, label, "%d", value); }
		void add(int indentLevel, String label, Float   value) { if (value==null) add(indentLevel, label, "<null> (%s)", "Float"  ); else add(indentLevel, label, "%f", value); }
		void add(int indentLevel, String label, Double  value) { if (value==null) add(indentLevel, label, "<null> (%s)", "Double" ); else add(indentLevel, label, "%f", value); }
		void add(int indentLevel, String label, Boolean value) { if (value==null) add(indentLevel, label, "<null> (%s)", "Boolean"); else add(indentLevel, label, "%s", value); }
		void add(int indentLevel, String label, String  value) { if (value==null) add(indentLevel, label, "<null> (%s)", "String" ); else add(indentLevel, label, "\"%s\"", value); }
		
		void addEmptyLine() { add(null); }
		
		void add(int indentLevel, String label, String format, Object... args) {
			add(new Entry(indentLevel, label, format, args));
		}
		void add(int indentLevel, String label) {
			add(new Entry(indentLevel, label, ""));
		}

		String generateOutput() {
			return generateOutput("");
		}
		String generateOutput(String baseIndent) {
			HashMap<Integer,Integer> labelLengths = new HashMap<>();
			for (Entry entry:this)
				if (entry!=null){
					Integer maxLength = labelLengths.get(entry.indentLevel);
					if (maxLength==null) maxLength=0;
					maxLength = Math.max(entry.label.length(), maxLength);
					labelLengths.put(entry.indentLevel,maxLength);
				}
			
			HashMap<Integer,String> indents = new HashMap<>();
			for (Integer indentLevel:labelLengths.keySet()) {
				String str = ""; int i=0;
				while (i<indentLevel) { str += "    "; i++; }
				indents.put(indentLevel, str);
			}
			
			StringBuilder sb = new StringBuilder();
			for (Entry entry:this)
				if (entry == null)
					sb.append(String.format("%n"));
				else {
					String spacer = entry.valueStr.isEmpty() ? "" : entry.label.isEmpty() ? "  " : ": ";
					String indent = indents.get(entry.indentLevel);
					int labelLength = labelLengths.get(entry.indentLevel);
					String labelFormat = labelLength==0 ? "%s" : "%-"+labelLength+"s";
					sb.append(String.format("%s%s"+labelFormat+"%s%s%n", baseIndent, indent, entry.label, spacer, entry.valueStr));
				}
			
			return sb.toString();
		}

		static class Entry {
			final int indentLevel;
			final private String label;
			final private String valueStr;
			public Entry(int indentLevel, String label, String format, Object... args) {
				this.indentLevel = indentLevel;
				this.label = label==null ? "" : label.trim();
				this.valueStr = String.format(Locale.ENGLISH, format, args);
			}
		}
		
	}
	
	static class FileNameNExt implements Comparable<FileNameNExt>{
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
	
	static final InterestingNodes interestingNodes = new InterestingNodes();
	static class InterestingNodes {
		
		HashMap<String,HashMap<String,Boolean>> map = new HashMap<>();
		
		void set(String treeIDStr, DataTreeNode treeNode, Boolean isInteresting) {
			if (treeNode==null) return;
			if (treeIDStr==null) return;
			
			HashMap<String, Boolean> treeMap = map.get(treeIDStr);
			if (treeMap==null) {
				if (isInteresting==null) return;
				map.put(treeIDStr, treeMap = new HashMap<>());
			}
			String path = treeNode.getPath();
			
			if (isInteresting==null) {
				treeMap.remove(path);
				if (treeMap.isEmpty()) map.remove(treeIDStr);
			} else
				treeMap.put(path, isInteresting);
		}

		Boolean get(String treeIDStr, DataTreeNode treeNode) {
			if (treeNode==null) return null;
			if (treeIDStr==null) return null;
			
			HashMap<String, Boolean> treeMap = map.get(treeIDStr);
			if (treeMap==null) return null;
			String path = treeNode.getPath();
			
			return treeMap.get(path);
		}

		public void showTo(PrintStream out) {
			out.println("Interesting Nodes:");
			showTo(out, true, "    ");
			out.println("Not Interesting Nodes:");
			showTo(out, false, "    ");
		}

		public void showTo(PrintStream out, boolean value, String indent) {
			Vector<String> treeIDStrs = new Vector<>(map.keySet());
			treeIDStrs.sort(null);
			for (String treeIDStr:treeIDStrs) {
				out.printf("%s%s%n", indent, treeIDStr);
				
				HashMap<String, Boolean> treeMap = map.get(treeIDStr);
				Vector<String> nodePaths = new Vector<>(treeMap.keySet());
				nodePaths.sort(Comparator.comparing(String::toLowerCase));
				for (String nodePath:nodePaths) {
					Boolean nodeValue = treeMap.get(nodePath);
					if (nodeValue!=null && nodeValue.booleanValue()==value)
						out.printf("%s    %s%n", indent, nodePath);
				}
			}
		}

		void readfile() {
			
			map.clear();
			
			try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(SteamInspector.INTERESTING_NODES_INI), StandardCharsets.UTF_8))) {
				
				String line, valueStr;
				HashMap<String, Boolean> treeMap = null;
				while ( (line=in.readLine())!=null ) {
					
					if ( (valueStr=getValue(line,"TreeID: "))!=null )
						map.put(valueStr, treeMap = new HashMap<>());
					
					if ( (valueStr=getValue(line,"Intersting: "))!=null && treeMap!=null)
						treeMap.put(valueStr,true);
					
					if ( (valueStr=getValue(line,"NotIntersting: "))!=null && treeMap!=null )
						treeMap.put(valueStr,false);
					
				}
				
			} catch (FileNotFoundException e) {
			} catch (IOException e) { e.printStackTrace(); }
		}
		
		private String getValue(String line, String prefix) {
			if (line.startsWith(prefix))
				return line.substring(prefix.length());
			return null;
		}

		void writefile() {
			
			try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(SteamInspector.INTERESTING_NODES_INI), StandardCharsets.UTF_8))) {
				
				Vector<String> treeIDStrs = new Vector<>(map.keySet());
				treeIDStrs.sort(null);
				
				for (String treeIDStr:treeIDStrs) {
					out.printf("TreeID: %s%n", treeIDStr);
					
					HashMap<String, Boolean> treeMap = map.get(treeIDStr);
					Vector<String> nodePaths = new Vector<>(treeMap.keySet());
					nodePaths.sort(Comparator.<String,String>comparing(String::toLowerCase).thenComparing(Comparator.naturalOrder()));
					
					for (String nodePath:nodePaths) {
						Boolean nodeValue = treeMap.get(nodePath);
						if (nodeValue!=null)
							out.printf("%s%s%n", nodeValue ? "Intersting: " : "NotIntersting: ", nodePath);
					}
					
					out.printf("%n");
				}
				
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	static TreeRoot createDataTreeRoot(DataTreeNode rootNode, String treeIDStr, boolean isRootVisible, boolean expandAllRows) {
		return new TreeRoot(rootNode, isRootVisible, expandAllRows, new DataTreeNodeContextMenu(treeIDStr), ()->{
			rootNode.doToAllNodesChildNodesAndFutureChildNodes(node->{
				node.setInteresting(interestingNodes.get(treeIDStr, node));
			});
		});
	}
	
	interface TreeNodeII extends TreeNode  {
		Iterable<? extends TreeNode> getChildren();
	}
	
	interface DataTreeNode extends TreeNodeII {
		
		default String getFullInfo() {
			String str = "";
			str += String.format("Class : %s%n", getClass().getCanonicalName());
			str += !hasName()  ? String.format("Name : none%n")  : String.format("Name : \"%s\"%n", getName());
			str += !hasValue() ? String.format("Value : none%n") : String.format("Value : %s%n", getValueStr());
			str += String.format("Path : %s%n", getPath());
			str += String.format("AccessCall : %s%n", getAccessCall());
			return str;
		}
		void doToAllNodesChildNodesAndFutureChildNodes(Consumer<DataTreeNode> action);
		String getName();
		String getValueStr();
		String getPath();
		String getAccessCall();
		boolean hasName();
		boolean hasValue();

		void setInteresting(Boolean isInteresting);
		
		boolean areChildrenSortable();
		void setChildrenOrder(ChildrenOrder order, DefaultTreeModel currentTreeModel);
		ChildrenOrder getChildrenOrder();
		
		enum ChildrenOrder {
			ByName("by Name");
			private String title;
			ChildrenOrder(String title) { this.title = title;}
			@Override public String toString() { return title==null ? name() : title; }
			
		}
	}

	static class DataTreeNodeContextMenu extends AbstractTreeContextMenu {
		private static final long serialVersionUID = 7620430144231207201L;
		
		private final String treeIDStr;
		private final JMenuItem miName;
		private final JMenuItem miValue;
		private final JMenuItem miPath;
		private final JMenuItem miAccessCall;
		private final JMenuItem miFullInfo;
		private final JMenuItem miCollapseChildren;
		private final JMenuItem miExpandChildren;

		private final SortChildrenMenu menuSortChildren;
		private final JRadioButtonMenuItem miMarkInteresting;
		private final JRadioButtonMenuItem miMarkUnInteresting;
		private final JRadioButtonMenuItem miMarkUndefined;
		
		private DataTreeNode clickedTreeNode;
		private TreePath clickedTreePath;
		private JTree tree;
		private DefaultTreeModel currentTreeModel;

		//data.getClass().getCanonicalName()+"<RawData>"
		DataTreeNodeContextMenu(String treeIDStr) {
			this.treeIDStr = treeIDStr;
			clickedTreeNode = null;
			clickedTreePath = null;
			tree = null;
			add(miName            = SteamInspector.createMenuItem("Copy Name"          , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getName())));
			add(miValue           = SteamInspector.createMenuItem("Copy Value"         , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getValueStr())));
			add(miPath            = SteamInspector.createMenuItem("Copy Path"          , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getPath())));
			add(miAccessCall      = SteamInspector.createMenuItem("Copy Access Call"   , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getAccessCall())));
			add(miFullInfo        = SteamInspector.createMenuItem("Copy Full Info"     , true, e->ClipboardTools.copyToClipBoard(getFullInfo(clickedTreeNode))));
			addSeparator();
			add(miMarkInteresting   = SteamInspector.createRadioButtonMenuItem("Interesting"  , false, true, b->setInteresting(b, true )));
			add(miMarkUnInteresting = SteamInspector.createRadioButtonMenuItem("Uninteresting", false, true, b->setInteresting(b, false)));
			add(miMarkUndefined     = SteamInspector.createRadioButtonMenuItem("<Undefined>"  , false, true, b->setInteresting(b, null )));
			addSeparator();
			add(SteamInspector.createMenuItem("Expand Full Tree", true, e->{
				if (tree!=null)
					for (int i=0; i<tree.getRowCount(); i++)
						tree.expandRow(i);
			}));
			add(miCollapseChildren = SteamInspector.createMenuItem("Collapse Children", true, e->doCollapseExpandChildren(JTree::collapsePath)));
			add(miExpandChildren   = SteamInspector.createMenuItem("Expand Children"  , true, e->doCollapseExpandChildren(JTree::expandPath  )));
			add(menuSortChildren = new SortChildrenMenu("Sort Children"));
			
		}

		private void doCollapseExpandChildren(BiConsumer<JTree,TreePath> childPathAction) {
			if (tree!=null && clickedTreeNode!=null && clickedTreePath!=null) {
				Iterable<? extends TreeNode> children = clickedTreeNode.getChildren();
				if (children!=null) {
					tree.expandPath(clickedTreePath);
					for (TreeNode child:children)
						childPathAction.accept(tree, clickedTreePath.pathByAddingChild(child));
				}
			}
		}

		private String getFullInfo(DataTreeNode node) {
			String str = node.getFullInfo();
			str += String.format("TreeID: %s%n", treeIDStr);
			str += String.format("TreeNodeID: %s // %s%n", treeIDStr, node.getPath());
			return str;
		}

		private void setInteresting(boolean b, Boolean value) {
			if (b && treeIDStr!=null && clickedTreeNode!=null) {
				interestingNodes.set(treeIDStr,clickedTreeNode, value );
				interestingNodes.writefile();
				clickedTreeNode.setInteresting(value);
				currentTreeModel.nodeStructureChanged(clickedTreeNode);
			}
		}
		
		private void setChildrenOrder(DataTreeNode.ChildrenOrder order) {
			if (clickedTreeNode==null) return;
			clickedTreeNode.setChildrenOrder(order, currentTreeModel);
		}
		
		@Override
		public void showContextMenu(JTree tree, DefaultTreeModel currentTreeModel, int x, int y, TreePath clickedTreePath, Object clickedTreeNode) {
			this.tree = tree;
			this.currentTreeModel = currentTreeModel;
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
			miExpandChildren  .setEnabled(this.clickedTreeNode!=null);
			
			miMarkInteresting  .setEnabled(this.clickedTreeNode!=null && treeIDStr!=null);
			miMarkUnInteresting.setEnabled(this.clickedTreeNode!=null && treeIDStr!=null);
			miMarkUndefined    .setEnabled(this.clickedTreeNode!=null && treeIDStr!=null);
			Boolean isInteresting = interestingNodes.get(treeIDStr,this.clickedTreeNode);
			miMarkInteresting  .setSelected(this.clickedTreeNode!=null && isInteresting!=null && isInteresting==true );
			miMarkUnInteresting.setSelected(this.clickedTreeNode!=null && isInteresting!=null && isInteresting==false);
			miMarkUndefined    .setSelected(this.clickedTreeNode!=null && isInteresting==null );
			
			menuSortChildren.prepareMenuItems();
			
			show(tree, x, y);
		}

		private class SortChildrenMenu extends JMenu {
			private static final long serialVersionUID = -6772692062748277925L;
			
			private final JCheckBoxMenuItem miOriginalOrder;
			private final EnumMap<DataTreeNode.ChildrenOrder,JCheckBoxMenuItem> menuItems;

			public SortChildrenMenu(String title) {
				super(title);
				add(miOriginalOrder = SteamInspector.createCheckBoxMenuItem("<Original Order>", false, true, b->setChildrenOrder(null)));
				menuItems = new EnumMap<>(DataTreeNode.ChildrenOrder.class);
				addSeparator();
				for (DataTreeNode.ChildrenOrder order:DataTreeNode.ChildrenOrder.values()) {
					JCheckBoxMenuItem mi = SteamInspector.createCheckBoxMenuItem(order.toString(), false, true, e->setChildrenOrder(order));
					menuItems.put(order,mi);
					add(mi);
				}
			}

			public void prepareMenuItems() {
				setEnabled(clickedTreeNode!=null && clickedTreeNode.areChildrenSortable());
				
				DataTreeNode.ChildrenOrder childrenOrder;
				if (clickedTreeNode!=null && clickedTreeNode.areChildrenSortable())
					childrenOrder = clickedTreeNode.getChildrenOrder();
				else
					childrenOrder = null;
				
				miOriginalOrder.setSelected(childrenOrder==null);
				menuItems.forEach((order,mi)->mi.setSelected(order==childrenOrder));
			}
			
		}
	}

	@SuppressWarnings("unused")
	private static class GroupingNode<ValueType> extends MultiPurposeNode implements FilterableNode, SortableNode {
		
		private final Collection<ValueType> values;
		private final Comparator<ValueType> sortOrder;
		private final NodeCreator1<ValueType> createChildNode;
		private final BiConsumer<TreeNode, Vector<TreeNode>> createAllChildNodes;
		private GroupingNodeFilter<ValueType,?> filter = null;
		private GroupingNodeSorter<ValueType,?> sorter = null;
		
		private static <IT,VT> Comparator<Map.Entry<IT,VT>> createMapKeyOrder(Comparator<IT> keyOrder) {
			return Comparator.comparing(Map.Entry<IT,VT>::getKey,keyOrder);
		}
		private static <IT,VT> Comparator<Map.Entry<IT,VT>> createMapValueOrder(Comparator<VT> keyOrder) {
			return Comparator.comparing(Map.Entry<IT,VT>::getValue,keyOrder);
		}
		
		private static <IT,VT> GroupingNode<Map.Entry<IT,VT>> create(TreeNode parent, String title, HashMap<IT,VT> values, Comparator<VT> sortOrder, NodeCreator1<VT> createChildNode) {
			return create(parent, title, values, sortOrder, null, createChildNode);
		}
		private static <IT,VT> GroupingNode<Map.Entry<IT,VT>> create(TreeNode parent, String title, HashMap<IT,VT> values, Comparator<VT> sortOrder, Icon icon, NodeCreator1<VT> createChildNode) {
			return new GroupingNode<Map.Entry<IT,VT>>(parent, title, values.entrySet(), createMapValueOrder(sortOrder), icon, (p,e)->createChildNode.create(p,e.getValue()), null);
		}
		private static <IT,VT> GroupingNode<Map.Entry<IT,VT>> create(TreeNode parent, String title, HashMap<IT,VT> values, Comparator<Map.Entry<IT,VT>> sortOrder, NodeCreator2<IT,VT> createChildNode) {
			return create(parent, title, values, sortOrder, null, createChildNode);
		}
		private static <IT,VT> GroupingNode<Map.Entry<IT,VT>> create(TreeNode parent, String title, HashMap<IT,VT> values, Comparator<Map.Entry<IT,VT>> sortOrder, Icon icon, NodeCreator2<IT,VT> createChildNode) {
			return new GroupingNode<Map.Entry<IT,VT>>(parent, title, values.entrySet(), sortOrder, icon, (p,e)->createChildNode.create(p,e.getKey(),e.getValue()), null);
		}
		private static <VT> GroupingNode<VT> create(TreeNode parent, String title, Collection<VT> values, Comparator<VT> sortOrder, NodeCreator1<VT> createChildNode) {
			return new GroupingNode<>(parent, title, values, sortOrder, null, createChildNode, null);
		}
		private static <VT> GroupingNode<VT> create(TreeNode parent, String title, Collection<VT> values, Comparator<VT> sortOrder, Icon icon, NodeCreator1<VT> createChildNode) {
			return new GroupingNode<>(parent, title, values, sortOrder, icon, createChildNode, null);
		}
		private static GroupingNode<Object> create(TreeNode parent, String title, BiConsumer<TreeNode,Vector<TreeNode>> createAllChildNodes) {
			return new GroupingNode<>(parent, title, null, null, null, null, createAllChildNodes);
		}
		private static GroupingNode<Object> create(TreeNode parent, String title, Icon icon, BiConsumer<TreeNode,Vector<TreeNode>> createAllChildNodes) {
			return new GroupingNode<>(parent, title, null, null, icon, null, createAllChildNodes);
		}
		private GroupingNode(TreeNode parent, String title, Collection<ValueType> values, Comparator<ValueType> sortOrder, Icon icon, NodeCreator1<ValueType> createChildNode, BiConsumer<TreeNode,Vector<TreeNode>> createAllChildNodes) {
			super(parent, title, areChildrenExpectable(values, createAllChildNodes), !areChildrenExpectable(values, createAllChildNodes), icon==null ? TreeIcons.Folder.getIcon() : icon);
			this.values = values;
			this.sortOrder = sortOrder;
			this.createChildNode = createChildNode;
			this.createAllChildNodes = createAllChildNodes;
		}
		
		private static <ValueType> boolean areChildrenExpectable(Collection<ValueType> values, BiConsumer<TreeNode, Vector<TreeNode>> createAllChildNodes) {
			return (values!=null && !values.isEmpty()) || createAllChildNodes!=null;
		}
		
		interface NodeCreator1<ValueType> {
			TreeNode create(TreeNode parent, ValueType value);
		}

		interface NodeCreator2<IDType, ValueType> {
			TreeNode create(TreeNode parent, IDType id, ValueType value);
		}
		
		@Override
		protected Vector<? extends TreeNode> createChildren() {
			Vector<TreeNode> children = new Vector<>();
			if (createAllChildNodes!=null) {
				createAllChildNodes.accept(this,children);
				
			} else {
				Vector<ValueType> vector = new Vector<>(values);
				
				Comparator<ValueType> sortOrder = this.sortOrder;
				if (sorter!=null)
					sortOrder = sorter.getOrder(this.sortOrder);
				if (sortOrder!=null)
					vector.sort(sortOrder);
				
				vector.forEach(value->{
					if (filter==null || filter.allows(value)) {
						TreeNode treeNode = createChildNode.create(this,value);
						if (treeNode!=null) children.add(treeNode);
					}
				});
			}
			return children;
		}
		
		@Override public Filter getFilter() { return filter; }
		@Override public Sorter getSorter() { return sorter; }
		
		GroupingNode<ValueType> setFilter(GroupingNodeFilter<ValueType,?> filter) {
			if (createAllChildNodes!=null) throw new IllegalStateException("You can't set a filter, if you have created child nodes directly.");
			this.filter = filter;
			if (filter!=null) filter.setHost(this);
			return this;
		}
		
		GroupingNode<ValueType> setSorter(GroupingNodeSorter<ValueType,?> sorter) {
			if (createAllChildNodes!=null) throw new IllegalStateException("You can't set a sorter, if you have created child nodes directly.");
			this.sorter = sorter;
			if (sorter!=null) sorter.setHost(this);
			return this;
		}
		
		private static <IT, VT, FilterOptType extends Enum<FilterOptType> & FilterOption> GroupingNodeFilter<Map.Entry<IT,VT>,FilterOptType> createMapFilter(
				Class<FilterOptType> filterOptionClass,
				FilterOptType[] options,
				Function<FilterOption,FilterOptType> cast,
				BiPredicate<VT,FilterOptType> valueMeetsOption
		) {
			return new GroupingNodeFilter<Map.Entry<IT,VT>,FilterOptType>(filterOptionClass, options) {
				@Override protected FilterOptType cast(FilterOption opt) {
					if (opt==null) return null;
					return cast.apply(opt);
				}
				@Override protected boolean valueMeetsOption(Map.Entry<IT,VT> value, FilterOptType option) {
					if (value==null) return true;
					VT value2 = value.getValue();
					if (value2==null) return true;
					return valueMeetsOption.test(value2, option);
				}
			};
		}

		private static abstract class GroupingNodeFilter<ValueType, FilterOptType extends Enum<FilterOptType> & FilterOption> implements Filter {
			
			private final FilterOptType[] options;
			private final EnumSet<FilterOptType> currentSetting;
			private GroupingNode<ValueType> host;
		
			GroupingNodeFilter(Class<FilterOptType> filterOptionClass, FilterOptType[] options) {
				this.options = options;
				currentSetting = EnumSet.noneOf(filterOptionClass);
				host = null;
			}
		
			private void setHost(GroupingNode<ValueType> host) { this.host = host; }
			@Override public FilterOptType[] getFilterOptions() { return options; }
			@Override public boolean isFilterOptionSet(FilterOption option) { return currentSetting.contains(cast(option)); }
			protected abstract boolean valueMeetsOption(ValueType value, FilterOptType option);
		
			private boolean allows(ValueType value) {
				//System.out.printf("allows \"%s\" ?%n", value);
				if (value==null) return true;
				//System.out.print("   ");
				for (FilterOptType option:options) {
					//System.out.print("\""+option+"\"  |  ");
					if (option!=null && isFilterOptionSet(option) && !valueMeetsOption(value,option)) {
						//System.out.println("--> false");
						return false;
					}
				}
				//System.out.println("--> true");
				return true;
			}
		
			@Override public void setFilterOption(FilterOption option, boolean active, DefaultTreeModel currentTreeModel) {
				//System.out.printf("setFilterOption( \"%s\", %s )%n", option, active);
				if (option==null) {
					//System.out.println("setFilterOption:  option==null --> ABORT");
					return;
				}
				//System.out.printf("setFilterOption:  cast( [%s] \"%s\" )%n", option==null?null:option.getClass(), option);
				FilterOptType filterOption = cast(option);
				if (filterOption==null) {
					//System.out.println("setFilterOption:  cast(option)==null --> ABORT");
					return;
				}
				//System.out.printf("setFilterOption:  active=%s  current=%s%n", active, currentSetting.contains(filterOption));
				if (active && !currentSetting.contains(filterOption)) {
					//System.out.println("setFilterOption:  add");
					currentSetting.add(filterOption);
					rebuildChildren(currentTreeModel);
					
				} else if (!active && currentSetting.contains(filterOption)) {
					//System.out.println("setFilterOption:  remove");
					currentSetting.remove(filterOption);
					rebuildChildren(currentTreeModel);
				}
			}
		
			@Override public void clearFilter(DefaultTreeModel currentTreeModel) {
				currentSetting.clear();
				rebuildChildren(currentTreeModel);
			}
		
			private void rebuildChildren(DefaultTreeModel currentTreeModel) {
				if (host==null) throw new IllegalStateException();
				host.rebuildChildren(currentTreeModel);
			}
		
			protected abstract FilterOptType cast(FilterOption option);
		}

		private static abstract class GroupingNodeSorter<ValueType,SortOptionType extends SortOption> implements Sorter {

			private GroupingNode<ValueType> host;
			private final SortOptionType[] sortOptions;
			private SortOptionType currentOption;
			
			GroupingNodeSorter(SortOptionType[] sortOptions) {
				this.sortOptions = sortOptions;
				currentOption = null;
			}
			
			private Comparator<ValueType> getOrder(Comparator<ValueType> defaultSortOrder) {
				if (currentOption == null) return defaultSortOrder;
				return getOrder(currentOption);
			}

			abstract SortOptionType cast(SortOption option);
			protected abstract Comparator<ValueType> getOrder(SortOptionType option);

			private void setHost(GroupingNode<ValueType> host) { this.host = host; }
			@Override public SortOptionType[] getSortOptions() { return sortOptions; }

			@Override public boolean isOriginalOrder() { return isSortOptionSet(null); }
			@Override public void resetToOriginalOrder(DefaultTreeModel treeModel) { setOrder(null, treeModel); }

			@Override public boolean isSortOptionSet(SortOption option) { return currentOption==cast(option); }

			@Override public void setOrder(SortOption option, DefaultTreeModel treeModel) {
				currentOption = cast(option);
				rebuildChildren(treeModel);
			}


			private void rebuildChildren(DefaultTreeModel treeModel) {
				if (host==null) throw new IllegalStateException();
				host.rebuildChildren(treeModel);
			}
		}
		
		
	}
	
	private static MultiPurposeNode createImageUrlNode(TreeNode parent, String label, String url) { return createUrlNode(parent, TreeIcons.ImageFile.getIcon(), label, url, url, true ); }
	private static MultiPurposeNode createUrlNode(TreeNode parent,            String label,                    String url) { return createUrlNode(parent, null, label, url, url, false); }
	private static MultiPurposeNode createUrlNode(TreeNode parent, Icon icon, String label,                    String url) { return createUrlNode(parent, icon, label, url, url, false); }
	private static MultiPurposeNode createUrlNode(TreeNode parent, Icon icon, String label, String urlInTitle, String url, boolean isImageUrl) {
		return createUrlNode(parent, icon, "", url, isImageUrl, urlInTitle==null ? "%s" : "%s: \"%s\"", label, urlInTitle);
	}
	private static MultiPurposeNode createUrlNode(TreeNode parent, Icon icon, String urlLabel, String url, boolean isImageUrl, String format, Object...args) {
		return createUrlNode(parent, icon, url==null ? null : new LabeledUrl(urlLabel, url), isImageUrl, format, args);
	}
	private static MultiPurposeNode createUrlNode(TreeNode parent, Icon icon, LabeledUrl labeledUrl, boolean isImageUrl, String format, Object... args) {
		SimpleLeafNode node = new SimpleLeafNode(parent, icon, format, args);
		if (labeledUrl!=null) {
			node.setURL(labeledUrl, isImageUrl);
			node.setExternViewable(labeledUrl, ExternalViewerInfo.Browser);
		}
		return node;
	}

	static class SimpleLeafNode extends MultiPurposeNode {
		
		SimpleLeafNode(TreeNode parent,                           String format, Object...args) { this(parent, null, Locale.ENGLISH, format, args); }
		SimpleLeafNode(TreeNode parent,            Locale locale, String format, Object...args) { this(parent, null, locale, format, args); }
		SimpleLeafNode(TreeNode parent, Icon icon,                String format, Object...args) { this(parent, icon, Locale.ENGLISH, format, args); }
		SimpleLeafNode(TreeNode parent, Icon icon, Locale locale, String format, Object...args) { this(parent, icon, String.format(locale, format, args)); }
		SimpleLeafNode(TreeNode parent,            String title) { this(parent, null, title); }
		SimpleLeafNode(TreeNode parent, Icon icon, String title) { super(parent, title, false, true, icon); }
		
		@Override protected Vector<? extends SimpleLeafNode> createChildren() { return new Vector<>(); }
		
		static TreeRoot createSingleTextLineTree(String format, Object...args) {
			return new TreeRoot( new SimpleLeafNode(null, format, args), true, true );
		}
	}

	private static abstract class MultiPurposeNode extends BaseTreeNode<TreeNode,TreeNode> implements ExternViewableNode, FileBasedNode, URLBasedNode, TextContentSource, TreeContentSource, ImageContentSource {
		
		private Color textColor    = null;
		private LabeledFile file   = null;
		private LabeledUrl url     = null;
		private boolean isImageUrl = false;
		private TreeRoot dataTree  = null;
		private Supplier<String>   textSource   = null;
		private ExternViewableItem viewableItem = null;
		
		private MultiPurposeNode(TreeNode parent, String title, boolean allowsChildren, boolean isLeaf, Icon icon) {
			super(parent, title, allowsChildren, isLeaf, icon);
		}

		void setTextColor(Color textColor) { this.textColor = textColor; }
		@Override Color getTextColor() { return textColor; }
		
		MultiPurposeNode setDataTree  (TreeRoot dataTree)           { this.dataTree   = dataTree  ; return this; }
		MultiPurposeNode setTextSource(Supplier<String> textSource) { this.textSource = textSource; return this; }
		MultiPurposeNode setTextSource(Base64String   base64String) { return setTextSource(()->Base64String.toString(base64String)); }
		
		@Override ContentType getContentType() {
			if (url!=null && isImageUrl) return ContentType.Image;
			if (dataTree  !=null) return ContentType.DataTree;
			if (textSource!=null) return ContentType.PlainText;
			return null;
		}
		
		@Override public TreeRoot      getContentAsTree() { return dataTree; }
		@Override public String        getContentAsText() { return textSource!=null ? textSource.get() : null; }
		@Override public BufferedImage getContentAsImage() { return url!=null && isImageUrl ? readImageFromURL(url.url,"image") : null; }
		
		
		MultiPurposeNode setURL(LabeledUrl url, boolean isImageUrl, ExternalViewerInfo viewerInfo) {
			setURL(url, isImageUrl);
			setExternViewable(url, viewerInfo);
			return this;
		}
		
		MultiPurposeNode setURL(String     url, boolean isImageUrl) { return setURL(new LabeledUrl(url),isImageUrl); }
		MultiPurposeNode setURL(LabeledUrl url, boolean isImageUrl) { this.url = url; this.isImageUrl = isImageUrl; return this; }
		@Override 	public LabeledUrl getURL() { return url; }
		
		MultiPurposeNode setFile(File file, ExternalViewerInfo viewerInfo) {
			setFile(file);
			setExternViewable(file, viewerInfo);
			return this;
		}
		
		MultiPurposeNode setFile(File        file) { return setFile(new LabeledFile(file)); }
		MultiPurposeNode setFile(LabeledFile file) { this.file = file; return this; }
		@Override public LabeledFile getFile() { return file; }
		
		MultiPurposeNode setExternViewable(File file, ExternalViewerInfo viewerInfo) {
			return setExternViewable(new LabeledFile(file), viewerInfo);
		}
		MultiPurposeNode setExternViewable(String url, ExternalViewerInfo viewerInfo) {
			return setExternViewable(new LabeledUrl(url), viewerInfo);
		}
		MultiPurposeNode setExternViewable(LabeledFile file, ExternalViewerInfo viewerInfo) {
			if (file==null) throw new IllegalArgumentException();
			if (viewerInfo==null) throw new IllegalArgumentException();
			viewableItem = new ExternViewableItem(file,viewerInfo);
			return this;
		}
		MultiPurposeNode setExternViewable(FilePromise filePromise, ExternalViewerInfo viewerInfo) {
			if (filePromise==null) throw new IllegalArgumentException();
			if (viewerInfo==null) throw new IllegalArgumentException();
			viewableItem = new ExternViewableItem(filePromise,viewerInfo);
			return this;
		}
		MultiPurposeNode setExternViewable(LabeledUrl url, ExternalViewerInfo viewerInfo) {
			if (url==null) throw new IllegalArgumentException();
			if (viewerInfo==null) throw new IllegalArgumentException();
			viewableItem = new ExternViewableItem(url,viewerInfo);
			return this;
		}
		@Override public ExternViewableItem getExternViewableItem() { return viewableItem; }
	}

	@SuppressWarnings("unused")
	private static class Base64ImageNode extends SimpleLeafNode implements ImageContentSource {
		
		private final String base64Data;

		Base64ImageNode(TreeNode parent, String label, String base64Data) {
			super(parent, TreeIcons.ImageFile.getIcon(), "%s: <Base64> %d chars", label, base64Data.length());
			this.base64Data = base64Data;
		}

		@Override ContentType getContentType() { return ContentType.Image; }
		@Override public BufferedImage getContentAsImage() { return createImageFromBase64(base64Data); }
	}
	
	private static class PrimitiveValueNode extends SimpleLeafNode {
		PrimitiveValueNode(TreeNode parent, String label, boolean value) { super(parent, "%s: "+  "%s"  , label, value); }
		PrimitiveValueNode(TreeNode parent, String label, int     value) { super(parent, "%s: "+  "%d"  , label, value); }
		PrimitiveValueNode(TreeNode parent, String label, long    value) { super(parent, "%s: "+  "%d"  , label, value); }
		PrimitiveValueNode(TreeNode parent, String label, double  value) { super(parent, "%s: "+"%1.4f" , label, value); }
		PrimitiveValueNode(TreeNode parent, String label, String  value) { super(parent, "%s: "+"\"%s\"", label, value); }
	}
	
	@SuppressWarnings("unused")
	private static class RawVDFDataNode extends SimpleLeafNode {
		
		RawVDFDataNode(TreeNode parent, String title, VDFTreeNode rawData, Class<?> rawDataHostClass) {
			this(parent, title, rawData, rawDataHostClass, null);
		}
		RawVDFDataNode(TreeNode parent, String title, VDFTreeNode rawData, String treeIDStr) {
			this(parent, title, rawData, treeIDStr       , null);
		}
		RawVDFDataNode(TreeNode parent, String title, VDFTreeNode rawData, Class<?> rawDataHostClass, Icon icon) {
			this(parent, title, rawData, getRawDataTreeIDStr(rawDataHostClass), icon);
		}
		RawVDFDataNode(TreeNode parent, String title, VDFTreeNode rawData, String treeIDStr, Icon icon) {
			super(parent, icon!=null ? icon : TreeIcons.VDFFile.getIcon(), title);
			if (rawData==null) setDataTree(SimpleLeafNode.createSingleTextLineTree("<VDF>RawData == <null>"));
			else setDataTree(rawData.createDataTreeRoot(treeIDStr));
		}
	}

	private static class RawJsonDataNode extends SimpleLeafNode {
	
		RawJsonDataNode(TreeNode parent, String title, JSON_Data.Value<NV,V> rawValue, Class<?> rawDataHostClass) {
			this(parent, title, rawValue, rawDataHostClass, null);
		}
		@SuppressWarnings("unused")
		RawJsonDataNode(TreeNode parent, String title, JSON_Data.Value<NV,V> rawValue, String treeIDStr) {
			this(parent, title, rawValue, treeIDStr       , null);
		}
		RawJsonDataNode(TreeNode parent, String title, JSON_Data.Value<NV,V> rawValue, Class<?> rawDataHostClass, Icon icon) {
			this(parent, title, rawValue, getRawDataTreeIDStr(rawDataHostClass), icon);
		}
		RawJsonDataNode(TreeNode parent, String title, JSON_Data.Value<NV,V> rawValue, String treeIDStr, Icon icon) {
			super(parent, icon!=null ? icon : TreeIcons.JSONFile.getIcon(), title);
			if (rawValue==null) setDataTree(SimpleLeafNode.createSingleTextLineTree("<JSON>RawData == <null>"));
			else setDataTree(JSONHelper.createDataTreeRoot(treeIDStr, rawValue, false));
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
		
		private static TreeNode createGameScreenShotsNode(TreeNode parent, Long id, ScreenShotLists.ScreenShotList screenShots) {
			return createGameScreenShotsNode(parent, "by "+Data.getPlayerName(id), screenShots, null);
		}
		private static TreeNode createGameScreenShotsNode(TreeNode parent, Integer gameID, ScreenShotLists.ScreenShotList screenShots) {
			MultiPurposeNode node = createGameScreenShotsNode(parent, Data.getGameTitle(gameID), screenShots, Data.getGameIcon(gameID,TreeIcons.ImageFile));
			gameChangeListeners.add(gameID, new GameChangeListener() {
				@Override public TreeNode getTreeNode() { return node; }
				@Override public void gameTitleWasChanged() { node.setTitle(Data.getGameTitle(gameID)); }
			});
			return node;
		}
		private static MultiPurposeNode createGameScreenShotsNode(TreeNode parent, String title, ScreenShotLists.ScreenShotList screenShots, Icon icon) {
			return GroupingNode.create(parent, title, screenShots, Comparator.naturalOrder(), icon, ScreenShotNode::new)
					.setFile(screenShots.imagesFolder);
		}

		static class Root extends BaseTreeNode<TreeNode,TreeNode> {
			Root() {
				super(null, "PlayersNGames.Root", true, false);
			}
			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				GroupingNode<Map.Entry<Integer,Game>> gamesNode   = GroupingNode.create(this, "Games"  , Data.games  , Comparator.<Game>naturalOrder(), GameNode  ::new);
				GroupingNode<Map.Entry<Long, Player>> playersNode = GroupingNode.create(this, "Players", Data.players, Data.createPlayerIdKeyOrder()  , PlayerNode::new);
				children.add(gamesNode);
				children.add(playersNode);
				
				gamesNode.setSorter(new GroupingNode.GroupingNodeSorter<Map.Entry<Integer,Game>,Game.GameSortOption>(Game.GameSortOption.createOptionList()) {
					@Override protected Comparator<Map.Entry<Integer,Game>> getOrder(Game.GameSortOption option) {
						if (option==null) return null;
						Comparator<Game> order = option.getOrder();
						if (order==null) return null;
						return Comparator.comparing(Map.Entry<Integer,Game>::getValue,order);
					}
					@Override Game.GameSortOption cast(SortOption option) {
						if (option instanceof Game.GameSortOption)
							return (Game.GameSortOption) option;
						return null;
					}
				});
				
				return children;
			}
		}
		
		private static class GameNode extends BaseTreeNode<TreeNode,TreeNode> implements URLBasedNode, ExternViewableNode {
		
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

			@Override public LabeledUrl getURL() { return Data.getShopURL(game.appID); }
			@Override public ExternViewableItem getExternViewableItem() { return ExternalViewerInfo.Browser.createItem(getURL()); }

			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				
				if (game.appManifest!=null) {
					children.add(new FileSystem.AppManifestNode(this, game.appManifest.file));
				}
				if (!game.gameInfos.isEmpty()) {
					children.add(GroupingNode.create(
							this, "Game Infos",
							game.gameInfos,
							Data.createPlayerIdKeyOrder(),
							GameInfosNode::new
					));
				}
				if (!game.lastPlayedData.isEmpty()) { // TODO
					children.add(GroupingNode.create(
							this, "Last Played",
							game.lastPlayedData,
							Data.createPlayerIdKeyOrder(),
							(p,playerID,appData)->PlayerNode.createAppDataNode(p, appData, "by "+Data.getPlayerName(playerID), null)
					));
				}
				if (!game.achievementProgress.isEmpty()) {
					children.add(GroupingNode.create(
							this, "Achievement Progress",
							game.achievementProgress,
							Data.createPlayerIdKeyOrder(),
							AchievementProgressNode.AchievementProgressInGameNode::new
					));
				}
				if (game.imageFiles!=null && !game.imageFiles.isEmpty()) {
					children.add(GroupingNode.create(
							this, "Images",
							game.imageFiles,
							GroupingNode.createMapKeyOrder(Comparator.<String>naturalOrder()),
							TreeIcons.ImageFile.getIcon(),
							(p,i,v)->FileSystem.FolderNode.createNode(p,v,null,null)
					));
				}
				if (!game.screenShots.isEmpty()) {
					children.add(GroupingNode.create(
							this, "ScreenShots",
							game.screenShots,
							Data.createPlayerIdKeyOrder(),
							TreeIcons.ImageFile.getIcon(),
							PlayersNGames::createGameScreenShotsNode
					));
				}
				if (!game.steamCloudFolders.isEmpty()) {
					children.add(GroupingNode.create(
							this, "SteamCloud Shared Data",
							game.steamCloudFolders,
							Data.createPlayerIdKeyOrder(),
							TreeIcons.Folder.getIcon(),
							(p,i,v)->FileSystem.FolderNode.createNode(p,v,f->"by "+Data.getPlayerName(i),null)
					));
				}
				
				return children;
			}
		}

		private static class PlayerNode extends BaseTreeNode<TreeNode,TreeNode> implements FileBasedNode, URLBasedNode, ExternViewableNode  {

			//private long playerID;
			private Player player;

			public PlayerNode(TreeNode parent, Long playerID, Player player) {
				super(parent, generateTitle(player), true, false);
				//this.playerID = playerID;
				this.player = player;
			}

			private static String generateTitle(Player player) {
				String title = player.getName(true);
				Integer playerLevel = player.getPlayerLevel();
				if (playerLevel!=null) title += " (Steam Level: "+playerLevel+")";
				return title;
			}

			@Override public LabeledFile getFile() { return new LabeledFile(player.folder); }
			@Override public LabeledUrl getURL() { return Data.getSteamPlayerProfileURL(player.playerID); }
			@Override public ExternViewableItem getExternViewableItem() { return ExternalViewerInfo.Browser.createItem(getURL()); }

			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				
				if (player.steamCloudFolders!=null && !player.steamCloudFolders.isEmpty()) {
					children.add(
						GroupingNode.create(
							this, "SteamCloud Shared Data",
							player.steamCloudFolders,
							Data.createGameIdKeyOrder(),
							PlayerNode::createSteamCloudFolderNode
						).setFile(player.folder)
					);
				}
				if (player.screenShots!=null && !player.screenShots.isEmpty()) {
					children.add(
						GroupingNode.create(
							this, "ScreenShots",
							player.screenShots,
							Data.createGameIdKeyOrder(),
							TreeIcons.ImageFile.getIcon(),
							PlayersNGames::createGameScreenShotsNode
						).setFile(player.screenShots.folder)
					);
				}
				if (player.configFolder!=null) {
					children.add(new FileSystem.FolderNode(this, "Config Folder", player.configFolder));
				}
				if (player.localconfig!=null) {
					
					if (player.localconfig.vdfTreeNode!=null)
						children.add(new RawVDFDataNode(
							this, "LocalConfig",
							player.localconfig.vdfTreeNode,
							player.localconfig.getClass()
						).setFile(
							player.localconfig.file,
							ExternalViewerInfo.TextEditor
						));
					else
						children.add(new FileSystem.VDF_File(this, "LocalConfig (File)", player.localconfig.file,"LocalConfig<VDF>"));
					
					if (player.localconfig.softwareValveSteamApps!=null)
						children.add(createSoftwareValveSteamAppsNode(this,player.localconfig.softwareValveSteamApps));
					
					if (player.localconfig.friendList!=null)
						children.add(new FriendListNode(this, player.localconfig.friendList));
				}
				if (player.achievementProgress!=null) {
					children.add(new AchievementProgressNode(this, player.achievementProgress));
				}
				if (!player.gameInfos.isEmpty()) {
					GroupingNode<Map.Entry<Integer, GameInfos>> groupingNode1;
					children.add(groupingNode1 = GroupingNode.create(
							this, "Game Infos",
							player.gameInfos,
							Data.createGameIdKeyOrder(),
							GameInfosNode::new
					));
					groupingNode1.setFile(player.gameStateFolder);
					groupingNode1.setFilter(GroupingNode.createMapFilter(
							GameInfosFilterOptions.class,
							GameInfosFilterOptions.values(),
							GameInfosFilterOptions::cast,
							GameInfos::meetsFilterOption
					));
				}
				
				return children;
			}

			private static FolderNode createSteamCloudFolderNode(TreeNode parent, Integer gameID, File file) {
				FileSystem.FolderNode node = new FileSystem.FolderNode(parent, Data.getGameTitle(gameID), file, Data.getGameIcon(gameID, TreeIcons.Folder));
				gameChangeListeners.add(gameID, new GameChangeListener() {
					@Override public TreeNode getTreeNode() { return node; }
					@Override public void gameTitleWasChanged() { node.setTitle(Data.getGameTitle(gameID)); }
				});
				return node;
			}

			private static TreeNode createSoftwareValveSteamAppsNode(TreeNode parent, SoftwareValveSteamApps apps) {
				String baseNodeTitle = "Last Play Times";
				MultiPurposeNode baseNode;
				if (apps.hasParsedData) {
//					Comparator<SoftwareValveSteamApps.App> sortOrder = Comparator.comparing(app->app.appID,Comparator.nullsLast(Data.createGameIdOrder()));
//					sortOrder = sortOrder.thenComparing(app->app.nodeName);
					Comparator<SoftwareValveSteamApps.AppData> sortOrder = Comparator.comparing(app->app.lastPlayed_Ts,Comparator.nullsLast(Comparator.reverseOrder()));
					sortOrder = sortOrder.thenComparing(app->app.nodeName);
					
					baseNode = GroupingNode.create(parent, baseNodeTitle, apps.apps, sortOrder, PlayerNode::createAppDataNode)
							.setTextSource(()->{
						ValueListOutput out = new ValueListOutput();
						addLine      (out,0,apps.playerLevel    , apps.str_playerLevel , "Player Level", "%d");
						addDateLine_s(out,0,apps.lastSyncTime_Ts, apps.str_lastSyncTime, "Last Sync Time of \"Played Times\"");
						return out.generateOutput();
					});
					
				} else {
					baseNode = new SimpleLeafNode(parent, baseNodeTitle);
					if (apps.rawData!=null)
						baseNode.setDataTree(apps.rawData.createRawDataTreeRoot(apps.getClass()));
				}
				
				return baseNode;
			}

			private static TreeNode createAppDataNode(TreeNode p, SoftwareValveSteamApps.AppData app) {
				String title = "Game \""+app.nodeName+"\"";
				Icon icon = null;
				if (app.appID!=null) {
					title = Data.getGameTitle(app.appID);
					icon  = Data.getGameIcon (app.appID,TreeIcons.Folder);
				}
				
				SimpleLeafNode node = createAppDataNode(p, app, title, icon);
				
				if (app.appID!=null) {
					node.setURL(Data.getShopURL(app.appID), false, ExternalViewerInfo.Browser);
					gameChangeListeners.add(app.appID, new GameChangeListener() {
						@Override public TreeNode getTreeNode() { return node; }
						@Override public void gameTitleWasChanged() { node.setTitle(Data.getGameTitle(app.appID)); }
					});
				}
				return node;
			}

			private static SimpleLeafNode createAppDataNode(TreeNode p, SoftwareValveSteamApps.AppData app, String title, Icon icon) { // TODO
				SimpleLeafNode node = new SimpleLeafNode(p, icon, title);
				if (app.hasParsedData)
					node.setTextSource(()->{
						ValueListOutput out = new ValueListOutput();
						if (app.appID               !=null) out.add(0, "App ID   ", "%d%s", app.appID, Data.hasGameATitle(app.appID) ? "  ->  "+Data.getGameTitle(app.appID) : "");
						else                                out.add(0, "Node Name", app.nodeName);
						addDateLine_s  (out, 0, app.lastPlayed_Ts          , app.str_lastPlayed          , "Last Time Played"       );
						addTimeLine_min(out, 0, app.playtime_min           , app.str_playtime            , "Playtime"               );
						addTimeLine_min(out, 0, app.playtime_min_2weeks    , app.str_playtime_2wks       , "Playtime (last 2 Weeks)");
						addDateLine_s  (out, 0, app.autocloud_lastlaunch_Ts, app.str_autocloud_lastlaunch, "Autocloud > Last Launch");
						addDateLine_s  (out, 0, app.autocloud_lastexit_Ts  , app.str_autocloud_lastexit  , "Autocloud > Last Exit  ");
						if (app.badgeData           !=null) out.add(0, "Badge Data          ", app.badgeData       );
						if (app.news                !=null) out.add(0, "News                ", app.news            );
						if (app._1161580_eula_0     !=null) out.add(0, "\"1161580_eula_0\"  ", app._1161580_eula_0 );
						if (app.eula_47870          !=null) out.add(0, "\"eula_47870\"      ", app.eula_47870      );
						if (app.viewedLaunchEULA    !=null) out.add(0, "\"viewedLaunchEULA\"", app.viewedLaunchEULA);
						
						return out.generateOutput();
					});
				
				else if (app.rawData!=null) 
					node.setDataTree(app.rawData.createRawDataTreeRoot(app.getClass()));
				
				return node;
			}

			private static void addLine(ValueListOutput out, int indentLevel, Integer parsedValue, String rawStr, String label, String format) {
				if (parsedValue != null)
					out.add(indentLevel, label, format, parsedValue);
				else if (rawStr != null)
					out.add(indentLevel, label, rawStr);
			}
			private static void addTimeLine_min(ValueListOutput out, int indentLevel, Integer parsedValue_min, String rawStr, String label) {
				if (parsedValue_min != null)
					out.add(indentLevel, label, "%d min  (%d:%02dh))", parsedValue_min, parsedValue_min / 60, parsedValue_min % 60);
				else if (rawStr != null)
					out.add(indentLevel, label, rawStr);
			}
			private static void addDateLine_s(ValueListOutput out, int indentLevel, Long parsedValue_s, String rawStr, String label) {
				if (parsedValue_s != null)
					out.add(indentLevel, label, "%d  ( %s )", parsedValue_s, getTimeStr(parsedValue_s*1000));
				else if (rawStr != null)
					out.add(indentLevel, label, rawStr);
			}
		}
		
		private static class FriendListNode extends BaseTreeNode<TreeNode,TreeNode> implements TextContentSource, TreeContentSource {

			private final FriendList data;

			public FriendListNode(TreeNode parent, FriendList friendList) {
				super(parent,"Friends",true,false);
				this.data = friendList;
			}

			@Override ContentType getContentType() { return data.values!=null ? ContentType.PlainText : data.rawData!=null ? ContentType.DataTree : null; }
			@Override public String getContentAsText() {
				if (data.values==null) return null;
				Vector<String> valueKeys = new Vector<>(data.values.keySet());
				valueKeys.sort(null);
				Iterator<String> iterator = valueKeys
						.stream()
						.map(key->String.format("%s: \"%s\"%n", key, data.values.get(key)))
						.iterator();
				return String.join("", (Iterable<String>)()->iterator);
			}
			@Override public TreeRoot getContentAsTree() {
				if (data.rawData==null) return null;
				return data.rawData.createRawDataTreeRoot(data.getClass());
			}

			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				if (data.rawData!=null)
					children.add( new RawVDFDataNode(this, "Raw VDF Data", data.rawData, data.getClass()) );
				if (data.friends!=null) {
					Vector<FriendList.Friend> vector = new Vector<>(data.friends);
					vector.sort(Comparator.<FriendList.Friend,Long>comparing(friend->friend.hasParsedData ? friend.id : null,Comparator.nullsFirst(Comparator.naturalOrder())));
					for (FriendList.Friend friend:vector)
						children.add( new FriendNode(this, friend) );
				}
				return children;
			}
			
			static class FriendNode extends BaseTreeNode<TreeNode,TreeNode> implements TextContentSource, TreeContentSource, ExternViewableNode, URLBasedNode {
				
				private static final Color COLOR_IS_PERSON = new Color(0x008000);
				private final FriendList.Friend friend;
				
				FriendNode(TreeNode parent, FriendList.Friend friend) {
					super(parent, generateTitle(friend), false, true);
					this.friend = friend;
					if (friend==null) throw new IllegalArgumentException();
				}

				private static String generateTitle(FriendList.Friend friend) {
					if (friend==null) return "(NULL) Friend";
					String str = "Friend";
					if (friend.hasParsedData) {
						str += String.format(" %016X", friend.id);
						if (friend.name!=null) str += String.format(" \"%s\"", friend.name);
						if (friend.tag !=null) str += String.format(" (Tag:%s)", friend.tag);
					} else
						if (friend.rawData!=null) str = "(Raw Data) " + str;
					return str;
				}

				@Override public LabeledUrl getURL() { return !friend.isPerson ? null : Data.getSteamPlayerProfileURL(friend.playerID); }
				@Override public ExternViewableItem getExternViewableItem() { return !friend.isPerson ? null : ExternalViewerInfo.Browser.createItem(getURL()); }

				@Override Color getTextColor() {
					if (friend.isPerson) return COLOR_IS_PERSON;
					return null;
				}

				@Override ContentType getContentType() { return friend.hasParsedData ? ContentType.PlainText : friend.rawData!=null ? ContentType.DataTree : null; }
				@Override public String getContentAsText() {
					if (!friend.hasParsedData) return null;
					ValueListOutput out = new ValueListOutput();
					if (friend.name  !=null) out.add(0, "Name", "\"%s\"", friend.name);
					if (friend.tag   !=null) out.add(0, "Tag" , "\"%s\"", friend.tag);
					out.add(0, "Is Person", "%s", friend.isPerson);
					out.add(0, "ID"       , "%d", friend.id);
					out.add(0, "",     "0x%016X", friend.id);
					out.add(0, "", "[b0..b31]%d [b32..b63]%d", friend.id_lower, friend.id_upper);
					if (friend.avatar!=null) out.add(0, "Avatar", "\"%s\"", friend.avatar);
					if (friend.nameHistory!=null) {
						out.add(0, "Name History");
						Vector<Integer> idVec = new Vector<>(friend.nameHistory.keySet());
						idVec.sort(null);
						for (Integer id:idVec) {
							String val = friend.nameHistory.get(id);
							out.add(1, "", "[%d] \"%s\"", id, val);
						}
					}
					return out.generateOutput();
				}
				@Override public TreeRoot getContentAsTree() {
					return friend.rawData==null ? null : friend.rawData.createRawDataTreeRoot(friend.getClass());
				}

				@Override protected Vector<? extends TreeNode> createChildren() { return null; }
			}
		}
		
		private static class AchievementProgressNode extends BaseTreeNode<TreeNode,TreeNode> implements FileBasedNode, ExternViewableNode, TreeContentSource {
			
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

			@Override ContentType getContentType() { return data.rawData!=null ? ContentType.DataTree : null; }
			@Override public TreeRoot getContentAsTree() {
				if (data.rawData!=null)
					return JSONHelper.createRawDataTreeRoot(data.getClass(), data.rawData, false);
				return null;
			}

			@Override public LabeledFile getFile() { return new LabeledFile(data.file); }
			@Override public ExternViewableItem getExternViewableItem() { return ExternalViewerInfo.TextEditor.createItem(getFile()); }

			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				if (data.rawData!=null)
					children.add( new RawJsonDataNode(this, "Raw JSON Data", data.rawData, data.getClass()).setFile(data.file, ExternalViewerInfo.TextEditor) );
				if (data.gameStates!=null) {
					Vector<Integer> vec = new Vector<>(data.gameStates.keySet());
					vec.sort(Data.createGameIdOrder());
					for (Integer gameID:vec)
						children.add(new AchievementProgressInGameNode(this,gameID,data.gameStates.get(gameID)));
				}
				if (data.gameStates_withoutID!=null) {
					for (AchievementProgressInGame gameStatus:data.gameStates_withoutID)
						children.add(new AchievementProgressInGameNode(this,(Integer)null,gameStatus));
					
				}
				return children;
			}
			
			static class AchievementProgressInGameNode extends BaseTreeNode<TreeNode,TreeNode> implements TextContentSource, TreeContentSource {

				private final AchievementProgressInGame progress;

				public AchievementProgressInGameNode(TreeNode parent, Long playerID, AchievementProgressInGame progress) {
					super(parent,"by "+Data.getPlayerName(playerID),true,false);
					this.progress = progress;
				}
				public AchievementProgressInGameNode(TreeNode parent, Integer gameID, AchievementProgressInGame gameStatus) {
					super(parent,Data.getGameTitle(gameID),true,false,Data.getGameIcon(gameID, TreeIcons.Folder));
					if (gameStatus!=null) {
						gameChangeListeners.add(gameID, new GameChangeListener() {
							@Override public TreeNode getTreeNode() { return AchievementProgressInGameNode.this; }
							@Override public void gameTitleWasChanged() { setTitle(Data.getGameTitle(gameID)); }
						});
					}
					this.progress = gameStatus;
				}

				@Override ContentType getContentType() {
					if (progress!=null) {
						if (progress.hasParsedData) return ContentType.PlainText;
						if (progress.rawData!=null) return ContentType.DataTree;
					}
					
					return null;
				}
				@Override public String getContentAsText() {
					if (progress==null || !progress.hasParsedData) return null;
					String str = "";
					str += String.format(Locale.ENGLISH, "Unlocked: %d/%d (%f%%)%n", progress.unlocked, progress.total, progress.total==0 ? Double.NaN : progress.unlocked/(double)progress.total*100);
					str += String.format(Locale.ENGLISH, "Percentage: %f%n"  , progress.percentage);
					str += String.format(Locale.ENGLISH, "All Unlocked: %s%n", progress.allUnlocked);
					str += String.format(Locale.ENGLISH, "Cache Time: %d (%s)%n", progress.cacheTime, getTimeStr(progress.cacheTime*1000));
					return str;
				}
				@Override public TreeRoot getContentAsTree() {
					if (progress==null || progress.rawData==null) return null;
					return JSONHelper.createRawDataTreeRoot(progress.getClass(), progress.rawData, false);
				}

				@Override
				protected Vector<? extends TreeNode> createChildren() {
					Vector<TreeNode> children = new Vector<>();
					if (progress!=null) {
						if (progress.rawData!=null)
							children.add(new RawJsonDataNode   (this, "Raw JSON Data", progress.rawData, progress.getClass()) );
						if (progress.hasParsedData) {
							children.add(new SimpleLeafNode    (this, "Unlocked: %d/%d (%f%%)", progress.unlocked, progress.total, progress.total==0 ? Double.NaN : progress.unlocked/(double)progress.total*100) );
							children.add(new PrimitiveValueNode(this, "Percentage"  , progress.percentage));
							children.add(new PrimitiveValueNode(this, "All Unlocked", progress.allUnlocked));
							children.add(new SimpleLeafNode    (this, "Cache Time: %d (%s)", progress.cacheTime, getTimeStr(progress.cacheTime*1000)));
						}
					}
					return children;
				}
			}
		}
		
		private static class GameInfosNode extends BaseTreeNode<TreeNode,TreeNode> implements FileBasedNode, ExternViewableNode, TreeContentSource {
			
			private final GameInfos data;

			GameInfosNode(TreeNode parent, Long playerID, GameInfos gameInfos) {
				super(parent, "by "+Data.getPlayerName(playerID)+generateExtraInfo(gameInfos), true, false);
				this.data = gameInfos;
			}
			GameInfosNode(TreeNode parent, Integer gameID, GameInfos gameInfos) {
				super(parent, Data.getGameTitle(gameID)+generateExtraInfo(gameInfos), true, false, generateMergedIcon( Data.getGameIcon(gameID, TreeIcons.Folder), gameInfos ));
				gameChangeListeners.add(gameID, new GameChangeListener() {
					@Override public TreeNode getTreeNode() { return GameInfosNode.this; }
					@Override public void gameTitleWasChanged() { setTitle(Data.getGameTitle(gameID)+generateExtraInfo(gameInfos)); }
				});
				this.data = gameInfos;
			}

			private static Icon generateMergedIcon(Icon baseIcon, GameInfos gameInfos) {
				if (baseIcon==null) baseIcon = TreeIcons.Folder.getIcon();
				return IconSource.setSideBySide(
					true, 1, baseIcon,
					gameInfos.fullDesc    ==null ? IconSource.createEmptyIcon(16,16) : TreeIcons.TextFile   .getIcon(),
					gameInfos.badge       ==null ? IconSource.createEmptyIcon(16,16) : TreeIcons.Badge      .getIcon(),
					gameInfos.achievements==null ? IconSource.createEmptyIcon(16,16) : TreeIcons.Achievement.getIcon()
				);
			}
			
			private static String generateExtraInfo(GameInfos gameInfos) {
				String str = "";
				if (gameInfos.badge!=null) {
					String str1 = gameInfos.badge.getTreeNodeExtraInfo();
					if (str1!=null && !str1.isEmpty())
						str += (str.isEmpty()?"":", ") + str1;
				}
				if (gameInfos.achievements!=null) {
					String str1 = gameInfos.achievements.getTreeNodeExtraInfo();
					if (str1!=null && !str1.isEmpty())
						str += (str.isEmpty()?"":", ") + str1;
				}
				if (gameInfos.communityItems!=null) {
					if (gameInfos.communityItems.hasParsedData) {
						if (!gameInfos.communityItems.items.isEmpty())
							str += (str.isEmpty()?"":", ") + "CI:"+gameInfos.communityItems.items.size();
					} else
						if (gameInfos.communityItems.rawData!=null)
							str += (str.isEmpty()?"":", ") + "CI:R";
				}
				return str.isEmpty() ? "" : " ("+str+")";
			}
			
			@Override public LabeledFile getFile() { return new LabeledFile(data.file); }
			@Override public ExternViewableItem getExternViewableItem() { return ExternalViewerInfo.TextEditor.createItem(getFile()); }

			@Override ContentType getContentType() { return ContentType.DataTree; }
			@Override public TreeRoot getContentAsTree() {
				if (data.rawData==null) return null;
				return JSONHelper.createRawDataTreeRoot(data.getClass(), data.rawData, false);
			}
			
			@Override Color getTextColor() {
				return NodeColorizer.getTextColor_WarnNew(data.rawData);
			}
			
			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				if (data.fullDesc!=null) {
					children.add(new SimpleLeafNode(this, TreeIcons.TextFile.getIcon(), "Full Game Description").setTextSource(()->data.fullDesc));
				}
				if (data.shortDesc!=null) {
					children.add(new SimpleLeafNode(this, TreeIcons.TextFile.getIcon(), "Short Game Description").setTextSource(()->data.shortDesc));
				}
				if (data.badge!=null) {
					children.add(new BadgeNode(this, data.badge));
				}
				if (data.achievements!=null) {
					children.add(new AchievementsNode(this, data.achievements));
				}
				if (data.achievementMap!=null) {
					if (data.achievementMap.hasParsedData) {
						if (!data.achievementMap.entries.isEmpty() || !JSON_Data.isEmpty(data.achievementMap.parsedJsonValue))
						children.add(
							GroupingNode
							.create(this, "Achievement Map", data.achievementMap.entries, null, TreeIcons.Achievement.getIcon(), AchievementMapEntryNode::new)
							.setDataTree(JSONHelper.createDataTreeRoot(data.achievementMap.getClass(), "ParsedJsonValue", data.achievementMap.parsedJsonValue,false))
						);
					} else
						if (data.achievementMap.rawData!=null)
							children.add(new RawJsonDataNode(this, "Achievement Map [Raw Data]", data.achievementMap.rawData, data.achievementMap.getClass(), TreeIcons.Achievement.getIcon()));
				}
				if (data.communityItems!=null) {
					if (data.communityItems.hasParsedData) {
						if (!data.communityItems.items.isEmpty())
							children.add(GroupingNode.create(this, "Community Items", data.communityItems.items, null, CommunityItemNode::new));
					} else
						if (data.communityItems.rawData!=null)
							children.add(new RawJsonDataNode(this, "Community Items [Raw Data]", data.communityItems.rawData, data.communityItems.getClass()));
				}
				if (!CombinedBase64ValuesNode.isEmpty(data.appActivity,data.gameActivity,data.userNews)) {
					children.add(new CombinedBase64ValuesNode(this, "Some Base64 Values", data.appActivity,data.gameActivity,data.userNews));
				}
				if (data.socialMedia!=null) {
					if (data.socialMedia.hasParsedData) {
						if (!data.socialMedia.entries.isEmpty())
							children.add(GroupingNode.create(this, "Social Media", data.socialMedia.entries, null, GameInfosNode::createSocialMediaEntryNode));
					} else
						if (data.socialMedia.rawData!=null)
							children.add(new RawJsonDataNode(this, "Social Media", data.socialMedia.rawData, data.socialMedia.getClass()));
				}
				if (data.associations!=null) {
					if (data.associations.hasParsedData) {
						if (!data.associations.isEmpty())
							children.add(GroupingNode.create(this, "Associations", (p,ch)->{
								addNodesTo(p,ch,"Developer",data.associations.developers);
								addNodesTo(p,ch,"Franchise",data.associations.franchises);
								addNodesTo(p,ch,"Publisher",data.associations.publishers);
							}));
					} else
						if (data.associations.rawData!=null)
							children.add(new RawJsonDataNode(this, "Associations", data.associations.rawData, data.associations.getClass()));
				}
				if (data.friends!=null) {
					if (data.friends.hasParsedData) {
						if (!data.friends.isEmpty()) {
							children.add(
									GroupingNode.create(this, "Played or Owned by Players", (p,ch)->addRawDataNodesTo(p,ch,data.friends, data.playerID))
									.setTextSource(()->toString(data.friends, data.playerID, data.gameID))
							);
						}
					} else
						if (data.friends.rawData!=null)
							children.add(new RawJsonDataNode(this, "Played or Owned by Players (Raw Data)", data.friends.rawData, data.friends.getClass()));
				}
				if (data.releaseData!=null) {
					if (data.releaseData.hasParsedData) {
						if (!data.releaseData.isEmpty()) {
							// spaceholder for future data
						}
					} else
						if (data.releaseData.rawData!=null)
							children.add(new RawJsonDataNode(this, "Release Data (Raw Data)", data.releaseData.rawData, data.releaseData.getClass()));
				}
				if (data.workshop!=null) {
					if (data.workshop.hasParsedData) {
						if (!data.workshop.entries.isEmpty())
							children.add(GroupingNode.create(this, "Workshop", data.workshop.entries, null, GameInfosNode::createWorkshopEntryNode));
					} else
						if (data.workshop.rawData!=null)
							children.add(new RawJsonDataNode(this, "Workshop", data.workshop.rawData, data.workshop.getClass()));
				}
				if (data.blocks!=null) {
					MultiPurposeNode node = GroupingNode
							.create(this, "All Data Blocks (unparsed)", data.blocks, null, BlockNode::new)
							.setExternViewable(data.file,ExternalViewerInfo.TextEditor);
					if (data.rawData!=null) {
						node.setTextColor(NodeColorizer.getTextColor_WarnNew(data.rawData));
						node.setDataTree(JSONHelper.createRawDataTreeRoot(data.getClass(), data.rawData, false));
					}
					children.add(node);
				}
				
				return children;
			}
			
			private static void addRawDataNodesTo(TreeNode p, Vector<TreeNode> ch, GameInfos.Friends friends, long playerID) {
				if (friends==null) throw new IllegalArgumentException();
				if (!friends.hasParsedData) throw new IllegalArgumentException();
				
				friends.played_ever    .forEach(entry->addRawDataNodeTo(p, ch, entry, "Played ever"    ));
				friends.played_recently.forEach(entry->addRawDataNodeTo(p, ch, entry, "Played recently"));
				if (friends.your_info!=null)           addRawDataNodeTo(p, ch, friends.your_info, String.format("Infos for %s:%n", Data.getPlayerName(playerID)));
			}
			private static void addRawDataNodeTo(TreeNode p, Vector<TreeNode> ch, GameInfos.Friends.Entry entry, String label) {
				if (entry.rawData!=null)
					ch.add(new RawJsonDataNode(p, String.format("Raw Data \"%s\"", label), entry.rawData, entry.getClass()));
			}
			private static String toString(GameInfos.Friends friends, long playerID, int gameID) {
				if (friends==null) throw new IllegalArgumentException();
				if (!friends.hasParsedData) throw new IllegalArgumentException();
				
				StringBuilder sb = new StringBuilder();
				String indent = "    ";
				if ( friends.in_wishlist    .length>0 ) sb.append(String.format("In Wishlist" +" by%n%s", toString(indent,friends.in_wishlist)));
				if ( friends.owns           .length>0 ) sb.append(String.format("Owned"       +" by%n%s", toString(indent,friends.owns       )));
				if (!friends.played_ever    .isEmpty()) sb.append(String.format("Played ever" +" by%n%s", toString(indent,friends.played_ever, gameID)));
				if (!friends.played_recently.isEmpty()) sb.append(String.format("Played recently by%n%s", toString(indent,friends.played_ever, gameID)));
				if (friends.your_info!=null) {
					sb.append(String.format("Infos for %s:%n", Data.getPlayerName(playerID)));
					sb.append(toString(indent, friends.your_info, gameID));
				}
				return sb.toString();
			}
			private static String toString(String indent, GameInfos.Friends.Entry entry, int gameID) {
				if (entry==null) throw new IllegalArgumentException();
				if (!entry.hasParsedData) throw new IllegalArgumentException();
				
				ValueListOutput out = new ValueListOutput();
				if (entry.steamid               !=null) out.add(0, "SteamID"          , "%s", entry.steamid.str);
				if (entry.owned                 !=null) out.add(0, "Owns"             , "%s -> %s", Data.getGameTitle(gameID), entry.owned);
				if (entry.minutes_played        !=null) out.add(0, "Played"           , "%s min", entry.minutes_played        );
				if (entry.minutes_played_forever!=null) out.add(0, "Played (over all)", "%s min", entry.minutes_played_forever);
				return out.generateOutput(indent);
			}
			private static String toString(String indent, Vector<GameInfos.Friends.Entry> arr, int gameID) {
				StringBuilder sb = new StringBuilder();
				arr.forEach(entry->{
					String playerName = "Player ???";
					if (entry!=null && entry.steamid!=null) playerName = entry.steamid.getPlayerName();
					sb.append(String.format("%s%s%n", indent, playerName));
					if (entry!=null) {
						if (entry.hasParsedData)
							sb.append(String.format("%s%n", toString(indent+"    ", entry, gameID)));
						else if (entry.rawData!=null)
							sb.append(String.format("<Raw Data>%n"));
					}
				});
				return sb.toString();
			}
			private static String toString(String indent, Data.SteamId[] arr) {
				StringBuilder sb = new StringBuilder();
				for (Data.SteamId steamId : arr)
					sb.append(String.format("%s%s%n", indent, steamId==null ? "???" : steamId.getPlayerName()));
				return sb.toString();
			}
			
			private static TreeNode createSocialMediaEntryNode(TreeNode parent, GameInfos.SocialMedia.Entry entry) {
				if (entry.hasParsedData) {
					Icon icon = null;
					if (entry.type!=null) {
						switch (entry.type) {
						case Facebook: icon = TreeIcons.Facebook.getIcon(); break;
						case Twitch  : icon = TreeIcons.Twitch  .getIcon(); break;
						case Twitter : icon = TreeIcons.Twitter .getIcon(); break;
						case YouTube : icon = TreeIcons.YouTube .getIcon(); break;
						}
					}
					return createUrlNode(parent, icon, String.format("%s \"%s\"", entry.type==null ? "<Type "+entry.typeN+">" : entry.type.toString(), entry.name), entry.url);
				}
				if (entry.rawData!=null)
					return new RawJsonDataNode(parent, "Entry", entry.rawData, entry.getClass());
				return null;
			}
			
			private static TreeNode createWorkshopEntryNode(TreeNode parent, GameInfos.Workshop.Entry entry) {
				if (entry.hasParsedData) {
					MultiPurposeNode node;
					if (
						isEmpty(entry.url) &&
						isEmpty(entry.preview_url) &&
						isEmpty(entry.publishedfileid) &&
						!isPlayerID(entry.banner) &&
						!isPlayerID(entry.creator) &&
						!isAppID(entry.creator_appid) &&
						!isAppID(entry.consumer_appid)
					)
						node = new SimpleLeafNode(parent, "Workshop Item \"%s\"", entry.title);
					else
						node = GroupingNode.create(parent, "Workshop Item \""+entry.title+"\"", (p,ch)->{
							if (!isEmpty(entry.url            )) ch.add(createUrlNode(p, null, ""       ,entry.url                           , false, "%s: \"%s\"", "URL"          , entry.url        ));
							if (!isEmpty(entry.preview_url    )) ch.add(createUrlNode(p, null, "Preview",entry.preview_url                   , true , "%s: \"%s\"", "Preview Image", entry.preview_url));
							if (!isEmpty(entry.publishedfileid)) ch.add(createUrlNode(p, null, Data.getWorkshopItemURL(entry.publishedfileid), false, "%s: \"%s\"", "Workshop Item", entry.publishedfileid));
							if ( isPlayerID(entry.banner      )) ch.add(createUrlNode(p, null, Data.getSteamPlayerProfileURL(entry.banner )  , false, "%s: \"%s\"", "Banner"       , entry.banner));
							if ( isPlayerID(entry.creator     )) ch.add(createUrlNode(p, null, Data.getSteamPlayerProfileURL(entry.creator)  , false, "%s: \"%s\"", "Creator"      , entry.creator));
							if ( isAppID(entry.creator_appid  )) ch.add(createUrlNode(p, null, Data.getShopURL(""+entry.creator_appid )      , false, "%s: %d"    , "Creator App"  , entry.creator_appid ));
							if ( isAppID(entry.consumer_appid )) ch.add(createUrlNode(p, null, Data.getShopURL(""+entry.consumer_appid)      , false, "%s: %s"    , "Consumer App" , entry.consumer_appid));
						});
					
					node.setTextSource(()->entry.generateStringOutput());
					if      (!isEmpty(entry.publishedfileid)) node.setURL(Data.getWorkshopItemURL(entry.publishedfileid), false, ExternalViewerInfo.Browser);
					else if (!isEmpty(entry.url            )) node.setURL(new LabeledUrl(          entry.url        )   , false, ExternalViewerInfo.Browser);
					else if (!isEmpty(entry.preview_url    )) node.setURL(new LabeledUrl("Preview",entry.preview_url)   , false, ExternalViewerInfo.Browser);
					return node;
				}
				if (entry.rawData!=null) return new RawJsonDataNode(parent, "Workshop Item", entry.rawData, entry.getClass());
				return null;
			}
			
			private static boolean isPlayerID(String id) {
				return SteamId.parse(id).isPlayer();
			}
			private static boolean isAppID(long appid) {
				return 0<appid && appid<0xFFFFFFFFL;
			}
			private static boolean isEmpty(String str) {
				return str==null || str.isEmpty();
			}
			private static void addNodesTo(TreeNode parent, Vector<TreeNode> ch, String nodeLabel, Vector<GameInfos.Associations.Association> array) {
				if (array==null) return;
				array.forEach(v->{
					if      (v.hasParsedData) ch.add(createUrlNode(parent, nodeLabel + (v.name!=null ? " \""+v.name+"\"" : ""), v.url));
					else if (v.rawData!=null) ch.add(new RawJsonDataNode(parent, "["+nodeLabel+"]", v.rawData, v.getClass()));
				});
			}

			static class CombinedBase64ValuesNode extends BaseTreeNode<TreeNode,TreeNode> {
				
				private final GameInfos.AppActivity  appActivity;
				private final GameInfos.GameActivity gameActivity;
				private final GameInfos.UserNews     userNews;
				
				public CombinedBase64ValuesNode(TreeNode parent, String title, GameInfos.AppActivity appActivity, GameInfos.GameActivity gameActivity, GameInfos.UserNews userNews) {
					super(parent, title, true, false);
					this.appActivity  = appActivity;
					this.gameActivity = gameActivity;
					this.userNews     = userNews;
				}

				public static boolean isEmpty(GameInfos.AppActivity appActivity, GameInfos.GameActivity gameActivity, GameInfos.UserNews userNews) {
					if (appActivity !=null && !appActivity .isEmpty()) return false;
					if (gameActivity!=null && !gameActivity.isEmpty()) return false;
					if (userNews    !=null && !userNews    .isEmpty()) return false;
					return true;
				}

				@Override protected Vector<? extends TreeNode> createChildren() {
					Vector<TreeNode> children = new Vector<>();
					if (appActivity!=null) {
						if (appActivity.hasParsedData)
							children.add(new SimpleLeafNode(this, "App Activity").setTextSource(appActivity.value));
						else if (appActivity.rawData!=null)
							children.add(new RawJsonDataNode(this, "App Activity [Raw Data]", appActivity.rawData, appActivity.getClass()));
					}
					if (gameActivity!=null) {
						if (gameActivity.hasParsedData)
							for (int i=0; i<gameActivity.values.size(); i++)
								children.add(new SimpleLeafNode(this, "Game Activity ["+(i+1)+"]").setTextSource(gameActivity.values.get(i)));
						else if (gameActivity.rawData!=null)
							children.add(new RawJsonDataNode(this, "Game Activity [Raw Data]", gameActivity.rawData, gameActivity.getClass()));
					}
					if (userNews!=null) {
						if (userNews.hasParsedData)
							for (int i=0; i<userNews.values.size(); i++)
								children.add(new SimpleLeafNode(this, "User News ["+(i+1)+"]").setTextSource(userNews.values.get(i)));
						else if (userNews.rawData!=null)
							children.add(new RawJsonDataNode(this, "User News [Raw Data]", userNews.rawData, userNews.getClass()));
					}
					return children;
				}
			}

			static class CommunityItemNode extends BaseTreeNode<TreeNode,TreeNode> implements TreeContentSource, TextContentSource{
				
				private final GameInfos.CommunityItems.CommunityItem data;

				public CommunityItemNode(TreeNode parent, GameInfos.CommunityItems.CommunityItem data) {
					super(parent, generateTitle(data), hasSubNodes(data), !hasSubNodes(data), getIcon(data));
					this.data = data;
				}

				private static Icon getIcon(CommunityItem data) {
					//if (data.hasParsedData)
					//	return Data.getGameIcon((int) data.appID, null);
					return null;
				}

				private static String generateTitle(CommunityItem data) {
					String str = "CommunityItem";
					if (data.hasParsedData) {
						String classLabel = CommunityItem.getClassLabel(data.itemClass);
						if (classLabel!=null) str = classLabel;
						else str = "CommunityItem <Class "+data.itemClass+">";
						
						if (data.itemName!=null && !data.itemName.isEmpty())
							str += " \""+data.itemName+"\"";
						
					} else if (data.rawData!=null)
						str += " [Raw Data]";
					
					return str;
				}
				
				@Override ContentType getContentType() {
					if (data.hasParsedData)
						return ContentType.PlainText;
					if (data.rawData!=null)
						return ContentType.DataTree;
					return null;
				}

				@Override public String getContentAsText() {
					if (!data.hasParsedData) return null;
					ValueListOutput valueListOutput = new ValueListOutput();
					valueListOutput.add(0, "Is Active   ", "%s"     , data.isActive       );
					valueListOutput.add(0, "App ID      ", "%d"     , data.appID          );
					valueListOutput.add(0, "Name        ", "\"%s\"" , data.itemName       );
					valueListOutput.add(0, "Title       ", "\"%s\"" , data.itemTitle      );
					valueListOutput.add(0, "Description ", "\"%s\"" , data.itemDescription);
					valueListOutput.add(0, "Class       ", "%d"     , data.itemClass      );
					valueListOutput.add(0, "Series      ", "%d"     , data.itemSeries     );
					valueListOutput.add(0, "Type        ", "%d"     , data.itemType       );
					valueListOutput.add(0, "Last Changed", "%d (%s)", data.itemLastChanged, getTimeStr(data.itemLastChanged*1000));
					if (data.itemImageLarge       !=null) valueListOutput.add(0, "Image Large          ", "\"%s\"", data.itemImageLarge       );
					if (data.itemImageSmall       !=null) valueListOutput.add(0, "Image Small          ", "\"%s\"", data.itemImageSmall       );
					if (data.itemImageComposed    !=null) valueListOutput.add(0, "Image Composed       ", "\"%s\"", data.itemImageComposed    );
					if (data.itemImageComposedFoil!=null) valueListOutput.add(0, "Image Composed (Foil)", "\"%s\"", data.itemImageComposedFoil);
					if (data.itemMovieMp4         !=null) valueListOutput.add(0, "Movie MP4            ", "\"%s\"", data.itemMovieMp4         );
					if (data.itemMovieMp4Small    !=null) valueListOutput.add(0, "Movie MP4 (Small)    ", "\"%s\"", data.itemMovieMp4Small    );
					if (data.itemMovieWebm        !=null) valueListOutput.add(0, "Movie WEBM           ", "\"%s\"", data.itemMovieWebm        );
					if (data.itemMovieWebmSmall   !=null) valueListOutput.add(0, "Movie WEBM (Small)   ", "\"%s\"", data.itemMovieWebmSmall   );
					if (data.itemKeyValues        !=null) {
						valueListOutput.add(0, "Key Values", " ");
						if (data.itemKeyValues.hasParsedData) {
							valueListOutput.add(1, "<parsed data>");
							if (data.itemKeyValues.card_border_color     !=null) valueListOutput.add(1, "Trading Card Border Color       ", "\"%s\"", data.itemKeyValues.card_border_color     );
							if (data.itemKeyValues.card_drop_method      !=null) valueListOutput.add(1, "Trading Card Drop Method        ", "%s"    , data.itemKeyValues.card_drop_method      );
							if (data.itemKeyValues.card_drop_rate_minutes!=null) valueListOutput.add(1, "Trading Card Drop Rate (Minutes)", "%s"    , data.itemKeyValues.card_drop_rate_minutes);
							if (data.itemKeyValues.card_drops_enabled    !=null) valueListOutput.add(1, "Trading Card Drops Enabled      ", "%s"    , data.itemKeyValues.card_drops_enabled    );
							if (data.itemKeyValues.droprate              !=null) valueListOutput.add(1, "Drop Rate                       ", "%s"    , data.itemKeyValues.droprate              );
							if (data.itemKeyValues.item_release_state    !=null) valueListOutput.add(1, "Item Release State              ", "%s"    , data.itemKeyValues.item_release_state    );
							if (data.itemKeyValues.notes                 !=null) valueListOutput.add(1, "Notes                           ", "\"%s\"", data.itemKeyValues.notes                 );
							if (data.itemKeyValues.projected_release_date!=null) valueListOutput.add(1, "Projected Release Date          ", "%s"    , data.itemKeyValues.projected_release_date);
							if (data.itemKeyValues.levels                !=null) valueListOutput.add(1, "Badge Levels                    ", "%s"    , data.itemKeyValues.levels.size()         );
						} else if (data.itemKeyValues.rawData!=null)             valueListOutput.add(1, "<raw data>");
					}
					if (data.itemKeyValues_str    !=null) valueListOutput.add(0, "Key Values (JSON)", "%s", data.itemKeyValues_str);
					return valueListOutput.generateOutput();
				}
				
				@Override public TreeRoot getContentAsTree() {
					if (data.rawData==null) return null;
					return JSONHelper.createRawDataTreeRoot(data.getClass(), data.rawData, false);
				}

				@Override protected Vector<? extends TreeNode> createChildren() {
					Vector<TreeNode> children = new Vector<>();
					//log_ln(this, "createChildren()");
					if (data.hasParsedData) {
						//log_ln(this, "data.hasParsedData");
						addImageUrlNode   (this, children, data, data.itemImageLarge       , "Image Large"          );
						addImageUrlNode   (this, children, data, data.itemImageSmall       , "Image Small"          );
						addBase64ImageNode(this, children,       data.itemImageComposed    , "Image Composed"       );
						addBase64ImageNode(this, children,       data.itemImageComposedFoil, "Image Composed (Foil)");
						addUrlNode        (this, children, data, data.itemMovieMp4         , "Movie MP4"            );
						addUrlNode        (this, children, data, data.itemMovieMp4Small    , "Movie MP4 (Small)"    );
						addUrlNode        (this, children, data, data.itemMovieWebm        , "Movie WEBM"           );
						addUrlNode        (this, children, data, data.itemMovieWebmSmall   , "Movie WEBM (Small)"   );
						//log_ln(this, "data.itemKeyValues%s", data.itemKeyValues==null ? " == <null>" : data.itemKeyValues.hasParsedData ? " has parsed data" : " has NO parsed data");
						if (data.itemKeyValues!=null)
							addKeyValues(this, children, data, data.itemKeyValues);
					}
					return children;
				}

				private static void addKeyValues(TreeNode parent, Vector<TreeNode> children, CommunityItem data, CommunityItem.KeyValues values) {
					if (values.hasParsedData) {
						addImageUrlNode(parent, children, data, values.card_border_logo      , "Trading Card Border Logo"  );
						addImageUrlNode(parent, children, data, values.item_image_border     , "Trading Card Border"       );
						addImageUrlNode(parent, children, data, values.item_image_border_foil, "Trading Card Border (foil)");
						
						//log_ln(parent, "data.itemKeyValues.levels%s", values.levels==null ? " == <null>" : ".size() == "+values.levels.size());
						if (values.levels!=null)
							for (CommunityItem.KeyValues.Level level:values.levels) {
								String label = "Badge <Level "+level.id.toUpperCase()+">";
								if (level.name!=null) label += " \""+level.name+"\"";
								if (level.image!=null && !level.image.isEmpty())
									addImageUrlNode(parent, children, data, level.image, label);
								else
									children.add(new SimpleLeafNode(parent, "%s", label));
							}
						
					} else if (values.rawData!=null) {
						children.add(new RawJsonDataNode(parent, "KeyValues (Raw Data)", values.rawData, values.getClass()));
					}
				}

				private static boolean hasSubNodes(CommunityItem data) {
					if (!data.hasParsedData) return false;
					if (data.itemImageLarge       !=null && !data.itemImageLarge       .isEmpty()) return true;
					if (data.itemImageSmall       !=null && !data.itemImageSmall       .isEmpty()) return true;
					if (data.itemImageComposed    !=null && !data.itemImageComposed    .isEmpty()) return true;
					if (data.itemImageComposedFoil!=null && !data.itemImageComposedFoil.isEmpty()) return true;
					if (data.itemMovieMp4         !=null && !data.itemMovieMp4         .isEmpty()) return true;
					if (data.itemMovieMp4Small    !=null && !data.itemMovieMp4Small    .isEmpty()) return true;
					if (data.itemMovieWebm        !=null && !data.itemMovieWebm        .isEmpty()) return true;
					if (data.itemMovieWebmSmall   !=null && !data.itemMovieWebmSmall   .isEmpty()) return true;
					if (data.itemKeyValues!=null && data.itemKeyValues.hasParsedData) {
						if (data.itemKeyValues.card_border_logo      !=null && !data.itemKeyValues.card_border_logo      .isEmpty()) return true;
						if (data.itemKeyValues.item_image_border     !=null && !data.itemKeyValues.item_image_border     .isEmpty()) return true;
						if (data.itemKeyValues.item_image_border_foil!=null && !data.itemKeyValues.item_image_border_foil.isEmpty()) return true;
						if (data.itemKeyValues.levels!=null) return true;
					}
						
					return false;
				}

				private static void addImageUrlNode(TreeNode parent, Vector<TreeNode> children, CommunityItem data, String shortUrl, String label) {
					if (shortUrl!=null && !shortUrl.isEmpty()) {
						String url = data.getURL(shortUrl);
						children.add(
							new SimpleLeafNode(parent, TreeIcons.ImageFile.getIcon(), "%s: \"%s\"", label, shortUrl)
								.setURL(url, true)
								.setExternViewable(url, ExternalViewerInfo.Browser)
						);
					}
				}

				private static void addUrlNode(TreeNode parent, Vector<TreeNode> children, CommunityItem data, String shortUrl, String label) {
					if (shortUrl!=null && !shortUrl.isEmpty()) {
						String url = data.getURL(shortUrl);
						children.add(
							new SimpleLeafNode(parent, "%s: \"%s\"", label, shortUrl)
								.setURL(url, false)
								.setExternViewable(url, ExternalViewerInfo.Browser)
						);
					}
				}

				private static void addBase64ImageNode(TreeNode parent, Vector<TreeNode> children, String base64Data, String label) {
					if (base64Data!=null && !base64Data.isEmpty())
						children.add(new SimpleLeafNode(parent, label).setTextSource(Base64String.parse(base64Data.replace('_','/').replace('-','+'), null)));
				}
			}

			static class AchievementsNode extends BaseTreeNode<TreeNode,TreeNode> {

				private final GameInfos.Achievements achievements;

				public AchievementsNode(TreeNode parent, GameInfos.Achievements achievements) {
					super(parent, generateTitle(achievements), true, false, TreeIcons.Achievement);
					this.achievements = achievements;
				}
				
				private static String generateTitle(GameInfos.Achievements achievements) {
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
						children.add(new RawJsonDataNode(this, "raw data", achievements.rawData, achievements.getClass()));
					if (achievements.hasParsedData) {
						if (achievements.achievedHidden!=null) children.add(GroupingNode.create(this, "Achieved (hidden)", achievements.achievedHidden, null, AchievementNode::new));
						if (achievements.highlight     !=null) children.add(GroupingNode.create(this, "Highlight"        , achievements.highlight     , null, AchievementNode::new));
						if (achievements.unachieved    !=null) children.add(GroupingNode.create(this, "Unachieved"       , achievements.unachieved    , null, AchievementNode::new));
					}
					return children;
				}

				static class AchievementNode extends BaseTreeNode<TreeNode,TreeNode> implements ImageNTextContentSource, TreeContentSource {
					
					private GameInfos.Achievements.Achievement achievement;
					
					AchievementNode(TreeNode parent, GameInfos.Achievements.Achievement achievement) {
						super(parent, generateTitle(achievement), true, false, TreeIcons.Achievement);
						this.achievement = achievement;
					}
					
					private static String generateTitle(GameInfos.Achievements.Achievement achievement) {
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
						return JSONHelper.createRawDataTreeRoot(achievement.getClass(), achievement.rawData, false);
					}

					@Override protected Vector<? extends TreeNode> createChildren() {
						Vector<TreeNode> children = new Vector<>();
						if (achievement.rawData!=null)
							children.add(new RawJsonDataNode   (this, "raw data"   , achievement.rawData    , achievement.getClass()));
						if (achievement.hasParsedData) {
							children.add(new PrimitiveValueNode(this, "Description", achievement.description));
							children.add(new PrimitiveValueNode(this, "ID"         , achievement.id         ));
							children.add(new SimpleLeafNode    (this, "Unlocked: %d (%s)", achievement.unlocked, achievement.unlocked==0 ? "not yet" : getTimeStr(achievement.unlocked*1000)));
							if (achievement.achievedRatio!=null)
								children.add(new PrimitiveValueNode(this, "Achieved Ratio", achievement.achievedRatio));
							children.add(createImageUrlNode      (this, "Image"      , achievement.image      ));
						}
						return children;
					}
				}
				
			}
			static class AchievementMapEntryNode extends BaseTreeNode<TreeNode,TreeNode> implements ImageNTextContentSource, TreeContentSource {
				
				private final GameInfos.AchievementMap.Entry data;
				
				AchievementMapEntryNode(TreeNode parent, GameInfos.AchievementMap.Entry data) {
					super(parent, generateTitle(data), false, true, TreeIcons.Achievement);
					this.data = data;
				}
				
				private static String generateTitle(GameInfos.AchievementMap.Entry data) {
					if (data==null)
						return "Entry == <null>";
					if (data.hasParsedData) {
						String str = data.name;
						if (data.isAchieved) str = "[+] "+str;
						return str;
					}
					if (data.rawData!=null)
						return "Entry [ Raw Data ]";
					return "Entry ???";
				}
				
				@Override ContentType getContentType() {
					if (data.hasParsedData) return ContentType.ImageNText;
					if (data.rawData!=null) return ContentType.DataTree;
					return null;
				}

				@Override
				public BufferedImage getContentAsImage() {
					return readImageFromURL(data.image,"entry image");
				}

				@Override
				public String getContentAsText() {
					if (data.hasParsedData) {
						ValueListOutput out = new ValueListOutput();
						out.add(0, "ID"         , "\"%s\"", data.id);
						out.add(0, "Name"       , "\"%s\"", data.name);
						out.add(0, "Image"      , "\"%s\"", data.image);
						out.add(0, "Description", "\"%s\"", data.description);
						out.add(0, "Is Achieved", "%s"    , data.isAchieved);
						out.add(0, "Ratio"      , "%f"    , data.achievedRatio);
						return out.generateOutput();
					}
					return null;
				}

				@Override
				public TreeRoot getContentAsTree() {
					if (data.rawData==null) return null;
					return JSONHelper.createRawDataTreeRoot(data.getClass(), data.rawData, false);
				}

				@Override protected Vector<? extends TreeNode> createChildren() { return null; }
			}
			

			static class BadgeNode extends BaseTreeNode<TreeNode,TreeNode> implements ImageContentSource, ExternViewableNode, URLBasedNode {

				private final GameInfos.Badge badge;

				public BadgeNode(TreeNode parent, GameInfos.Badge badge) {
					super(parent, generateTitle(badge), true, false, TreeIcons.Badge);
					this.badge = badge;
				}
				
				private static String generateTitle(GameInfos.Badge badge) {
					if (badge==null) return "(NULL) Badge";
					String str = "Badge";
					if (badge.hasParsedData) {
						str += String.format("\"%s\"", badge.name);
						str += String.format(" (Level: %d/%d, XP: %d)", badge.currentLevel, badge.maxLevel, badge.currentXP);
					}
					return str;
				}

				@Override public LabeledUrl getURL() { return LabeledUrl.create("Badge Icon", badge.iconURL); }
				@Override public ExternViewableItem getExternViewableItem() { return ExternalViewerInfo.Browser.createItem(getURL()); }

				@Override ContentType getContentType() { return ContentType.Image; }
				@Override public BufferedImage getContentAsImage() { return readImageFromURL(badge.iconURL,"badge icon"); }

				@Override protected Vector<? extends TreeNode> createChildren() {
					Vector<TreeNode> children = new Vector<>();
					
					if (badge.rawData!=null)
						children.add(new RawJsonDataNode(this, "raw data", badge.rawData, badge.getClass()));
					
					if (badge.hasParsedData) {
						children.add(new SimpleLeafNode(this, "Next Level: \"%s\", XP: %d", badge.nextLevelName, badge.nextLevelXP));
						
						//children.add(new PrimitiveValueNode(this, "name"           , badge.name         ));
						children.add(new PrimitiveValueNode(this, "has badge data" , badge.hasBadgeData ));
						//children.add(new PrimitiveValueNode(this, "max level"      , badge.maxLevel     ));
						//children.add(new PrimitiveValueNode(this, "current level"  , badge.currentLevel ));
						//children.add(new PrimitiveValueNode(this, "current XP"     , badge.currentXP    ));
						//children.add(new PrimitiveValueNode(this, "next level name", badge.nextLevelName));
						//children.add(new PrimitiveValueNode(this, "next level XP"  , badge.nextLevelXP  ));
						children.add(createImageUrlNode      (this, "icon URL"       , badge.iconURL      ));
						children.add(GroupingNode
								.create(this, "Trading Cards", badge.tradingCards, null, TradingCardNode::new)
								.setExternViewable(new FilePromise("HTML-Overview", badge::createTempTradingCardsOverviewHTML), ExternalViewerInfo.Browser)
						);
					}
					return children;
				}
			}

			static class TradingCardNode extends BaseTreeNode<TreeNode,TreeNode> implements ImageContentSource, ExternViewableNode, URLBasedNode {
				
				private final GameInfos.Badge.TradingCard tradingCard;
				
				TradingCardNode(TreeNode parent, GameInfos.Badge.TradingCard tradingCard) {
					super(parent, generateTitle(tradingCard), true, false);
					this.tradingCard = tradingCard;
				}
				
				private static String generateTitle(GameInfos.Badge.TradingCard tradingCard) {
					if (tradingCard==null)
						return "(NULL) Trading Card";
					if (!tradingCard.hasParsedData)
						return "Trading Card"+(tradingCard.rawData!=null ? " [Raw Data]" : "");
					
					String str = String.format("Trading Card \"%s\"", tradingCard.name);
					if (tradingCard.owned!=0) str += String.format(" (%d)", tradingCard.owned);
					return str;
				}

				@Override public LabeledUrl getURL() { return LabeledUrl.create("Trading Card Image", tradingCard.imageURL); }
				@Override public ExternViewableItem getExternViewableItem() { return ExternalViewerInfo.Browser.createItem(getURL()); }

				@Override ContentType getContentType() { return ContentType.Image; }
				@Override public BufferedImage getContentAsImage() { return readImageFromURL(tradingCard.imageURL,"trading card image"); }
			
				@Override protected Vector<? extends TreeNode> createChildren() {
					Vector<TreeNode> children = new Vector<>();
					if (tradingCard.rawData!=null)
						children.add(new RawJsonDataNode   (this, "raw data"   , tradingCard.rawData   , tradingCard.getClass()));
					if (tradingCard.hasParsedData) {
						children.add(new PrimitiveValueNode(this, "name"       , tradingCard.name      ));
						children.add(new PrimitiveValueNode(this, "title"      , tradingCard.title     ));
						children.add(new PrimitiveValueNode(this, "owned"      , tradingCard.owned     ));
						children.add(createImageUrlNode      (this, "artwork URL", tradingCard.artworkURL));
						children.add(createImageUrlNode      (this, "image URL"  , tradingCard.imageURL  ));
						children.add(new PrimitiveValueNode(this, "market hash", tradingCard.marketHash));
					}
					return children;
				}
			}

			static class BlockNode extends BaseTreeNode<TreeNode,TreeNode> implements TreeContentSource {
				
				private final GameInfos.Block block;
			
				BlockNode(TreeNode parent, GameInfos.Block block) {
					super(parent, generateTitle(block), false, true);
					this.block = block;
				}
			
				private static String generateTitle(GameInfos.Block block) {
					if (block==null)
						return "Block ???";
					
					if (block.hasParsedData)
						return String.format("[%d] %s V%d", block.blockIndex, block.label, block.version);
					
					String str = String.format("Block %d V%d", block.blockIndex, block.version);
					if (block.rawData!=null) str += " (RawData)";
					return str;
				}
			
				@Override Color getTextColor() {
					return NodeColorizer.getTextColor_WarnNew(block.dataValue);
				}

				@Override ContentType getContentType() {
					if (block.hasParsedData && block.dataValue!=null)
						return ContentType.DataTree;
					if (block.rawData!=null)
						return ContentType.DataTree;
					return null;
				}

				@Override
				public TreeRoot getContentAsTree() {
					if (block.hasParsedData && block.dataValue!=null)
						return JSONHelper.createDataTreeRoot(block.getClass(), "DataValue", block.dataValue, false);
					if (block.rawData!=null)
						return JSONHelper.createRawDataTreeRoot(block.getClass(), block.rawData, false);
					return null;
				}

				@Override protected Vector<? extends TreeNode> createChildren() { return null; }
			}
		}

		private static class ScreenShotNode extends BaseTreeNode<TreeNode,TreeNode> implements FileBasedNode, ExternViewableNode, ImageContentSource {
			
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
		
			@Override public ExternViewableItem getExternViewableItem() { return ExternalViewerInfo.ImageViewer.createItem(getFile()); }
			@Override public LabeledFile getFile() { return new LabeledFile(screenShot.image); }
			@Override protected Vector<? extends TreeNode> createChildren() { return null; }
		}
		
	}
	
	static class FileSystem {
		
		@SuppressWarnings("unused")
		private static Icon getIconForFile(String filename) {
			return new JFileChooser().getIcon(new File(filename));
		}
		private static String getTreeIDStr(String nodeName, File file) {
			return String.format("%s(%s)", nodeName, file==null ? "<null>" : file.getAbsolutePath());
		}
		
		static class Root extends BaseTreeNode<TreeNode,TreeNode> {
		
			Root() { super(null, "FolderStructure.Root", true, false); }
		
			@Override
			protected Vector<TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				
				Vector<File> steamAppsFolder = new Vector<>(); 
				SteamInspector.KnownFolders.forEachSteamAppsFolder((i,folder)->{
					if (folder!=null && folder.isDirectory())
						steamAppsFolder.add(folder);
				});
				if (!steamAppsFolder.isEmpty()) {
					children.add(new AppManifestsRoot(this,steamAppsFolder));
					children.add(new FolderRoot(this,"SteamApps Folder",steamAppsFolder,File::getAbsolutePath));
				}
				
				File folder;
				
				folder = SteamInspector.KnownFolders.getSteamClientFolder();
				if (folder!=null && folder.isDirectory()) children.add(new FolderRoot(this,"SteamClient Folder",folder));
				
				folder = SteamInspector.KnownFolders.getSteamClientSubFolder(SteamInspector.KnownFolders.SteamClientSubFolders.USERDATA);
				if (folder!=null && folder.isDirectory()) children.add(new FolderRoot(this,"<UserData>",folder));
				
				folder = SteamInspector.KnownFolders.getSteamClientSubFolder(SteamInspector.KnownFolders.SteamClientSubFolders.APPCACHE);
				if (folder!=null && folder.isDirectory()) children.add(new FolderRoot(this,"<AppCache>",folder));
				
				folder = SteamInspector.KnownFolders.getSteamClientSubFolder(SteamInspector.KnownFolders.SteamClientSubFolders.APPCACHE_LIBRARYCACHE);
				if (folder!=null && folder.isDirectory()) {
					children.add(new FolderRoot(this,"<LibraryCache>",folder));
					children.add(new GameImagesRoot(this,folder));
				}
				
				folder = SteamInspector.KnownFolders.getSteamClientSubFolder(SteamInspector.KnownFolders.SteamClientSubFolders.STEAM_GAMES);
				if (folder!=null && folder.isDirectory()) children.add(new FolderRoot(this,"Game Icons (as Folder)",folder));
				
				folder = SteamInspector.FOLDER_TEST_FILES;
				try { folder = folder.getCanonicalFile(); } catch (IOException e) {}
				if (folder.isDirectory()) children.add(new FolderRoot(this,"Test Files",folder));
				
				return children;
			}
		
		}

		private static class GameImagesRoot extends BaseTreeNode<TreeNode,TreeNode> {
		
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
							children.add(new SimpleLeafNode(this, "no \"%s\"", key.toString()));
					}
					return children;
				}
			}
		}

		private static class AppManifestsRoot extends BaseTreeNode<TreeNode,TreeNode> {
		
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

		private static class FolderRoot extends FolderNode {
			private final String rootTitle;
			
			FolderRoot(TreeNode parent, String rootTitle, File folder) {
				this(parent, rootTitle, folder, TreeIcons.RootFolder_Simple);
			}
			FolderRoot(TreeNode parent, String rootTitle, File folder, TreeIcons icon) {
				super(parent, folder, icon);
				this.rootTitle = rootTitle;
			}
			@SuppressWarnings("unused")
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

		private static abstract class FileSystemNode extends BaseTreeNode<TreeNode,FileSystemNode> implements FileBasedNode {
			
			protected final File fileObj;
			
			@SuppressWarnings("unused")
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

		protected static class FolderNode extends FileSystemNode {
			
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
				this(parent, title, folder, icon==null ? null : icon.getIcon());
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
				this(parent, title, files, keepFileOrder, getNodeTitle, icon==null ? null : icon.getIcon());
			}
			FolderNode(TreeNode parent, String title, File[] files, boolean keepFileOrder, Function<File,String> getNodeTitle, Icon icon) {
				this(parent, title, files, keepFileOrder, getNodeTitle, null, icon);
			}
			FolderNode(TreeNode parent, String title, File[] files, boolean keepFileOrder, Function<File,String> getNodeTitle, Function<File,Icon> getNodeIcon, TreeIcons icon) {
				this(parent, title, files, keepFileOrder, getNodeTitle, getNodeIcon, icon==null ? null : icon.getIcon());
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
				Comparator<FileNameNExt> splitted = Comparator.comparing((FileNameNExt sfn)->Data.parseNumber(sfn.name), Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Comparator.naturalOrder());
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
					FileSystemNode node = createNode(parent, file, getNodeTitle, getNodeIcon);
					if (node!=null) children.add(node);
				}
				
				return children;
			}
			static FileSystemNode createNode(TreeNode parent, File file, Function<File, String> getNodeTitle, Function<File, Icon> getNodeIcon) {
				FileSystemNode node;
				if (file.isDirectory()) {
					
					String title = getNodeTitle!=null ? getNodeTitle.apply(file) : null;
					
					Icon icon = getNodeIcon!=null ? getNodeIcon.apply(file) : null;
					if (icon==null) icon = TreeIcons.Folder.getIcon();
					
					node = new FolderNode(parent, title, file, icon);
					
				} else if (file.isFile()) {
					
					if (TextFile.is(file)) {
						node = new TextFile(parent, file);
						
					} else if (VDF_File.is(file)) {
						node = new VDF_File(parent, file, getTreeIDStr("VDF_File", file));
						
					} else if (JSON_File.is(file)) {
						node = new JSON_File(parent, file, getTreeIDStr("JSON_File", file));
						
					} else if (ACF_File.is(file)) {
						node = new ACF_File(parent, file, getTreeIDStr("ACF_File", file));
						
					} else if (AppManifestNode.is(file)) {
						node = new AppManifestNode(parent, file);
						
					} else if (ImageFile.is(file)) {
						node = new ImageFile(parent, file);
						
					} else if (ZipArchiveFile.is(file)) {
						node = new ZipArchiveFile(parent, file);
						
					} else {
						node = new FileNode(parent, file);
					}
				} else
					node = null;
				return node;
			}
		}

		private static class FileNode extends FileSystemNode implements ByteFileSource, ExternViewableNode {
		
			protected byte[] byteContent;
			private final ExternalViewerInfo viewerInfo;
			
			FileNode(TreeNode parent, File file) {
				this(parent, file, TreeIcons.GeneralFile, null);
			}
			protected FileNode(TreeNode parent, File file, TreeIcons icon, ExternalViewerInfo viewerInfo) {
				super(parent, file, file.getName(), false, true, icon);
				this.viewerInfo = viewerInfo;
				this.byteContent = null;
				if (!fileObj.isFile())
					throw new IllegalStateException("Can't create a FileSystem.FileNode from nonexisting file or nonfile");
			}
			
			@Override public boolean isLarge() { return getFileSize()>400000; }
			@Override public long getFileSize() { return fileObj.length(); }

			@Override
			public String toString() {
				return String.format("%s (%s)", fileObj.getName(), getSizeStr(fileObj));
			}

			@Override protected Vector<FileSystemNode> createChildren() {
				throw new UnsupportedOperationException("Call of FileSystem.FileNode.createChildren() is not supported.");
			}

			@Override public ExternViewableItem getExternViewableItem() {
				return viewerInfo==null ? null : viewerInfo.createItem(getFile());
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

		private static class ZipArchiveFile extends FileNode {

			ZipArchiveFile(TreeNode parent, File file) {
				super(parent, file, TreeIcons.RootFolder, ExternalViewerInfo.ZipViewer);
			}
			
			static boolean is(File file) {
				return file.isFile() && fileNameEndsWith(file, ".zip", ".7z", ".rar");
			}
		}

		private static class ImageFile extends FileNode implements ImageContentSource {
			
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
					Data.showException("IOException", e, fileObj);
					return createImageOfMessage("IOException: "+e.getMessage(),150,150,Color.RED);
				}
				return imageContent;
			}
		}

		private static class TextFile extends FileNode implements ByteBasedTextFileSource {
			
			protected final Charset charset;
			protected String textContent;

			TextFile(TreeNode parent, File file) {
				this(parent, file, TreeIcons.TextFile, null);
			}

			TextFile(TreeNode parent, File file, TreeIcons icon, Charset charset) {
				super(parent, file, icon, ExternalViewerInfo.TextEditor);
				this.charset = charset;
				this.textContent = null;
			}

			static boolean is(File file) {
				return file.isFile() && fileNameEndsWith(file, ".txt", ".sii", ".lua");
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
		
		private static class JSON_File extends TextFile implements ParsedByteBasedTextFileSource {
			
			private JSON_Data.Value<NV,V> parseResult;
			private final String treeIDStr;

			JSON_File(TreeNode parent, File file, String treeIDStr) {
				this(parent, file, treeIDStr, TreeIcons.JSONFile);
			}
			JSON_File(TreeNode parent, File file, String treeIDStr, TreeIcons icon) {
				super(parent, file, icon, StandardCharsets.UTF_8);
				this.treeIDStr = treeIDStr;
				parseResult = null;
			}

			static boolean is(File file) {
				return file.isFile() && fileNameEndsWith(file,".json");
			}
			
			@Override ContentType getContentType() {
				return ContentType.ParsedByteBasedText;
			}
			
			@Override
			public TreeRoot getContentAsTree() {
				if (parseResult == null) {
					String text = getContentAsText();
					if (text==null) return null;
					try {
						parseResult = JSONHelper.parseJsonText(text);
					} catch (JSON_Parser.ParseException e) {
						Data.showException(e, fileObj);
						return SimpleLeafNode.createSingleTextLineTree("Parse Error: %s", e.getMessage());
					}
				}
				if (parseResult != null)
					return JSONHelper.createDataTreeRoot(treeIDStr, parseResult, isLarge());
				
				return SimpleLeafNode.createSingleTextLineTree("Parse Error: Parser returns <null>");
			}
		}

		private static class VDF_File extends TextFile implements ParsedByteBasedTextFileSource {
			
			private VDFParser.Result vdfData;
			private final String predefinedTitle;
			private final String treeIDStr;

			VDF_File(TreeNode parent, String predefinedTitle, File file, String treeIDStr) {
				this(parent, predefinedTitle, file, treeIDStr, TreeIcons.VDFFile);
			}
			VDF_File(TreeNode parent, File file, String treeIDStr) {
				this(parent, null, file, null, TreeIcons.VDFFile);
			}
			VDF_File(TreeNode parent, File file, String treeIDStr, TreeIcons icon) {
				this(parent, null, file, treeIDStr, icon);
			}
			VDF_File(TreeNode parent, String predefinedTitle,  File file, String treeIDStr, TreeIcons icon) {
				super(parent, file, icon, StandardCharsets.UTF_8);
				this.predefinedTitle = predefinedTitle;
				this.treeIDStr = treeIDStr;
				this.vdfData = null;
			}

			@Override
			public String toString() {
				if (predefinedTitle!=null) return predefinedTitle;
				return super.toString();
			}
			static boolean is(File file) {
				return file.isFile() && fileNameEndsWith(file,".vdf");
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
					} catch (VDFParseException e) {
						System.err.printf("ParseException: %s%n", e.getMessage());
						//e.printStackTrace();
						return SimpleLeafNode.createSingleTextLineTree("Parse Error: %s", e.getMessage());
					}
				}
				return vdfData==null ? null : vdfData.getTreeRoot(treeIDStr,isLarge());
			}
		}

		private static class ACF_File extends VDF_File {
			
			ACF_File(TreeNode parent, File file, String treeIDStr) {
				this(parent, file, treeIDStr, TreeIcons.VDFFile);
			}
			ACF_File(TreeNode parent, File file, String treeIDStr, TreeIcons icon) {
				super(parent, file, treeIDStr, icon);
			}
		
			static boolean is(File file) {
				return file.isFile() && fileNameEndsWith(file,".acf");
			}
		}

		private static class AppManifestNode extends ACF_File {
			
			private final int id;
		
			AppManifestNode(TreeNode parent, File file) {
				super(parent, file, "AppManifest<VDF>", TreeIcons.AppManifest);
				id = AppManifest.getAppIDFromFile(file);
			}
		
			static boolean is(File file) {
				return file.isFile() && AppManifest.getAppIDFromFile(file) != null;
			}

			@Override public String toString() {
				return String.format("App %d (%s, %s)", id, fileObj==null ? "" : fileObj.getName(), getSizeStr(fileObj));
			}
		}
	}
}

