package net.schwarzbaer.java.tools.steaminspector;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Vector;

import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.tree.TreeNode;

import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BaseTreeNode;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BytesContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.ExtendedTextContentSource;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TreeIcons;

class FolderStructure {
	
	private static final File FOLDER_STEAMLIBRARY_STEAMAPPS = new File("c:\\__Games\\SteamLibrary\\steamapps\\");
	private static final File FOLDER_STEAM_USERDATA         = new File("c:\\Program Files (x86)\\Steam\\userdata");
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
	
	static class Root extends BaseTreeNode<TreeNode> {

		Root() { super(null, "FolderStructure.Root", true, false); }

		@Override
		protected Vector<TreeNode> createChildren() {
			Vector<TreeNode> children = new Vector<>();
			
			if (FOLDER_STEAMLIBRARY_STEAMAPPS.isDirectory())
				children.add(new AppManifests.Root(this,FOLDER_STEAMLIBRARY_STEAMAPPS));
			if (FOLDER_STEAM_USERDATA.isDirectory())
				children.add(new UserData.Root(this,FOLDER_STEAM_USERDATA));
			
			return children;
		}

	}
	
	static class AppManifests {

		static class Root extends BaseTreeNode<AppManifestNode> {
		
			private final File folder;
		
			Root(TreeNode parent, File folder) {
				super(parent, "AppManifests", true, false, TreeIcons.RootFolder);
				this.folder = folder;
			}
		
			@Override public String toString() {
				return String.format("AppManifests [%s]", folder.getAbsolutePath());
			}

			@Override
			protected Vector<AppManifestNode> createChildren() {
				Vector<AppManifestNode> children = new Vector<>();
				
				File[] files = folder.listFiles((FileFilter) AppManifestNode::isAppManifest);
				Arrays.sort(files,Comparator.comparing(AppManifestNode::getAppIDFromFile, Comparator.nullsLast(Comparator.naturalOrder())));
				for (File file:files)
					children.add(new AppManifestNode(this, file));
				
				return children;
			}
			
		}

		static class AppManifestNode extends BaseTreeNode<TreeNode> implements ExtendedTextContentSource {
		
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
		
			AppManifestNode(Root parent, File file) {
				super(parent, getAppIDFromFile(file).toString(), false, true, TreeIcons.VDFFile);
				this.file = file;
				id = getAppIDFromFile(file);
			}

			@Override public String toString() {
				return String.format("App %d (%s, %s)", id, file==null ? "" : file.getName(), getSize(file));
			}

			@Override protected Vector<TreeNode> createChildren() {
				throw new UnsupportedOperationException("Call of AppManifestNode.createChildren() is not supported.");
			}

			@Override ContentType getContentType() {
				return ContentType.ExtendedText;
			}

			@Override public byte[] getContentAsBytes() {
				try {
					return Files.readAllBytes(file.toPath());
				} catch (IOException e) {
					return null;
				}
			}

			@Override public String getContentAsText() {
				byte[] bytes = getContentAsBytes();
				if (bytes==null) return "Can't read content";
				return new String(bytes);
			}
		}
	}
	
	static class UserData {

		static class Root extends FolderNode {
			Root(TreeNode parent, File folder) {
				super(parent, folder, TreeIcons.RootFolder);
			}
			@Override public String toString() {
				return String.format("UserData [%s]", folder.getAbsolutePath());
			}
		}

		static abstract class UserDataNode extends BaseTreeNode<UserDataNode> {
			protected UserDataNode(TreeNode parent, String title, boolean allowsChildren, boolean isLeaf, TreeIcons icon) {
				super(parent, title, allowsChildren, isLeaf, icon);
			}
			protected UserDataNode(TreeNode parent, String title, boolean allowsChildren, boolean isLeaf) {
				super(parent, title, allowsChildren, isLeaf);
			}
		}

		static class FolderNode extends UserDataNode {
		
			protected final File folder;
		
			FolderNode(TreeNode parent, File folder) {
				this(parent, folder, TreeIcons.Folder);
			}
			protected FolderNode(TreeNode parent, File folder, TreeIcons icon) {
				super(parent, folder.getName(), true, false, icon);
				this.folder = folder;
			}
		
			@Override
			protected Vector<UserDataNode> createChildren() {
				Vector<UserDataNode> children = new Vector<>();
				
				File[] files = folder.listFiles((FileFilter) file -> {
					String name = file.getName();
					if (file.isDirectory())
						return !name.equals(".") && !name.equals("..");
					return file.isFile();
				});
				
				Arrays.sort(files, (f1,f2) -> {
					String name1 = f1.getName();
					String name2 = f2.getName();
					Integer n1 = parseNumber(name1);
					Integer n2 = parseNumber(name2);
					if (n1!=null && n2!=null) return n1.intValue() - n2.intValue();
					if (n1==null && n2==null) return name1.compareToIgnoreCase(name2);
					return n1!=null ? -1 : +1;
				});
				
				for (File file:files) {
					if (file.isDirectory())
						children.add(new FolderNode(this, file));
					
					else if (file.isFile()) {
						
						if (VDF_File.isVDFFile(file))
							children.add(new VDF_File(this, file));
						
						else if (TextFile.isTextFile(file))
							children.add(new TextFile(this, file));
						
						else
							children.add(new FileNode(this, file));
					}
				}
				
				return children;
			}
			private Integer parseNumber(String name) {
				try {
					int n = Integer.parseInt(name);
					if (name.equals(Integer.toString(n))) return n;
				}
				catch (NumberFormatException e) {}
				return null;
			}
		}

		static class FileNode extends UserDataNode implements BytesContentSource {
		
			protected final File file;
			protected byte[] byteContent;
			
			FileNode(TreeNode parent, File file) {
				this(parent, file, TreeIcons.GeneralFile);
			}
			protected FileNode(TreeNode parent, File file, TreeIcons icon) {
				super(parent, file.getName(), false, true, icon);
				this.byteContent = null;
				this.file = file;
				if (!this.file.isFile())
					throw new IllegalStateException("Can't create a UserDataFileNode from nonexisting file or nonfile");
			}
			
			static Icon getIconForFile(String filename) {
				return new JFileChooser().getIcon(new File(filename));
			}

			@Override
			public String toString() {
				return String.format("%s (%s)", file.getName(), getSize(file));
			}

			@Override protected Vector<UserDataNode> createChildren() {
				throw new UnsupportedOperationException("Call of UserDataFileNode.createChildren() is not supported.");
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
					return Files.readAllBytes(file.toPath());
				} catch (IOException e) {
					return null;
				}
			}
		}

		static class TextFile extends FileNode implements ExtendedTextContentSource {
			
			protected String textContent;
			private Charset charset;

			TextFile(TreeNode parent, File file) {
				this(parent, file, TreeIcons.TextFile, null);
			}

			protected TextFile(TreeNode parent, File file, TreeIcons icon, Charset charset) {
				super(parent, file, icon);
				textContent = null;
			}

			static boolean isTextFile(File file) {
				String name = file.getName();
				return name.endsWith(".json");
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

		static class VDF_File extends TextFile {
			
			VDF_File(TreeNode parent, File file) {
				super(parent, file, TreeIcons.VDFFile, StandardCharsets.UTF_8);
			}

			static boolean isVDFFile(File file) {
				String name = file.getName();
				return name.endsWith(".vdf");
			}
			
			@Override ContentType getContentType() {
				return ContentType.ExtendedText; // will be changed later
			}
		}
	}
}
