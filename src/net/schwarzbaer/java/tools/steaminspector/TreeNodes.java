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
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.tree.TreeNode;

import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BaseTreeNode;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BytesContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ExtendedTextContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ImageContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ParsedTextContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TreeIcons;
import net.schwarzbaer.java.tools.steaminspector.VDFParser.ParseException;

class TreeNodes {
	
	private static final File FOLDER_TEST_FILES                  = new File("./test");
	private static final File FOLDER_STEAMLIBRARY_STEAMAPPS      = new File("C:\\__Games\\SteamLibrary\\steamapps\\");
	private static final File FOLDER_STEAM_USERDATA              = new File("C:\\Program Files (x86)\\Steam\\userdata");
	private static final File FOLDER_STEAM_APPCACHE              = new File("C:\\Program Files (x86)\\Steam\\appcache");
	private static final File FOLDER_STEAM_APPCACHE_LIBRARYCACHE = new File("C:\\Program Files (x86)\\Steam\\appcache\\librarycache");
	private static final File FOLDER_STEAM_STEAM_GAMES           = new File("C:\\Program Files (x86)\\Steam\\steam\\games");
	// C:\Program Files (x86)\Steam\appcache\librarycache
	//        425580_icon.jpg 
	//        425580_header.jpg 
	//        425580_library_600x900.jpg 
	//        425580_library_hero.jpg 
	//        425580_library_hero_blur.jpg 
	//        425580_logo.png 
	// eb32e3c266a74c7d51835ebf7c866bf2dbf59b47.ico    ||   C:\Program Files (x86)\Steam\steam\games

