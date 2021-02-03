package net.schwarzbaer.java.tools.steaminspector;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.tree.TreeNode;

import net.schwarzbaer.gui.IconSource;
import net.schwarzbaer.gui.IconSource.CachedIcons;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Array;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Object;
import net.schwarzbaer.java.lib.jsonparser.JSON_Parser;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.AbstractTreeContextMenu;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.AppSettings.ValueKey;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BaseTreeNode;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BytesContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ExtendedTextContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ExternalViewerInfo;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ImageContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ParsedTextContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TreeContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TreeRoot;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.AppManifest;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.Game;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.GameImages;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.Player;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.Player.GameStateInfo.GameStateInfoBlock;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.Data.ScreenShot;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.FileSystem.FolderNode;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.FileSystem.ImageFile;
import net.schwarzbaer.java.tools.steaminspector.VDFParser.VDFTreeNode;
import net.schwarzbaer.system.ClipboardTools;

class TreeNodes {

	enum TreeIcons { GeneralFile, TextFile, ImageFile, AudioFile, VDFFile, AppManifest, JSONFile, Folder, RootFolder_Simple, RootFolder }
	static CachedIcons<TreeIcons> TreeIconsIS;
	
	enum JsonTreeIcons { Object, Array, String, Number, Boolean }
	static CachedIcons<JsonTreeIcons> JsonTreeIconsIS;
	
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
	
	static boolean fileNameEndsWith(File file, String... suffixes) {
		String name = file.getName().toLowerCase();
		for (String suffix:suffixes)
			if (name.endsWith(suffix))
				return true;
		return false;
	}

	static boolean isImageFile(File file) {
		return file.isFile() && fileNameEndsWith(file,".jpg",".jpeg",".png",".bmp",".ico",".tga");
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
	
	static Integer parseNumber(String name) {
		try {
			int n = Integer.parseInt(name);
			if (name.equals(Integer.toString(n))) return n;
		}
		catch (NumberFormatException e) {}
		return null;
	}
	
	static Long parseLongNumber(String name) {
		try {
			long n = Long.parseLong(name);
			if (name.equals(Long.toString(n))) return n;
		}
		catch (NumberFormatException e) {}
		return null;
	}

	static class HashMatrix<KeyType1,KeyType2,ValueType> {
		
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

	static class LabeledFile {
		final String label;
		final File file;
		LabeledFile(File file) { this(null,file); }
		LabeledFile(String label, File file) {
			if (file==null) throw new IllegalArgumentException();
			this.label = label!=null ? label : file.getName();
			this.file = file;
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

	interface FileBasedNode {
		LabeledFile getFile();
	}
	
	interface ExternViewableNode {
		ExternalViewerInfo getExternalViewerInfo();
	}
	
	interface DataTreeNode {
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

	static class DataTreeNodeContextMenu extends AbstractTreeContextMenu {
		private static final long serialVersionUID = 7620430144231207201L;
		
		private final JMenuItem miName;
		private final JMenuItem miValue;
		private final JMenuItem miPath;
		private final JMenuItem miAccessCall;
		private final JMenuItem miFullInfo;
		private DataTreeNode clickedTreeNode;
		private JTree invoker;
		
		DataTreeNodeContextMenu() {
			clickedTreeNode = null;
			invoker = null;
			add(miName       = SteamInspector.createMenuItem("Copy Name"       , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getName())));
			add(miValue      = SteamInspector.createMenuItem("Copy Value"      , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getValueStr())));
			add(miPath       = SteamInspector.createMenuItem("Copy Path"       , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getPath())));
			add(miAccessCall = SteamInspector.createMenuItem("Copy Access Call", true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getAccessCall())));
			add(miFullInfo   = SteamInspector.createMenuItem("Copy Full Info"  , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getFullInfo())));
			addSeparator();
			add(SteamInspector.createMenuItem("Expand Full Tree", true, e->{
				if (invoker!=null)
					for (int i=0; i<invoker.getRowCount(); i++)
						invoker.expandRow(i);
			}));
		}
		
		@Override
		public void showContextMenu(JTree invoker, int x, int y, Object clickedTreeNode) {
			this.invoker = invoker;
			this.clickedTreeNode = null;
			if (clickedTreeNode instanceof DataTreeNode)
				this.clickedTreeNode = (DataTreeNode) clickedTreeNode;
			
			miName      .setEnabled(this.clickedTreeNode!=null && this.clickedTreeNode.hasName());
			miValue     .setEnabled(this.clickedTreeNode!=null && this.clickedTreeNode.hasValue());
			miPath      .setEnabled(this.clickedTreeNode!=null);
			miAccessCall.setEnabled(this.clickedTreeNode!=null);
			miFullInfo  .setEnabled(this.clickedTreeNode!=null);
			
			show(invoker, x, y);
		}
	}

