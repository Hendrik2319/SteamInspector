package net.schwarzbaer.java.tools.steaminspector;

import java.io.File;
import java.io.FileFilter;
import java.util.function.Consumer;

class FileTools
{
	
	static File[] getFilesAndFolders(File folder) {
		File[] files = folder.listFiles((FileFilter) file -> {
			String name = file.getName();
			if (file.isDirectory())
				return !name.equals(".") && !name.equals("..");
			return file.isFile();
		});
		return files;
	}

	static void forEachSubFile(File folder, Consumer<File> subdirAction, Consumer<File> imageFileAction, Consumer<File> otherAction)
	{
		File[] files = getFilesAndFolders(folder);
		for (File file:files)
		{
			if (file.isDirectory())
				subdirAction.accept(file);
			
			else if (isImageFile(file))
				imageFileAction.accept(file);
			
			else
				otherAction.accept(file);
		}
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

}
