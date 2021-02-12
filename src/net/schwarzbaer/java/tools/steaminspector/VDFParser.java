package net.schwarzbaer.java.tools.steaminspector;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.Locale;
import java.util.Vector;
import java.util.function.Function;

import javax.swing.Icon;
import javax.swing.tree.DefaultTreeModel;

import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TreeContextMenuHandler;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TreeRoot;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.DataTreeNode;

class VDFParser {

/*
 * File        =   ValuePair,  {  WhiteSpace,  ValuePair  } ;
 * 	
 * ValuePair   =   StringBlock,  WhiteSpace,  ( StringBlock | ArrayBlock ) ;
 * 	
 * StringBlock =   '"',  { AlleZeichen - '"' },  '"' ;
 * 	
 * ArrayBlock  =   '{',  WhiteSpace,  ValuePair,  {  WhiteSpace,  ValuePair  },  WhiteSpace,  '}' ;
 * 	
 * WhiteSpace  =   WhiteSpaceChar,  { WhiteSpaceChar } ;
 * 	
 * WhiteSpaceChar  =   ( ' ' | '\r' | '\n' | '\t' ) ;
 * 	
 */
	
	private final String text;

	private VDFParser(String text) {
		this.text = text;
	}
	
	public static Data parse(File file, Charset charset) throws ParseException {
		try {
			byte[] bytes = Files.readAllBytes(file.toPath());
			return parse(new String(bytes,charset));
		} catch (IOException e) {
			return null;
		}
	}

	public static Data parse(String text) throws ParseException {
		return new VDFParser(text).parse();
	}

	private Data parse() throws ParseException {
		Data data = new Data();
		
		try (LineNumberReader textIn = new LineNumberReader(new StringReader(text));) {
			textIn.setLineNumber(1);
			
			ValuePair valuePair;
			while ( (valuePair = ValuePair.read(textIn))!=null ) {
				data.add(valuePair);
			}
			
		} catch (IOException e) { // automatic close
			throw new ParseException(e, -1, "Closing StringReader: IOException occured.");
		}
		
		if (data.rootPairs.isEmpty())
			throw new ParseException(-1, "Parsed VDF File is empty.");
		
		return data;
	}

	private static class ValuePair {
		
		private final Block label;
		private final Block datablock;
		private boolean wasProcessed;
		private Boolean hasUnprocessedChildren;
		
		private ValuePair(Block label, Block datablock) {
			this.label = label;
			this.datablock = datablock;
			this.wasProcessed = false;
			this.hasUnprocessedChildren = null;
			if (this.label==null || this.datablock==null)
				throw new IllegalArgumentException("Both Blocks in a ValuePair must be non-null");
			if (this.label.isClosingBracketDummy() != this.datablock.isClosingBracketDummy())
				throw new IllegalArgumentException();
		}
		
		public static ValuePair createClosingBracketDummy() {
			return new ValuePair(Block.createClosingBracketDummy(), Block.createClosingBracketDummy());
		}

		@Override
		public String toString() {
			return String.format("ValuePair [label=%s, datablock=%s]", label, datablock);
		}

		public boolean isClosingBracketDummy() {
			return label.isClosingBracketDummy() || datablock.isClosingBracketDummy();
		}

		private static ValuePair read(LineNumberReader textIn) throws ParseException {
			return read(textIn,false);
		}
		private static ValuePair read(LineNumberReader textIn, boolean allowClosingBracket) throws ParseException {
			int lineNumber;
			Block block;
			
			lineNumber = textIn.getLineNumber();
			block = Block.read(textIn,allowClosingBracket);
			if (block==null)
				return null;
			if (allowClosingBracket && block.isClosingBracketDummy())
				return ValuePair.createClosingBracketDummy();
			if (block.type!=Block.Type.String)
				throw new ParseException(lineNumber,textIn.getLineNumber(), "Reading label of ValuePair: String Block expected. Got %s Block. [%s]", block.type, block);
			
			Block label = block;
			
			lineNumber = textIn.getLineNumber();
			block = Block.read(textIn);
			if (block==null)
				throw new ParseException(lineNumber,textIn.getLineNumber(), "Reading data block of ValuePair: Block expected. Got End-Of-File.");
			
			return new ValuePair(label, block);
		}
	}

	private static class Block {
		enum Type { String, Array }

		final Type type;
		final String str;
		final Vector<ValuePair> array;
		
		private Block() { // ClosingBracketDummy
			this.type = null;
			this.str = null;
			this.array = null;
		}
		
		private Block(String str) {
			this.type = Type.String;
			this.str = str;
			this.array = null;
		}
		
		private Block(Vector<ValuePair> array) {
			this.type = Type.Array;
			this.str = null;
			this.array = array;
		}

