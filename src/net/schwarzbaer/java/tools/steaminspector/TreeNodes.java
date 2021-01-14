package net.schwarzbaer.java.tools.steaminspector;

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
import java.util.Vector;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JTree;
import javax.swing.tree.TreeNode;

import net.schwarzbaer.gui.IconSource;
import net.schwarzbaer.gui.IconSource.CachedIcons;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.Value.Type;
import net.schwarzbaer.java.lib.jsonparser.JSON_Parser;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.AbstractContextMenu;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.AppSettings.ValueKey;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BaseTreeNode;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BytesContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ExtendedTextContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ExternalViewerInfo;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ImageContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ParsedTextContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TreeRoot;
import net.schwarzbaer.java.tools.steaminspector.VDFParser.ParseException;
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
		
		private static String STEAMAPPS_SUBPATH = "steamapps";
		
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
			File[] folders = getSteamLibraryFolders();
			if (folders!=null)
				for (int i=0; i<folders.length; i++)
					action.accept(i, new File(folders[i],STEAMAPPS_SUBPATH));
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
		
		ValueType get(KeyType1 key1, KeyType2 key2) {
			HashMap<KeyType2, ValueType> map = matrix.get(key1);
			if (map==null) return null;
			return map.get(key2);
		}
	}
	
	static class FileSystem {
		
		static class Root extends BaseTreeNode<TreeNode,TreeNode> {
		
			Root() { super(null, "FolderStructure.Root", true, false); }
		
			@Override
			protected Vector<TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				
				KnownFolders.forEachSteamAppsFolder((i,folder)->{
					if (folder!=null && folder.isDirectory())
						children.add(new AppManifestsRoot(this,folder));
				});
				KnownFolders.forEachSteamAppsFolder((i,folder)->{
					if (folder!=null && folder.isDirectory())
						children.add(new FolderRoot(this,"AppManifests (as Folder)",folder));
				});
				
				File folder;
				folder = KnownFolders.getSteamClientSubFolder(KnownFolders.SteamClientSubFolders.USERDATA);
				if (folder!=null && folder.isDirectory()) children.add(new FolderRoot(this,"UserData (as Folder)",folder));
				
				folder = KnownFolders.getSteamClientSubFolder(KnownFolders.SteamClientSubFolders.APPCACHE);
				if (folder!=null && folder.isDirectory()) children.add(new FolderRoot(this,"AppCache (as Folder)",folder));
				
				folder = KnownFolders.getSteamClientSubFolder(KnownFolders.SteamClientSubFolders.APPCACHE_LIBRARYCACHE);
				if (folder!=null && folder.isDirectory()) {
					children.add(new FolderRoot(this,"LibraryCache (as Folder)",folder));
					children.add(new LibraryCacheRoot(this,folder));
				}
				
				folder = KnownFolders.getSteamClientSubFolder(KnownFolders.SteamClientSubFolders.STEAM_GAMES);
				if (folder!=null && folder.isDirectory()) children.add(new FolderRoot(this,"Game Icons (as Folder)",folder));
				
				folder = FOLDER_TEST_FILES;
				if (folder.isDirectory()) children.add(new FolderRoot(this,"Test Files",folder));
				
				return children;
			}
		
		}

		static class LibraryCacheRoot extends BaseTreeNode<TreeNode,TreeNode> {
		
			private File folder;

			LibraryCacheRoot(TreeNode parent, File folder) {
				super(parent, "LibraryCache", true, false, TreeIcons.RootFolder);
				this.folder = folder;
			}

			@Override
			public String toString() {
				return String.format("%s [%s]", title, folder.getAbsolutePath());
			}

			@Override
			protected Vector<? extends TreeNode> createChildren() {
				
				File[] files = FolderNode.getFilesAndFolders(folder);
				
				Vector<File> otherFiles = new Vector<>();
				Vector<File> imageFiles = new Vector<>();
				HashMatrix<Integer, String, File> appImages = new HashMatrix<>();
				
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
				
				Vector<TreeNode> children = new Vector<>();
				
				Vector<Integer> keySet1 = new Vector<>(appImages.keySet1); keySet1.sort(null);
				Vector<String>  keySet2 = new Vector<>(appImages.keySet2); keySet2.sort(null);
				
				children.add(new ImageGroup1<Integer, String>(this, "Images (by Game)" ,  true, keySet1, keySet2, appImages::get));
				children.add(new ImageGroup1<String, Integer>(this, "Images (by Label)", false, keySet2, keySet1, (k2,k1)->appImages.get(k1,k2)));
				children.add(new FolderNode(this, "Other Images", imageFiles, TreeIcons.Folder));
				children.add(new FolderNode(this, "Other Files", otherFiles, TreeIcons.Folder));
				
				return children;
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
					Integer number = FolderNode.parseNumber(numberStr);
					if (number==null) return null;
					return new ImageFileName(number,labelStr);
				}
				
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
							children.add(new BaseTreeNode.DummyTextNode(this,"no \""+key.toString()+"\""));
					}
					return children;
				}
			}
		}

		static class AppManifestsRoot extends FolderRoot {
		
			AppManifestsRoot(TreeNode parent, File folder) {
				super(parent, "AppManifests", folder, TreeIcons.RootFolder);
			}
		
			@Override
			protected Vector<? extends AppManifestNode> createChildren() {
				Vector<AppManifestNode> children = new Vector<>();
				
				File[] files = fileObj.listFiles((FileFilter) AppManifestNode::is);
				Arrays.sort(files,Comparator.comparing(AppManifestNode::getAppIDFromFile, Comparator.nullsLast(Comparator.naturalOrder())));
				for (File file:files)
					children.add(new AppManifestNode(this, file));
				
				return children;
			}
			
		}

		static class FolderRoot extends FolderNode {
			private final String rootTitle;
			FolderRoot(TreeNode parent, String rootTitle, File folder) {
				this(parent, rootTitle, folder, TreeIcons.RootFolder_Simple);
			}
			protected FolderRoot(TreeNode parent, String rootTitle, File folder, TreeIcons icon) {
				super(parent, folder, icon);
				this.rootTitle = rootTitle;
			}
			@Override public String toString() {
				return String.format("%s [%s]", rootTitle, fileObj.getAbsolutePath());
			}
		}

		static abstract class FileSystemNode extends BaseTreeNode<TreeNode,FileSystemNode> {
			
			protected final File fileObj;
			
			protected FileSystemNode(TreeNode parent, File file, String title, boolean allowsChildren, boolean isLeaf) {
				this(parent, file, title, allowsChildren, isLeaf, null);
			}
			protected FileSystemNode(TreeNode parent, File file, String title, boolean allowsChildren, boolean isLeaf, TreeIcons icon) {
				super(parent, title, allowsChildren, isLeaf, icon);
				this.fileObj = file;
			}
			
			ExternalViewerInfo getExternalViewerInfo() { return null; }
			String getFileName() { return fileObj==null ? "" : fileObj.getName(); }
			String getFilePath() { return fileObj==null ? "" : fileObj.getAbsolutePath(); }
		}

		static class FolderNode extends FileSystemNode {
			
			private final Vector<File> files;

			FolderNode(TreeNode parent, File folder) {
				this(parent, folder, TreeIcons.Folder);
			}
			protected FolderNode(TreeNode parent, File folder, TreeIcons icon) {
				super(parent, folder, folder.getName(), true, false, icon);
				this.files = null;
			}
			FolderNode(TreeNode parent, String title, Vector<File> files, TreeIcons icon) {
				super(parent, null, title, true, files.isEmpty(), icon);
				this.files = files;
			}
		
			@Override
			protected Vector<? extends FileSystemNode> createChildren() {
				File[] files = this.files!=null ? this.files.toArray(new File[this.files.size()]) : getFilesAndFolders(fileObj);
				sortFiles(files);
				return createNodes(this,files);
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
			
			static void sortFiles(File[] files) {
				//Comparator<String> fileNameAsNumber = Comparator.comparing(FolderNode::parseNumber, Comparator.nullsLast(Comparator.naturalOrder()));
				//Comparator<String> fileNameComparator = fileNameAsNumber.thenComparing(String::toLowerCase);
				Comparator<SplittedFilename> splitted = Comparator.comparing((SplittedFilename sfn)->parseNumber(sfn.name), Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Comparator.naturalOrder());
				Comparator<String> fileNameComparator = Comparator.comparing(SplittedFilename::create, splitted);
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
			
			private static class SplittedFilename implements Comparable<SplittedFilename>{
				final String name;
				final String extension;
				private SplittedFilename(String name, String extension) {
					this.name = name;
					this.extension = extension;
				}
				static SplittedFilename create(String filename) {
					int pos = filename.lastIndexOf('.');
					if (pos<0) return new SplittedFilename(filename, null);
					return new SplittedFilename(filename.substring(0, pos), filename.substring(pos+1));
				}
				@Override
				public int compareTo(SplittedFilename other) {
					int comparedNames = this.name.compareToIgnoreCase(other.name);
					if (comparedNames!=0) return comparedNames;
					if (this.extension==null && other.extension==null) return 0;
					if (this .extension==null) return -1;
					if (other.extension==null) return +1;
					return this.extension.compareToIgnoreCase(other.extension);
				}
			}
			
			static Integer parseNumber(String name) {
				try {
					int n = Integer.parseInt(name);
					if (name.equals(Integer.toString(n))) return n;
				}
				catch (NumberFormatException e) {}
				return null;
			}
			
			static Vector<? extends FileSystemNode> createNodes(TreeNode parent, File[] files) {
				Vector<FileSystemNode> children = new Vector<>();
				
				for (File file:files) {
					if (file.isDirectory())
						children.add(new FolderNode(parent, file));
					
					else if (file.isFile()) {
						
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

		static class FileNode extends FileSystemNode implements BytesContentSource {
		
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
			static boolean fileNameEndsWith(File file, String... suffixes) {
				String name = file.getName().toLowerCase();
				for (String suffix:suffixes)
					if (name.endsWith(suffix))
						return true;
				return false;
			}

			@Override
			public String toString() {
				return String.format("%s (%s)", fileObj.getName(), getSizeStr(fileObj));
			}

			@Override protected Vector<FileSystemNode> createChildren() {
				throw new UnsupportedOperationException("Call of FileSystem.FileNode.createChildren() is not supported.");
			}

			@Override ExternalViewerInfo getExternalViewerInfo() {
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
			
			static final ExternalViewerInfo externalViewerInfo =
					new ExternalViewerInfo( "Image Viewer", SteamInspector.AppSettings.ValueKey.ImageViewer );
			
			private BufferedImage imageContent;

			ImageFile(TreeNode parent, File file) {
				super(parent, file, TreeIcons.ImageFile, externalViewerInfo);
				imageContent = null;
			}
			
			static boolean is(File file) {
				return fileNameEndsWith(file,".jpg",".jpeg",".png",".bmp",".ico");
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
				} catch (IOException e) {
					e.printStackTrace();
				}
				return imageContent;
			}
		}

		static class TextFile extends FileNode implements ExtendedTextContentSource {
			
			static final ExternalViewerInfo externalViewerInfo =
					new ExternalViewerInfo( "Text Editor", SteamInspector.AppSettings.ValueKey.TextEditor );
			
			protected final Charset charset;
			protected String textContent;

			TextFile(TreeNode parent, File file) {
				this(parent, file, TreeIcons.TextFile, null);
			}

			protected TextFile(TreeNode parent, File file, TreeIcons icon, Charset charset) {
				super(parent, file, icon, externalViewerInfo);
				this.charset = charset;
				this.textContent = null;
			}

			static boolean is(File file) {
				return false;
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
		
		interface DataTreeNode {
			default String getFullInfo() {
				String str = "";
				str += !hasName()  ? String.format("Name : none%n")  : String.format("Name : \"%s\"%n", getName());
				str += !hasValue() ? String.format("Value : none%n") : String.format("Value : %s%n", getValueStr());
				str += String.format("Path : %s%n", getPath());
				return str;
			}
			String getName();
			String getValueStr();
			String getPath();
			boolean hasName();
			boolean hasValue();
			
		}
		static class DataTreeNodeContextMenu extends AbstractContextMenu {
			private static final long serialVersionUID = 7620430144231207201L;
			private final JMenuItem miFullInfo;
			private final JMenuItem miPath;
			private final JMenuItem miName;
			private final JMenuItem miValue;
			private DataTreeNode clickedTreeNode;
			private JTree invoker;
			
			DataTreeNodeContextMenu() {
				clickedTreeNode = null;
				invoker = null;
				add(miFullInfo = SteamInspector.createMenuItem("Copy Full Info", true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getFullInfo())));
				add(miPath     = SteamInspector.createMenuItem("Copy Path"     , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getPath())));
				add(miName     = SteamInspector.createMenuItem("Copy Name"     , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getName())));
				add(miValue    = SteamInspector.createMenuItem("Copy Value"    , true, e->ClipboardTools.copyToClipBoard(clickedTreeNode.getValueStr())));
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
				
				miFullInfo.setEnabled(this.clickedTreeNode!=null);
				miPath    .setEnabled(this.clickedTreeNode!=null);
				miName    .setEnabled(this.clickedTreeNode!=null && this.clickedTreeNode.hasName());
				miValue   .setEnabled(this.clickedTreeNode!=null && this.clickedTreeNode.hasValue());
				
				show(invoker, x, y);
			}
		};

		static class JSON_File extends TextFile implements ParsedTextContentSource {
			
			private static final DataTreeNodeContextMenu contextMenu = new DataTreeNodeContextMenu();
			private JSON_Parser.Result parseResult;

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
					JSON_Parser parser = new JSON_Parser(text);
					try {
						parseResult = parser.parse_withParseException();
					} catch (JSON_Parser.ParseException e) {
						System.err.printf("ParseException: %s%n", e.getMessage());
						//e.printStackTrace();
						return BaseTreeNode.DummyTextNode.createSingleTextLineTree_("Parse Error: %s", e.getMessage());
					}
					if (parseResult==null)
						// return null;
						return BaseTreeNode.DummyTextNode.createSingleTextLineTree_("Parse Error: Parser returns <null>");
				}
				return JSON_TreeNode.create(parseResult,isLarge());
			}
			
			static class JSON_TreeNode<ValueType> extends BaseTreeNode<JSON_TreeNode<?>,JSON_TreeNode<?>> implements DataTreeNode {

				private final Vector<ValueType> children;
				private final Function<ValueType, String> getName;
				private final Function<ValueType, JSON_Data.Value> getValue;
				private final String name;
				private final JSON_Data.Value value;

				private JSON_TreeNode(JSON_TreeNode<?> parent, String title, JsonTreeIcons icon, String name, JSON_Data.Value value, Vector<ValueType> children, Function<ValueType,String> getName, Function<ValueType,JSON_Data.Value> getValue) {
					super(parent, title, children!=null, children==null || children.isEmpty(), icon==null ? null : JsonTreeIconsIS.getCachedIcon(icon));
					this.name = name;
					this.value = value;
					this.children = children;
					this.getName = getName;
					this.getValue = getValue;
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

				@Override public String getName    () { return name; }
				@Override public String getValueStr() { return value.toString(); }
				private Type getValueType() { return value==null ? null : value.type; }
				@Override public boolean hasName () { return name !=null; }
				@Override public boolean hasValue() { return value!=null; }

				@Override
				protected Vector<? extends JSON_TreeNode<?>> createChildren() {
					if (children==null) return null;
					Vector<JSON_TreeNode<?>> childNodes = new Vector<>();
					for (ValueType value:children) childNodes.add(create(this,getName.apply(value),getValue.apply(value)));
					return childNodes;
				}
				
				public static TreeRoot create(JSON_Parser.Result parseResult, boolean isLarge) {
					if (parseResult.object!=null) return new TreeRoot(create(null,null,new JSON_Data.ObjectValue(parseResult.object)),true,!isLarge,contextMenu);
					if (parseResult.array !=null) return new TreeRoot(create(null,null,new JSON_Data. ArrayValue(parseResult.array )),true,!isLarge,contextMenu);
					return BaseTreeNode.DummyTextNode.createSingleTextLineTree_("Parse Error: Parser returns neither an JSON array nor an JSON object");
				}
				
				private static JSON_TreeNode<?> create(JSON_TreeNode<?> parent, String name, JSON_Data.Value value) {
					String title = getTitle(name,value);
					JsonTreeIcons icon = getIcon(value.type);
					switch (value.type) {
					case Object: return new JSON_TreeNode<>(parent, title, icon, name, value, ((JSON_Data.ObjectValue)value).value, vt->vt.name, vt->vt.value);
					case Array : return new JSON_TreeNode<>(parent, title, icon, name, value, ((JSON_Data.ArrayValue )value).value, vt->null, vt->vt);
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
				
				private static String getTitle(String name, JSON_Data.Value value) {
					switch (value.type) {
					case Object : return getTitle(name, "{", ((JSON_Data. ObjectValue) value).value.size(), "}");
					case Array  : return getTitle(name, "[", ((JSON_Data.  ArrayValue) value).value.size(), "]");
					case Bool   : return getTitle(name, "" , ((JSON_Data.   BoolValue) value).value, "");
					case String : return getTitle(name, "" , ((JSON_Data. StringValue) value).value, "");
					case Integer: return getTitle(name, "" , ((JSON_Data.IntegerValue) value).value, "");
					case Float  : return getTitle(name, "" , ((JSON_Data.  FloatValue) value).value, "");
					case Null   : return getTitle(name, "" , ((JSON_Data.   NullValue) value).value, "");
					}
					return null;
				}
				
				private static String getTitle(String name, String openingBracket, Object value, String closingBracket) {
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
					} catch (ParseException e) {
						System.err.printf("ParseException: %s%n", e.getMessage());
						//e.printStackTrace();
						return BaseTreeNode.DummyTextNode.createSingleTextLineTree_("Parse Error: %s", e.getMessage());
					}
				}
				return vdfData==null ? null : vdfData.getRootTreeNode(isLarge(),contextMenu);
			}
		}

		static class AppManifestNode extends VDF_File {
			
			private static final String prefix = "appmanifest_";
			private static final String suffix = ".acf";
			
			static boolean is(File file) {
				return getAppIDFromFile(file) != null;
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
		
			private final int id;
			private final File file;
		
			AppManifestNode(TreeNode parent, File file) {
				super(parent, file, TreeIcons.AppManifest);
				this.file = file;
				id = getAppIDFromFile(file);
			}
		
			@Override public String toString() {
				return String.format("App %d (%s, %s)", id, file==null ? "" : file.getName(), getSizeStr(file));
			}
		}
	}
}