	static String getSize(File file) {
		long length = file==null ? 0 : file.length();
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
		
		static class Root extends BaseTreeNode<TreeNode> {
		
			Root() { super(null, "FolderStructure.Root", true, false); }
		
			@Override
			protected Vector<TreeNode> createChildren() {
				Vector<TreeNode> children = new Vector<>();
				
				if (FOLDER_STEAMLIBRARY_STEAMAPPS.isDirectory()) {
					children.add(new AppManifestsRoot(this,FOLDER_STEAMLIBRARY_STEAMAPPS));
					children.add(new FolderRoot(this,"AppManifests (as Folder)",FOLDER_STEAMLIBRARY_STEAMAPPS));
				}
				if (FOLDER_STEAM_USERDATA.isDirectory())
					children.add(new FolderRoot(this,"UserData (as Folder)",FOLDER_STEAM_USERDATA));
				if (FOLDER_STEAM_APPCACHE.isDirectory())
					children.add(new FolderRoot(this,"AppCache (as Folder)",FOLDER_STEAM_APPCACHE));
				if (FOLDER_STEAM_APPCACHE_LIBRARYCACHE.isDirectory()) {
					children.add(new FolderRoot(this,"LibraryCache (as Folder)",FOLDER_STEAM_APPCACHE_LIBRARYCACHE));
					children.add(new LibraryCacheRoot(this,FOLDER_STEAM_APPCACHE_LIBRARYCACHE));
				}
				if (FOLDER_STEAM_STEAM_GAMES.isDirectory())
					children.add(new FolderRoot(this,"Game Icons (as Folder)",FOLDER_STEAM_STEAM_GAMES));
				if (FOLDER_TEST_FILES.isDirectory())
					children.add(new FolderRoot(this,"Test Files",FOLDER_TEST_FILES));
				
				return children;
			}
		
		}

		static class LibraryCacheRoot extends BaseTreeNode<TreeNode> {
		
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
						
					} else if (ImageFile.isImageFile(file)) {
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

			static class ImageGroup1<KeyType1,KeyType2> extends BaseTreeNode<TreeNode> {

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

			static class ImageGroup2<KeyType> extends BaseTreeNode<TreeNode> {

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
				super(parent, "AppManifests", folder);
			}
		
			@Override
			protected Vector<? extends AppManifestNode> createChildren() {
				Vector<AppManifestNode> children = new Vector<>();
				
				File[] files = fileObj.listFiles((FileFilter) AppManifestNode::isAppManifest);
				Arrays.sort(files,Comparator.comparing(AppManifestNode::getAppIDFromFile, Comparator.nullsLast(Comparator.naturalOrder())));
				for (File file:files)
					children.add(new AppManifestNode(this, file));
				
				return children;
			}
			
		}

		static class FolderRoot extends FolderNode {
			private final String rootTitle;
			FolderRoot(TreeNode parent, String rootTitle, File folder) {
				super(parent, folder, TreeIcons.RootFolder);
				this.rootTitle = rootTitle;
			}
			@Override public String toString() {
				return String.format("%s [%s]", rootTitle, fileObj.getAbsolutePath());
			}
		}

		static abstract class FileSystemNode extends BaseTreeNode<FileSystemNode> {
			
			protected final File fileObj;
			
			protected FileSystemNode(TreeNode parent, File file, String title, boolean allowsChildren, boolean isLeaf) {
				this(parent, file, title, allowsChildren, isLeaf, null);
			}
			protected FileSystemNode(TreeNode parent, File file, String title, boolean allowsChildren, boolean isLeaf, TreeIcons icon) {
				super(parent, title, allowsChildren, isLeaf, icon);
				this.fileObj = file;
			}
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
				Arrays.sort(files, (f1,f2) -> {
					String name1 = f1.getName();
					String name2 = f2.getName();
					Integer n1 = parseNumber(name1);
					Integer n2 = parseNumber(name2);
					if (n1!=null && n2!=null) return n1.intValue() - n2.intValue();
					if (n1==null && n2==null) return name1.compareToIgnoreCase(name2);
					return n1!=null ? -1 : +1;
				});
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
						
						if (TextFile.isTextFile(file))
							children.add(new TextFile(parent, file));
						
						else if (VDF_File.isVDFFile(file))
							children.add(new VDF_File(parent, file));
						
						else if (AppManifestNode.isAppManifest(file))
							children.add(new AppManifestNode(parent, file));
						
						else if (ImageFile.isImageFile(file))
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
			
			FileNode(TreeNode parent, File file) {
				this(parent, file, TreeIcons.GeneralFile);
			}
			protected FileNode(TreeNode parent, File file, TreeIcons icon) {
				super(parent, file, file.getName(), false, true, icon);
				this.byteContent = null;
				if (!fileObj.isFile())
					throw new IllegalStateException("Can't create a FileSystem.FileNode from nonexisting file or nonfile");
			}
			
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
				return String.format("%s (%s)", fileObj.getName(), getSize(fileObj));
			}

			@Override protected Vector<FileSystemNode> createChildren() {
				throw new UnsupportedOperationException("Call of FileSystem.FileNode.createChildren() is not supported.");
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
				super(parent, file, TreeIcons.ImageFile);
				imageContent = null;
			}
			
			static boolean isImageFile(File file) {
				return fileNameEndsWith(file,".jpg",".jpeg",".png",".bmp");
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
			
			protected final Charset charset;
			protected String textContent;

			TextFile(TreeNode parent, File file) {
				this(parent, file, TreeIcons.TextFile, null);
			}

			protected TextFile(TreeNode parent, File file, TreeIcons icon, Charset charset) {
				super(parent, file, icon);
				this.charset = charset;
				this.textContent = null;
			}

			static boolean isTextFile(File file) {
				return fileNameEndsWith(file,".json");
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

		static class VDF_File extends TextFile implements ParsedTextContentSource {
			
			private VDFParser.Data vdfData;

			VDF_File(TreeNode parent, File file) {
				this(parent, file, TreeIcons.VDFFile);
			}
			protected VDF_File(TreeNode parent, File file, TreeIcons icon) {
				super(parent, file, icon, StandardCharsets.UTF_8);
				vdfData = null;
			}

			static boolean isVDFFile(File file) {
				return fileNameEndsWith(file,".vdf");
			}
			
			@Override ContentType getContentType() {
				return ContentType.ParsedText;
			}
			
			@Override
			public TreeNode getContentAsTree() {
				if (vdfData==null) {
					String text = getContentAsText();
					try {
						vdfData = VDFParser.parse(text);
					} catch (ParseException e) {
						System.err.printf("ParseException: %s", e.getMessage());
						//e.printStackTrace();
						return BaseTreeNode.DummyTextNode.createSingleTextLineTree("Parse Error");
					}
				}
				return vdfData==null ? null : vdfData.getRootTreeNode();
			}
		}

		static class AppManifestNode extends VDF_File {
			
			private static final String prefix = "appmanifest_";
			private static final String suffix = ".acf";
			
			static boolean isAppManifest(File file) {
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
				return String.format("App %d (%s, %s)", id, file==null ? "" : file.getName(), getSize(file));
			}
		}
	}
}
