package net.schwarzbaer.java.tools.steaminspector;

import java.awt.Color;
import java.io.File;
import java.util.Comparator;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.tree.DefaultTreeModel;

import net.schwarzbaer.java.lib.jsonparser.JSON_Data;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.TraverseException;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.Value;
import net.schwarzbaer.java.lib.jsonparser.JSON_Parser;
import net.schwarzbaer.java.lib.jsonparser.JSON_Parser.ParseException;
import net.schwarzbaer.java.tools.steaminspector.Data.NV;
import net.schwarzbaer.java.tools.steaminspector.Data.V;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BaseTreeNode;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TreeRoot;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.DataTreeNode;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.JsonTreeIcons;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.NodeColorizer.ColorizableNode;

class JSONHelper {

	public static class FactoryForExtras implements JSON_Data.FactoryForExtras<NV,V> {
		@Override public NV createNamedValueExtra(Value.Type type) { return null; }
		@Override public V  createValueExtra     (Value.Type type) { return new V(type); }
	}

	private static Value<NV, V> parseJson(JSON_Parser<NV, V> parser) throws ParseException {
		Value<NV, V> result = parser.parse_withParseException();
		JSON_Data.traverseAllValues(result, false, null, (path,v)->v.extra.setHost(v));
		return result;
	}

	static Value<NV, V> parseJsonFile(File   file) throws ParseException { return parseJson(new JSON_Parser<>(file,new FactoryForExtras())); }

	static Value<NV, V> parseJsonText(String text) throws ParseException { return parseJson(new JSON_Parser<>(text,new FactoryForExtras())); }
	static Value<NV, V> parseJsonText(String text, String debugOutputPrefixStr) throws TraverseException {
		try {
			return JSONHelper.parseJsonText(text);
		} catch (ParseException e) {
			throw new TraverseException("%s isn't a well formed JSON text: %s", (String)debugOutputPrefixStr, e.getMessage());
		}
	}
	
	static TreeRoot createRawDataTreeRoot(Class<?> rawDataHostClass, Value<NV, V> value, boolean isLarge) {
		return createDataTreeRoot(TreeNodes.getRawDataTreeIDStr(rawDataHostClass), value, isLarge);
	}
	static TreeRoot createDataTreeRoot(Class<?> hostClass, String suffix, Value<NV, V> value, boolean isLarge) {
		return createDataTreeRoot(TreeNodes.getTreeIDStr(hostClass,suffix), value, isLarge);
	}
	static TreeRoot createDataTreeRoot(String treeIDStr, Value<NV, V> value, boolean isLarge) {
		return TreeNodes.createDataTreeRoot(JSON_TreeNode.create(null,null,value), treeIDStr, true,!isLarge);
	}

	static class JSON_TreeNode<ChildValueType> extends BaseTreeNode<JSON_TreeNode<?>,JSON_TreeNode<?>> implements DataTreeNode, ColorizableNode {

		private final Vector<ChildValueType> childValues;
		private final Function<ChildValueType, String> getChildName;
		private final Function<ChildValueType, Value<NV,V>> getChildValue;
		final String name;
		final Value<NV,V> value;
		private ChildrenOrder childrenOrder;
		private Consumer<DataTreeNode> childNodeAction;

		private JSON_TreeNode(JSON_TreeNode<?> parent, String title, JsonTreeIcons icon, String name, Value<NV,V> value, Vector<ChildValueType> childValues, Function<ChildValueType,String> getChildName, Function<ChildValueType,Value<NV,V>> getChildValue) {
			super(parent, title, childValues!=null, childValues==null || childValues.isEmpty(), icon==null ? null : icon.getIcon());
			this.name = name;
			this.value = value;
			this.childValues = childValues;
			this.getChildName = getChildName;
			this.getChildValue = getChildValue;
			this.childrenOrder = null;
			childNodeAction = null;
			if (this.value==null) throw new IllegalArgumentException("JSON_TreeNode( ... , value == null, ... ) is not allowed");
		}

		@Override public void doToAllNodesChildNodesAndFutureChildNodes(Consumer<DataTreeNode> childNodeAction) {
			this.childNodeAction = childNodeAction;
			if (this.childNodeAction!=null)
				this.childNodeAction.accept(this);
			if (children!=null)
				for (JSON_TreeNode<?> childNode:children)
					childNode.doToAllNodesChildNodesAndFutureChildNodes(this.childNodeAction);
		}

		@Override public String getPath() {
			if (parent==null) {
				if (name!=null)
					return name;
				return "<Root"+value.type+">";
			}
			Value.Type parentType = parent.getValueType();
			String indexInParent = parentType==Value.Type.Array ? "["+parent.getIndex(this)+"]" : "";
			String nameRef = name!=null ? "."+name : "";
			return parent.getPath()+indexInParent+nameRef;
		}
		
