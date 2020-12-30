package net.schwarzbaer.java.tools.steaminspector;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Vector;

import javax.swing.tree.TreeNode;

import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BaseTreeNode;

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
				super(parent, "AppManifests", true, false);
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

		static class AppManifestNode extends BaseTreeNode<TreeNode> {
		
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
				super(parent, getAppIDFromFile(file).toString(), false, true);
				this.file = file;
				id = getAppIDFromFile(file);
			}
		
			@Override protected Vector<TreeNode> createChildren() {
				throw new UnsupportedOperationException("Call of AppManifestNode.createChildren() is not supported.");
			}

			@Override ContentType getContentType() {
				return ContentType.PlainText;
			}

			@Override byte[] getContentAsBytes() {
				try {
					return Files.readAllBytes(file.toPath());
				} catch (IOException e) {
					return null;
				}
			}

			@Override String getContentAsText() {
				byte[] bytes = getContentAsBytes();
				if (bytes==null) return "Can't read content";
				return new String(bytes);
			}
		}
	}
	
	static class UserData {

		static class Root extends FolderNode {
			Root(TreeNode parent, File folder) {
				super(parent, folder);
			}
			@Override public String toString() {
				return String.format("UserData [%s]", folder.getAbsolutePath());
			}
		}

		static abstract class UserDataNode extends BaseTreeNode<UserDataNode> {
			UserDataNode(TreeNode parent, String title, boolean allowsChildren, boolean isLeaf) {
				super(parent, title, allowsChildren, isLeaf);
			}
		}

		static class FolderNode extends UserDataNode {
		
			protected final File folder;
		
			FolderNode(TreeNode parent, File folder) {
				super(parent, folder.getName(), true, false);
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

		static class FileNode extends UserDataNode {
		
			protected final File file;
		
			FileNode(TreeNode parent, File file) {
				super(parent, file.getName(), false, true);
				this.file = file;
				if (!this.file.isFile())
					throw new IllegalStateException("Can't create a UserDataFileNode from nonexisting file or nonfile");
			}

			@Override
			public String toString() {
				return String.format("%s (%s)", file.getName(), getSize(file));
			}

			static String getSize(File file) {
				long length = file.length();
				if (length               <1100) return String.format("%d B"    , length);
				if (length/1024          <1100) return String.format("%1.1f kB", length/1024f);
				if (length/1024/1024     <1100) return String.format("%1.1f MB", length/1024f/1024f);
				if (length/1024/1024/1024<1100) return String.format("%1.1f GB", length/1024f/1024f/1024f);
				return "["+length+"]";
			}

			@Override protected Vector<UserDataNode> createChildren() {
				throw new UnsupportedOperationException("Call of UserDataFileNode.createChildren() is not supported.");
			}

			@Override ContentType getContentType() {
				return ContentType.HexText;
			}

			@Override byte[] getContentAsBytes() {
				try {
					return Files.readAllBytes(file.toPath());
				} catch (IOException e) {
					return null;
				}
			}
		}

		static class TextFile extends FileNode {
			
			TextFile(TreeNode parent, File file) {
				super(parent, file);
			}
		
			static boolean isTextFile(File file) {
				String name = file.getName();
				return name.endsWith(".json");
			}
		
			@Override ContentType getContentType() {
				return ContentType.PlainText;
			}
		
			@Override String getContentAsText() {
				byte[] bytes = getContentAsBytes();
				if (bytes==null) return "Can't read content";
				return new String(bytes);
			}
		}

		static class VDF_File extends TextFile {
			
			VDF_File(TreeNode parent, File file) {
				super(parent, file);
			}

			static boolean isVDFFile(File file) {
				String name = file.getName();
				return name.endsWith(".vdf");
			}
		}
	}
}