		@Override
		public String toString() {
			if (isClosingBracketDummy()) return "ClosingBracketDummy-Block";
			if (type!=null)
				switch (type) {
				case Array : return String.format("%s-Block [%s]", type, array==null ? "null" : array.size()+" items");
				case String: return String.format("%s-Block [%s]", type, str  ==null ? "null" : "\""+str+"\"");
				}
			return String.format("Block [type=%s, str=%s, array=%s]", type, str, array);
		}

		static Block createClosingBracketDummy() {
			return new Block();
		}

		boolean isClosingBracketDummy() {
			return type==null && str==null && array==null;
		}

		static Block read(LineNumberReader textIn) throws ParseException {
			return read(textIn, false);
		}
		static Block read(LineNumberReader textIn, boolean allowClosingBracket) throws ParseException {
			
			Token token = Token.read(textIn);
			int lineNumber = textIn.getLineNumber();
			if (token==null) // End-Of-File
				return null;
			
			if (token.type==Token.Type.String) // String Block
				return new Block(token.str);
			
			if (token.type==Token.Type.OpeningBracket)
				return readArray(textIn);
			
			if (token.type==Token.Type.ClosingBracket && allowClosingBracket)
				return Block.createClosingBracketDummy();
			
			throw new ParseException(lineNumber,textIn.getLineNumber(), "Reading Block: String Token or OpeningBracket Token%s expected. Got %s Token. (%s)", allowClosingBracket ? " or ClosingBracket  Token" : "", token.type, token);
		}

		private static Block readArray(LineNumberReader textIn) throws ParseException {
			
			Vector<ValuePair> array = new Vector<>();
			ValuePair vp;
			int lineNumber = textIn.getLineNumber();
			while ( (vp=ValuePair.read(textIn,true))!=null ) {
				if (vp.isClosingBracketDummy())
					return new Block(array); // Array Block
				array.add(vp);
				lineNumber = textIn.getLineNumber();
			}
			throw new ParseException(lineNumber,textIn.getLineNumber(), "Reading Array Block: Reached End-Of-File before ClosingBracket Token.");
		}
	}

	private static class Token {
		enum Type { String, OpeningBracket, ClosingBracket }

		final Type type;
		final String str;

		Token(Type type, String str) {
			this.type = type;
			this.str = str;
		}

		static Token read(LineNumberReader textIn) throws ParseException {
			
			try {
				
				int ch;
				while ( (ch=textIn.read())>=0 ) {
					if (ch=='\n') continue;
					if (ch=='\r') continue;
					if (ch=='	') continue;
					if (ch==' ') continue;
					if (ch=='{') return new Token(Token.Type.OpeningBracket, null);
					if (ch=='}') return new Token(Token.Type.ClosingBracket, null);
					if (ch=='"') {
						StringBuilder sb = new StringBuilder();
						while ( (ch=textIn.read())>=0 ) {
							if (ch=='"')
								return new Token(Token.Type.String, sb.toString());
							if (ch=='\\') {
								ch=textIn.read();
								if (ch<0) break;
								switch (ch) {
								case 'b': ch='\b'; break;
								case 't': ch='\t'; break;
								case 'n': ch='\n'; break;
								case 'r': ch='\r'; break;
								case 'f': ch='\f'; break;
								case '\'': break;
								case '\"': break;
								case '\\': break;
								default:
									sb.append('\\');
									// unknown escape char --> repair escape sequence
									//   '\' + '#'  -->  '\#'
								}
							}
							sb.append((char)ch);
						}
						if (ch<0) throw new ParseException(textIn.getLineNumber(), "Reading String Token: Reached End-Of-File before closing \" char.");
					}
					if (ch=='/') {
						ch=textIn.read();
						if (ch==0)   throw new ParseException(textIn.getLineNumber(), "Reading Token: Unexpected char at end of file: \"/\" [%d]", (int)'/');
						if (ch!='/') throw new ParseException(textIn.getLineNumber(), "Reading Token: Unexpected chars: \"/%s\" [%d+%d]", (char)ch, (int)'/', ch);
						while ( (ch=textIn.read())>=0 && ch!='\n') {
						}
						if (ch<0) break; // End-Of-File after line comment
						continue;
					}
					throw new ParseException(textIn.getLineNumber(), "Reading Token: Unexpected char: \"%s\" [%d]", (char)ch, ch);
				}
			
			} catch (IOException e) {
				throw new ParseException(e, textIn.getLineNumber(), "Reading Token: IOException occured.");
			}
			
			return null; // End-Of-File
		}
	}
	
	static class ParseException extends Exception {
		private static final long serialVersionUID = 365326060770041096L;

		private ParseException(Throwable cause, int line, String format, Object... args) {
			super(constructMsg(line, line, format, args), cause);
		}
		private ParseException(int startLine, int endLine, String format, Object... args) {
			super(constructMsg(startLine, endLine, format, args));
		}
		private ParseException(int line, String format, Object... args) {
			super(constructMsg(line, line, format, args));
		}