	static class GroupingNode<ValueType> extends BaseTreeNode<TreeNode,TreeNode> implements FileBasedNode, ExternViewableNode {
		
		private final Collection<ValueType> values;
		private final Comparator<ValueType> sortOrder;
		private final NodeCreator1<ValueType> createChildNode;
		private File file;
		private ExternalViewerInfo externalViewerInfo;
		
		static <I,V> GroupingNode<Map.Entry<I,V>> create(TreeNode parent, String title, HashMap<I,V> values, Comparator<Map.Entry<I,V>> sortOrder, NodeCreator2<I,V> createChildNode) {
			return create(parent, title, values, sortOrder, createChildNode, null);
		}
		static <I,V> GroupingNode<Map.Entry<I,V>> create(TreeNode parent, String title, HashMap<I,V> values, Comparator<Map.Entry<I,V>> sortOrder, NodeCreator2<I,V> createChildNode, Icon icon) {
			return new GroupingNode<Map.Entry<I,V>>(parent, title, values.entrySet(), sortOrder, (p,e)->createChildNode.create(p,e.getKey(),e.getValue()), icon);
		}
		static <V> GroupingNode<V> create(TreeNode parent, String title, Collection<V> values, Comparator<V> sortOrder, NodeCreator1<V> createChildNode) {
			return new GroupingNode<>(parent, title, values, sortOrder, createChildNode, null);
		}
		static <V> GroupingNode<V> create(TreeNode parent, String title, Collection<V> values, Comparator<V> sortOrder, NodeCreator1<V> createChildNode, Icon icon) {
			return new GroupingNode<>(parent, title, values, sortOrder, createChildNode, icon);
		}
		GroupingNode(TreeNode parent, String title, Collection<ValueType> values, Comparator<ValueType> sortOrder, NodeCreator1<ValueType> createChildNode, Icon icon) {
			super(parent, title, true, false, icon);
			this.values = values;
			this.sortOrder = sortOrder;
			this.createChildNode = createChildNode;
			file = null;
			externalViewerInfo = null;
		}
	
		public void setFileSource(File file, ExternalViewerInfo externalViewerInfo) {
			this.file = file;
			this.externalViewerInfo = externalViewerInfo;
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
		
		@Override public LabeledFile getFile() {
			return file==null ? null : new LabeledFile(file);
		}
		@Override public ExternalViewerInfo getExternalViewerInfo() {
			return externalViewerInfo;
		}
	
		interface NodeCreator1<ValueType> {
			TreeNode create(TreeNode parent, ValueType value);
		}
		
		interface NodeCreator2<IDType, ValueType> {
			TreeNode create(TreeNode parent, IDType id, ValueType value);
		}
	}

	static class RawJsonDataNode<NV extends JSON_Data.NamedValueExtra, V extends JSON_Data.ValueExtra> extends BaseTreeNode<TreeNode,TreeNode> implements TreeContentSource, FileBasedNode, ExternViewableNode {
	
		private final File file;
		private final JSON_Parser.Result<NV,V> rawData;
		private final JSON_Data.Value <NV,V> rawValue;
	
		RawJsonDataNode(TreeNode parent, String title, JSON_Parser.Result<NV,V> rawData) {
			this(parent, title, rawData, null, null);
		}
		RawJsonDataNode(TreeNode parent, String title, JSON_Parser.Result<NV,V> rawData, Icon icon) {
			this(parent, title, rawData, null, icon);
		}
		RawJsonDataNode(TreeNode parent, String title, JSON_Parser.Result<NV,V> rawData, File file, Icon icon) {
			super(parent, title, false, true, icon);
			this.file = file;
			this.rawData = rawData;
			this.rawValue = null;
		}
		RawJsonDataNode(TreeNode parent, String title, JSON_Data.Value<NV,V> rawValue) {
			this(parent, title, rawValue, null, null);
		}
		RawJsonDataNode(TreeNode parent, String title, JSON_Data.Value<NV,V> rawValue, Icon icon) {
			this(parent, title, rawValue, null, icon);
		}
		RawJsonDataNode(TreeNode parent, String title, JSON_Data.Value<NV,V> rawValue, File file, Icon icon) {
			super(parent, title, false, true, icon);
			this.file = file;
			this.rawData = null;
			this.rawValue = rawValue;
		}
		@Override protected Vector<? extends TreeNode> createChildren() { return null; }
		
