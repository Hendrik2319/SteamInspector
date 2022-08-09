package net.schwarzbaer.java.tools.steaminspector;

import java.awt.Dimension;
import java.awt.Window;
import java.io.File;
import java.util.Vector;

import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.schwarzbaer.gui.ProgressDialog;
import net.schwarzbaer.gui.StandardDialog;
import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.AppSettings.ValueKey;

class SteamScreenshotsCleanUp {
	
	public static void startStandAlone() {
		new SteamScreenshotsCleanUp(null).initialize().showGUI();
	}

	public static void startAsDialog(Window parent) {
		new SteamScreenshotsCleanUp(parent).initialize().showGUI();
	}

	private final Window mainWindow;
	private final JFileChooser folderChooser;
	private final JTree tree;
	@SuppressWarnings("unused")
	private DefaultTreeModel treeModel;
	private File generalScreenshotFolder;
	private final Vector<GeneralScreenshot> generalScreenshots;
	private final GuiVariant<? extends Window> gui;

	SteamScreenshotsCleanUp(Window parent) {
		generalScreenshotFolder = null;
		generalScreenshots = new Vector<GeneralScreenshot>();
		
		if (parent==null) gui = new GuiVariant.StandAlone("Planet Crafter - SaveGame Viewer");
		else              gui = new GuiVariant.Dialog(parent, "Planet Crafter - SaveGame Viewer");
		mainWindow = gui.getWindow();
		
		folderChooser = new JFileChooser("./");
		folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		folderChooser.setMultiSelectionEnabled(false);
		
		tree = new JTree(treeModel = null);
		tree.setCellRenderer(new SteamInspector.BaseTreeNodeRenderer());
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.addTreeSelectionListener(e->{
			TreePath path = e.getNewLeadSelectionPath();
			if (path==null) return;
			showTreeNodeContent(path.getLastPathComponent());
		});
		
		JScrollPane treeScrollPane = new JScrollPane(tree);
		treeScrollPane.setPreferredSize(new Dimension(500, 800));
		
		JScrollPane filesScrollPane = new JScrollPane();
		
		JSplitPane contentPane = new JSplitPane();
		contentPane.setLeftComponent (treeScrollPane);
		contentPane.setRightComponent(filesScrollPane);
		
		gui.createGUI(contentPane);
		SteamInspector.settings.registerExtraWindow(mainWindow, ValueKey.SSCU_WindowX, ValueKey.SSCU_WindowY, ValueKey.SSCU_WindowWidth, ValueKey.SSCU_WindowHeight);
	}

	private void showGUI() {
		gui.showGUI();
	}
	
	private void showTreeNodeContent(Object lastPathComponent) {
		// TODO Auto-generated method stub
		
	}

	SteamScreenshotsCleanUp initialize() {
		ProgressDialog.runWithProgressDialog(mainWindow, "Initialize", 300, pd->{
			generalScreenshotFolder = getFolder(pd, "Determine General Screenshot Folder", ValueKey.SSCU_GeneralScreenshotFolder);
			if (generalScreenshotFolder==null) return;
			
			SteamInspector.showIndeterminateTask(pd, "Load Game Data");
			if (Data.isEmpty())
				Data.loadData();
			
			SteamInspector.showIndeterminateTask(pd, "Scan General Screenshot Folder");
			// in general Screenshot Folder: 263280_20191217003048_1.png
			// in Game Screenshot Folder: 20191217003048_1.jpg  
			File[] files = generalScreenshotFolder.listFiles(File::isFile);
			generalScreenshots.clear();
			for (File file : files) {
				String filename = file.getName();
				
				int pos1 = filename.indexOf("_");
				if (pos1<0) continue;
				
				int gameID;
				try { gameID = Integer.parseInt(filename.substring(0, pos1)); }
				catch (NumberFormatException e) { continue; }
				
				int pos2 = filename.indexOf(".", pos1);
				if (pos2<0) continue;
				String nameWithoutExt = filename.substring(pos1+1,pos2);
				
				generalScreenshots.add(new GeneralScreenshot(file, gameID, nameWithoutExt));
			}
			System.out.printf("Found %d images in \"%s\".%n", generalScreenshots.size(), generalScreenshotFolder.getAbsolutePath());
			
			SteamInspector.showIndeterminateTask(pd, "Update GUI");
			tree.setModel(treeModel = new DefaultTreeModel(new RootTreeNode()));
			
		});
		return this;
	}
	
	private File getFolder(ProgressDialog pd, String taskTitle, ValueKey valueKey) {
		SteamInspector.showIndeterminateTask(pd, taskTitle);
		File folder = SteamInspector.settings.getFile(valueKey, null);
		if (folder==null || !folder.isDirectory()) {
			folder = null;
			if (folderChooser.showOpenDialog(mainWindow)==JFileChooser.APPROVE_OPTION) {
				folder = folderChooser.getSelectedFile();
				SteamInspector.settings.putFile(valueKey, folder);
			}
		}
		return folder;
	}

	private static abstract class GuiVariant<MainWindow extends Window> {
		protected final MainWindow mainWindow;
		GuiVariant(MainWindow mainWindow) {
			this.mainWindow = mainWindow;
		}
		Window getWindow() { return mainWindow; }
		abstract void createGUI(JComponent contentPane);
		abstract void showGUI();
		
		static class StandAlone extends GuiVariant<StandardMainWindow> {
			StandAlone(String title) { super(new StandardMainWindow(title)); }
			@Override public void createGUI(JComponent contentPane) { mainWindow.startGUI(contentPane); }
			@Override public void showGUI() {}
		}
		static class Dialog extends GuiVariant<StandardDialog> {
			Dialog(Window parent, String title) { super(new StandardDialog(parent, title)); }
			@Override void createGUI(JComponent contentPane) { mainWindow.createGUI(contentPane, SteamInspector.createButton("Close", true, e->mainWindow.closeDialog())); }
			@Override void showGUI() { mainWindow.showDialog(); }
		}
	}

	private static class GeneralScreenshot {

		public GeneralScreenshot(File file, int gameID, String nameWithoutExt) {
			// TODO Auto-generated constructor stub
		}
	
	}

	private static class RootTreeNode extends SteamInspector.BaseTreeNode<TreeNode, TreeNode> {

		RootTreeNode() {
			super(null, "<Root>", true, false);
		}

		@Override protected Vector<? extends TreeNode> createChildren() {
			return new Vector<>();
		}
		
	}

}