		Value<NV,V> getSubNodeValue(Object... path) {
			try {
				return JSON_Data.getSubNode(value, path);
			} catch (TraverseException e) {
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
			Value.Type parentType = parent.getValueType();
			return path + (parentType==Value.Type.Array ? parent.getIndex(this) : "\""+name+"\"");
		}

		@Override public String getName    () { return name; }
		@Override public String getValueStr() { return value.toString(); }
		private Value.Type getValueType() { return value.type; }
		@Override public boolean hasName () { return name !=null; }
		@Override public boolean hasValue() { return value!=null; }

		@Override public String getFullInfo() {
			String str = DataTreeNode.super.getFullInfo();
			str += String.format("Value Hash      : 0x%08X%n", value.hashCode());
			str += String.format("was processed   : %s%n", value.extra.wasProcessed);
			str += String.format("has unprocessed children: %s%n", value.extra.hasUnprocessedChildren());
			return str;
		}

		@Override public void setInteresting(Boolean isInteresting) {
			value.extra.isInteresting = isInteresting;
		}
		
		@Override public Boolean isInteresting() {
			Boolean value = this.value.extra.isInteresting;
			if (value==null && parent!=null)
				value = parent.isInteresting();
			return value;
		}
		@Override public boolean wasProcessed() {
			return value.extra.wasProcessed;
		}
		@Override public boolean hasUnprocessedChildren() {
			return value.extra.hasUnprocessedChildren();
		}

		@Override public boolean areChildrenSortable() { return value.type==JSON_Data.Value.Type.Object; }
		@Override public ChildrenOrder getChildrenOrder() { return childrenOrder; }
		@Override public void setChildrenOrder(ChildrenOrder childrenOrder, DefaultTreeModel currentTreeModel) {
			this.childrenOrder = childrenOrder;
			rebuildChildren(currentTreeModel);
		}

		@Override
		protected Vector<? extends JSON_TreeNode<?>> createChildren() {
			if (childValues==null) return null;
			
			Vector<ChildValueType> vector = new Vector<>(childValues);
			if (childrenOrder!=null)
				switch (childrenOrder) {
				case ByName:
					vector.sort(Comparator.comparing(getChildName,Data.createNumberStringOrder()));
					break;
				}
			
			Vector<JSON_TreeNode<?>> childNodes = new Vector<>();
			for (ChildValueType value:vector)
				childNodes.add(create(this,getChildName.apply(value),getChildValue.apply(value)));
			return childNodes;
		}
		
		@Override
		protected void doAfterCreateChildren() {
			for (JSON_TreeNode<?> child:children)
				child.doToAllNodesChildNodesAndFutureChildNodes(childNodeAction);
		}

		private static JSON_TreeNode<?> create(JSON_TreeNode<?> parent, String name, Value<NV,V> value) {
			String title = getTitle(name,value);
			JsonTreeIcons icon = getIcon(value.type);
			switch (value.type) {
			case Object: return new JSON_TreeNode<>(parent, title, icon, name, value, value.castToObjectValue().value, vt->vt.name, vt->vt.value);
			case Array : return new JSON_TreeNode<>(parent, title, icon, name, value, value.castToArrayValue ().value, vt->null, vt->vt);
			default    : return new JSON_TreeNode<>(parent, title, icon, name, value, null, null, null);
			}
		}
		
		@Override
		Color getTextColor() {
			return TreeNodes.NodeColorizer.getTextColor(this);
		}

		private static JsonTreeIcons getIcon(Value.Type type) {
			if (type==null) return null;
			switch(type) {
			case Object: return JsonTreeIcons.Object;
			case Array : return JsonTreeIcons.Array;
			case String: return JsonTreeIcons.String;
			case Bool  : return JsonTreeIcons.Boolean;
			case Null  : return JsonTreeIcons.Null;
			case Float :
			case Integer: return JsonTreeIcons.Number;
			}
			return null;
		}
		
		private static String getTitle(String name, Value<NV,V> value) {
			switch (value.type) {
			case Object : return getTitle(name, "{" , value.castToObjectValue ().value.size(), "}" );
			case Array  : return getTitle(name, "[" , value.castToArrayValue  ().value.size(), "]" );
			case String : return getTitle(name, "\"", value.castToStringValue ().value       , "\"");
			case Bool   : return getTitle(name, ""  , value.castToBoolValue   ().value       , ""  );
			case Integer: return getTitle(name, ""  , value.castToIntegerValue().value       , ""  );
			case Float  : return getTitle(name, ""  , value.castToFloatValue  ().value       , ""  );
			case Null   : return getTitle(name, ""  , value.castToNullValue   ().value       , ""  );
			}
			return null;
		}
		
		protected static String getTitle(String name, String openingBracket, Object value, String closingBracket) {
			if (name==null || name.isEmpty()) return String.format("%s%s%s", openingBracket, value, closingBracket);
			return String.format("%s : %s%s%s", name, openingBracket, value, closingBracket);
		}
	}
	
}