		private static String constructMsg(int startLine, int endLine, String format, Object... args) {
			String msg2 = String.format(Locale.ENGLISH, format, args);
			if (startLine==endLine) {
				if (startLine<0)
					return String.format("End-Of-File: %s" , msg2);
				return String.format("in Line %d: %s" , startLine, msg2);
			}
			return String.format("in Lines %d..%d: %s" , startLine, endLine, msg2);
		}
	}

	static class Data {
		
		private final Vector<ValuePair> rootPairs;
		
		private Data() {
			rootPairs = new Vector<>();
		}

		TreeRoot getTreeRoot(boolean isLarge, TreeContextMenuHandler tcmh) {
			return new TreeRoot(createVDFTreeNode(), false, !isLarge, tcmh);
			//return new TreeRoot(VDFTreeNode.createRoot(rootPairs), false, !isLarge, tcmh);
		}

		VDFTreeNode createVDFTreeNode() {
			return new VDFTreeNode(rootPairs);
		}

		private void add(ValuePair valuePair) {
			rootPairs.add(valuePair);
		}

	}
	
	static class VDFTreeNode extends SteamInspector.BaseTreeNode<VDFTreeNode,VDFTreeNode> implements DataTreeNode {
		
		enum Type {
			Root(null), String(Block.Type.String), Array(Block.Type.Array);
			private final Block.Type blockType;
			Type(Block.Type blockType) { this.blockType = blockType; }
		}
		
		private final ValuePair base;
		private final Vector<ValuePair> valuePairArray;
		private final String name;
		private final String value;
		private final Type type;
		private ChildrenOrder childrenOrder;

		VDFTreeNode(Vector<ValuePair> valuePairArray) { // Root
			super(null,"VDF Tree Root",true,valuePairArray==null || valuePairArray.isEmpty());
			this.base = null;
			this.type = Type.Root;
			this.valuePairArray = valuePairArray;
			this.name = null;
			this.value = null;
			this.childrenOrder=null;
		}
		VDFTreeNode(VDFTreeNode parent, ValuePair base, String name, String value) { // String Value
			super(parent, String.format("%s : \"%s\"", name, value), false, true);
			this.base = base;
			this.type = Type.String;
			this.valuePairArray = null;
			this.name = name;
			this.value = value;
			this.base.hasUnprocessedChildren = false;
			this.childrenOrder=null;
			if (this.base==null) throw new IllegalArgumentException();
			if (this.base.datablock.type!=type.blockType) throw new IllegalArgumentException();
			if (this.value==null) throw new IllegalArgumentException();
		}
		VDFTreeNode(VDFTreeNode parent, ValuePair base, String name, Vector<ValuePair> valuePairArray) { // Array Value
			super(parent, name, true,valuePairArray==null || valuePairArray.isEmpty());
			this.base = base;
			this.type = Type.Array;
			this.valuePairArray = valuePairArray;
			this.name = name;
			this.value = null;
			this.childrenOrder=null;
			if (this.base==null) throw new IllegalArgumentException();
			if (this.base.datablock.type!=type.blockType) throw new IllegalArgumentException();
			if (this.valuePairArray==null) throw new IllegalArgumentException();
		}
		
		TreeRoot getTreeRoot(TreeContextMenuHandler tcmh) {
			return new TreeRoot(this, type!=Type.Root, true, tcmh);
		}
		
		boolean is(Type type) { return this.type==type; }
		
		void markAsProcessed() {
			if (type!=Type.Root)
				base.wasProcessed = true;
		}
		
		private boolean wasProcessed() {
			if (type==Type.Root) return true;
			return base.wasProcessed;
		}
		
		boolean hasUnprocessedChildren() {
			if (type==Type.Root || base.hasUnprocessedChildren==null) {
				if (base!=null) base.hasUnprocessedChildren=false;
				checkChildren("hasUnprocessedChildren()");
				for (VDFTreeNode child:children) {
					if (!child.wasProcessed() || child.hasUnprocessedChildren()) {
						if (base!=null) base.hasUnprocessedChildren=true;
						return true;
					}
				}
			}
			return false;
		}
		
		@Override public String getPath() {
			if (parent==null) return "Root";
			return parent.getPath()+"["+parent.getIndex(this)+"]."+name;
		}
		
		@Override public String getAccessCall() {
			return String.format("<root>.getSubNode(%s)", getAccessPath());
		}
		private String getAccessPath() {
			if (parent==null) return "";
			String path = parent.getAccessPath();
			if (!path.isEmpty()) path += ",";
			return path + "\""+name+"\"";
		}
		
