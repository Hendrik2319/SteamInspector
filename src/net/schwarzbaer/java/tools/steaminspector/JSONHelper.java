package net.schwarzbaer.java.tools.steaminspector;

import java.awt.Color;
import java.io.File;
import java.util.Vector;
import java.util.function.Function;

import net.schwarzbaer.java.lib.jsonparser.JSON_Data;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Array;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Object;
import net.schwarzbaer.java.lib.jsonparser.JSON_Parser;
import net.schwarzbaer.java.tools.steaminspector.Data.NV;
import net.schwarzbaer.java.tools.steaminspector.Data.V;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.BaseTreeNode;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.TreeRoot;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.DataTreeNode;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.DataTreeNodeContextMenu;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.JsonTreeIcons;

class JSONHelper {

	public static class FactoryForExtras implements JSON_Data.FactoryForExtras<NV,V> {
		@Override public NV createNamedValueExtra(JSON_Data.Value.Type type) { return null; }
		@Override public V  createValueExtra     (JSON_Data.Value.Type type) { return new V(type); }
	}

	private static JSON_Data.Value<NV, V> parseJson(JSON_Parser<NV, V> parser) throws JSON_Parser.ParseException {
		JSON_Data.Value<NV, V> result = parser.parse_withParseException();
		JSON_Data.traverseAllValues(result, null, (path,v)->v.extra.setHost(v));
		return result;
	}

	static JSON_Data.Value<NV, V> parseJsonFile(File   file) throws JSON_Parser.ParseException { return parseJson(new JSON_Parser<>(file,new FactoryForExtras())); }

	static JSON_Data.Value<NV, V> parseJsonText(String text) throws JSON_Parser.ParseException { return parseJson(new JSON_Parser<>(text,new FactoryForExtras())); }

	static JSON_Data.ArrayValue<NV, V> createArrayValue(JSON_Array<NV, V> array) {
		V extra = new V(JSON_Data.Value.Type.Array);
		JSON_Data.ArrayValue<NV, V> host = new JSON_Data.ArrayValue<>(array,extra);
		extra.setHost(host);
		return host;
	}

	static JSON_Data.ObjectValue<NV, V> createObjectValue(JSON_Object<NV, V> object) {
		V extra = new V(JSON_Data.Value.Type.Object);
		JSON_Data.ObjectValue<NV, V> host = new JSON_Data.ObjectValue<>(object,extra);
		extra.setHost(host);
		return host;
	}

	
	static TreeRoot createTreeRoot(JSON_Array<NV, V> array, boolean isLarge) {
		if (array == null) return null;
		return new TreeRoot(JSON_TreeNode.create(null,null,createArrayValue(array)),true,!isLarge,JSON_TreeNode.contextMenu);
	}
	
	static TreeRoot createTreeRoot(JSON_Object<NV, V> object, boolean isLarge) {
		if (object == null) return null;
		return new TreeRoot(JSON_TreeNode.create(null,null,createObjectValue(object)),true,!isLarge,JSON_TreeNode.contextMenu);
	}
	
	static TreeRoot createTreeRoot(JSON_Data.Value<NV, V> value, boolean isLarge) {
		return new TreeRoot(JSON_TreeNode.create(null,null,value),true,!isLarge,JSON_TreeNode.contextMenu);
	}
	
	private static final Color COLOR_WAS_PARTIALLY_PROCESSED = new Color(0xC000C0);
	private static final Color COLOR_WAS_FULLY_PROCESSED     = new Color(0x00B000);

	static Color getTextColor(JSON_Data.Value<NV, V> value) {
		if (value==null) return COLOR_WAS_FULLY_PROCESSED;
		boolean wasProcessed = value.extra.wasProcessed;
		boolean hasUnprocessedChildren = value.extra.hasUnprocessedChildren();
		return !wasProcessed ? null : hasUnprocessedChildren ? COLOR_WAS_PARTIALLY_PROCESSED : COLOR_WAS_FULLY_PROCESSED;
	}

	static class JSON_TreeNode<ChildValueType> extends BaseTreeNode<JSON_TreeNode<?>,JSON_TreeNode<?>> implements DataTreeNode {
		private static final DataTreeNodeContextMenu contextMenu = new DataTreeNodeContextMenu();

		private final Vector<ChildValueType> childValues;
		private final Function<ChildValueType, String> getChildName;
		private final Function<ChildValueType, JSON_Data.Value<NV,V>> getChildValue;
		final String name;
		final JSON_Data.Value<NV,V> value;

		private JSON_TreeNode(JSON_TreeNode<?> parent, String title, JsonTreeIcons icon, String name, JSON_Data.Value<NV,V> value, Vector<ChildValueType> childValues, Function<ChildValueType,String> getChildName, Function<ChildValueType,JSON_Data.Value<NV,V>> getChildValue) {
			super(parent, title, childValues!=null, childValues==null || childValues.isEmpty(), icon==null ? null : icon.getIcon());
			this.name = name;
			this.value = value;
			this.childValues = childValues;
			this.getChildName = getChildName;
			this.getChildValue = getChildValue;
			if (this.value==null) throw new IllegalArgumentException("JSON_TreeNode( ... , value == null, ... ) is not allowed");
		}

		@Override public String getPath() {
			if (parent==null) {
				if (name!=null)
					return name;
				return "<Root"+value.type+">";
			}
			JSON_Data.Value.Type parentType = parent.getValueType();
			String indexInParent = parentType==JSON_Data.Value.Type.Array ? "["+parent.getIndex(this)+"]" : "";
			String nameRef = name!=null ? "."+name : "";
			return parent.getPath()+indexInParent+nameRef;
		}
		
		JSON_Data.Value<NV,V> getSubNodeValue(Object... path) {
			try {
				return JSON_Data.getSubNode(value, path);
			} catch (JSON_Data.TraverseException e) {
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
		private JSON_Data.Value.Type getValueType() { return value.type; }
		@Override public boolean hasName () { return name !=null; }
		@Override public boolean hasValue() { return value!=null; }

		@Override
		protected Vector<? extends JSON_TreeNode<?>> createChildren() {
			if (childValues==null) return null;
			Vector<JSON_TreeNode<?>> childNodes = new Vector<>();
			for (ChildValueType value:childValues) childNodes.add(create(this,getChildName.apply(value),getChildValue.apply(value)));
			return childNodes;
		}
		
		private static JSON_TreeNode<?> create(JSON_TreeNode<?> parent, String name, JSON_Data.Value<NV,V> value) {
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
			return JSONHelper.getTextColor(value);
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
		
		private static String getTitle(String name, JSON_Data.Value<NV,V> value) {
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