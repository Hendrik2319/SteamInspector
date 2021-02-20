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
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.Icon;
import javax.swing.tree.DefaultTreeModel;

import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TreeRoot;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.DataTreeNode;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.NodeColorizer.ColorizableNode;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.VdfTreeIcons;

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
	
	public static Result parse(File file, Charset charset) throws VDFParseException {
		try {
			byte[] bytes = Files.readAllBytes(file.toPath());
			return parse(new String(bytes,charset));
		} catch (IOException e) {
			return null;
		}
	}

	public static Result parse(String text) throws VDFParseException {
		return new VDFParser(text).parse();
	}

	private Result parse() throws VDFParseException {
		Result data = new Result();
		
		try (LineNumberReader textIn = new LineNumberReader(new StringReader(text));) {
			textIn.setLineNumber(1);
			
			ValuePair valuePair;
			while ( (valuePair = ValuePair.read(textIn))!=null ) {
				data.add(valuePair);
			}
			
		} catch (IOException e) { // automatic close
			throw new VDFParseException(e, -1, "Closing StringReader: IOException occured.");
		}
		
		if (data.rootPairs.isEmpty())
			throw new VDFParseException(-1, "Parsed VDF File is empty.");
		
		return data;
	}

	private static class ValuePair {
		
		private final Block label;
		private final Block datablock;
		private boolean wasProcessed;
		private Boolean hasUnprocessedChildren;
		private Boolean isInteresting;
		
		private ValuePair(Block label, Block datablock) {
			if (label==null || datablock==null) throw new IllegalArgumentException("Both Blocks in a ValuePair must be non-null");
			if (label.isClosingBracketDummy() != datablock.isClosingBracketDummy()) throw new IllegalArgumentException();
			this.label = label;
			this.datablock = datablock;
			this.wasProcessed = false;
			this.hasUnprocessedChildren = this.datablock.type==Block.Type.String ? false : null;
			this.isInteresting = null;
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

		private static ValuePair read(LineNumberReader textIn) throws VDFParseException {
			return read(textIn,false);
		}
		private static ValuePair read(LineNumberReader textIn, boolean allowClosingBracket) throws VDFParseException {
			int lineNumber;
			Block block;
			
			lineNumber = textIn.getLineNumber();
			block = Block.read(textIn,allowClosingBracket);
			if (block==null)
				return null;
			if (allowClosingBracket && block.isClosingBracketDummy())
				return ValuePair.createClosingBracketDummy();
			if (block.type!=Block.Type.String)
				throw new VDFParseException(lineNumber,textIn.getLineNumber(), "Reading label of ValuePair: String Block expected. Got %s Block. [%s]", block.type, block);
			
			Block label = block;
			
			lineNumber = textIn.getLineNumber();
			block = Block.read(textIn);
			if (block==null)
				throw new VDFParseException(lineNumber,textIn.getLineNumber(), "Reading data block of ValuePair: Block expected. Got End-Of-File.");
			
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

		static Block read(LineNumberReader textIn) throws VDFParseException {
			return read(textIn, false);
		}
		static Block read(LineNumberReader textIn, boolean allowClosingBracket) throws VDFParseException {
			
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
			
			throw new VDFParseException(lineNumber,textIn.getLineNumber(), "Reading Block: String Token or OpeningBracket Token%s expected. Got %s Token. (%s)", allowClosingBracket ? " or ClosingBracket  Token" : "", token.type, token);
		}

		private static Block readArray(LineNumberReader textIn) throws VDFParseException {
			
			Vector<ValuePair> array = new Vector<>();
			ValuePair vp;
			int lineNumber = textIn.getLineNumber();
			while ( (vp=ValuePair.read(textIn,true))!=null ) {
				if (vp.isClosingBracketDummy())
					return new Block(array); // Array Block
				array.add(vp);
				lineNumber = textIn.getLineNumber();
			}
			throw new VDFParseException(lineNumber,textIn.getLineNumber(), "Reading Array Block: Reached End-Of-File before ClosingBracket Token.");
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

		static Token read(LineNumberReader textIn) throws VDFParseException {
			
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
						if (ch<0) throw new VDFParseException(textIn.getLineNumber(), "Reading String Token: Reached End-Of-File before closing \" char.");
					}
					if (ch=='/') {
						ch=textIn.read();
						if (ch==0)   throw new VDFParseException(textIn.getLineNumber(), "Reading Token: Unexpected char at end of file: \"/\" [%d]", (int)'/');
						if (ch!='/') throw new VDFParseException(textIn.getLineNumber(), "Reading Token: Unexpected chars: \"/%s\" [%d+%d]", (char)ch, (int)'/', ch);
						while ( (ch=textIn.read())>=0 && ch!='\n') {
						}
						if (ch<0) break; // End-Of-File after line comment
						continue;
					}
					throw new VDFParseException(textIn.getLineNumber(), "Reading Token: Unexpected char: \"%s\" [%d]", (char)ch, ch);
				}
			
			} catch (IOException e) {
				throw new VDFParseException(e, textIn.getLineNumber(), "Reading Token: IOException occured.");
			}
			
			return null; // End-Of-File
		}
	}
	
	static class VDFParseException extends Exception {
		private static final long serialVersionUID = 365326060770041096L;

		private VDFParseException(Throwable cause, int line, String format, Object... args) {
			super(constructMsg(line, line, format, args), cause);
		}
		private VDFParseException(int startLine, int endLine, String format, Object... args) {
			super(constructMsg(startLine, endLine, format, args));
		}
		private VDFParseException(int line, String format, Object... args) {
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

	static class VDFTraverseException extends Exception {
		private static final long serialVersionUID = 3887870847155384728L;
	
		VDFTraverseException(String format, Object...args) {
			super(String.format(Locale.ENGLISH, format, args));
		}
	}

	static class Result {
		
		private final Vector<ValuePair> rootPairs;
		
		private Result() {
			rootPairs = new Vector<>();
		}

		TreeRoot getTreeRoot(String treeIDStr, boolean isLarge) {
			return TreeNodes.createDataTreeRoot(createVDFTreeNode(), treeIDStr, false, !isLarge);
		}

		VDFTreeNode createVDFTreeNode() {
			return new VDFTreeNode(rootPairs);
		}

		private void add(ValuePair valuePair) {
			rootPairs.add(valuePair);
		}

	}
	
	static class VDFTreeNode extends SteamInspector.BaseTreeNode<VDFTreeNode,VDFTreeNode> implements DataTreeNode, ColorizableNode {
		
		enum Type { Root, String, Array }
		
		private final ValuePair base;
		private final Vector<ValuePair> valuePairArray;
		private final String name;
		private final String value;
		private final Type type;
		private ChildrenOrder childrenOrder;
		private Consumer<DataTreeNode> childNodeAction;

		private static boolean isLeaf(ValuePair base) {
			if (base                ==null) throw new IllegalArgumentException();
			if (base.datablock      ==null) throw new IllegalStateException();
			if (base.datablock.type!=Block.Type.Array) return true;
			if (base.datablock.array==null) throw new IllegalStateException();
			return base.datablock.array.isEmpty();
		}
		private static boolean allowsChildren(ValuePair base) {
			if (base==null) throw new IllegalArgumentException();
			if (base.datablock==null) throw new IllegalStateException();
			return base.datablock.type==Block.Type.Array;
		}
		private static String getTitle(ValuePair base) {
			if (base               ==null) throw new IllegalArgumentException();
			if (base.datablock     ==null) throw new IllegalStateException();
			if (base.datablock.type==null) throw new IllegalStateException();
			if (base.label         ==null) throw new IllegalStateException();
			if (base.label.type!=Block.Type.String) throw new IllegalStateException();
			if (base.label.str     ==null) throw new IllegalStateException();
			switch (base.datablock.type) {
			case Array:
				return base.label.str;
			case String:
				if (base.datablock.str==null) throw new IllegalStateException();
				return String.format("%s : \"%s\"", base.label.str, base.datablock.str);
			}
			throw new IllegalStateException();
		}
		VDFTreeNode(Vector<ValuePair> valuePairArray) { // Root
			super(null,"VDF Tree Root",true,valuePairArray==null || valuePairArray.isEmpty());
			this.base = null;
			this.childrenOrder = null;
			this.childNodeAction = null;
			this.name = null;
			this.type = Type.Root;
			this.valuePairArray = valuePairArray;
			this.value = null;
		}
		VDFTreeNode(VDFTreeNode parent, ValuePair base) {
			super(parent, getTitle(base), allowsChildren(base), isLeaf(base));
			if (base               ==null) throw new IllegalArgumentException();
			if (base.datablock     ==null) throw new IllegalStateException();
			if (base.datablock.type==null) throw new IllegalStateException();
			if (base.label         ==null) throw new IllegalStateException();
			if (base.label.type!=Block.Type.String) throw new IllegalStateException();
			if (base.label.str     ==null) throw new IllegalStateException();
			
			this.base = base;
			this.childrenOrder=null;
			this.childNodeAction = null;
			this.name = this.base.label.str;
			
			switch (this.base.datablock.type) {
			
			case Array:
				if (this.base.datablock.array==null) throw new IllegalStateException();
				this.type = Type.Array;
				this.valuePairArray = this.base.datablock.array;
				this.value = null;
				break;
				
			case String:
				if (this.base.datablock.str==null) throw new IllegalStateException();
				this.type = Type.String;
				this.valuePairArray = null;
				this.value = this.base.datablock.str;
				break;
				
			default:
				throw new IllegalStateException();

			}
		}
		
		@Override public void doToAllNodesChildNodesAndFutureChildNodes(Consumer<DataTreeNode> childNodeAction) {
			this.childNodeAction = childNodeAction;
			if (this.childNodeAction!=null)
				this.childNodeAction.accept(this);
			if (children!=null)
				for (VDFTreeNode childNode:children)
					childNode.doToAllNodesChildNodesAndFutureChildNodes(this.childNodeAction);
		}
		
		TreeRoot createRawDataTreeRoot(Class<?> rawDataHostClass) {
			return createDataTreeRoot(TreeNodes.getRawDataTreeIDStr(rawDataHostClass));
		}
		TreeRoot createDataTreeRoot(String treeIDStr) {
			return TreeNodes.createDataTreeRoot(this, treeIDStr, type!=Type.Root, true);
		}
		
		boolean is(Type type) { return this.type==type; }
		Type getType() { return type; }
		
		boolean isEmptyArray() {
			return valuePairArray==null || valuePairArray.isEmpty();
		}
		
		@Override public void setInteresting(Boolean isInteresting) {
			if (base!=null)
				base.isInteresting = isInteresting;
		}
		
		@Override public Boolean isInteresting() {
			Boolean value = base==null ? null : base.isInteresting;
			if (value==null && parent!=null)
				value = parent.isInteresting();
			return value;
		}
		
		void markAsProcessed() {
			if (base!=null)
				base.wasProcessed = true;
		}
		
		@Override public boolean wasProcessed() {
			if (base==null) return true; // Root <--> base==null
			return base.wasProcessed;
		}
		
		@Override public boolean hasUnprocessedChildren() {
			if (base==null || base.hasUnprocessedChildren==null) {
				if (base!=null) base.hasUnprocessedChildren=false;
				checkChildren("hasUnprocessedChildren()");
				for (VDFTreeNode child:children) {
					if (!child.wasProcessed() || child.hasUnprocessedChildren()) {
						if (base!=null) base.hasUnprocessedChildren=true;
						return true;
					}
				}
			}
			return base==null ? false : base.hasUnprocessedChildren;
		}
		
		@Override public String getPath() {
			if (parent==null) return "<root>";
			return parent.getPath()+/*"["+parent.getIndex(this)+"]"+*/"."+name;
		}
		
		@Override public String getAccessCall() {
			String methodName = "getSubNode";
			switch (type) {
			case Root: break;
			case Array : methodName = "getArray"; break;
			case String: methodName = "getString"; break;
			}
			return String.format("<root>.%s(%s)", methodName, getAccessPath());
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
			str += String.format("was processed   : %s%n", wasProcessed());
			str += String.format("has unprocessed children: %s%n", hasUnprocessedChildren());
			return str;
		}
		
		@Override
		Icon getIcon() {
			switch (type) {
			case Root  : return null;
			case Array : return VdfTreeIcons.Array .getIcon();
			case String: return VdfTreeIcons.String.getIcon();
			}
			return null;
		}
		@Override Color getTextColor() {
			return TreeNodes.NodeColorizer.getTextColor(this);
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
						vector.sort(Comparator.<ValuePair,String>comparing(vp->vp.label.str,Data.createNumberStringOrder()));
						break;
					}
				for (ValuePair valuePair:vector)
					children.add(new VDFTreeNode(this, valuePair));
			}
			return children;
		}
		
		@Override
		protected void doAfterCreateChildren() {
			for (VDFTreeNode child:children)
				child.doToAllNodesChildNodesAndFutureChildNodes(childNodeAction);
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
		
		boolean containsValue(String name) {
			checkChildren("containsValue(...)");
			for (VDFTreeNode child:children)
				if (name.equals(child.name))
					return true;
			return false;
		}
		
		VDFTreeNode getSubNode(String... path) throws VDFTraverseException { return getSubNode_intern("getSubNode()", false, path); }
		String      getString (String... path) throws VDFTraverseException { return getString (false, path); }
		VDFTreeNode getArray  (String... path) throws VDFTraverseException { return getArray  (false, path); }
		String      getString (boolean optionalValue, String... path) throws VDFTraverseException { return getValue(Type.String, optionalValue, node->node.value, "getString", path); }
		VDFTreeNode getArray  (boolean optionalValue, String... path) throws VDFTraverseException { return getValue(Type.Array , optionalValue, node->node      , "getArray" , path); }
		
		private <ValueType> ValueType getValue(Type type, boolean optionalValue, Function<VDFTreeNode,ValueType> getValue, String functionName, String... path) throws VDFTraverseException {
			VDFTreeNode node = getSubNode_intern(functionName, optionalValue, path);
			if (node==null) {
				if (optionalValue) return null;
				throw new IllegalStateException();
			}
			if (node.type != type)
				throw new VDFTraverseException("Node at path [%s] isn't a %s node.", node.getAccessPath(), type);
			
			node.markAsProcessed();
			return getValue.apply(node);
		}
		
		private VDFTreeNode getSubNode_intern(String functionName, boolean lastValueIsOptional, String... path) throws VDFTraverseException {
			VDFTreeNode node = this;
			int i = 0;
			while (path!=null && i<path.length) {
				if ((node.type!=Type.Array && node.type!=Type.Root) || node.valuePairArray==null)
					throw new VDFTraverseException("Node at path [%s] isn't either a Root node or an Array node.", node.getAccessPath());
				
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
				if (!subNodeFound) {
					if (i==path.length-1 && lastValueIsOptional) return null;
					throw new VDFTraverseException("Can't find subnode \"%s\" in node at path [%s].", subNodeName, node.getAccessPath());
				}
			}
			return node;
		}
		
	}
}