		@Override public boolean hasName() { return name!=null; }
		@Override public String  getName() { return name; }
		@Override public boolean hasValue() { return value!=null || (valuePairArray!=null && !valuePairArray.isEmpty()); }
		@Override public String getValueStr() {
			if (value!=null) return String.format("\"%s\"", value);
			if (valuePairArray!=null) return String.format("Array[%d]", valuePairArray.size());
			return null;
		}

		@Override public String getFullInfo() {
			String str = DataTreeNode.super.getFullInfo();
			str += String.format("Value Hash      : 0x%08X%n", hashCode());
			str += String.format("was processed   : %s%n", wasProcessed());
			str += String.format("has unprocessed children: %s%n", hasUnprocessedChildren());
			return str;
		}
		
		@Override
		Icon getIcon() {
			switch (type) {
			case Array:
				break;
			case Root:
				break;
			case String:
				break;
			}
			// TODO Auto-generated method stub
			return super.getIcon();
		}
		@Override Color getTextColor() {
			return JSONHelper.getTextColor(wasProcessed(),hasUnprocessedChildren());
		}
		
		@Override public boolean areChildrenSortable() { return type == Type.Array; }
		@Override public ChildrenOrder getChildrenOrder() { return childrenOrder; }
		@Override public void setChildrenOrder(ChildrenOrder childrenOrder, DefaultTreeModel currentTreeModel) {
			this.childrenOrder = childrenOrder;
			rebuildChildren(currentTreeModel);
		}
		
		@Override
		protected Vector<? extends VDFTreeNode> createChildren() {
			Vector<VDFTreeNode> children = new Vector<>();
			if (valuePairArray!=null) {
				Vector<ValuePair> vector = new Vector<>(valuePairArray);
				if (childrenOrder!=null)
					switch (childrenOrder) {
					case ByName:
						vector.sort(Comparator.<ValuePair,String>comparing(vp->vp.label.str,net.schwarzbaer.java.tools.steaminspector.Data.createNumberStringOrder()));
						break;
					}
				for (ValuePair valuePair:vector)
					switch (valuePair.datablock.type) {
					case String: children.add(new VDFTreeNode(this, valuePair, valuePair.label.str, valuePair.datablock.str  )); break;
					case Array : children.add(new VDFTreeNode(this, valuePair, valuePair.label.str, valuePair.datablock.array)); break;
					}
			}
			return children;
		}
		
		void forEach(ForEachAction action) {
			checkChildren("forEach(action)");
			for (VDFTreeNode child:children) {
				boolean wasProcessed = action.applyTo(child, child.type, child.name, child.value);
				if (wasProcessed) child.markAsProcessed();
			}
		}
		interface ForEachAction {
			boolean applyTo(VDFTreeNode subNode, Type type, String name, String value);
		}
		
		VDFTreeNode getSubNode(String... path) {
			return getSubNode_intern("getSubNode()", path);
		}
		private VDFTreeNode getSubNode_intern(String functionName, String... path) {
			VDFTreeNode node = this;
			int i = 0;
			while (path!=null && i<path.length) {
				if (node.valuePairArray==null)
					return null;
				
				node.markAsProcessed();
				
				String subNodeName = path[i];
				if (subNodeName==null) throw new IllegalArgumentException();
				
				node.checkChildren(functionName);
				boolean subNodeFound = false;
				for (VDFTreeNode subNode:node.children)
					if (subNodeName.equals(subNode.name)) {
						subNodeFound = true;
						node = subNode;
						i++;
						break;
					}
				if (!subNodeFound)
					return null;
			}
			return node;
		}
		
		String      getString(String... path) { return getValue(Type.String, node->node.value, "getString", path); }
		VDFTreeNode getArray (String... path) { return getValue(Type.Array , node->node      , "getArray" , path); }
		
		private <ValueType> ValueType getValue(Type type, Function<VDFTreeNode,ValueType> getValue, String functionName, String... path) {
			VDFTreeNode node = getSubNode_intern(functionName, path);
			if (node==null) return null;
			return node.getValue(type, getValue);
		}
		
//		String      getString(String name) { return getValue(name, Type.String, node->node.value, "getString"); }
//		VDFTreeNode getArray (String name) { return getValue(name, Type.Array , node->node      , "getArray" ); }
//		
//		private <ValueType> ValueType getValue(String name, Type type, Function<VDFTreeNode,ValueType> getValue, String functionName) {
//			if (name==null) return null;
//			checkChildren(String.format("%s(\"%s\")", functionName, name));
//			for (VDFTreeNode child:children) {
//				if (name.equals(child.name)) {
//					return child.getValue(type, getValue);
//				}
//			}
//			return null;
//		}
		
		private <ValueType> ValueType getValue(Type type, Function<VDFTreeNode, ValueType> getValue) {
			if (this.type==type) {
				markAsProcessed();
				return getValue.apply(this);
			} else
				return null;
		}
		
	}
}