		@Override ContentType getContentType() {
			return ContentType.DataTree;
		}
		@Override public TreeRoot getContentAsTree() {
			if (rawData !=null) return FileSystem.JSON_File.JSON_TreeNode.create(rawData , false);
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

	static class ParseException extends Exception {
		private static final long serialVersionUID = -7150324499542307039L;
		ParseException(String format, Object...args) {
			super(String.format(Locale.ENGLISH, format, args));
		}
	}

	static class Data {
		
		static class ScreenShot {
			final File image;
			final File thumbnail;
			ScreenShot(File image, File thumbnail) {
				this.image = image;
				this.thumbnail = thumbnail;
			}
		}
		
		static class ScreenShots {

			final HashMap<Integer,Vector<ScreenShot>> list;

			ScreenShots(File folder) {
				
				list = new HashMap<>();
				File subFolder = new File(folder,"remote");
				if (subFolder.isDirectory()) {
					File[] folders = subFolder.listFiles(file->file.isDirectory() && parseNumber(file.getName())!=null);
					for (File gameFolder:folders) {
						Integer gameID = parseNumber(gameFolder.getName());
						Vector<ScreenShot> screenShots = new Vector<>();
						list.put(gameID, screenShots);
						
						File imagesFolder = new File(gameFolder,"screenshots");
						if (imagesFolder.isDirectory()) {
							File thumbnailsFolder = new File(imagesFolder,"thumbnails");
							File[] imageFiles = imagesFolder.listFiles(TreeNodes::isImageFile);
							for (File image:imageFiles) {
								File thumbnail = null;
								if (thumbnailsFolder.isDirectory())
									thumbnail = new File(thumbnailsFolder,image.getName());
								screenShots.add(new ScreenShot(image,thumbnail));
							}
						}
					}
				}
			}
			
		}
		static class Player {
			
			private static class NV extends JSON_Data.NamedValueExtra.Dummy{}
			private static class V  extends JSON_Data.ValueExtra.Dummy{}

			final long playerID;
			final HashMap<Integer, File> steamCloudFolders;
			final ScreenShots screenShots;
			final File configFolder;
			final VDFTreeNode localconfig;
			final HashMap<Integer,GameStateInfo> gameStateInfos;
			final AchievementProgress achievementProgress;

			Player(long playerID, File playerFolder) {
				this.playerID = playerID;
				File[] folders = playerFolder.listFiles(file->file.isDirectory() && parseNumber(file.getName())!=null);
				
				steamCloudFolders = new HashMap<Integer,File>();
				for (File folder:folders) {
					String name = folder.getName();
					if (!name.equals("7") && !name.equals("760")) {
						Integer gameID = parseNumber(name);
						steamCloudFolders.put(gameID, folder);
					}
				}
				File folder;
				
				// Folders
				folder = new File(playerFolder,"760");
				if (folder.isDirectory()) {
					screenShots = new ScreenShots(folder);
				} else
					screenShots = null;
				
				folder = new File(playerFolder,"config");
				if (folder.isDirectory()) {
					configFolder = folder;
				} else
					configFolder = null;
				
				// localconfig
				VDFParser.Data localconfigData = null;
				if (configFolder!=null) {
					File localconfigFile = new File(configFolder,"localconfig.vdf");
					if (localconfigFile.isFile()) {
						try { localconfigData = VDFParser.parse(localconfigFile,StandardCharsets.UTF_8); }
						catch (VDFParser.ParseException e) {}
					}
				}
				localconfig = localconfigData!=null ? localconfigData.createVDFTreeNode() : null;
				
				if (localconfig!=null) {
					// TODO: parse friends
					// Root[0].UserLocalConfigStore[2].friends[1].###########
					// localconfig.getSubNode("UserLocalConfigStore","friends").foreEachArray(...)
				}
				
				gameStateInfos = new HashMap<>();
				AchievementProgress achievementProgress_ = null;
				if (configFolder!=null) {
					File gameStateFolder = new File(configFolder,"librarycache");
					if (gameStateFolder.isDirectory()) {
						File[] files = gameStateFolder.listFiles(file->file.isFile());
						for (File file:files) {
							FileNameNExt fileNameNExt = FileNameNExt.create(file.getName());
							if (fileNameNExt.extension!=null && fileNameNExt.extension.equalsIgnoreCase("json")) {
								Integer gameID;
								if (fileNameNExt.name.equalsIgnoreCase("achievement_progress")) {
									// \config\librarycache\achievement_progress.json
									JSON_Parser.Result<NV,V> result = new JSON_Parser<NV,V>(file,null).parse();
									if (result!=null) {
										AchievementProgress parsed = AchievementProgress.parse(file,result.object);
										achievementProgress_ = parsed!=null ? parsed : new AchievementProgress(file,result);
									}
									
								} else if ((gameID=parseNumber(fileNameNExt.name))!=null) {
									// \config\librarycache\1465680.json
									JSON_Parser.Result<NV, V> result=null;
									try {
										result = new JSON_Parser<NV,V>(file,null).parse_withParseException();
									} catch (JSON_Parser.ParseException e) {
										//e.printStackTrace();
										System.err.printf("(JSON) ParseException: %s%n   in File \"%s\"%n", e.getMessage(), file.getAbsolutePath());
									}
									if (result!=null) {
										GameStateInfo info = null;
										try {
											info = GameStateInfo.parse(file,result.array);
										} catch (ParseException e) {
											//e.printStackTrace();
											System.err.printf("(GameStateInfo) ParseException: %s%n   in File \"%s\"%n", e.getMessage(), file.getAbsolutePath());
										}
										if (info!=null)
											gameStateInfos.put(gameID, info);
										else
											gameStateInfos.put(gameID, new GameStateInfo(file,result));
									}
								}
							}
						}
					}
				}
				achievementProgress = achievementProgress_;
				
			}

			public String getName() {
				if (localconfig != null) {
					VDFTreeNode nameNode = localconfig.getSubNode("UserLocalConfigStore","friends","PersonaName");
					if (nameNode!=null && nameNode.value!=null && !nameNode.value.isEmpty())
						return nameNode.value;
				}
				return "Player "+playerID;
			}
			
			static class AchievementProgress {

				final File file;
				final JSON_Parser.Result<NV, V> rawData;

				public AchievementProgress(File file, JSON_Parser.Result<NV, V> rawData) {
					this.file = file;
					this.rawData = rawData;
				}

				public static AchievementProgress parse(File file, JSON_Data.JSON_Object<NV,V> object) {
					// TODO: parse AchievementProgress
					return null;
				}
				
			}
			
			static class GameStateInfo {

				final File file;
				final JSON_Parser.Result<NV, V> rawData;
				final Vector<GameStateInfoBlock> blocks;

				public GameStateInfo(File file, JSON_Parser.Result<NV, V> rawData) {
					this.file = file;
					this.rawData = rawData;
					this.blocks = null;
				}

				public GameStateInfo(File file, Vector<GameStateInfoBlock> blocks) {
					this.file = file;
					this.rawData = null;
					this.blocks = blocks;
					for (GameStateInfoBlock block:this.blocks) {
						switch (block.label) {
						case "achievements":
							// TODO: parse GameStateInfo.Block["achievements"] 
							break;
							
						case "badge":
							// TODO: parse GameStateInfo.Block["badge"] 
							break;
							
						case "descriptions":
							// TODO: parse GameStateInfo.Block["descriptions"] 
							break;
						}
					}
				}

				public static GameStateInfo parse(File file, JSON_Array<NV,V> array) throws ParseException {
					if (array==null) throw new ParseException("GameStateInfo isn't a JSON_Array");
					Vector<GameStateInfoBlock> blocks = new Vector<>();
					for (int i=0; i<array.size(); i++) {
						JSON_Data.Value<NV,V> value = array.get(i);
						GameStateInfoBlock block = GameStateInfoBlock.parse(i,value);
						if (block!=null) blocks.add(block);
						else blocks.add(new GameStateInfoBlock(i,value));
					}
					return new GameStateInfo(file,blocks);
				}
				
				static <ResultType, JsonValueType extends JSON_Data.GenericValue<NV,V,ResultType>> ResultType getValue(
						JSON_Data.Value<NV,V> value,
						Function<JSON_Data.Value<NV,V>,JsonValueType> cast,
						String debugOutputPrefixStr, String jsonValueTypeLabel
				) throws ParseException {
					if (value==null) throw new ParseException("%s==NULL", debugOutputPrefixStr);
					JsonValueType jsonValue = cast.apply(value);
					if (jsonValue      ==null) throw new ParseException("%s isn't a %s", debugOutputPrefixStr, jsonValueTypeLabel);
					if (jsonValue.value==null) throw new ParseException("%s.value==NULL", debugOutputPrefixStr);
					return jsonValue.value;
				}

				static class GameStateInfoBlock {

					final int blockIndex;
					final String label;
					final long version;
					final JSON_Data.Value<NV, V> dataValue;
					final JSON_Data.Value<NV, V> rawData;

					public GameStateInfoBlock(int blockIndex, String label, long version, JSON_Data.Value<NV, V> dataValue) {
						this.blockIndex = blockIndex;
						this.label = label;
						this.version = version;
						this.dataValue = dataValue;
						this.rawData = null;
					}

					public GameStateInfoBlock(int blockIndex, JSON_Data.Value<NV, V> rawData) {
						this.blockIndex = blockIndex;
						this.label = null;
						this.version = -1;
						this.dataValue = null;
						this.rawData = rawData;
					}

					public static GameStateInfoBlock parse(int blockIndex, JSON_Data.Value<NV, V> value) throws ParseException {
						String blockStr = "GameStateInfo.Block["+blockIndex+"]";
						JSON_Data.ArrayValue<NV, V> arrayValue = value.castToArrayValue();
						if (arrayValue==null) throw new ParseException("%s isn't a JSON_Array", blockStr);
						JSON_Array<NV, V> array = arrayValue.value;
						if (array==null    ) throw new ParseException("%s.value==NULL", blockStr);
						if (array.size()!=2) throw new ParseException("%s.value.length(==%d) != 2", blockStr, array.size());
						JSON_Data.Value<NV, V>     labelValue = array.get(0);
						JSON_Data.Value<NV, V> blockdataValue = array.get(1);
						String                label = getValue(    labelValue,JSON_Data.Value::castToStringValue,blockStr+".value[0:label]"    ,"StringValue");
						JSON_Object<NV,V> blockdata = getValue(blockdataValue,JSON_Data.Value::castToObjectValue,blockStr+".value[1:blockdata]","ObjectValue");
						if (blockdata.size()>2)  throw new ParseException("%s.value[1:blockdata].object.length(==%d) > 2: Too much values", blockStr, blockdata.size());
						if (blockdata.isEmpty()) throw new ParseException("%s.value[1:blockdata].object is empty: Too few values", blockStr);
						JSON_Data.Value<NV, V> versionValue = blockdata.getValue("version");
						JSON_Data.Value<NV, V>    dataValue = blockdata.getValue("data");
						Long version = getValue(versionValue,JSON_Data.Value::castToIntegerValue,blockStr+".value[1:blockdata].object.version","IntegerValue");
						if (dataValue==null && blockdata.size()>1) throw new ParseException("%s.value[1:blockdata].object.data==NULL, but there are other values", blockStr);
						return new GameStateInfoBlock(blockIndex,label,version,dataValue);
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
			private final AppManifest appManifest;
			private final HashMap<String, File> imageFiles;
			private final HashMap<Long, File> steamCloudFolders;
			private final HashMap<Long, Vector<ScreenShot>> screenShots;
			private final String title;
			
			Game(int appID, AppManifest appManifest, HashMap<String, File> imageFiles, HashMap<Long, File> steamCloudFolders, HashMap<Long, Vector<ScreenShot>> screenShots) {
				this.appID = appID;
				this.appManifest = appManifest;
				this.imageFiles = imageFiles;
				this.steamCloudFolders = steamCloudFolders;
				this.screenShots = screenShots;
				
				title = appManifest==null ? null : appManifest.getGameTitle();
			}
			
			boolean hasATitle() {
				return title!=null;
			}

			String getTitle() {
				if (title!=null) return title+" ["+appID+"]";
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
				if (this.appManifest!=null && other.appManifest==null) return -1;
				if (this.appManifest==null && other.appManifest!=null) return +1;
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
	}
	static class PlayersNGames {
		
		private static final HashMap<Integer,Game> games = new HashMap<>();
		private static final HashMap<Long,Player> players = new HashMap<>();
		private static Comparator<Integer> gameIdOrder = Comparator.<Integer,Integer>comparing(id->hasGameATitle(id)?0:1).thenComparing(Comparator.naturalOrder());
	
		static void loadData() {
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
			
			players.clear();;
			folder = KnownFolders.getSteamClientSubFolder(KnownFolders.SteamClientSubFolders.USERDATA);
			File[] files = folder.listFiles(file->file.isDirectory() && parseLongNumber(file.getName())!=null);
			for (File playerFolder:files) {
				Long playerID = parseLongNumber(playerFolder.getName());
				if (playerID==null) throw new IllegalStateException();
				players.put(playerID, new Player(playerID,playerFolder));
			}
			
			// collect all AppIDs
			HashSet<Integer> idSet = new HashSet<>();
			idSet.addAll(appManifests.keySet());
			if (gameImages!=null)
				idSet.addAll(gameImages.getGameIDs());
			players.forEach((playerID,player)->{
				idSet.addAll(player.steamCloudFolders.keySet());
				if (player.screenShots!=null)
					idSet.addAll(player.screenShots.list.keySet());
			});
			
			
			games.clear();
			for (Integer appID:idSet) {
				HashMap<String, File> imageFiles = gameImages==null ? null : gameImages.getImageFileMap(appID);
				
				AppManifest appManifest = appManifests.get(appID);
				
				HashMap<Long,File> steamCloudFolders = new HashMap<>();
				HashMap<Long,Vector<ScreenShot>> screenShots = new HashMap<>();
				players.forEach((playerID,player)->{
					
					File steamCloudFolder = player.steamCloudFolders.get(appID);
					if (steamCloudFolder!=null)
						steamCloudFolders.put(playerID, steamCloudFolder);
					
					if (player.screenShots!=null) {
						Vector<ScreenShot> screenShots_ = player.screenShots.list.get(appID);
						if (screenShots_!=null && !screenShots_.isEmpty())
							screenShots.put(playerID, screenShots_);
					}
				});
				
				games.put(appID, new Game(appID, appManifest, imageFiles, steamCloudFolders, screenShots));
			}
			
		}

		private static String getPlayerName(Long playerID) {
			if (playerID==null) return "Player ???";
			Player game = players.get(playerID);
			if (game==null) return "Player "+playerID;
			return game.getName();
		}

		private static Icon getGameIcon(Integer gameID, TreeIcons defaultIcon) {
			if (gameID==null) return TreeIconsIS.getCachedIcon(defaultIcon);
			Data.Game game = games.get(gameID);
			if (game==null) return TreeIconsIS.getCachedIcon(defaultIcon);
			return game.getIcon();
		}

		private static String getGameTitle(Integer gameID) {
			if (gameID==null) return "Game ???";
			Data.Game game = games.get(gameID);
			if (game==null) return "Game "+gameID;
			return game.getTitle();
		}

		private static boolean hasGameATitle(Integer gameID) {
			if (gameID==null) return false;
			Data.Game game = games.get(gameID);
			if (game==null) return false;
			return game.hasATitle();
		}

		static class Root extends BaseTreeNode<TreeNode,TreeNode> {
			Root() {
				super(null, "PlayersNGames.Root", true, false);
			}
			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				children.add(new GamesRoot(this));
				children.add(new PlayersRoot(this));
				return children;
			}
		}
		
		static class PlayersRoot extends BaseTreeNode<Root,PlayerNode> {
			PlayersRoot(Root parent) {
				super(parent, "Players", true, false);
			}
	
			@Override
			protected Vector<? extends PlayerNode> createChildren() {
				Vector<PlayerNode> children = new Vector<>();
				
				//players.forEach((playerID,player)->children.add(new PlayerNode(this, playerID, player)));
				Comparator<Map.Entry<Long, Player>> entryOrder = Comparator.comparing(Map.Entry<Long, Player>::getKey);
				Stream<Map.Entry<Long, Player>> sortedEntries = players.entrySet().stream().sorted(entryOrder);
				sortedEntries.forEachOrdered(entry->children.add(new PlayerNode(this, entry.getKey(), entry.getValue())));
				
				return children;
			}
		}
		
		static class PlayerNode extends BaseTreeNode<PlayersRoot,TreeNode> {

			//private long playerID;
			private Player player;

			public PlayerNode(PlayersRoot playersRoot, long playerID, Player player) {
				super(playersRoot, player.getName()+"  ["+playerID+"]", true, false);
				//this.playerID = playerID;
				this.player = player;
			}

			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				
				if (player.steamCloudFolders!=null && !player.steamCloudFolders.isEmpty()) {
					HashMap<File,String> folderLabels = new HashMap<>();
					player.steamCloudFolders.forEach((gameID,folder)->folderLabels.put(folder, getGameTitle(gameID)));
					
					HashMap<File,Icon> folderIcons = new HashMap<>();
					player.steamCloudFolders.forEach((gameID,folder)->folderIcons.put(folder, getGameIcon(gameID, TreeIcons.Folder)));
					
					Comparator<Map.Entry<Integer, File>> entryOrder = Comparator.comparing(Map.Entry<Integer, File>::getKey,gameIdOrder);
					Stream<Map.Entry<Integer, File>> sortedEntries = player.steamCloudFolders.entrySet().stream().sorted(entryOrder);
					File[] gameDataFolders = sortedEntries.map(entry->entry.getValue()).toArray(File[]::new);
					
					children.add(new FileSystem.FolderNode(this, "SteamCloud Shared Data", gameDataFolders, true, folderLabels::get, folderIcons::get, TreeIcons.Folder));
				}
				if (player.screenShots!=null && !player.screenShots.list.isEmpty()) {
					children.add(new ScreenShotsNode<Integer>(this,player.screenShots.list,id->getGameTitle(id),id->getGameIcon(id,TreeIcons.ImageFile),gameIdOrder));
				}
				if (player.configFolder!=null) {
					children.add(new FileSystem.FolderNode(this, "Config Folder", player.configFolder));
				}
				if (player.achievementProgress!=null) {
					if (player.achievementProgress.rawData!=null) {
						children.add(new RawJsonDataNode<>(this, "AchievementProgress [RawData]",player.achievementProgress.rawData, player.achievementProgress.file, null));
					} else {
						// TODO: TreeNode for AchievementProgress
					}
				}
				if (!player.gameStateInfos.isEmpty()) {
					Comparator<Map.Entry<Integer, Data.Player.GameStateInfo>> sortOrder =
							Comparator.comparing(Map.Entry<Integer,Data.Player.GameStateInfo>::getKey,gameIdOrder);
					children.add(GroupingNode.create(this, "GameStateInfos", player.gameStateInfos, sortOrder, this::createGameStateInfoNode));
				}
				
				return children;
			}
			
			TreeNode createGameStateInfoNode(TreeNode parent, Integer gameID, Data.Player.GameStateInfo gameStateInfo) {
				Icon gameIcon = getGameIcon(gameID, TreeIcons.Folder);
				String gameTitle = getGameTitle(gameID);
				if (gameStateInfo.rawData!=null)
					return new RawJsonDataNode<>(this, gameTitle, gameStateInfo.rawData, gameStateInfo.file, gameIcon);
				if (gameStateInfo.blocks!=null) {
					GroupingNode<GameStateInfoBlock> groupingNode = GroupingNode.create(this, gameTitle, gameStateInfo.blocks, null, GameStateInfoBlockNode::new, gameIcon);
					groupingNode.setFileSource(gameStateInfo.file,ExternalViewerInfo.TextEditor);
					return groupingNode;
				}
				return new SimpleTextNode(this,gameIcon,"[No Data] "+gameTitle);
			}
		}
		
		static class GameStateInfoBlockNode extends BaseTreeNode<TreeNode,TreeNode> {
			
			private Data.Player.GameStateInfo.GameStateInfoBlock block;

			GameStateInfoBlockNode(TreeNode parent, Data.Player.GameStateInfo.GameStateInfoBlock block) {
				super(parent, getTitle(block), true, false);
				this.block = block;
			}

			private static String getTitle(Data.Player.GameStateInfo.GameStateInfoBlock block) {
				if (block==null) return "Block ???";
				if (block.rawData!=null) return "Block "+block.blockIndex+" (RawData)";
				return "["+block.blockIndex+"] "+block.label;
			}

			@Override protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				if (block.rawData!=null)
					children.add(new RawJsonDataNode<>(this, "raw data", block.rawData));
				else {
					children.add(new SimpleTextNode(this, "version: %d", block.version));
					if (block.dataValue!=null)
						children.add(new RawJsonDataNode<>(this, "data", block.dataValue));
					// TODO: [GameStateInfoBlock] children for other block values
				}
				return children;
			}
		}
		
		static class GamesRoot extends BaseTreeNode<Root,GameNode> {
			GamesRoot(Root parent) {
				super(parent, "Games", true, false);
			}
	
			@Override
			protected Vector<? extends GameNode> createChildren() {
				Vector<GameNode> children = new Vector<>();
				Vector<Game> gamesVec = new Vector<>(games.values());
				gamesVec.sort(null);
				for (Game game:gamesVec) children.add(new GameNode(this, game));
				return children;
			}
		}
		
		static class GameNode extends BaseTreeNode<GamesRoot,TreeNode> {
	
			private final Game game;

			protected GameNode(GamesRoot parent, Game game) {
				super(parent, game.getTitle(), true, false, game.getIcon());
				this.game = game;
			}
	
			@Override
			protected Vector<? extends TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				
				if (game.appManifest!=null)
					children.add(new FileSystem.AppManifestNode(this, game.appManifest.file));
				
				if (game.imageFiles!=null && !game.imageFiles.isEmpty())
					children.add(new FileSystem.FolderNode(this, "Images", game.imageFiles.values(), TreeIcons.ImageFile));
				
				if (game.steamCloudFolders!=null && !game.steamCloudFolders.isEmpty()) {
					HashMap<File,String> folderLabels = new HashMap<>();
					game.steamCloudFolders.forEach((playerID,folder)->folderLabels.put(folder, String.format("by %s", getPlayerName(playerID))));
					
					Stream<Entry<Long, File>> sorted = game.steamCloudFolders.entrySet().stream().sorted(Comparator.comparing(Map.Entry<Long,File>::getKey));
					File[] files = sorted.map(Map.Entry<Long,File>::getValue).toArray(File[]::new);
					//Collection<File> files = game.gameDataFolders.values();
					
					children.add(new FileSystem.FolderNode(this, "SteamCloud Shared Data", files, true, folderLabels::get, TreeIcons.Folder));
				}
				if (game.screenShots!=null && !game.screenShots.isEmpty()) {
					children.add(new ScreenShotsNode<>(this,game.screenShots,id->String.format("by %s", getPlayerName(id)),Comparator.<Long>naturalOrder()));
				}
					
				return children;
			}
		}
		
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
				return String.format("%s [%s]", title, folder.getAbsolutePath());
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
							children.add(new BaseTreeNode.SimpleTextNode(this, "no \"%s\"", key.toString()));
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

		static class FileNode extends FileSystemNode implements BytesContentSource, ExternViewableNode {
		
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

			private BufferedImage createImageOfMessage(String message, int width, int height, Color textColor) {
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
		}

		static class TextFile extends FileNode implements ExtendedTextContentSource {
			
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
				return ContentType.ExtendedText;
			}
		
			@Override public String getContentAsText() {
				if (textContent!=null) return textContent;
				byte[] bytes = getContentAsBytes();
				if (bytes==null) return "Can't read content";
				return textContent = charset!=null ? new String(bytes, charset) : new String(bytes);
			}
		}
		
		static class JSON_File extends TextFile implements ParsedTextContentSource {
			
			private static class NV_ extends JSON_Data.NamedValueExtra.Dummy{}
			private static class V_  extends JSON_Data.ValueExtra.Dummy{}
			
			private static final DataTreeNodeContextMenu contextMenu = new DataTreeNodeContextMenu();
			private JSON_Parser.Result<NV_,V_> parseResult;

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
				return ContentType.ParsedText;
			}
			
			@Override
			public TreeRoot getContentAsTree() {
				if (parseResult==null) {
					String text = getContentAsText();
					if (text==null) return null;
					JSON_Parser<NV_,V_> parser = new JSON_Parser<>(text,null);
					try {
						parseResult = parser.parse_withParseException();
					} catch (JSON_Parser.ParseException e) {
						System.err.printf("ParseException: %s%n", e.getMessage());
						//e.printStackTrace();
						return BaseTreeNode.SimpleTextNode.createSingleTextLineTree("Parse Error: %s", e.getMessage());
					}
					if (parseResult==null)
						// return null;
						return BaseTreeNode.SimpleTextNode.createSingleTextLineTree("Parse Error: Parser returns <null>");
				}
				return JSON_TreeNode.create(parseResult,isLarge());
			}
			
			static class JSON_TreeNode<ValueType, NV extends JSON_Data.NamedValueExtra, V extends JSON_Data.ValueExtra> extends BaseTreeNode<JSON_TreeNode<?,NV,V>,JSON_TreeNode<?,NV,V>> implements DataTreeNode {

				private final Vector<ValueType> childValues;
				private final Function<ValueType, String> getName;
				private final Function<ValueType, JSON_Data.Value<NV,V>> getValue;
				final String name;
				final JSON_Data.Value<NV,V> value;

				private JSON_TreeNode(JSON_TreeNode<?,NV,V> parent, String title, JsonTreeIcons icon, String name, JSON_Data.Value<NV,V> value, Vector<ValueType> childValues, Function<ValueType,String> getName, Function<ValueType,JSON_Data.Value<NV,V>> getValue) {
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
					} catch (JSON_Data.PathIsNotSolvableException e) {
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
				protected Vector<? extends JSON_TreeNode<?,NV,V>> createChildren() {
					if (childValues==null) return null;
					Vector<JSON_TreeNode<?,NV,V>> childNodes = new Vector<>();
					for (ValueType value:childValues) childNodes.add(create(this,getName.apply(value),getValue.apply(value)));
					return childNodes;
				}
				
				static <NV extends JSON_Data.NamedValueExtra, V extends JSON_Data.ValueExtra> TreeRoot create(JSON_Parser.Result<NV,V> parseResult, boolean isLarge) {
					if (parseResult.object!=null) return new TreeRoot(create(null,null,new JSON_Data.ObjectValue<NV,V>(parseResult.object,null)),true,!isLarge,contextMenu);
					if (parseResult.array !=null) return new TreeRoot(create(null,null,new JSON_Data. ArrayValue<NV,V>(parseResult.array ,null)),true,!isLarge,contextMenu);
					return BaseTreeNode.SimpleTextNode.createSingleTextLineTree("Parse Error: Parser returns neither an JSON array nor an JSON object");
				}
				
				static <NV extends JSON_Data.NamedValueExtra, V extends JSON_Data.ValueExtra> TreeRoot create(JSON_Data.Value<NV,V> value, boolean isLarge) {
					return new TreeRoot(create(null,null,value),true,!isLarge,contextMenu);
				}
				
				private static <NV extends JSON_Data.NamedValueExtra, V extends JSON_Data.ValueExtra> JSON_TreeNode<?,NV,V> create(JSON_TreeNode<?,NV,V> parent, String name, JSON_Data.Value<NV,V> value) {
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
				
				private static <NV extends JSON_Data.NamedValueExtra, V extends JSON_Data.ValueExtra> String getTitle(String name, JSON_Data.Value<NV,V> value) {
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

		static class VDF_File extends TextFile implements ParsedTextContentSource {
			
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
				return ContentType.ParsedText;
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
						return BaseTreeNode.SimpleTextNode.createSingleTextLineTree("Parse Error: %s", e.getMessage());
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
