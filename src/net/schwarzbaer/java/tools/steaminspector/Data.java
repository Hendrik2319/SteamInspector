package net.schwarzbaer.java.tools.steaminspector;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Vector;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.imageio.ImageIO;
import javax.swing.Icon;

import net.schwarzbaer.java.lib.gui.IconSource;
import net.schwarzbaer.java.lib.gui.ValueListOutput;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Array;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.JSON_Object;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.TraverseException;
import net.schwarzbaer.java.lib.jsonparser.JSON_Data.Value;
import net.schwarzbaer.java.lib.jsonparser.JSON_Helper;
import net.schwarzbaer.java.lib.jsonparser.JSON_Parser;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.LabeledUrl;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.MainTreeContextMenu.FilterOption;
import net.schwarzbaer.java.tools.steaminspector.SteamInspector.MainTreeContextMenu.SortOption;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.FileNameNExt;
import net.schwarzbaer.java.tools.steaminspector.TreeNodes.TreeIcons;
import net.schwarzbaer.java.tools.steaminspector.VDFParser.VDFParseException;
import net.schwarzbaer.java.tools.steaminspector.VDFParser.VDFTraverseException;
import net.schwarzbaer.java.tools.steaminspector.VDFParser.VDFTreeNode;

class Data {

	@SuppressWarnings("unused")
	private static class DevHelper {
		private static final String PACKAGE_PREFIX = "net.schwarzbaer.java.tools.steaminspector.";
		
		static OptionalValues optional = new OptionalValues();
		static class OptionalValues {
			final OptionalVdfValues                vdfValues      = new OptionalVdfValues();
			final JSON_Helper.OptionalValues<NV,V> jsonValues     = new JSON_Helper.OptionalValues<NV,V>();
			final OptionalJsonValues_Old           jsonValues_old = new OptionalJsonValues_Old();
			void clear() {
				vdfValues     .clear();
				jsonValues    .clear();
				jsonValues_old.clear();
			}
			void show(PrintStream out) {
				vdfValues     .show("Optional VDF Values",out);
				jsonValues    .show("Optional JSON Values",out);
				jsonValues_old.show("Optional JSON Values [OLD]",out);
			}
		}
		
		static class OptionalVdfValues extends HashMap<String,OptionalVdfValues.NodeTypes> {
			private static final long serialVersionUID = 4079411257348652398L;
			
			void scan(VDFTreeNode node, Class<?> dataClass, String annex) {
				scan(node, getValueLabelFor(dataClass,annex));
			}
			void scan(VDFTreeNode node, Class<?> dataClass) {
				scan(node, getValueLabelFor(dataClass));
			}
			void scan(VDFTreeNode node, String id) {
				NodeTypes nodeTypes = get(id);
				boolean nodeTypesNotNew;
				if (nodeTypes != null)
					nodeTypesNotNew = true;
				else {
					nodeTypesNotNew = false;
					put(id, nodeTypes = new NodeTypes());
				}
				scanNode(node,nodeTypes,nodeTypesNotNew);
			}

			private void scanNode(final VDFTreeNode node, final NodeTypes nodeTypes, final boolean nodeTypesNotNew) {
				if (node==null)
					nodeTypes.types.add(null);
				else {
					VDFTreeNode.Type type = node.getType();
					if (type==null) throw new IllegalStateException("VDFTreeNode.type == <null>");
					nodeTypes.types.add(type);
					switch(type) {
					case String:
						String str = node.getValue();
						if (str==null) throw new IllegalStateException("VDFTreeNode.type == String, but VDFTreeNode.value == null");
						nodeTypes.parsedValueTypes.add( NodeTypes.ParsedValueType.determine(str) );
						break;
					case Root:
					case Array:
						if (nodeTypes.arrayValues==null)
							nodeTypes.arrayValues = new HashMap<>();
						if (node.isEmptyArray())
							nodeTypes.isEmptyArrayPossible = true;
						nodeTypes.arrayValues.forEach((name,subNodeTypes)->{
							if (!node.containsValue(name))
								subNodeTypes.types.add(null);
						});
						node.forEach((subNode,t,name,v)->{
							NodeTypes subNodeTypes = nodeTypes.arrayValues.get(name);
							boolean subNodeTypesNotNew;
							if (subNodeTypes != null)
								subNodeTypesNotNew = true;
							else {
								subNodeTypesNotNew = false;
								nodeTypes.arrayValues.put(name, subNodeTypes = new NodeTypes());
								if (nodeTypesNotNew) subNodeTypes.types.add(null); // Block already exists, but this subNode is new --> subNode is optional 
							}
							scanNode(subNode,subNodeTypes,subNodeTypesNotNew);
							return false;
						});
						break;
					}
				}
			}

			void show(String label, PrintStream out) {
				if (isEmpty()) return;
				out.printf("%s: %d blocks%n", label, size());
				String indent = "    ";
				forEach_keySorted(this,(id,nodeTypes)->{
					out.printf("%sBlock \"%s\"%n", indent, id);
					nodeTypes.show(out, indent+indent, "<Base>");
				});
			}

			@Override public String toString() {
				StringBuilder sb = new StringBuilder();
				sb.append(String.format("%s {%n",getClass().getSimpleName()));
				forEach_keySorted(this,(id,nodeTypes)->{
					nodeTypes.toString(sb, "    ", id);
				});
				sb.append("}\r\n");
				return sb.toString();
			}
			
			static class NodeTypes {
				enum ParsedValueType {
					String, Bool, Integer, Long, Float;

					static ParsedValueType determine(String value) {
						if (value==null)
							throw new IllegalArgumentException("ParsedValueType.determine( null ) is not allowed");
						
						try {
							java.lang.Integer.parseInt(value);
							return Integer;
						} catch (NumberFormatException e) {}
						
						try {
							java.lang.Long.parseLong(value);
							return Long;
						} catch (NumberFormatException e) {}
						
						try {
							double d = java.lang.Double.parseDouble(value);
							if (Double.isFinite(d)) return Float;
						} catch (NumberFormatException e) {}
						
						java.lang.String value_lc = value.toLowerCase();
						if (value_lc.equals("true") || value_lc.equals("false"))
							return Bool;
						
						return String;
					}
				}
				
				
				final HashSet<VDFTreeNode.Type> types = new HashSet<>();
				final EnumSet<ParsedValueType> parsedValueTypes = EnumSet.noneOf(ParsedValueType.class);
				HashMap<String,NodeTypes> arrayValues = null;
				boolean isEmptyArrayPossible = false;
				
				void show(PrintStream out, String indent, String name) {
					out.printf("%s%s:%s%s%s%n", indent, name, getTypesStr(), isEmptyArrayPossible ? " or empty array" : "", parsedValueTypes.isEmpty() ? "" : " { Values: "+getParsedValueTypesStr()+" }");
					if (arrayValues!=null) {
						forEach_keySorted(arrayValues,(subName,subNodeTypes)->{
							String name2 = name+"."+subName;
							subNodeTypes.show(out,indent,name2);
						});
					}
				}

				void toString(StringBuilder sb, String indent, String name) {
					sb.append(String.format("%s%s:%s%s%s", indent, name, getTypesStr(), isEmptyArrayPossible ? " | empty array" : "", parsedValueTypes.isEmpty() ? "" : " | {"+getParsedValueTypesStr()+"}"));
					if (arrayValues == null)
						sb.append(String.format("%n"));
					else {
						sb.append(String.format(" {%n"));
						String indent2 = indent+"    ";
						forEach_keySorted(arrayValues,(subName,subNodeTypes)->{
							subNodeTypes.toString(sb,indent2,subName);
						});
						sb.append(String.format("%s}%n", indent));
					}
				}

				private String getTypesStr() {
					Iterable<String> it = ()->types.stream()
							.sorted(Comparator.nullsLast(Comparator.naturalOrder()))
							.map(t->t==null ? "<unset>" : t.toString())
							.iterator();
					return String.format("[%s]", String.join(", ", it));
				}

				private String getParsedValueTypesStr() {
					Iterable<String> it = ()->parsedValueTypes.stream()
							.sorted(Comparator.nullsLast(Comparator.naturalOrder()))
							.map(t->t==null ? "<null>" : t.toString())
							.iterator();
					return String.join(", ", it);
				}

				@Override public String toString() {
					StringBuilder sb = new StringBuilder();
					toString(sb, "", String.format("<%s>", getClass().getSimpleName()));
					return sb.toString();
				}
				
			}
		}
		
		static class OptionalJsonValues_Old extends HashMap<String,HashMap<String,HashSet<JSON_Data.Value.Type>>> {
			private static final long serialVersionUID = 3844179176678445499L;

			void scan(JSON_Object<NV,V> object, String prefixStr) {
				
				HashMap<String, HashSet<JSON_Data.Value.Type>> valueMap = get(prefixStr);
				boolean valueMapIsNew = false;
				if (valueMap==null) {
					valueMapIsNew = true;
					put(prefixStr, valueMap=new HashMap<>());
				}
				
				valueMap.forEach((name,types)->{
					JSON_Data.Value<NV, V> value = object.getValue(name);
					if (value==null) types.add(null);
				});
				
				for (JSON_Data.NamedValue<NV,V> nvalue:object) {
					HashSet<JSON_Data.Value.Type> types = valueMap.get(nvalue.name);
					if (types==null) {
						valueMap.put(nvalue.name, types=new HashSet<>());
						if (!valueMapIsNew) types.add(null);
					}
					types.add(nvalue.value.type);
				}
			}
			
			@Override public String toString() {
				StringBuilder sb = new StringBuilder();
				sb.append(String.format("%s {%n",getClass().getSimpleName()));
				String indent = "    ";
				forEach((blockName,valueMap)->{
					sb.append(String.format("%s%s {%n", indent, blockName));
					valueMap.forEach((valueName,types)->{
						sb.append(String.format("%s%s = %s%n", indent+indent, valueName, types));
					});
					sb.append(String.format("%s}%n", indent));
				});
				sb.append("}\r\n");
				return sb.toString();
			}

			void show(String label, PrintStream out) {
				if (isEmpty()) return;
				Vector<String> prefixStrs = new Vector<>(keySet());
				prefixStrs.sort(null);
				out.printf("%s: [%d blocks]%n", label, prefixStrs.size());
				for (String prefixStr:prefixStrs) {
					HashMap<String, HashSet<JSON_Data.Value.Type>> valueMap = get(prefixStr);
					Vector<String> names = new Vector<>(valueMap.keySet());
					names.sort(null);
					out.printf("   Block \"%s\" [%d]%n", prefixStr, names.size());
					for (String name:names) {
						HashSet<JSON_Data.Value.Type> typeSet = valueMap.get(name);
						Vector<JSON_Data.Value.Type> types = new Vector<>(typeSet);
						types.sort(Comparator.nullsLast(Comparator.naturalOrder()));
						for (JSON_Data.Value.Type type:types) {
							String comment = ":"+type;
							if (type==null ) {
								if (name.endsWith("[]"))
									comment = " is empty"; // array was empty
								else if (name.isEmpty())
									comment = " == <null>"; // base value of this block was NULL
								else
									comment = " is optional"; // sub value of an object was optional
							}
							out.printf("      %s%s%n", name, comment);
						}
					}
				}
					
			}
		}
		
		static abstract class ExtHashMap<SelfType,TypeType> extends HashMap<String,HashSet<TypeType>> {
			private static final long serialVersionUID = -3042424737957471534L;
			SelfType add(String name, TypeType type) {
				HashSet<TypeType> hashSet = get(name);
				if (hashSet==null) put(name,hashSet = new HashSet<>());
				hashSet.add(type);
				return getThis();
			}
			boolean contains(String name, TypeType type) {
				HashSet<TypeType> hashSet = get(name);
				return hashSet!=null && hashSet.contains(type);
			}
			protected abstract SelfType getThis();
		}

		static abstract class KnownValues<SelfType,NodeType,TypeType> extends ExtHashMap<SelfType,TypeType> {
			private static final long serialVersionUID = 5989432566005669294L;
			private final String defaultPrefixStr;
			
			KnownValues(Class<?> dataClass, String annex) {
				String str = getValueLabelFor(dataClass, annex);
				defaultPrefixStr = str;
			}
			
			final void scanUnexpectedValues(NodeType node) {
				if (defaultPrefixStr==null) throw new IllegalStateException("Can't call scanUnexpectedValues without prefixStr, if KnownValues was constructed without class object.");
				scanUnexpectedValues(node, defaultPrefixStr);
			}
			abstract void scanUnexpectedValues(NodeType node, String prefixStr);
		}

		static class KnownJsonValues extends KnownValues<KnownJsonValues, JSON_Object<NV,V>, JSON_Data.Value.Type> {
			private static final long serialVersionUID = 875837641187739890L;
			
			KnownJsonValues() { this(null, null); }
			KnownJsonValues(Class<?> dataClass) { this(dataClass, null); }
			KnownJsonValues(Class<?> dataClass, String annex) { super(dataClass, annex); }
			
			@Override protected KnownJsonValues getThis() { return this; }
			
			@Override void scanUnexpectedValues(JSON_Object<NV, V> object, String prefixStr) {
				for (JSON_Data.NamedValue<NV,V> nvalue:object)
					if (!contains(nvalue.name, nvalue.value.type))
						unknownValues.add(prefixStr,nvalue.name,nvalue.value.type);
			}
		}

		static class KnownVdfValues extends KnownValues<KnownVdfValues, VDFTreeNode, VDFTreeNode.Type> {
			private static final long serialVersionUID = -8137083046811709725L;
			
			KnownVdfValues() { this(null, null); }
			KnownVdfValues(Class<?> dataClass) { this(dataClass, null); }
			KnownVdfValues(Class<?> dataClass, String annex) { super(dataClass, annex); }
			
			@Override protected KnownVdfValues getThis() { return this; }
			
			@Override void scanUnexpectedValues(VDFTreeNode node, String prefixStr) {
				node.forEach((subNode,t,n,v) -> {
					if (!contains(n,t))
						unknownValues.add(prefixStr, n, t);
					return false;
				});
			}
		}

		static class KnownVdfValuesRecursive extends HashSet<String>{
			private static final long serialVersionUID = 7831756644369424798L;
			private final String defaultPrefixStr;
			
			KnownVdfValuesRecursive() { this(null,null); }
			KnownVdfValuesRecursive(Class<?> dataClass) { this(dataClass,null); }
			KnownVdfValuesRecursive(Class<?> dataClass, String annex) {
				defaultPrefixStr = getValueLabelFor(dataClass, annex);
			}
			
			KnownVdfValuesRecursive add(VDFTreeNode.Type type, String... path) {
				add(String.join(".",path)+":"+type);
				return this;
			}
			
			final void scanUnexpectedValues(VDFTreeNode node) {
				if (defaultPrefixStr==null) throw new IllegalStateException("Can't call scanUnexpectedValues without prefixStr, if KnownVdfValuesRecursive was constructed without class object.");
				scanUnexpectedValues(node, defaultPrefixStr);
			}
			
			void scanUnexpectedValues(VDFTreeNode node, String blockPrefix) {
				check(blockPrefix, "", "", node.getType());
				scanUnexpectedValues(node, "", blockPrefix);
			}

			private void scanUnexpectedValues(VDFTreeNode node, String path, String blockPrefix) {
				String pathPrefix = path + ( path.isEmpty() ? "" : "." );
				node.forEach((subNode,t,n,v) -> {
					check(blockPrefix, pathPrefix, n, t);
					switch (t) {
					case String: break;
					case Root: case Array:
						scanUnexpectedValues(subNode, pathPrefix+n, blockPrefix);
						break;
					}
					return false;
				});
				
			}

			private void check(String blockPrefix, String pathPrefix, String name, VDFTreeNode.Type type) {
				String entry = pathPrefix+name+":"+type;
				if (!contains(entry))
					unknownValues.add(String.format("%s > %s", blockPrefix, entry));
			}
		}
		
		
		static final UnknownValues unknownValues = new UnknownValues();
		static class UnknownValues extends HashSet<String> {
			private static final long serialVersionUID = 7229990445347378652L;
			
			void add(String baseLabel, String name, VDFTreeNode.Type type) {
				if (name==null) add(String.format("[VDF]%s:%s"   , baseLabel,       type==null?"<null>":type));
				else            add(String.format("[VDF]%s.%s:%s", baseLabel, name, type==null?"<null>":type));
			}
			void add(String baseLabel, String name, JSON_Data.Value.Type type) {
				if (name==null) add(String.format("[JSON]%s:%s"   , baseLabel,       type==null?"<null>":type));
				else            add(String.format("[JSON]%s.%s:%s", baseLabel, name, type==null?"<null>":type));
			}
			void show(PrintStream out) {
				if (isEmpty()) return;
				Vector<String> vec = new Vector<>(this);
				out.printf("Unknown Labels: [%d]%n", vec.size());
				vec.sort(Comparator.<String,String>comparing(String::toLowerCase).thenComparing(Comparator.naturalOrder()));
				for (String str:vec)
					out.printf("   \"%s\"%n", str);
			}
		}

		private static String getValueLabelFor(Class<?> dataClass) {
			return getValueLabelFor(dataClass, null);
		}

		private static String getValueLabelFor(Class<?> dataClass, String annex) {
			if (dataClass == null)
				return null;
			
			String str = dataClass.getCanonicalName();
			if (str==null) str = dataClass.getName();
			
			if (str.startsWith(PACKAGE_PREFIX))
				str = str.substring(PACKAGE_PREFIX.length());
			
			if (annex!=null)
				str += annex;
			
			return str;
		}

		static void scanVdfStructure(VDFTreeNode node, String nodeLabel) {
			node.forEach((node1,t,n,v) -> {
				unknownValues.add(nodeLabel, n, t);
				if (t==VDFTreeNode.Type.Array)
					scanVdfStructure(node1, nodeLabel+"."+n);
				return false;
			});
		}
		
		static void scanJsonStructure(JSON_Data.Value<NV, V> value, Class<?> dataClass, String annex) {
			scanJsonStructure(value, getValueLabelFor(dataClass, annex));
		}
		static void scanJsonStructure(JSON_Data.Value<NV, V> value, Class<?> dataClass) {
			scanJsonStructure(value, getValueLabelFor(dataClass, null));
		}
		static void scanJsonStructure(JSON_Data.Value<NV, V> value, String valueLabel) {
			scanJsonStructure(value, valueLabel, false);
		}
		static void scanJsonStructure(JSON_Data.Value<NV, V> value, String valueLabel, boolean scanOptionalValues) {
			if (value==null) { unknownValues.add(valueLabel+" = <null>"); return; }
			unknownValues.add(valueLabel+":"+value.type);
			switch (value.type) {
			case Bool: case Float: case Integer: case Null: case String: break;
			case Object:
				JSON_Data.ObjectValue<NV,V> objectValue = value.castToObjectValue();
				if (objectValue==null)
					unknownValues.add(valueLabel+":"+value.type+" is not instance of JSON_Data.ObjectValue");
				else 
					scanJsonStructure(objectValue.value, valueLabel, scanOptionalValues);
				break;
			case Array:
				JSON_Data.ArrayValue<NV,V> arrayValue = value.castToArrayValue();
				if (arrayValue==null)
					unknownValues.add(valueLabel+":"+value.type+" is not instance of JSON_Data.ArrayValue");
				else
					scanJsonStructure(arrayValue.value, valueLabel, scanOptionalValues);
				break;
			}
		}
	
		static void scanJsonStructure(JSON_Object<NV,V> object, Class<?> dataClass, String annex) {
			scanJsonStructure(object, getValueLabelFor(dataClass, annex));
		}
		static void scanJsonStructure(JSON_Object<NV,V> object, Class<?> dataClass) {
			scanJsonStructure(object, getValueLabelFor(dataClass, null));
		}
		static void scanJsonStructure(JSON_Object<NV,V> object, String valueLabel) {
			scanJsonStructure(object, valueLabel, false);
		}
		static void scanJsonStructure(JSON_Object<NV,V> object, String valueLabel, boolean scanOptionalValues) {
			if (object==null)
				unknownValues.add(valueLabel+" (JSON_Object == <null>)");
			else {
				if (scanOptionalValues)
					optional.jsonValues_old.scan(object, valueLabel);
				for (JSON_Data.NamedValue<NV, V> nval:object)
					scanJsonStructure(nval.value, valueLabel+"."+(nval.name==null?"<null>":nval.name), scanOptionalValues);
			}
		}
	
		static void scanJsonStructure(JSON_Array<NV,V> array, Class<?> dataClass, String annex) {
			scanJsonStructure(array, getValueLabelFor(dataClass, annex));
		}
		static void scanJsonStructure(JSON_Array<NV,V> array, Class<?> dataClass) {
			scanJsonStructure(array, getValueLabelFor(dataClass, null));
		}
		static void scanJsonStructure(JSON_Array<NV,V> array, String valueLabel) {
			scanJsonStructure(array, valueLabel, false);
		}
		static void scanJsonStructure(JSON_Array<NV,V> array, String valueLabel, boolean scanOptionalValues) {
			if (array==null)
				unknownValues.add(valueLabel+" (JSON_Array == <null>)");
			else
				for (JSON_Data.Value<NV,V> val:array)
					scanJsonStructure(val, valueLabel+"[]", scanOptionalValues);
		}
	
		static void scanJsonStructure_OAO(
				JSON_Data.Value<NV,V> baseValue,
				String baseValueLabel,
				String subArrayName,
				Vector<String> knownValueNames,
				Vector<String> knownSubArrayValueNames,
				String errorPrefix,
				File file
		) {
			JSON_Object<NV,V> object = null;
			try { object = JSON_Data.getObjectValue(baseValue, errorPrefix); }
			catch (TraverseException e) { showException(e, file); }
			if (object!=null) {
				for (JSON_Data.NamedValue<NV,V> nvalue:object) {
					String valueStr = nvalue.value.type+"...";
					if (!knownValueNames.contains(nvalue.name)) valueStr = nvalue.value.toString();
					unknownValues.add(baseValueLabel+"."+nvalue.name+" = "+valueStr);
					if (subArrayName.equals(nvalue.name)) {
						JSON_Array<NV,V> array = null;
						try { array = JSON_Data.getArrayValue(nvalue.value, errorPrefix+"."+subArrayName); }
						catch (TraverseException e) { showException(e, file); }
						if (array!=null) {
							for (int i=0; i<array.size(); i++) {
								JSON_Object<NV, V> object1 = null;
								try { object1 = JSON_Data.getObjectValue(array.get(i), errorPrefix+"."+subArrayName+"["+i+"]"); }
								catch (TraverseException e) { showException(e, file); }
								if (object1!=null) {
									for (JSON_Data.NamedValue<NV, V> nvalue1:object1) {
										valueStr = nvalue1.value.type+"...";
										if (!knownSubArrayValueNames.contains(nvalue1.name)) valueStr = nvalue1.value.toString();
										unknownValues.add(baseValueLabel+"."+"rgCards."+nvalue1.name+" = "+valueStr);
									}
								}
							}
						}
					}
				}
			}
		}
	
		static Vector<String> strList(String...strings) {
			return new Vector<>(Arrays.asList(strings));
		}
	}
	
	static class NV extends JSON_Data.NamedValueExtra.Dummy {}
	static class V implements JSON_Data.ValueExtra {
		
		public final JSON_Data.Value.Type type;
		public JSON_Data.Value<NV,V> host;
		public boolean wasProcessed;
		public Boolean hasUnprocessedChildren;
		public Boolean isInteresting;
		
		public V(JSON_Data.Value.Type type) {
			this.type = type;
			this.host = null; 
			wasProcessed = false;
			hasUnprocessedChildren = type!=null && type.isSimple ? false : null;
			isInteresting = null;
		}
		void setHost(JSON_Data.Value<NV,V> host) {
			this.host = host;
			if (this.host==null) new IllegalArgumentException("Host must not be <null>");
			if (this.host.type!=type) new IllegalArgumentException("Host has wrong type: "+this.host.type+"!="+type);
		}
		
		@Override public void markAsProcessed() {
			wasProcessed = true;
		}
		
		public boolean hasUnprocessedChildren() {
			// ArrayValue   @Override public boolean hasUnprocessedChildren() { return JSON_hasUnprocessedChildren(this,this.value, v-> v      ); }
			// ObjectValue  @Override public boolean hasUnprocessedChildren() { return JSON_hasUnprocessedChildren(this,this.value,nv->nv.value); }
			if (type==JSON_Data.Value.Type.Array ) {
				if (host                   ==null) throw new IllegalStateException();
				if (host.castToArrayValue()==null) throw new IllegalStateException();
				return hasUnprocessedChildren(host,host.castToArrayValue ().value, v-> v      );
			}
			if (type==JSON_Data.Value.Type.Object) {
				if (host                    ==null) throw new IllegalStateException();
				if (host.castToObjectValue()==null) throw new IllegalStateException();
				return hasUnprocessedChildren(host,host.castToObjectValue().value,nv->nv.value);
			}
			return false;
		}
		
		private static <ChildType> boolean hasUnprocessedChildren(JSON_Data.Value<NV,V> baseValue, Vector<ChildType> children, Function<ChildType,JSON_Data.Value<NV,V>> getValue) {
			if (baseValue.extra.hasUnprocessedChildren!=null) return baseValue.extra.hasUnprocessedChildren;
			baseValue.extra.hasUnprocessedChildren=false;
			for (ChildType child:children) {
				JSON_Data.Value<NV,V> childValue = getValue.apply(child);
				if (!childValue.extra.wasProcessed || childValue.extra.hasUnprocessedChildren()) {
					baseValue.extra.hasUnprocessedChildren=true;
					break;
				}
			}
			return baseValue.extra.hasUnprocessedChildren;
		}
	}

	static void showException(JSON_Data.TraverseException e, File file) { showException("JSON_TraverseException"    , e, file); }
	static void showException(JSON_Parser.ParseException  e, File file) { showException("JSON_Parser.ParseException", e, file); }
	static void showException(VDFParseException           e, File file) { showException("VDFParseException"         , e, file); }
	static void showException(VDFTraverseException        e, File file) { showException("VDFTraverseException"      , e, file); }
	static void showException(Base64Exception             e, File file) { showException("Base64Exception"           , e, file); }
	static void showException(String prefix, Throwable e, File file) {
		showException(prefix, e.getMessage(), file);
	}
	static void showException(String prefix, String message, File file) {
		String str = String.format("%s: %s%n", prefix, message);
		if (file!=null) str += String.format("   in File \"%s\"%n", file.getAbsolutePath());
		System.err.print(str);
	}
	
	static final KnownGameTitles knownGameTitles = new KnownGameTitles();
	static class KnownGameTitles extends HashMap<Integer,String> {
		private static final long serialVersionUID = 2599578502526459790L;
		
		void readFromFile() {
			File file = new File(SteamInspector.KNOWN_GAME_TITLES_INI);
			try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
				
				System.out.printf("Read known game titles from file \"%s\" ...%n", file.getAbsolutePath());
				clear();
				String line;
				while ( (line=in.readLine())!=null ) {
					int pos = line.indexOf('=');
					if (pos>0) {
						String gameIDStr = line.substring(0, pos);
						String gameTitle = line.substring(pos+1);
						Integer gameID = parseNumber(gameIDStr);
						if (gameID!=null)
							put(gameID,gameTitle);
					}
				}
				System.out.printf("... done%n");
				
			} catch (FileNotFoundException e) {
			} catch (IOException e) {
				System.err.printf("IOException while reading KnownGameTitles from file: %s%n", SteamInspector.KNOWN_GAME_TITLES_INI);
				e.printStackTrace();
			}
		}
		void writeToFile() {
			File file = new File(SteamInspector.KNOWN_GAME_TITLES_INI);
			System.out.printf("Write known game titles to file \"%s\" ...%n", file.getAbsolutePath());
			try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
				
				Vector<Integer> gameIDs = new Vector<>(this.keySet());
				gameIDs.sort(null);
				for (Integer gameID:gameIDs) {
					String title = get(gameID);
					if (gameID!=null && title!=null && !title.isEmpty())
						out.printf("%s=%s%n", gameID, title);
				}
				
			} catch (FileNotFoundException e) {}
			System.out.printf("... done%n");
		}
	}

	static final HashMap<Integer,Game> games = new HashMap<>();
	static final HashMap<Long,Player> players = new HashMap<>();
	
	static boolean isEmpty() {
		return games.isEmpty() && players.isEmpty();
	}
	
	static void loadData() {
		DevHelper.unknownValues.clear();
		DevHelper.optional.clear();
		knownGameTitles.readFromFile();
		
		/*
		File scanTestFile = new File(SteamInspector.FOLDER_TEST_FILES,"scanTest.json");
		if (scanTestFile.isFile()) {
			JSON_Data.Value<NV, V> result;
			try { result = JSONHelper.parseJsonFile(scanTestFile); }
			catch (JSON_Parser.ParseException e) { showException(e, scanTestFile); result=null; }
			
			if (result!=null) {
				// TO-DO: ScanTest
				DevHelper.optionalValuesRe.scan(result, "<BaseValue>ScanTest");
				JSON_Data.ObjectValue<NV, V> objectValue = result.castToObjectValue();
				if (objectValue!=null && objectValue.value!=null) {
					JSON_Object<NV, V> object = objectValue.value;
					DevHelper.optionalValuesRe.scan(object, "<Object>ScanTest");
					try {
						JSON_Array<NV, V> array = JSON_Data.getArrayValue(object, "array", "ScanTest");
						DevHelper.optionalValuesRe.scan(array, "<Array>ScanTest.array");
					} catch (TraverseException e) {
						showException(e, scanTestFile);
					}
					DevHelper.optionalValues.scan(object, "[OLD]ScanTest<Object>[OLD]");
				}
				DevHelper.scanJsonStructure(result, "[OLD]ScanTest<Value>[OLD]", true);
			}
		}
		*/
		
		File folder = SteamInspector.KnownFolders.getSteamClientSubFolder(SteamInspector.KnownFolders.SteamClientSubFolders.APPCACHE_LIBRARYCACHE);
		GameImages gameImages = null;
		if (folder!=null && folder.isDirectory())
			gameImages = new GameImages(folder);
		
		HashMap<Integer,AppManifest> appManifests = new HashMap<>();
		SteamInspector.KnownFolders.forEachSteamAppsFolder((i,f)->{
			if (f!=null && f.isDirectory()) {
				File[] files = f.listFiles(file->AppManifest.getAppIDFromFile(file)!=null);
				for (File file:files) {
					Integer appID = AppManifest.getAppIDFromFile(file);
					appManifests.put(appID, new AppManifest(appID,file));
				}
			}
		});
		
		players.clear();
		folder = SteamInspector.KnownFolders.getSteamClientSubFolder(SteamInspector.KnownFolders.SteamClientSubFolders.USERDATA);
		if (folder!=null) {
			File[] files = folder.listFiles(file->file.isDirectory() && parseLongNumber(file.getName())!=null);
			for (File playerFolder:files) {
				Long playerID = parseLongNumber(playerFolder.getName());
				if (playerID==null) throw new IllegalStateException();
				players.put(playerID, new Player(playerID,playerFolder));
			}
		}
		
		// collect all AppIDs
		HashSet<Integer> idSet = new HashSet<>();
		idSet.addAll(appManifests.keySet());
		if (gameImages!=null) idSet.addAll(gameImages.getGameIDs());
		players.forEach((playerID,player)->player.forEachGameIDSet(idSet::addAll));
		
		players.forEach((Long playerID,Player player)->{
			player.gameInfos.forEach((gameID,gameInfos)->{
				if (gameInfos.workshop!=null && gameInfos.workshop.entries!=null) {
					gameInfos.workshop.entries.forEach(entry->{
						long appID_L = entry.consumer_appid;
						String appName = entry.app_name;
						if (0<appID_L && appID_L<0x7FFFFFFFL && appName!=null && !appName.isEmpty()) {
							int appID = (int)appID_L;
							if (!knownGameTitles.containsKey(appID)) {
								knownGameTitles.put(appID, appName);
							}
						}
					});
				}
			});
		});
		
		games.clear();
		for (Integer appID:idSet) {
			HashMap<String, File> imageFiles = gameImages==null ? null : gameImages.getImageFileMap(appID);
			AppManifest appManifest = appManifests.get(appID);
			games.put(appID, new Game(appID, appManifest, imageFiles, players));
		}
		
		for (Game game:games.values())
			if (game.title!=null)
				knownGameTitles.put(game.appID, game.title);
		
		knownGameTitles.writeToFile();
		DevHelper.unknownValues.show(System.err);
		DevHelper.optional.show(System.err);
	}
	static String getPlayerName(Long playerID) {
		return Player.getName(playerID);
	}
	static Icon getGameIcon(Integer gameID, TreeIcons defaultIcon) {
		return Game.getIcon(gameID, defaultIcon);
	}
	static String getGameTitle(Integer gameID) {
		return Game.getTitle(gameID);
	}
	static String getGameTitle(Integer gameID, boolean attachGameID) {
		return Game.getTitle(gameID, attachGameID);
	}
	static boolean hasGameATitle(Integer gameID) {
		return Game.hasGameATitle(gameID);
	}
	static Comparator<String> createNumberStringOrder() {
		return Comparator.<String,Long>comparing(str->parseLongNumber(str), Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Comparator.naturalOrder());
	}
	static Comparator<Integer> createGameIdOrder() {
		//return Comparator.<Integer,Game>comparing(games::get,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Comparator.naturalOrder());
		
		Function<Integer,String> getTitle = gameID->{ Game game = games.get(gameID); return game==null ? null : game.title; };
		return Comparator.<Integer,String>comparing(getTitle,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Comparator.naturalOrder());
		
		//return Comparator.<Integer,Boolean>comparing(id->!hasGameATitle(id)).thenComparing(Comparator.naturalOrder());
	}
	static <ValueType> Comparator<Map.Entry<Integer,ValueType>> createGameIdKeyOrder() {
		return Comparator.comparing(Map.Entry<Integer,ValueType>::getKey, createGameIdOrder());
	}
	static <ValueType> Comparator<Map.Entry<Long,ValueType>> createPlayerIdKeyOrder() {
		return Comparator.comparing(Map.Entry<Long,ValueType>::getKey);
	}
	
	static <ValueType> void forEach_keySorted(Map<String,ValueType> map, BiConsumer<String,ValueType> action) {
		if (map==null || action==null) throw new IllegalArgumentException();
		Vector<String> keys = new Vector<>(map.keySet());
		keys.sort(Comparator.comparing(String::toLowerCase));
		for (String key:keys)
			action.accept(key, map.get(key));
	}
	
	static Integer parseNumber(String str) {
		if (str==null) return null;
		try {
			int n = Integer.parseInt(str);
			if (str.equals(Integer.toString(n))) return n;
		}
		catch (NumberFormatException e) {}
		return null;
	}
	
	static Long parseLongNumber(String str) {
		if (str==null) return null;
		try {
			long n = Long.parseLong(str);
			if (str.equals(Long.toString(n))) return n;
		}
		catch (NumberFormatException e) {}
		return null;
	}
	
	static class Base64String {
		final String string;
		final byte[] bytes;
		
		private Base64String(String string, byte[] bytes) {
			this.string = string;
			this.bytes = bytes;
		}

		static Base64String parse(String string, File file) {
			if (string==null) throw new IllegalArgumentException();
			byte[] bytes;
			try {
				bytes = parseBase64(string);
			} catch (Base64Exception e) {
				showException(e, file);
				bytes = null;
			}
			return new Base64String(string,bytes);
		}

		boolean isEmpty() {
			return (string==null || string.isEmpty()) && (bytes==null || bytes.length==0);
		}

		static String toString(Base64String value) {
			if (value==null) return "<null>";
			
			String str = "";
			str += "Original Text:\r\n";
			str += String.format("\"%s\"%n", value.string);
			str += "\r\n";
			str += "Decoded Bytes:\r\n";
			if (value.bytes==null)
				str += "   <Decode Error>\r\n";
			else
				str += SteamInspector.toHexTable(value.bytes,-1);
			
			
			return str;
		}
	}
	
	private static class Base64Exception extends Exception {
		private static final long serialVersionUID = 8633426460640897788L;

		public Base64Exception(IllegalArgumentException e) {
			super(e);
		}
	}
	
	private static byte[] parseBase64(String str) throws Base64Exception {
		try {
			return Base64.getDecoder().decode(str);
		} catch (IllegalArgumentException e) {
			throw new Base64Exception(e);
		}
	}
	
	private static <ClassType> Vector<ClassType> parseArray(
			ParseConstructor1<ClassType> parseConstructor,
			Function<JSON_Data.Value<NV,V>,ClassType> rawDataConstructor,
			JSON_Object<NV, V> object, String subValueName,
			String debugOutputPrefixStr, File file
	) throws TraverseException {
		JSON_Array<NV, V> array = JSON_Data.getArrayValue(object, subValueName, debugOutputPrefixStr);
		return parseArray(parseConstructor, rawDataConstructor, array, debugOutputPrefixStr+"."+subValueName, file);
	}
	
	@SuppressWarnings("unused")
	private static <ClassType> Vector<ClassType> parseArray(
			ParseConstructor2<ClassType> parseConstructor,
			Function<JSON_Data.Value<NV,V>,ClassType> rawDataConstructor,
			JSON_Object<NV, V> object, String subValueName,
			String debugOutputPrefixStr, File file
	) throws TraverseException {
		JSON_Array<NV, V> array = JSON_Data.getArrayValue(object, subValueName, debugOutputPrefixStr);
		return parseArray(parseConstructor, rawDataConstructor, array, debugOutputPrefixStr+"."+subValueName, file);
	}
	
	private static <ClassType> Vector<ClassType> parseArray(
			ParseConstructor1<ClassType> parseConstructor,
			Function<JSON_Data.Value<NV,V>,ClassType> rawDataConstructor,
			JSON_Data.Value<NV, V> value, String debugOutputPrefixStr, File file
	) throws TraverseException {
		JSON_Array<NV, V> array = JSON_Data.getArrayValue(value, debugOutputPrefixStr);
		return parseArray(parseConstructor, rawDataConstructor, array, debugOutputPrefixStr, file);
	}
	
	private static <ClassType> Vector<ClassType> parseArray(
			ParseConstructor2<ClassType> parseConstructor,
			Function<JSON_Data.Value<NV,V>,ClassType> rawDataConstructor,
			JSON_Data.Value<NV, V> value, String debugOutputPrefixStr, File file
	) throws TraverseException {
		JSON_Array<NV, V> array = JSON_Data.getArrayValue(value,  debugOutputPrefixStr);
		return parseArray(parseConstructor, rawDataConstructor, array, debugOutputPrefixStr, file);
	}

	private static <ClassType> Vector<ClassType> parseArray(
			ParseConstructor1<ClassType> parseConstructor,
			Function<JSON_Data.Value<NV,V>,ClassType> rawDataConstructor,
			JSON_Array<NV, V> rawArray, String debugOutputPrefixStr, File file
	) {
		if (rawArray==null) return null;
		Vector<ClassType> array = new Vector<>();
		for (int i=0; i<rawArray.size(); i++)
			array.add(parse(parseConstructor, rawDataConstructor, rawArray.get(i), debugOutputPrefixStr+"["+i+"]", file));
		return array;
	}

	private static <ClassType> Vector<ClassType> parseArray(
			ParseConstructor2<ClassType> parseConstructor,
			Function<JSON_Data.Value<NV,V>,ClassType> rawDataConstructor,
			JSON_Array<NV, V> rawArray, String debugOutputPrefixStr, File file
	) {
		if (rawArray==null) return null;
		Vector<ClassType> array = new Vector<>();
		for (int i=0; i<rawArray.size(); i++)
			array.add(parse(parseConstructor, rawDataConstructor, rawArray.get(i), debugOutputPrefixStr+"["+i+"]", file));
		return array;
	}
	
	private static <ClassType> ClassType parse(
			ParseConstructor1<ClassType> parseConstructor,
			Function<JSON_Data.Value<NV,V>,ClassType> rawDataConstructor,
			JSON_Data.Value<NV, V> value, String debugOutputPrefixStr, File file
	) {
		try {
			return parseConstructor.parse(value, debugOutputPrefixStr);
		} catch (TraverseException e) {
			showException(e, file);
			return rawDataConstructor.apply(value);
		}
	}
	
	private static <ClassType> ClassType parse(
			ParseConstructor2<ClassType> parseConstructor,
			Function<JSON_Data.Value<NV,V>,ClassType> rawDataConstructor,
			JSON_Data.Value<NV, V> value, String debugOutputPrefixStr, File file
	) {
		try {
			return parseConstructor.parse(value, debugOutputPrefixStr, file);
		} catch (TraverseException e) {
			showException(e, file);
			return rawDataConstructor.apply(value);
		}
	}
	
	private interface ParseConstructor1<ClassType> {
		ClassType parse(JSON_Data.Value<NV, V> value, String debugOutputPrefixStr) throws TraverseException;
	}
	private interface ParseConstructor2<ClassType> {
		ClassType parse(JSON_Data.Value<NV, V> value, String debugOutputPrefixStr, File file) throws TraverseException;
	}
	
	static LabeledUrl getShopURL(int appID) { return getShopURL(""+appID); }
	static LabeledUrl getShopURL(String appID) { return getGameURL(GameURL.ShopPage,appID); }
	
	enum GameURL { ShopPage, NewsHub, Community, Workshop, Discussions, Guides }
	static LabeledUrl getGameURL(GameURL type, String appID) {
		switch (type) {
		case ShopPage   : return new LabeledUrl("Shop Page", "https://store.steampowered.com/app/"+appID+"/");
		case Community  : return new LabeledUrl("Shop Page", "https://steamcommunity.com/app/"+appID);
		case Discussions: return new LabeledUrl("Shop Page", "https://steamcommunity.com/app/"+appID+"/discussions/");
		case Guides     : return new LabeledUrl("Shop Page", "https://steamcommunity.com/app/"+appID+"/guides/");
		case Workshop   : return new LabeledUrl("Shop Page", "https://steamcommunity.com/app/"+appID+"/workshop/");
		case NewsHub    : return new LabeledUrl("Shop Page", "https://store.steampowered.com/news/app/"+appID);
		}
		return null;
	}
	
	static LabeledUrl getWorkshopItemURL(String id) {
		return new LabeledUrl("Workshop Item", "https://steamcommunity.com/sharedfiles/filedetails/?id="+id);
	}
	static LabeledUrl getSteamPlayerProfileURL(long playerID) {
		return getSteamPlayerProfileURL(""+SteamId.getFullSteamID(playerID));
	}
	static LabeledUrl getSteamPlayerProfileURL(String fullSteamID) {
		return new LabeledUrl("Steam Player Profile", "https://steamcommunity.com/profiles/"+fullSteamID+"/");
	}
	
	static class SteamId {
		
		final String str;
		final Long steamid;
		
		private SteamId(String str, Long steamid) {
			this.str = str;
			this.steamid = steamid;
			
		}
		
		static SteamId parse(String steamidStr) {
			return new SteamId(steamidStr,parseLongNumber(steamidStr));
		}
		
		boolean isPlayer() {
			if (steamid==null) return false;
			if (steamid>>32 != 0x01100001L && steamid>>32 != 0) return false;
			if ((steamid & 0xFFFFFFFFL) == 0) return false;
			return true;
		}

		String getPlayerName() {
			if (steamid==null)
				return String.format("Player [%s]", str);
			
			String playerName = Data.getPlayerName(steamid.longValue()&0xFFFFFFFFL);
			return String.format("%s [%s]", playerName, str);
		}

		static long getFullSteamID(long playerID) {
			return (playerID & 0xFFFFFFFFL) | 0x0110000100000000L;
		}
	}

	static class Player {
		
		final long playerID;
		final File folder;
		final File configFolder;
		final File gameStateFolder;
		final HashMap<Integer, File> steamCloudFolders;
		final ScreenShotLists screenShots;
		final LocalConfig localconfig;
		final HashMap<Integer,GameInfos> gameInfos;
		final AchievementProgress achievementProgress;

		Player(long playerID, File folder) {
			this.playerID = playerID;
			this.folder = folder;
			
			File[] gameNumberFolders = folder.listFiles(file->file.isDirectory() && parseNumber(file.getName())!=null);
			steamCloudFolders = new HashMap<Integer,File>();
			for (File subFolder:gameNumberFolders) {
				String name = subFolder.getName();
				if (!name.equals("7") && !name.equals("760")) {
					Integer gameID = parseNumber(name);
					steamCloudFolders.put(gameID, subFolder);
				}
			}
			File subFolder;
			
			// Folders
			subFolder = new File(folder,"760");
			screenShots = !subFolder.isDirectory() ? null : new ScreenShotLists(subFolder);
			
			subFolder = new File(folder,"config");
			if (subFolder.isDirectory()) {
				configFolder = subFolder;
			} else
				configFolder = null;
			
			if (configFolder!=null) {
				File localconfigFile = new File(configFolder,"localconfig.vdf");
				if (localconfigFile.isFile())
					localconfig = new LocalConfig(localconfigFile,playerID);
				else
					localconfig = null;
			} else
				localconfig = null;
			
			
			// localconfig
			
			gameInfos = new HashMap<>();
			AchievementProgress preAchievementProgress = null;
			if (configFolder!=null) {
				File gameStateFolder = new File(configFolder,"librarycache");
				if (gameStateFolder.isDirectory()) {
					this.gameStateFolder = gameStateFolder;
					File[] files = gameStateFolder.listFiles(file->file.isFile());
					for (File file:files) {
						FileNameNExt fileNameNExt = FileNameNExt.create(file.getName());
						if (fileNameNExt.extension!=null && fileNameNExt.extension.equalsIgnoreCase("json")) {
							Integer gameID;
							if (fileNameNExt.name.equalsIgnoreCase("achievement_progress")) {
								// \config\librarycache\achievement_progress.json
								JSON_Data.Value<NV, V> result = null;
								try { result = JSONHelper.parseJsonFile(file); }
								catch (JSON_Parser.ParseException e) { showException(e, file); }
								if (result!=null)
									try {
										preAchievementProgress = new AchievementProgress(file,result,"AchievementProgress");
									} catch (TraverseException e) {
										showException(e, file);
										preAchievementProgress = new AchievementProgress(file,result);
									}
								
							} else if ((gameID=parseNumber(fileNameNExt.name))!=null) {
								// \config\librarycache\1465680.json
								JSON_Data.Value<NV, V> result = null;
								try { result = JSONHelper.parseJsonFile(file); }
								catch (JSON_Parser.ParseException e) { showException(e, file); }
								if (result!=null)
									try {
										gameInfos.put(gameID, new GameInfos(playerID,gameID,file,result,"GameInfos"));
									} catch (TraverseException e) {
										showException(e, file);
										gameInfos.put(gameID, new GameInfos(playerID,gameID,file,result));
									}
							}
						}
					}
				} else
					this.gameStateFolder = null;
			} else
				this.gameStateFolder = null;
			achievementProgress = preAchievementProgress;
			
		}

		void forEachGameIDSet(Consumer<Collection<Integer>> action) {
			action.accept(steamCloudFolders.keySet());
			if (screenShots!=null)
				action.accept(screenShots.keySet());
			action.accept(gameInfos.keySet());
			if (achievementProgress!=null && achievementProgress.hasParsedData)
				action.accept(achievementProgress.gameStates.keySet());
		}

		long getSteamID() {
			return SteamId.getFullSteamID(playerID);
		}

		Integer getPlayerLevel() {
			if (localconfig!=null && localconfig.softwareValveSteamApps!=null)
				return localconfig.softwareValveSteamApps.playerLevel;
			return null;
		}

		static String getName(Long playerID) {
			if (playerID==null) return "Player ???";
			Player player = players.get(playerID);
			if (player==null) return "Player "+playerID;
			return player.getName(false);
		}
		
		String getName(boolean addPlayerID) {
			if (localconfig!=null && localconfig.friendList!=null && localconfig.friendList.personaName!=null) {
				return localconfig.friendList.personaName + (addPlayerID? "  ["+playerID+"]" : "");
			}
			return "Player "+playerID;
		}

		static class LocalConfig {
		
			final long playerID;
			final File file;
			final VDFTreeNode vdfTreeNode;
			final FriendList friendList;
			final SoftwareValveSteamApps softwareValveSteamApps;
		
			public LocalConfig(File file, long playerID) {
				if (file==null || !file.isFile())
					throw new IllegalArgumentException();
				this.playerID = playerID;
				this.file = file;
				
				VDFParser.Result parseResult = null;
				try { parseResult = VDFParser.parse(this.file,StandardCharsets.UTF_8); }
				catch (VDFParseException e) { showException(e, this.file); }
				vdfTreeNode = parseResult!=null ? parseResult.createVDFTreeNode() : null;
				
				if (vdfTreeNode!=null) {
					friendList = parseSubNode(
							FriendList::new,FriendList::new,
							"LocalConfig[Player "+playerID+"].FriendList",
							file,vdfTreeNode,
							"UserLocalConfigStore","friends"
					);
					softwareValveSteamApps = parseSubNode(
							SoftwareValveSteamApps::new,SoftwareValveSteamApps::new,
							"LocalConfig[Player "+playerID+"].SoftwareValveSteamApps",
							file,vdfTreeNode,
							"UserLocalConfigStore","Software","Valve","Steam"
					);
					
				} else {
					friendList = null;
					softwareValveSteamApps = null;
				}
			}
			
			interface VDFParseConstructor<BlockClass> {
				BlockClass parse(VDFTreeNode blockNode, String debugOutputPrefixStr, File file) throws VDFTraverseException;
			}
			
			static <BlockClass> BlockClass parseSubNode(VDFParseConstructor<BlockClass> parseFunction, Function<VDFTreeNode,BlockClass> rawDataConstructor, String debugOutputPrefixStr, File file, VDFTreeNode baseTreeNode, String... path) {
				VDFTreeNode blockNode;
				try {
					blockNode = baseTreeNode.getSubNode(path);
				} catch (VDFTraverseException e) {
					showException(e, file);
					blockNode = null;
				}
				
				if (blockNode == null)
					return null;
				
				try {
					return parseFunction.parse(blockNode,debugOutputPrefixStr,file);
				} catch (VDFTraverseException e) {
					showException(e, file);
					return rawDataConstructor.apply(blockNode);
				}
			}
			
			private static class ValueContainer<ValueType> {
				ValueType value = null;
			}

			static class SoftwareValveSteamApps {
				
				final VDFTreeNode rawData;
				final boolean hasParsedData;
				
				final Vector<AppData> apps;
				final HashMap<Integer,AppData> appsWithID;
				final Vector<AppData> appsWithoutID;
				final String  str_playerLevel;
				final String  str_lastSyncTime;
				final Integer playerLevel;
				final Long    lastSyncTime_ks;

				SoftwareValveSteamApps(VDFTreeNode rawData) {
					this.rawData = rawData;
					hasParsedData = false;
					
					apps = null;
					appsWithID = null;
					appsWithoutID = null;
					str_playerLevel  = null;
					str_lastSyncTime = null;
					playerLevel      = null;
					lastSyncTime_ks  = null;
				}
				
				SoftwareValveSteamApps(VDFTreeNode blockNode, String debugOutputPrefixStr, File file) throws VDFTraverseException {
					this.rawData = null;
					hasParsedData = true;
					
					if ( blockNode==null) throw new VDFTraverseException("%s: base VDFTreeNode is NULL", debugOutputPrefixStr);
					if (!blockNode.is(VDFTreeNode.Type.Array)) throw new VDFTraverseException("%s: base VDFTreeNode is not an Array", debugOutputPrefixStr);
					blockNode.markAsProcessed();
					
					ValueContainer<String> playerLevel = new ValueContainer<>();
					ValueContainer<String> lastSyncTime = new ValueContainer<>();
					
					apps = new Vector<SoftwareValveSteamApps.AppData>();
					appsWithID = new HashMap<>();
					appsWithoutID = new Vector<>();
					blockNode.forEach((subNode, type, name, value)->{
						return processBlockSubNode(debugOutputPrefixStr, file, playerLevel, lastSyncTime, subNode, type, name, value);
					});
					
					this.playerLevel     = parseNumber    (str_playerLevel  = playerLevel .value);
					this.lastSyncTime_ks = parseLongNumber(str_lastSyncTime = lastSyncTime.value);
				}

				private boolean processBlockSubNode(
						String debugOutputPrefixStr, File file, ValueContainer<String> playerLevel, ValueContainer<String> lastSyncTime,
						VDFTreeNode subNode, VDFTreeNode.Type type, String name, String value
				) {
					switch (type) {
					
					case Root:
						System.err.printf("%s: contains a Root node%n", debugOutputPrefixStr);
						DevHelper.unknownValues.add("SoftwareValveSteamApps", name, type);
						break;

					case Array:
						switch (name) {
						case "Apps": case "apps":
							String subPrefixStr = debugOutputPrefixStr+".Apps";
							subNode.forEach((subNode1, type1, name1, value1)->{
								return processAppsSubNode(subPrefixStr, file, subNode1, type1, name1, value1);
							});
							return true;
							
						case "ShaderCacheManager": break;
						default: DevHelper.unknownValues.add("SoftwareValveSteamApps", name, type); break;
						}
						break;
						
					case String:
						switch (name) {
						case "PlayerLevel"            : playerLevel .value = value; return true;
						case "LastPlayedTimesSyncTime": lastSyncTime.value = value; return true;
						default: DevHelper.unknownValues.add("SoftwareValveSteamApps", name, type); break;
						}
						break;
					}
					return false;
				}

				private boolean processAppsSubNode(String debugOutputPrefixStr, File file, VDFTreeNode subNode, VDFTreeNode.Type type, String name, String value) {
					switch (type) {
					case Root:
						System.err.printf("%s: contains a Root node%n", debugOutputPrefixStr);
						DevHelper.unknownValues.add("SoftwareValveSteamApps.Apps", name, type);
						break;
						
					case Array:
						try {
							addApp(new AppData(name, subNode, debugOutputPrefixStr));
							return true;
						} catch (VDFTraverseException e) {
							showException(e, file);
							addApp(new AppData(name, subNode));
						}
						
					case String:
						DevHelper.unknownValues.add("SoftwareValveSteamApps.Apps", name, type);
						break;
					}
					return false;
				}

				private void addApp(AppData app) {
					apps.add(app);
					if (app.appID!=null)
						appsWithID.put(app.appID, app);
					else
						appsWithoutID.add(app);
				}

				static class AppData {
				    // Block "Data.Player.LocalConfig.SoftwareValveSteamApps.AppData"
					//     <Base>:[Array]
					//     <Base>.1161580_eula_0:[String, <unset>] { Values: Integer }
					//     <Base>.1465360_eula_0:[String, <unset>] { Values: Integer }
					//     <Base>.332310_eula_1:[Array, <unset>] or empty array
					//     <Base>.876160_eula_0:[String, <unset>] { Values: Integer }
					//     <Base>.autocloud:[Array, <unset>]
					//     <Base>.autocloud.lastexit:[String, <unset>] { Values: Integer }
					//     <Base>.autocloud.lastlaunch:[String] { Values: Integer }
					//     <Base>.BadgeData:[String, <unset>] { Values: String, Long, Float }
					//     <Base>.cloud:[Array, <unset>]
					//     <Base>.cloud.last_sync_state:[String] { Values: String }
					//     <Base>.cloud.quota_bytes:[String, <unset>] { Values: Long }
					//     <Base>.cloud.quota_files:[String, <unset>] { Values: Integer }
					//     <Base>.cloud.used_bytes:[String, <unset>] { Values: Integer }
					//     <Base>.cloud.used_files:[String, <unset>] { Values: Integer }
					//     <Base>.eula_47870:[String, <unset>] { Values: Integer }
					//     <Base>.LastPlayed:[String, <unset>] { Values: Integer }
					//     <Base>.News:[String, <unset>] { Values: Integer }
					//     <Base>.Playtime:[String, <unset>] { Values: Integer }
					//     <Base>.Playtime2wks:[String, <unset>] { Values: Integer }
					//     <Base>.ViewedLaunchEULA:[String, <unset>] { Values: Integer }

					private static final DevHelper.KnownVdfValuesRecursive KNOWN_VDF_VALUES = new DevHelper.KnownVdfValuesRecursive(AppData.class)
							.add(VDFTreeNode.Type.Array )
					        .add(VDFTreeNode.Type.String, "1161580_eula_0")
					        .add(VDFTreeNode.Type.String, "1465360_eula_0")
					        .add(VDFTreeNode.Type.Array , "332310_eula_1")
					        .add(VDFTreeNode.Type.String, "876160_eula_0")
							.add(VDFTreeNode.Type.Array , "autocloud")
							.add(VDFTreeNode.Type.String, "autocloud", "lastexit")
							.add(VDFTreeNode.Type.String, "autocloud", "lastlaunch") // not optional
							.add(VDFTreeNode.Type.String, "BadgeData")
					        .add(VDFTreeNode.Type.Array , "cloud")
					        .add(VDFTreeNode.Type.String, "cloud", "last_sync_state") // not optional
					        .add(VDFTreeNode.Type.String, "cloud", "quota_bytes")
					        .add(VDFTreeNode.Type.String, "cloud", "quota_files")
					        .add(VDFTreeNode.Type.String, "cloud", "used_bytes")
					        .add(VDFTreeNode.Type.String, "cloud", "used_files")
					        .add(VDFTreeNode.Type.String, "eula_47870")
							.add(VDFTreeNode.Type.String, "LastPlayed")
							.add(VDFTreeNode.Type.String, "News")
							.add(VDFTreeNode.Type.String, "Playtime")
							.add(VDFTreeNode.Type.String, "Playtime2wks")
							.add(VDFTreeNode.Type.String, "ViewedLaunchEULA");
					
					final VDFTreeNode rawData;
					final String nodeName;
					final Integer appID;
					final boolean hasParsedData;
					
					final String str_lastPlayed;
					final String str_playtime;
					final String str_playtime_2wks;
					final String str_autocloud_lastexit;
					final String str_autocloud_lastlaunch;

					final Long    lastPlayed_ks;
					final Integer playtime_min;
					final Integer playtime_2weeks_min;
					final Long    autocloud_lastexit_ks;
					final Long    autocloud_lastlaunch_ks;
					
					final String badgeData;
					final String news;
					
					final String viewedLaunchEULA;
					final String eula_47870;
					final String eula_332310_1;
					final String eula_876160_0;
					final String eula_1161580_0;
					final String eula_1465360_0;

					final String str_cloud_quota_bytes;
					final String str_cloud_quota_files;
					final String str_cloud_used_bytes;
					final String str_cloud_used_files;
					
					final String cloud_last_sync_state;
					final Long   cloud_quota_bytes;
					final Long   cloud_quota_files;
					final Long   cloud_used_bytes;
					final Long   cloud_used_files;
					
					AppData(String nodeName, VDFTreeNode rawData) {
						this.rawData = rawData;
						appID = parseNumber(this.nodeName = nodeName);
						hasParsedData = false;

						str_lastPlayed           = null;
						str_playtime             = null;
						str_playtime_2wks        = null;
						str_autocloud_lastexit   = null;
						str_autocloud_lastlaunch = null;
						
						lastPlayed_ks           = null;
						playtime_min            = null;
						playtime_2weeks_min     = null;
						autocloud_lastexit_ks   = null;
						autocloud_lastlaunch_ks = null;
						
						str_cloud_quota_bytes = null;
						str_cloud_quota_files = null;
						str_cloud_used_bytes  = null;
						str_cloud_used_files  = null;
						
						cloud_last_sync_state = null;
						cloud_quota_bytes = null;
						cloud_quota_files = null;
						cloud_used_bytes  = null;
						cloud_used_files  = null;
						
						
						badgeData = null;
						news      = null;
						
						viewedLaunchEULA = null;
						eula_47870       = null;
						eula_332310_1    = null;
						eula_876160_0    = null;
						eula_1161580_0   = null;
						eula_1465360_0   = null;
					}
					
					AppData(String nodeName, VDFTreeNode node, String debugOutputPrefixStr) throws VDFTraverseException {
						if (node==null) throw new IllegalArgumentException("node == null");
						if (nodeName==null) throw new IllegalArgumentException("nodeName == null");
						if (!node.is(VDFTreeNode.Type.Array)) throw new VDFTraverseException("%s: node.type != Array", debugOutputPrefixStr);
						
						this.rawData = null;
						appID = parseNumber(this.nodeName = nodeName);
						hasParsedData = true;
						
						//DevHelper.optional.vdfValues.scan(node, getClass());
						
						lastPlayed_ks       = parseLongNumber(str_lastPlayed    = node.getString_optional("LastPlayed"  ));
						playtime_min        = parseNumber    (str_playtime      = node.getString_optional("Playtime"    ));
						playtime_2weeks_min = parseNumber    (str_playtime_2wks = node.getString_optional("Playtime2wks"));
						
						autocloud_lastexit_ks   = parseLongNumber(str_autocloud_lastexit   = node.getString_optional("autocloud","lastexit"  ));
						autocloud_lastlaunch_ks = parseLongNumber(str_autocloud_lastlaunch = node.getString_optional("autocloud","lastlaunch"));
						
						cloud_last_sync_state =                                     node.getString_optional("cloud", "last_sync_state");
						cloud_quota_bytes = parseLongNumber(str_cloud_quota_bytes = node.getString_optional("cloud", "quota_bytes"));
						cloud_quota_files = parseLongNumber(str_cloud_quota_files = node.getString_optional("cloud", "quota_files"));
						cloud_used_bytes  = parseLongNumber(str_cloud_used_bytes  = node.getString_optional("cloud", "used_bytes" ));
						cloud_used_files  = parseLongNumber(str_cloud_used_files  = node.getString_optional("cloud", "used_files" ));
						
						badgeData = node.getString_optional("BadgeData");
						news      = node.getString_optional("News"     );
						
						VDFTreeNode eula_332310_1_Arr;
						viewedLaunchEULA = node.getString_optional("ViewedLaunchEULA");
						eula_47870       = node.getString_optional("eula_47870"    );
						eula_332310_1_Arr = node.getArray_optional("332310_eula_1" );
						eula_876160_0    = node.getString_optional("876160_eula_0" );
						eula_1161580_0   = node.getString_optional("1161580_eula_0");
						eula_1465360_0   = node.getString_optional("1465360_eula_0");
						
						if (eula_332310_1_Arr==null)
							eula_332310_1 = null;
						else if (eula_332310_1_Arr.isEmptyArray())
							eula_332310_1 = "<empty array>";
						else
							eula_332310_1 = "<non empty array>";
							
						
						//showValue("BadgeData"       , badgeData       );
						//showValue("ViewedLaunchEULA", viewedLaunchEULA);
						//showValue("1161580_eula_0"  , _1161580_eula_0 );
						//showValue("eula_47870"      , eula_47870      );
						//showValue("News"            , news            );
						
						KNOWN_VDF_VALUES.scanUnexpectedValues(node); //, "SoftwareValveSteamApps.Apps[]");
					}

					@SuppressWarnings("unused")
					private void showValue(String label, String value) {
						if (value==null) return;
						DevHelper.unknownValues.add(String.format("%s.%s = \"%s\"", "SoftwareValveSteamApps.Apps[]", label, value));
					}
					
					static boolean meetsFilterOption(AppData appData, FilterOption option) {
						if (appData==null) return true;
						if (option==null) return true;
						switch (option) {
						case RawData: return !appData.hasParsedData;
						case EULA:
							return appData.viewedLaunchEULA!=null
								|| appData.eula_47870    !=null
								|| appData.eula_332310_1 !=null
								|| appData.eula_876160_0 !=null
								|| appData.eula_1161580_0!=null
								|| appData.eula_1465360_0!=null;
						case AutoCloud     : return appData.str_autocloud_lastexit!=null || appData.str_autocloud_lastlaunch!=null;
						case CloudQuota    : return appData.str_cloud_quota_bytes !=null || appData.str_cloud_quota_files   !=null;
						case CloudUsed     : return appData.str_cloud_used_bytes  !=null || appData.str_cloud_used_files    !=null;
						case CloudSyncState: return appData.cloud_last_sync_state!=null;
						case BadgeData     : return appData.badgeData!=null;
						case NoBadgeData   : return appData.badgeData==null && appData.hasParsedData;
						case News          : return appData.news!=null;
						}
						return true;
					}
					
					enum FilterOption implements SteamInspector.MainTreeContextMenu.FilterOption {
						RawData    ("can't be parsed"),
						EULA       ("has EULA"),
						BadgeData  ("has Badge Data"),
						NoBadgeData("has NO Badge Data"),
						News       ("has News"),
						AutoCloud  ("has Auto Cloud value"),
						CloudQuota ("has Cloud Quota"),
						CloudUsed  ("has used Cloud"),
						CloudSyncState("has Cloud Sync State"),
						;
						private final String label;
						FilterOption(String label) { this.label = label; }
						@Override public String toString() { return label; }

						static FilterOption cast(SteamInspector.MainTreeContextMenu.FilterOption obj) {
							//System.out.printf("FilterOptions.cast( [%s] obj=%s )%n", obj==null ? null : obj.getClass(), obj );
							if (obj instanceof FilterOption)
								return (FilterOption) obj;
							return null;
						}
					}
				
				}
			}

			static class FriendList {
				
				//static final HashSet<String> unknownValueNames = new HashSet<>();
				
				final VDFTreeNode rawData;
				final Vector<FriendList.Friend> friends;
				final HashMap<String,String> values;
				final String personaName;
				
				FriendList(VDFTreeNode rawData) {
					this.rawData = rawData;
					friends = null;
					values = null;
					personaName = null;
				}
				
				FriendList(VDFTreeNode blockNode, String debugOutputPrefixStr, File file) throws VDFTraverseException {
					if ( blockNode==null) throw new VDFTraverseException("%s: base VDFTreeNode is NULL", debugOutputPrefixStr);
					if (!blockNode.is(VDFTreeNode.Type.Array)) throw new VDFTraverseException("%s: base VDFTreeNode is not an Array", debugOutputPrefixStr);
					blockNode.markAsProcessed();
					
					rawData = null;
					friends = new Vector<>();
					values = new HashMap<>();
					
					blockNode.forEach((subNode, type, name, value)->{
						switch (type) {
						
						case Root:
							System.err.printf("%s: base VDFTreeNode contains a Root node%n", debugOutputPrefixStr);
							DevHelper.unknownValues.add("FriendList", name, type);
							break;
			
						case Array: // Friend
							try {
								friends.add(new Friend(name,subNode));
								return true;
							} catch (VDFTraverseException e) {
								showException(e, file);
								friends.add(new Friend(subNode));
							}
							
						case String: // simple value
							values.put(name, value);
							return true;
						}
						return false;
					});
					
					personaName = values.get("PersonaName");
				}
			
				static class Friend {
					private static final DevHelper.KnownVdfValues KNOWN_VDF_VALUES = new DevHelper.KnownVdfValues(Friend.class)
							.add("name"  , VDFTreeNode.Type.String)
							.add("tag"   , VDFTreeNode.Type.String)
							.add("avatar", VDFTreeNode.Type.String)
							.add("NameHistory", VDFTreeNode.Type.Array);
					
					final VDFTreeNode rawData;
					final boolean hasParsedData;
					final long id;
					final long id_lower;
					final long id_upper;
					final long playerID;
					final boolean isPerson;
					final String name;
					final String tag;
					final String avatar;
					final HashMap<Integer, String> nameHistory;
			
					public Friend(VDFTreeNode rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						id     = -1;
						id_lower = -1;
						id_upper = -1;
						playerID = -1;
						isPerson = false;
						name   = null;
						tag    = null;
						avatar = null;
						nameHistory = null;
					}
			
					public Friend(String nodeName, VDFTreeNode node) throws VDFTraverseException {
						this.rawData = null;
						hasParsedData = true;
						
						//DevHelper.scanVdfStructure(node,"Friend");
						
						Long parsedNodeName = parseLongNumber(nodeName);
						if (parsedNodeName==null)
							throw new VDFTraverseException("Name of node [%s] isn't a number.", node.getPathStr());
							
						id       = parsedNodeName;
						id_lower = id & 0xFFFFFFFFL;
						id_upper = id>>32;
						playerID = id_lower;
						isPerson = id_upper==0;
						name     = node.getString(VDFTreeNode.OptionalType.LastValueIsOptional, "name"  );
						tag      = node.getString(VDFTreeNode.OptionalType.LastValueIsOptional, "tag"   );
						avatar   = node.getString(VDFTreeNode.OptionalType.LastValueIsOptional, "avatar");
						VDFTreeNode arrayNode = node.getArray(VDFTreeNode.OptionalType.LastValueIsOptional, "NameHistory");
						if (arrayNode!=null) {
							nameHistory = new HashMap<Integer,String>();
							arrayNode.forEach((subNode,t,n,v) -> {
								if (t==VDFTreeNode.Type.String) {
									Integer index = parseNumber(n);
									if (index!=null && v!=null) {
										nameHistory.put(index,v);
										return true;
									}
								}
								DevHelper.unknownValues.add("Friend.NameHistory", n, t);
								return false;
							});
						} else
							nameHistory = null;
						
						KNOWN_VDF_VALUES.scanUnexpectedValues(node); //, "TreeNodes.Player.FriendList.Friend");
					}
				}
			}
		}

		static class AchievementProgress {
			private static final DevHelper.KnownJsonValues KNOWN_JSON_VALUES = new DevHelper.KnownJsonValues(AchievementProgress.class)
					.add("nVersion", JSON_Data.Value.Type.Integer)
					.add("mapCache", JSON_Data.Value.Type.Object);

			final File file;
			final JSON_Data.Value<NV, V> rawData;
			final boolean hasParsedData;
			final Long version;
			final HashMap<Integer,AchievementProgress.AchievementProgressInGame> gameStates;
			final Vector<AchievementProgress.AchievementProgressInGame> gameStates_withoutID;

			AchievementProgress(File file, JSON_Data.Value<NV, V> rawData) {
				this.file = file;
				this.rawData = rawData;
				hasParsedData = false;
				version    = null;
				gameStates = null;
				gameStates_withoutID = null;
			}
			AchievementProgress(File file, JSON_Data.Value<NV, V> value, String debugOutputPrefixStr) throws TraverseException {
				this.file = file;
				this.rawData = value;
				hasParsedData = true;
				//DevHelper.scanJsonStructure(object, "AchievementProgress");
				
				JSON_Object<NV,V> object   = JSON_Data.getObjectValue (value, debugOutputPrefixStr);
				version                    = JSON_Data.getIntegerValue(object, "nVersion", debugOutputPrefixStr);
				JSON_Object<NV,V> mapCache = JSON_Data.getObjectValue (object, "mapCache", debugOutputPrefixStr);
				
				gameStates = new HashMap<>();
				gameStates_withoutID = new Vector<AchievementProgress.AchievementProgressInGame>();
				for (JSON_Data.NamedValue<NV,V> nv:mapCache) {
					AchievementProgress.AchievementProgressInGame progress;
					try {
						progress = new AchievementProgressInGame(nv.name,nv.value);
					} catch (TraverseException e) {
						showException(e, file);
						progress = new AchievementProgressInGame(nv);
					}
					
					Integer gameID = parseNumber(nv.name);
					if (gameID==null && progress.hasParsedData)
						gameID = (int) progress.appID;
					
					if (gameID!=null)
						gameStates.put(gameID, progress);
					else
						gameStates_withoutID.add(progress);
				}
				KNOWN_JSON_VALUES.scanUnexpectedValues(object); //, "TreeNodes.Player.AchievementProgress");
			}
			
			static class AchievementProgressInGame {
				private static final DevHelper.KnownJsonValues KNOWN_JSON_VALUES = new DevHelper.KnownJsonValues(AchievementProgressInGame.class)
						.add("all_unlocked", JSON_Data.Value.Type.Bool)
						.add("appid"       , JSON_Data.Value.Type.Integer)
						.add("cache_time"  , JSON_Data.Value.Type.Integer)
						.add("total"       , JSON_Data.Value.Type.Integer)
						.add("unlocked"    , JSON_Data.Value.Type.Integer)
						.add("percentage"  , JSON_Data.Value.Type.Integer)
						.add("percentage"  , JSON_Data.Value.Type.Float);
				// "AchievementProgress.GameStatus.all_unlocked:Bool"
				// "AchievementProgress.GameStatus.appid:Integer"
				// "AchievementProgress.GameStatus.cache_time:Integer"
				// "AchievementProgress.GameStatus.percentage:Float"
				// "AchievementProgress.GameStatus.percentage:Integer"
				// "AchievementProgress.GameStatus.total:Integer"
				// "AchievementProgress.GameStatus.unlocked:Integer"
				// "AchievementProgress.GameStatus:Object"
				
				final JSON_Data.Value<NV, V> rawData;
				final String name;
				final boolean hasParsedData;
				
				final boolean allUnlocked;
				final long    appID;
				final long    cacheTime;
				final long    total;
				final long    unlocked;
				final double  percentage;

				AchievementProgressInGame(JSON_Data.NamedValue<NV, V> rawData) {
					this.rawData = rawData.value;
					this.name    = rawData.name;
					hasParsedData = false;
					allUnlocked = false;
					appID       = -1;
					cacheTime   = -1;
					total       = -1;
					unlocked    = -1;
					percentage  = Double.NaN;
				}

				AchievementProgressInGame(String name, JSON_Data.Value<NV, V> value) throws TraverseException {
					this.rawData = null;
					this.name    = name;
					hasParsedData = true;
					// DevHelper.scanJsonStructure(value, "AchievementProgress.GameStatus");
					String prefixStr = "AchievementProgress.GameStatus["+name+"]";
					JSON_Object<NV,V> object = JSON_Data.getObjectValue(value, prefixStr);
					allUnlocked = JSON_Data.getBoolValue   (object, "all_unlocked", prefixStr);
					appID       = JSON_Data.getIntegerValue(object, "appid"       , prefixStr);
					cacheTime   = JSON_Data.getIntegerValue(object, "cache_time"  , prefixStr);
					total       = JSON_Data.getIntegerValue(object, "total"       , prefixStr);
					unlocked    = JSON_Data.getIntegerValue(object, "unlocked"    , prefixStr);
					percentage  = JSON_Data.getNumber(object, "percentage"  , prefixStr);
					KNOWN_JSON_VALUES.scanUnexpectedValues(object); //, "TreeNodes.Player.AchievementProgress.AchievementProgressInGame");
				}

				int getGameID() { return (int) appID; }
			}
		}
		
		static class GameInfos {

			static boolean meetsFilterOption(GameInfos gameInfos, GameInfosFilterOptions option) {
				if (option==null) return true;
				if (gameInfos==null) return true;
				switch (option) {
				case RawData         : return !gameInfos.hasParsedData && gameInfos.rawData       !=null;
				case Badge           : return  gameInfos.hasParsedData && !isEmpty(gameInfos.badge         );
				case Workshop        : return  gameInfos.hasParsedData && !isEmpty(gameInfos.workshop      );
				case Achievements    : return  gameInfos.hasParsedData && !isEmpty(gameInfos.achievements  );
				case AchievementMap  : return  gameInfos.hasParsedData && !isEmpty(gameInfos.achievementMap);
				case SocialMedia     : return  gameInfos.hasParsedData && !isEmpty(gameInfos.socialMedia   );
				case Associations    : return  gameInfos.hasParsedData && !isEmpty(gameInfos.associations  );
				case ReleaseData     : return  gameInfos.hasParsedData && !isEmpty(gameInfos.releaseData   );
				case Friends         : return  gameInfos.hasParsedData && !isEmpty(gameInfos.friends       );
				case CommunityItems  : return  gameInfos.hasParsedData && !isEmpty(gameInfos.communityItems);
				//case UserNews      : return  gameInfos.hasParsedData && !isEmpty(gameInfos.userNews      );
				//case GameActivity  : return  gameInfos.hasParsedData && !isEmpty(gameInfos.gameActivity  );
				//case AppActivity   : return  gameInfos.hasParsedData && !isEmpty(gameInfos.appActivity   );
				case Descriptions    : return  gameInfos.hasParsedData && ( !isEmpty(gameInfos.fullDesc) || !isEmpty(gameInfos.shortDesc) );
				case SomeBase64Values: return  gameInfos.hasParsedData && ( !isEmpty(gameInfos.appActivity ) || !isEmpty(gameInfos.gameActivity) || !isEmpty(gameInfos.userNews    ) );
				}
				return true;
			}
			
			static boolean isEmpty(String      data) { if (data==null) return true; return data.isEmpty(); }
			static boolean isEmpty(ParsedBlock data) { if (data==null) return true; return data.isEmpty(); }

			enum GameInfosFilterOptions implements FilterOption {
				RawData         ("is unparsed (Raw Data)"),
				Descriptions    ("has Descriptions"),
				Badge           ("has Badge Data"),
				Workshop        ("has Workshop Data"),
				Achievements    ("has Achievements Data"),
				AchievementMap  ("has Achievement Map"),
				CommunityItems  ("has Community Items"),
				SocialMedia     ("has Social Media"),
				Associations    ("has Associations"),
				Friends         ("has Played/Owned Infos"),
				ReleaseData     ("has Release Data"),
				//UserNews      ("has User News"),
				//GameActivity  ("has Game Activity"),
				//AppActivity   ("has App Activity"),
				SomeBase64Values("has Some Base64 Values"),
				;
				private final String label;
				
				GameInfosFilterOptions() { this(null); }
				GameInfosFilterOptions(String label) { this.label = label==null ? name() : label;}
				
				@Override public String toString() {
					return label;
				}

				static GameInfosFilterOptions cast(FilterOption obj) {
					//System.out.printf("FilterOptions.cast( [%s] obj=%s )%n", obj==null ? null : obj.getClass(), obj );
					if (obj instanceof GameInfosFilterOptions)
						return (GameInfosFilterOptions) obj;
					return null;
				}
			}

			final File file;
			final JSON_Data.Value<NV, V> rawData;
			final boolean hasParsedData;
			final Vector<Block> blocks;
			
			final long playerID;
			final int gameID;
			
			final String         fullDesc;
			final String         shortDesc;
			final Badge          badge;
			final Achievements   achievements;
			final UserNews       userNews;
			final GameActivity   gameActivity;
			final AchievementMap achievementMap;
			final SocialMedia    socialMedia;
			final Associations   associations;
			final AppActivity    appActivity;
			final ReleaseData    releaseData;
			final Friends        friends;
			final CommunityItems communityItems;
			final Workshop       workshop;

			GameInfos(long playerID, int gameID, File file, JSON_Data.Value<NV, V> rawData) {
				this.playerID = playerID;
				this.gameID = gameID;
				this.file = file;
				this.rawData = rawData;
				
				hasParsedData = false;
				blocks        = null;
				
				fullDesc       = null;
				shortDesc      = null;
				badge          = null;
				achievements   = null;
				userNews       = null;
				gameActivity   = null;
				achievementMap = null;
				socialMedia    = null;
				associations   = null;
				appActivity    = null;
				releaseData    = null;
				friends        = null;
				communityItems = null;
				workshop       = null;
			}
			
			GameInfos(long playerID, int gameID, File file, JSON_Data.Value<NV, V> fileContent, String debugOutputPrefixStr) throws TraverseException {
				this.playerID = playerID;
				this.gameID = gameID;
				this.file = file;
				this.rawData = fileContent;
				
				hasParsedData = true;
				
				JSON_Array<NV, V> array = JSON_Data.getArrayValue(fileContent, debugOutputPrefixStr);
				
				blocks = new Vector<>();
				for (int i=0; i<array.size(); i++) {
					JSON_Data.Value<NV,V> value = array.get(i);
					try {
						blocks.add(new Block(i,value));
					} catch (TraverseException e) {
						showException(e, file);
						blocks.add(Block.createRawData(i,value));
					}
				}
				
				String              preFullDesc            = null;
				String              preShortDesc           = null;
				Badge               preBadge               = null;
				Achievements        preAchievements        = null;
				UserNews            preUserNews            = null;
				GameActivity        preGameActivity        = null;
				AchievementMap      preAchievementMap      = null;
				SocialMedia         preSocialMedia         = null;
				Associations        preAssociations        = null;
				AppActivity         preAppActivity         = null;
				ReleaseData         preReleaseData         = null;
				Friends             preFriends             = null;
				CommunityItems      preCommunityItems      = null;
				Workshop            preWorkshop            = null;
				//WorkshopTrendyItems preWorkshopTrendyItems = null;
				
				for (Block block:blocks)
					if (block.hasParsedData) {
						String dataValueStr = String.format("%s.Block[%s].dataValue", debugOutputPrefixStr, block.label);
						//DevHelper.scanJsonStructure(block.dataValue,String.format("GameInfo.Block[\"%s\",V%d].dataValue", block.label, block.version));
						
						switch (block.label) {
							
						case "badge"                : preBadge               = parseBlock( Badge              ::new, Badge              ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "achievements"         : preAchievements        = parseBlock( Achievements       ::new, Achievements       ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "usernews"             : preUserNews            = parseBlock( UserNews           ::new, UserNews           ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "gameactivity"         : preGameActivity        = parseBlock( GameActivity       ::new, GameActivity       ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "achievementmap"       : preAchievementMap      = parseBlock( AchievementMap     ::new, AchievementMap     ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "socialmedia"          : preSocialMedia         = parseBlock( SocialMedia        ::new, SocialMedia        ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "associations"         : preAssociations        = parseBlock( Associations       ::new, Associations       ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "appactivity"          : preAppActivity         = parseBlock( AppActivity        ::new, AppActivity        ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "releasedata"          : preReleaseData         = parseBlock( ReleaseData        ::new, ReleaseData        ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "friends"              : preFriends             = parseBlock( Friends            ::new, Friends            ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "community_items"      : preCommunityItems      = parseBlock( CommunityItems     ::new, CommunityItems     ::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "workshop"             : preWorkshop            = parseBlock( Workshop           ::new, Workshop           ::new, block.dataValue, block.version, dataValueStr, file ); break;
						//case "workshop_trendy_items": preWorkshopTrendyItems = parseBlock( WorkshopTrendyItems::new, WorkshopTrendyItems::new, block.dataValue, block.version, dataValueStr, file ); break;
						case "workshop_trendy_items":
							if (block.dataValue!=null) {
								System.err.printf("Found GameInfos.Block["+block.label+"] with non null BlockData in \"%s\"%n", file);
								DevHelper.scanJsonStructure(block.dataValue,String.format("GameInfo.Block[\"%s\",V%d].dataValue", block.label, block.version));
							}
							break;
							
						case "descriptions":
							JSON_Object<NV, V> object = null;
							try { object = JSON_Data.getObjectValue(block.dataValue, dataValueStr); }
							catch (TraverseException e) { showException(e, file); }
							if (object!=null) {
								try { preFullDesc  = JSON_Data.getStringValue(object, "strFullDescription", dataValueStr); }
								catch (TraverseException e) { showException(e, file); }
								try { preShortDesc = JSON_Data.getStringValue(object, "strSnippet", dataValueStr); }
								catch (TraverseException e) { showException(e, file); }
							}
							break;
							
						//case "workshop_trendy_items": // unknown block
						//	System.err.printf("Found GameInfos.Block["+block.label+"] in \"%s\"%n", file);
						//	DevHelper.scanJsonStructure(block.dataValue,String.format("GameInfo.Block[\"%s\",V%d].dataValue", block.label, block.version));
						//	break;
							
						default:
							DevHelper.unknownValues.add("GameInfos.Block["+block.label+"] - Unknown Block");
						}
					}
				fullDesc            = preFullDesc      ;
				shortDesc           = preShortDesc     ;
				badge               = preBadge         ;
				achievements        = preAchievements  ;
				userNews            = preUserNews      ;
				gameActivity        = preGameActivity  ;
				achievementMap      = preAchievementMap;
				socialMedia         = preSocialMedia   ;
				associations        = preAssociations  ;
				appActivity         = preAppActivity   ;
				releaseData         = preReleaseData   ;
				friends             = preFriends       ;
				communityItems      = preCommunityItems;
				workshop            = preWorkshop      ;
				//workshopTrendyItems = preWorkshopTrendyItems;
			}
			private static <ClassType> ClassType parseBlock(
					BlockParseConstructor<ClassType> parseConstructor,
					BiFunction<JSON_Data.Value<NV,V>,Long,ClassType> rawDataConstructor,
					JSON_Data.Value<NV, V> blockDataValue, long version, String debugOutputPrefixStr, File file
			) {
				try {
					return parseConstructor.parse(blockDataValue, version, debugOutputPrefixStr, file);
				} catch (TraverseException e) {
					showException(e, file);
					return rawDataConstructor.apply(blockDataValue, version);
				}
			}

			interface BlockParseConstructor<ClassType> {
				ClassType parse(JSON_Data.Value<NV, V> blockDataValue, long version, String debugOutputPrefixStr, File file) throws TraverseException;
			}

			static class ParsedBlock {
				
				final JSON_Data.Value<NV, V> rawData;
				final boolean hasParsedData;
				final long version;
				
				ParsedBlock(JSON_Data.Value<NV, V> rawData, long version, boolean hasParsedData) {
					this.rawData = rawData;
					this.version = version;
					this.hasParsedData = hasParsedData;
				}
				
				boolean isEmpty() {
					return rawData==null;
				}
			}
			
			static class Workshop extends ParsedBlock {
				
				final Vector<Entry> entries;

				Workshop(JSON_Data.Value<NV, V> rawData, long version) {
					super(rawData, version, false);
					entries = null;
				}
				Workshop(JSON_Data.Value<NV, V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					//DevHelper.optionalJsonValues.scan(blockDataValue, "GameInfos.Workshop(V"+version+")");
					if (blockDataValue==null) entries = new Vector<>();
					else entries = parseArray(Entry::new, Entry::new, blockDataValue, dataValueStr,file);
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || entries.isEmpty());
				}
				
				static class Entry {

					private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues(Entry.class)
							.add("app_name"                    , JSON_Data.Value.Type.String )
							.add("available_revisions"         , JSON_Data.Value.Type.Array  )  // empty
							.add("ban_reason"                  , JSON_Data.Value.Type.String )
							.add("banned"                      , JSON_Data.Value.Type.Bool   )
							.add("banner"                      , JSON_Data.Value.Type.String )
							.add("can_be_deleted"              , JSON_Data.Value.Type.Bool   )
							.add("can_subscribe"               , JSON_Data.Value.Type.Bool   )
							.add("children"                    , JSON_Data.Value.Type.Array  )  // empty
							.add("consumer_appid"              , JSON_Data.Value.Type.Integer)
							.add("consumer_shortcutid"         , JSON_Data.Value.Type.Integer)
							.add("creator"                     , JSON_Data.Value.Type.String )
							.add("creator_appid"               , JSON_Data.Value.Type.Integer)
							.add("favorited"                   , JSON_Data.Value.Type.Integer)
							.add("file_size"                   , JSON_Data.Value.Type.String )
							.add("file_type"                   , JSON_Data.Value.Type.Integer)
							.add("file_url"                    , JSON_Data.Value.Type.String )  // optional
							.add("filename"                    , JSON_Data.Value.Type.String )
							.add("flags"                       , JSON_Data.Value.Type.Integer)
							.add("followers"                   , JSON_Data.Value.Type.Integer)
							.add("hcontent_file"               , JSON_Data.Value.Type.String )
							.add("hcontent_preview"            , JSON_Data.Value.Type.String )
							.add("kvtags"                      , JSON_Data.Value.Type.Array  )  // empty
							.add("language"                    , JSON_Data.Value.Type.Integer)
							.add("lifetime_favorited"          , JSON_Data.Value.Type.Integer)
							.add("lifetime_followers"          , JSON_Data.Value.Type.Integer)
							.add("lifetime_playtime"           , JSON_Data.Value.Type.String )
							.add("lifetime_playtime_sessions"  , JSON_Data.Value.Type.String )
							.add("lifetime_subscriptions"      , JSON_Data.Value.Type.Integer)
							.add("maybe_inappropriate_sex"     , JSON_Data.Value.Type.Bool   )  // optional
							.add("maybe_inappropriate_violence", JSON_Data.Value.Type.Bool   )  // optional
							.add("num_children"                , JSON_Data.Value.Type.Integer)
							.add("num_comments_public"         , JSON_Data.Value.Type.Integer)
							.add("num_reports"                 , JSON_Data.Value.Type.Integer)
							.add("preview_file_size"           , JSON_Data.Value.Type.String )
							.add("preview_url"                 , JSON_Data.Value.Type.String )
							.add("previews"                    , JSON_Data.Value.Type.Array  )  // empty
							.add("publishedfileid"             , JSON_Data.Value.Type.String )
							.add("reactions"                   , JSON_Data.Value.Type.Array  )  // optional || empty
							.add("result"                      , JSON_Data.Value.Type.Integer)
							.add("revision"                    , JSON_Data.Value.Type.Integer)
							.add("revision_change_number"      , JSON_Data.Value.Type.String )
							.add("short_description"           , JSON_Data.Value.Type.String )
							.add("show_subscribe_all"          , JSON_Data.Value.Type.Bool   )
							.add("subscriptions"               , JSON_Data.Value.Type.Integer)
							.add("tags"                        , JSON_Data.Value.Type.Array  )  // of Objects
							.add("time_created"                , JSON_Data.Value.Type.Integer)
							.add("time_updated"                , JSON_Data.Value.Type.Integer)
							.add("title"                       , JSON_Data.Value.Type.String )
							.add("url"                         , JSON_Data.Value.Type.String )
							.add("views"                       , JSON_Data.Value.Type.Integer)
							.add("visibility"                  , JSON_Data.Value.Type.Integer)
							.add("vote_data"                   , JSON_Data.Value.Type.Object )
							.add("workshop_accepted"           , JSON_Data.Value.Type.Bool   )
							.add("workshop_file"               , JSON_Data.Value.Type.Bool   );
					
					final JSON_Data.Value<NV, V> rawData;
					final boolean hasParsedData;

					final String  app_name;
					final String  ban_reason;
					final boolean banned;
					final String  banner;
					final boolean can_be_deleted;
					final boolean can_subscribe;
					final long    consumer_appid;
					final long    consumer_shortcutid;
					final String  creator;
					final long    creator_appid;
					final long    favorited;
					final String  file_size;
					final long    file_type;
					final String  file_url__OPT;
					final String  filename;
					final long    flags;
					final long    followers;
					final String  hcontent_file;
					final String  hcontent_preview;
					final long    language;
					final long    lifetime_favorited;
					final long    lifetime_followers;
					final String  lifetime_playtime;
					final String  lifetime_playtime_sessions;
					final long    lifetime_subscriptions;
					final Boolean maybe_inappropriate_sex__OPT;
					final Boolean maybe_inappropriate_violence__OPT;
					final long    num_children;
					final long    num_comments_public;
					final long    num_reports;
					final String  preview_file_size;
					final String  preview_url;
					final String  publishedfileid;
					final long    result;
					final long    revision;
					final String  revision_change_number;
					final String  short_description;
					final boolean show_subscribe_all;
					final long    subscriptions;
					final long    time_created;
					final long    time_updated;
					final String  title;
					final String  url;
					final long    views;
					final long    visibility;
					final boolean workshop_accepted;
					final boolean workshop_file;
					
					final Vector<Tag> tags;
					final double vote_score;
					final long   votes_down;
					final long   votes_up;

					
					Entry(JSON_Data.Value<NV, V> rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						
						app_name                     = null;
						ban_reason                   = null;
						banned                       = false;
						banner                       = null;
						can_be_deleted               = false;
						can_subscribe                = false;
						consumer_appid               = -1;
						consumer_shortcutid          = -1;
						creator                      = null;
						creator_appid                = -1;
						favorited                    = -1;
						file_size                    = null;
						file_type                    = -1;
						file_url__OPT                     = null;
						filename                     = null;
						flags                        = -1;
						followers                    = -1;
						hcontent_file                = null;
						hcontent_preview             = null;
						language                     = -1;
						lifetime_favorited           = -1;
						lifetime_followers           = -1;
						lifetime_playtime            = null;
						lifetime_playtime_sessions   = null;
						lifetime_subscriptions       = -1;
						maybe_inappropriate_sex__OPT      = null;
						maybe_inappropriate_violence__OPT = null;
						num_children                 = -1;
						num_comments_public          = -1;
						num_reports                  = -1;
						preview_file_size            = null;
						preview_url                  = null;
						publishedfileid              = null;
						result                       = -1;
						revision                     = -1;
						revision_change_number       = null;
						short_description            = null;
						show_subscribe_all           = false;
						subscriptions                = -1;
						time_created                 = -1;
						time_updated                 = -1;
						title                        = null;
						url                          = null;
						views                        = -1;
						visibility                   = -1;
						workshop_accepted            = false;
						workshop_file                = false;
						
						tags       = null;
						vote_score = Double.NaN;
						votes_down = -1;
						votes_up   = -1;
					}
					
					Entry(JSON_Data.Value<NV, V> value, String debugOutputPrefixStr) throws TraverseException {
						this.rawData = null;
						hasParsedData = true;
						//DevHelper.scanJsonStructure(value,"GameInfos.Workshop.Entry",true);
						
						JSON_Object<NV, V> object = JSON_Data.getObjectValue(value, debugOutputPrefixStr);
						
						JSON_Array<NV, V> available_revisions, children, kvtags, previews, reactions__OPT; // empty
						JSON_Array<NV, V> tags; // of Objects
						JSON_Object<NV, V> vote_data;
						app_name                          = JSON_Data.getStringValue (object, "app_name"                    , debugOutputPrefixStr);
						available_revisions               = JSON_Data.getArrayValue  (object, "available_revisions"         , debugOutputPrefixStr);
						ban_reason                        = JSON_Data.getStringValue (object, "ban_reason"                  , debugOutputPrefixStr);
						banned                            = JSON_Data.getBoolValue   (object, "banned"                      , debugOutputPrefixStr);
						banner                            = JSON_Data.getStringValue (object, "banner"                      , debugOutputPrefixStr);
						can_be_deleted                    = JSON_Data.getBoolValue   (object, "can_be_deleted"              , debugOutputPrefixStr);
						can_subscribe                     = JSON_Data.getBoolValue   (object, "can_subscribe"               , debugOutputPrefixStr);
						children                          = JSON_Data.getArrayValue  (object, "children"                    , debugOutputPrefixStr);
						consumer_appid                    = JSON_Data.getIntegerValue(object, "consumer_appid"              , debugOutputPrefixStr);
						consumer_shortcutid               = JSON_Data.getIntegerValue(object, "consumer_shortcutid"         , debugOutputPrefixStr);
						creator                           = JSON_Data.getStringValue (object, "creator"                     , debugOutputPrefixStr);
						creator_appid                     = JSON_Data.getIntegerValue(object, "creator_appid"               , debugOutputPrefixStr);
						favorited                         = JSON_Data.getIntegerValue(object, "favorited"                   , debugOutputPrefixStr);
						file_size                         = JSON_Data.getStringValue (object, "file_size"                   , debugOutputPrefixStr);
						file_type                         = JSON_Data.getIntegerValue(object, "file_type"                   , debugOutputPrefixStr);
						file_url__OPT                     = JSON_Data.getValue       (object, "file_url"                    , true, JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, debugOutputPrefixStr);
						filename                          = JSON_Data.getStringValue (object, "filename"                    , debugOutputPrefixStr);
						flags                             = JSON_Data.getIntegerValue(object, "flags"                       , debugOutputPrefixStr);
						followers                         = JSON_Data.getIntegerValue(object, "followers"                   , debugOutputPrefixStr);
						hcontent_file                     = JSON_Data.getStringValue (object, "hcontent_file"               , debugOutputPrefixStr);
						hcontent_preview                  = JSON_Data.getStringValue (object, "hcontent_preview"            , debugOutputPrefixStr);
						kvtags                            = JSON_Data.getArrayValue  (object, "kvtags"                      , debugOutputPrefixStr);
						language                          = JSON_Data.getIntegerValue(object, "language"                    , debugOutputPrefixStr);
						lifetime_favorited                = JSON_Data.getIntegerValue(object, "lifetime_favorited"          , debugOutputPrefixStr);
						lifetime_followers                = JSON_Data.getIntegerValue(object, "lifetime_followers"          , debugOutputPrefixStr);
						lifetime_playtime                 = JSON_Data.getStringValue (object, "lifetime_playtime"           , debugOutputPrefixStr);
						lifetime_playtime_sessions        = JSON_Data.getStringValue (object, "lifetime_playtime_sessions"  , debugOutputPrefixStr);
						lifetime_subscriptions            = JSON_Data.getIntegerValue(object, "lifetime_subscriptions"      , debugOutputPrefixStr);
						maybe_inappropriate_sex__OPT      = JSON_Data.getValue       (object, "maybe_inappropriate_sex"     , true, JSON_Data.Value.Type.Bool, JSON_Data.Value::castToBoolValue, false, debugOutputPrefixStr);
						maybe_inappropriate_violence__OPT = JSON_Data.getValue       (object, "maybe_inappropriate_violence", true, JSON_Data.Value.Type.Bool, JSON_Data.Value::castToBoolValue, false, debugOutputPrefixStr);
						num_children                      = JSON_Data.getIntegerValue(object, "num_children"                , debugOutputPrefixStr);
						num_comments_public               = JSON_Data.getIntegerValue(object, "num_comments_public"         , debugOutputPrefixStr);
						num_reports                       = JSON_Data.getIntegerValue(object, "num_reports"                 , debugOutputPrefixStr);
						preview_file_size                 = JSON_Data.getStringValue (object, "preview_file_size"           , debugOutputPrefixStr);
						preview_url                       = JSON_Data.getStringValue (object, "preview_url"                 , debugOutputPrefixStr);
						previews                          = JSON_Data.getArrayValue  (object, "previews"                    , debugOutputPrefixStr);
						publishedfileid                   = JSON_Data.getStringValue (object, "publishedfileid"             , debugOutputPrefixStr);
						reactions__OPT                    = JSON_Data.getValue       (object, "reactions"                   , true, JSON_Data.Value.Type.Array, JSON_Data.Value::castToArrayValue, false, debugOutputPrefixStr);
						result                            = JSON_Data.getIntegerValue(object, "result"                      , debugOutputPrefixStr);
						revision                          = JSON_Data.getIntegerValue(object, "revision"                    , debugOutputPrefixStr);
						revision_change_number            = JSON_Data.getStringValue (object, "revision_change_number"      , debugOutputPrefixStr);
						short_description                 = JSON_Data.getStringValue (object, "short_description"           , debugOutputPrefixStr);
						show_subscribe_all                = JSON_Data.getBoolValue   (object, "show_subscribe_all"          , debugOutputPrefixStr);
						subscriptions                     = JSON_Data.getIntegerValue(object, "subscriptions"               , debugOutputPrefixStr);
						tags                              = JSON_Data.getArrayValue  (object, "tags"                        , debugOutputPrefixStr);
						time_created                      = JSON_Data.getIntegerValue(object, "time_created"                , debugOutputPrefixStr);
						time_updated                      = JSON_Data.getIntegerValue(object, "time_updated"                , debugOutputPrefixStr);
						title                             = JSON_Data.getStringValue (object, "title"                       , debugOutputPrefixStr);
						url                               = JSON_Data.getStringValue (object, "url"                         , debugOutputPrefixStr);
						views                             = JSON_Data.getIntegerValue(object, "views"                       , debugOutputPrefixStr);
						visibility                        = JSON_Data.getIntegerValue(object, "visibility"                  , debugOutputPrefixStr);
						vote_data                         = JSON_Data.getObjectValue (object, "vote_data"                   , debugOutputPrefixStr);
						workshop_accepted                 = JSON_Data.getBoolValue   (object, "workshop_accepted"           , debugOutputPrefixStr);
						workshop_file                     = JSON_Data.getBoolValue   (object, "workshop_file"               , debugOutputPrefixStr);
						
						if (!available_revisions.isEmpty()) throw new TraverseException("%s.%s:Array is not empty", debugOutputPrefixStr, "available_revisions");
						if (!children           .isEmpty()) throw new TraverseException("%s.%s:Array is not empty", debugOutputPrefixStr, "children" );
						if (!kvtags             .isEmpty()) throw new TraverseException("%s.%s:Array is not empty", debugOutputPrefixStr, "kvtags"   );
						if (!previews           .isEmpty()) throw new TraverseException("%s.%s:Array is not empty", debugOutputPrefixStr, "previews" );
						if (reactions__OPT!=null && !reactions__OPT.isEmpty()) throw new TraverseException("%s.%s:Array is not empty", debugOutputPrefixStr, "reactions");
						
						this.tags = new Vector<>();
						for (int i=0; i<tags.size(); i++)
							this.tags.add(new Tag(tags.get(i), debugOutputPrefixStr+".tags["+i+"]"));
						
						//    Block "GameInfos.Workshop.Entry.vote_data" [3]
						//       score:Integer
						//       score:Float
						//       votes_down:Integer
						//       votes_up:Integer
						vote_score = JSON_Data.getNumber      (vote_data, "score"     , debugOutputPrefixStr+".vote_data");
						votes_down = JSON_Data.getIntegerValue(vote_data, "votes_down", debugOutputPrefixStr+".vote_data");
						votes_up   = JSON_Data.getIntegerValue(vote_data, "votes_up"  , debugOutputPrefixStr+".vote_data");
						
						KNOWN_VALUES.scanUnexpectedValues(object); //, "GameInfos.Workshop.Entry");
					}
					
					static class Tag {
						private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues(Tag.class)
								.add("adminonly", JSON_Data.Value.Type.Bool)
								.add("tag"      , JSON_Data.Value.Type.String);
						//    Block "GameInfos.Workshop.Entry.tags[]" [2]
						//       adminonly:Bool
						//       tag:String
						final boolean adminonly;
						final String tag;
						Tag(Value<NV, V> value, String debugOutputPrefixStr) throws TraverseException {
							JSON_Object<NV, V> object = JSON_Data.getObjectValue(value, debugOutputPrefixStr);
							adminonly = JSON_Data.getBoolValue  (object, "adminonly", debugOutputPrefixStr);
							tag       = JSON_Data.getStringValue(object, "tag"      , debugOutputPrefixStr);
							KNOWN_VALUES.scanUnexpectedValues(object);
						}
					}

					String generateStringOutput() {
						ValueListOutput out = new ValueListOutput();
						out.add(0, "title                            ", title                            );
						out.add(0, "short_description                ", short_description                );
						out.add(0, "app_name                         ", app_name                         );
						
						if (!tags.isEmpty()) {
							Iterable<String> it = ()->tags.stream().map(tag->String.format("%s\"%s\"", tag.adminonly ? "[A]" : "", tag.tag)).iterator();
							out.add(0, "tags", "(%d tags)  %s", tags.size(), String.join(", ",it));
						}
						
						out.addEmptyLine();
						out.add(0, "url                              ", url                              );
						out.add(0, "preview_url                      ", preview_url                      );
						out.add(0, "preview_file_size                ", preview_file_size                );
						out.add(0, "banner                           ", banner                           );
						out.add(0, "language                         ", language                         );
						out.add(0, "revision                         ", revision                         );
						out.add(0, "revision_change_number           ", revision_change_number           );
						out.addEmptyLine();
						out.add(0, "time_created                     ", "%d  (%s)", time_created, TreeNodes.getTimeStr(time_created*1000));
						out.add(0, "time_updated                     ", "%d  (%s)", time_updated, TreeNodes.getTimeStr(time_updated*1000));
						out.addEmptyLine();
						out.add(0, "vote_score"                       , vote_score                       );
						out.add(0, "votes_down"                       , votes_down                       );
						out.add(0, "votes_up  "                       , votes_up                         );
						out.addEmptyLine();
						out.add(0, "publishedfileid                  ", publishedfileid                  );
						out.add(0, "consumer_appid                   ", consumer_appid                   );
						out.add(0, "consumer_shortcutid              ", consumer_shortcutid              );
						out.add(0, "creator                          ", creator                          );
						out.add(0, "creator_appid                    ", creator_appid                    );
						out.addEmptyLine();
						out.add(0, "views                            ", views                            );
						out.add(0, "favorited                        ", favorited                        );
						out.add(0, "subscriptions                    ", subscriptions                    );
						out.add(0, "followers                        ", followers                        );
						out.add(0, "lifetime_favorited               ", lifetime_favorited               );
						out.add(0, "lifetime_followers               ", lifetime_followers               );
						out.add(0, "lifetime_playtime                ", lifetime_playtime                );
						out.add(0, "lifetime_playtime_sessions       ", lifetime_playtime_sessions       );
						out.add(0, "lifetime_subscriptions           ", lifetime_subscriptions           );
						out.addEmptyLine();
						out.add(0, "file_size                        ", file_size                        );
						out.add(0, "file_type                        ", file_type                        );
						if (file_url__OPT!=null)
						out.add(0, "file_url__OPT                    ", file_url__OPT                    );
						out.add(0, "filename                         ", filename                         );
						out.add(0, "flags                            ", flags                            );
						out.add(0, "hcontent_file                    ", hcontent_file                    );
						out.add(0, "hcontent_preview                 ", hcontent_preview                 );
						if (maybe_inappropriate_sex__OPT!=null)
						out.add(0, "maybe_inappropriate_sex__OPT     ", maybe_inappropriate_sex__OPT     );
						if (maybe_inappropriate_violence__OPT!=null)
						out.add(0, "maybe_inappropriate_violence__OPT", maybe_inappropriate_violence__OPT);
						out.add(0, "num_children                     ", num_children                     );
						out.add(0, "num_comments_public              ", num_comments_public              );
						out.add(0, "num_reports                      ", num_reports                      );
						out.add(0, "result                           ", result                           );
						out.add(0, "show_subscribe_all               ", show_subscribe_all               );
						
						out.add(0, "can_be_deleted                   ", can_be_deleted                   );
						out.add(0, "can_subscribe                    ", can_subscribe                    );
						
						out.add(0, "visibility                       ", visibility                       );
						
						out.add(0, "banned                           ", banned                           );
						out.add(1, "ban_reason                       ", ban_reason                       );
						out.add(0, "workshop_accepted                ", workshop_accepted                );
						out.add(0, "workshop_file                    ", workshop_file                    );
						
						return out.generateOutput();
					}
				}
			}
			// "GameInfo.Block["community_items",V1].dataValue:Array"

			static class CommunityItems extends ParsedBlock {
				
				final Vector<CommunityItem> items;

				CommunityItems(JSON_Data.Value<NV, V> rawData, long version) {
					super(rawData, version, false);
					items = null;
				}
				CommunityItems(JSON_Data.Value<NV, V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					items = parseArray(CommunityItem::new, CommunityItem::new, blockDataValue, dataValueStr,file);
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || items.isEmpty());
				}

				static class CommunityItem {

					private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues(CommunityItem.class)
							.add("active"                  , JSON_Data.Value.Type.Bool   )
							.add("appid"                   , JSON_Data.Value.Type.Integer)
							.add("item_class"              , JSON_Data.Value.Type.Integer)
							.add("item_description"        , JSON_Data.Value.Type.String )
							.add("item_image_composed"     , JSON_Data.Value.Type.String ) // optional value
							.add("item_image_composed_foil", JSON_Data.Value.Type.String ) // optional value
							.add("item_image_large"        , JSON_Data.Value.Type.String )
							.add("item_image_small"        , JSON_Data.Value.Type.String )
							.add("item_key_values"         , JSON_Data.Value.Type.String ) // optional value
							.add("item_last_changed"       , JSON_Data.Value.Type.Integer)
							.add("item_movie_mp4"          , JSON_Data.Value.Type.String ) // optional value
							.add("item_movie_mp4_small"    , JSON_Data.Value.Type.String ) // optional value
							.add("item_movie_webm"         , JSON_Data.Value.Type.String ) // optional value
							.add("item_movie_webm_small"   , JSON_Data.Value.Type.String ) // optional value
							.add("item_name"               , JSON_Data.Value.Type.String )
							.add("item_series"             , JSON_Data.Value.Type.Integer)
							.add("item_title"              , JSON_Data.Value.Type.String )
							.add("item_type"               , JSON_Data.Value.Type.Integer);
					
					final JSON_Data.Value<NV, V> rawData;
					final boolean hasParsedData;

					final boolean   isActive;
					final long      appID;
					final long      itemClass;
					final String    itemDescription;
					final String    itemImageComposed;
					final String    itemImageComposedFoil;
					final String    itemImageLarge;
					final String    itemImageSmall;
					final String    itemKeyValues_str;
					final KeyValues itemKeyValues;
					final long      itemLastChanged;
					final String    itemMovieMp4;
					final String    itemMovieMp4Small;
					final String    itemMovieWebm;
					final String    itemMovieWebmSmall;
					final String    itemName;
					final long      itemSeries;
					final String    itemTitle;
					final long      itemType;

					CommunityItem(JSON_Data.Value<NV, V> rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						
						isActive              = false;
						appID                 = -1;
						itemName              = null;
						itemTitle             = null;
						itemDescription       = null;
						itemClass             = -1;
						itemSeries            = -1;
						itemType              = -1;
						itemLastChanged       = -1;
						itemImageLarge        = null;
						itemImageSmall        = null;
						itemKeyValues         = null;
						itemKeyValues_str     = null;
						itemImageComposed     = null;
						itemImageComposedFoil = null;
						itemMovieMp4          = null;
						itemMovieMp4Small     = null;
						itemMovieWebm         = null;
						itemMovieWebmSmall    = null;
					}
					CommunityItem(JSON_Data.Value<NV, V> value, String dataValueStr, File file) throws TraverseException {
						this.rawData = null;
						hasParsedData = true;
						
						JSON_Object<NV, V> object = JSON_Data.getObjectValue(value, dataValueStr);
						//DevHelper.optionalValues.scan(object, "TreeNodes.Player.GameInfos.CommunityItems.CommunityItem");
						
						isActive        = JSON_Data.getBoolValue   (object, "active"           , dataValueStr);
						appID           = JSON_Data.getIntegerValue(object, "appid"            , dataValueStr);
						itemName        = JSON_Data.getStringValue (object, "item_name"        , dataValueStr);
						itemTitle       = JSON_Data.getStringValue (object, "item_title"       , dataValueStr);
						itemDescription = JSON_Data.getStringValue (object, "item_description" , dataValueStr);
						itemClass       = JSON_Data.getIntegerValue(object, "item_class"       , dataValueStr);
						itemSeries      = JSON_Data.getIntegerValue(object, "item_series"      , dataValueStr);
						itemType        = JSON_Data.getIntegerValue(object, "item_type"        , dataValueStr);
						itemLastChanged = JSON_Data.getIntegerValue(object, "item_last_changed", dataValueStr);
						itemImageLarge  = JSON_Data.getStringValue (object, "item_image_large" , dataValueStr);
						itemImageSmall  = JSON_Data.getStringValue (object, "item_image_small" , dataValueStr);
						
						itemKeyValues_str     = JSON_Data.getValue(object, "item_key_values"         , true , JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, dataValueStr);
						itemImageComposed     = JSON_Data.getValue(object, "item_image_composed"     , true , JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, dataValueStr);
						itemImageComposedFoil = JSON_Data.getValue(object, "item_image_composed_foil", true , JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, dataValueStr);
						itemMovieMp4          = JSON_Data.getValue(object, "item_movie_mp4"          , true , JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, dataValueStr);
						itemMovieMp4Small     = JSON_Data.getValue(object, "item_movie_mp4_small"    , true , JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, dataValueStr);
						itemMovieWebm         = JSON_Data.getValue(object, "item_movie_webm"         , true , JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, dataValueStr);
						itemMovieWebmSmall    = JSON_Data.getValue(object, "item_movie_webm_small"   , true , JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, false, dataValueStr);
						
						if (itemKeyValues_str == null)
							itemKeyValues = null;
						else {
							String keyValuesLabel = dataValueStr+".item_key_values";
							JSON_Data.Value<NV, V> parsedKeyValues = JSONHelper.parseJsonText(itemKeyValues_str, keyValuesLabel);
							itemKeyValues = parse(KeyValues::new,KeyValues::new,parsedKeyValues,keyValuesLabel+"<parsed JSON structure>",file);
						}
						
						if (getClassLabel(itemClass)==null && itemClass!=5)
							DevHelper.unknownValues.add("GameInfos.CommunityItems.CommunityItem.itemClass = "+itemClass+"  <New Emum Value>");
						
						//DevHelper.unknownValues.add("CommunityItem.itemMovieMp4       = "+(itemMovieMp4      ==null ? "<null>" : "\""+itemMovieMp4      +"\""));
						//DevHelper.unknownValues.add("CommunityItem.itemMovieMp4Small  = "+(itemMovieMp4Small ==null ? "<null>" : "\""+itemMovieMp4Small +"\""));
						//DevHelper.unknownValues.add("CommunityItem.itemMovieWebm      = "+(itemMovieWebm     ==null ? "<null>" : "\""+itemMovieWebm     +"\""));
						//DevHelper.unknownValues.add("CommunityItem.itemMovieWebmSmall = "+(itemMovieWebmSmall==null ? "<null>" : "\""+itemMovieWebmSmall+"\""));
						KNOWN_VALUES.scanUnexpectedValues(object); //, "TreeNodes.Player.GameInfos.CommunityItems.CommunityItem");
					}
					
					String getURL(String urlPart) {
						if (hasParsedData)
							return String.format("https://cdn.cloudflare.steamstatic.com/steamcommunity/public/images/items/%d/%s", appID, urlPart);
						return "";
					}
					
					public static String getClassLabel(long itemClass) {
						switch ((int)itemClass) {
						case 1: return "Badge";
						case 2: return "Trading Card";
						case 3: return "Profil Background";
						case 4: return "Emoticon";
						default: return null;
						}
					}

					static class KeyValues {
						private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues(KeyValues.class)
								.add("card_border_color"     , JSON_Data.Value.Type.String ) // optional value
								.add("card_border_logo"      , JSON_Data.Value.Type.String ) // optional value
								.add("card_drop_method"      , JSON_Data.Value.Type.Integer) // optional value
								.add("card_drop_rate_minutes", JSON_Data.Value.Type.Integer) // optional value
								.add("card_drops_enabled"    , JSON_Data.Value.Type.Integer) // optional value
								.add("droprate"              , JSON_Data.Value.Type.Integer) // optional value
								.add("item_image_border"     , JSON_Data.Value.Type.String ) // optional value
								.add("item_image_border_foil", JSON_Data.Value.Type.String ) // optional value
								.add("item_release_state"    , JSON_Data.Value.Type.Integer) // optional value
								.add("notes"                 , JSON_Data.Value.Type.String ) // optional value
								.add("projected_release_date", JSON_Data.Value.Type.Integer) // optional value
								.add("level_images"          , JSON_Data.Value.Type.Object ) // optional value
								.add("level_names"           , JSON_Data.Value.Type.Object );// optional value
						
						private static final DevHelper.KnownJsonValues KNOWN_LEVEL_IMAGES_VALUES = new DevHelper.KnownJsonValues(KeyValues.class, "[LevelImages]")
								.add("1"   , JSON_Data.Value.Type.String)
								.add("2"   , JSON_Data.Value.Type.String)
								.add("3"   , JSON_Data.Value.Type.String)
								.add("4"   , JSON_Data.Value.Type.String)
								.add("5"   , JSON_Data.Value.Type.String)
								.add("foil", JSON_Data.Value.Type.String);
						
						private static final DevHelper.KnownJsonValues KNOWN_LEVEL_NAMES_VALUES = new DevHelper.KnownJsonValues(KeyValues.class, "[LevelValues]")
								.add("1"   , JSON_Data.Value.Type.String)
								.add("2"   , JSON_Data.Value.Type.String)
								.add("3"   , JSON_Data.Value.Type.String)
								.add("4"   , JSON_Data.Value.Type.String)
								.add("5"   , JSON_Data.Value.Type.String)
								.add("foil", JSON_Data.Value.Type.String)
								.add("1"   , JSON_Data.Value.Type.Object)
								.add("2"   , JSON_Data.Value.Type.Object)
								.add("3"   , JSON_Data.Value.Type.Object)
								.add("4"   , JSON_Data.Value.Type.Object)
								.add("5"   , JSON_Data.Value.Type.Object)
								.add("foil", JSON_Data.Value.Type.Object);

						final JSON_Data.Value<NV, V> rawData;
						final boolean hasParsedData;

						final String card_border_color;
						final String card_border_logo;
						final Long   card_drop_method;
						final Long   card_drop_rate_minutes;
						final Long   card_drops_enabled;
						final Long   droprate;
						final String item_image_border;
						final String item_image_border_foil;
						final Long   item_release_state;
						final String notes;
						final Long   projected_release_date;
						final Vector<Level> levels;

						public KeyValues(JSON_Data.Value<NV, V> rawData) {
							this.rawData = rawData;
							hasParsedData = false;
							card_border_color      = null;
							card_border_logo       = null;
							card_drop_method       = null;
							card_drop_rate_minutes = null;
							card_drops_enabled     = null;
							droprate               = null;
							item_image_border      = null;
							item_image_border_foil = null;
							item_release_state     = null;
							notes                  = null;
							projected_release_date = null;
							levels                 = null;
						}

						public KeyValues(JSON_Data.Value<NV, V> value, String dataValueStr, File file) throws TraverseException {
							this.rawData = null;
							hasParsedData = true;
							JSON_Object<NV, V> level_images, level_names;
							
							//DevHelper.scanJsonStructure(value,"CommunityItem.KeyValues",true);
							JSON_Object<NV, V> object = JSON_Data.getObjectValue(value, dataValueStr);
							card_border_color      = JSON_Data.getValue(object, "card_border_color"     , true , JSON_Data.Value.Type.String , JSON_Data.Value::castToStringValue , false, dataValueStr);
							card_border_logo       = JSON_Data.getValue(object, "card_border_logo"      , true , JSON_Data.Value.Type.String , JSON_Data.Value::castToStringValue , false, dataValueStr);
							card_drop_method       = JSON_Data.getValue(object, "card_drop_method"      , true , JSON_Data.Value.Type.Integer, JSON_Data.Value::castToIntegerValue, false, dataValueStr);
							card_drop_rate_minutes = JSON_Data.getValue(object, "card_drop_rate_minutes", true , JSON_Data.Value.Type.Integer, JSON_Data.Value::castToIntegerValue, false, dataValueStr);
							card_drops_enabled     = JSON_Data.getValue(object, "card_drops_enabled"    , true , JSON_Data.Value.Type.Integer, JSON_Data.Value::castToIntegerValue, false, dataValueStr);
							droprate               = JSON_Data.getValue(object, "droprate"              , true , JSON_Data.Value.Type.Integer, JSON_Data.Value::castToIntegerValue, false, dataValueStr);
							item_image_border      = JSON_Data.getValue(object, "item_image_border"     , true , JSON_Data.Value.Type.String , JSON_Data.Value::castToStringValue , false, dataValueStr);
							item_image_border_foil = JSON_Data.getValue(object, "item_image_border_foil", true , JSON_Data.Value.Type.String , JSON_Data.Value::castToStringValue , false, dataValueStr);
							item_release_state     = JSON_Data.getValue(object, "item_release_state"    , true , JSON_Data.Value.Type.Integer, JSON_Data.Value::castToIntegerValue, false, dataValueStr);
							notes                  = JSON_Data.getValue(object, "notes"                 , true , JSON_Data.Value.Type.String , JSON_Data.Value::castToStringValue , false, dataValueStr);
							projected_release_date = JSON_Data.getValue(object, "projected_release_date", true , JSON_Data.Value.Type.Integer, JSON_Data.Value::castToIntegerValue, false, dataValueStr);
							level_images           = JSON_Data.getValue(object, "level_images"          , true , JSON_Data.Value.Type.Object , JSON_Data.Value::castToObjectValue , false, dataValueStr);
							level_names            = JSON_Data.getValue(object, "level_names"           , true , JSON_Data.Value.Type.Object , JSON_Data.Value::castToObjectValue , false, dataValueStr);
							
							if (level_images!=null || level_names!=null) {
								levels = new Vector<>();
								for (String id:new String[] {"1","2","3","4","5","foil"}) {
									try {
										levels.add(new Level(id,level_images,level_names,dataValueStr));
									} catch (TraverseException e) {
										showException(e, file);
										levels.add(new Level(id,null,null,dataValueStr));
									}
								}
							} else
								levels = null;
							
							//String path = "TreeNodes.Player.GameInfos.CommunityItems.CommunityItem";
							if (level_images!=null)
								KNOWN_LEVEL_IMAGES_VALUES.scanUnexpectedValues(level_images); //, path+".KeyValues.level_images");
							if (level_names !=null)
								KNOWN_LEVEL_NAMES_VALUES.scanUnexpectedValues(level_names); //, path+".KeyValues.level_names");
							KNOWN_VALUES.scanUnexpectedValues(object); //, path);
						}
						
						static class Level {
							final String id;
							final String image;
							private final String name;
							private final HashMap<String,String> nameMap;
							
							public Level(String id, JSON_Object<NV, V> level_images, JSON_Object<NV, V> level_names, String debugOutputPrefixStr) throws TraverseException {
								this.id = id;
								
								if (level_images==null) image = null;
								else image = JSON_Data.getStringValue(level_images, id, debugOutputPrefixStr+".level_images");
								
								if (level_names ==null) {
									name  = null;
									nameMap = null;
								} else {
									String             name_str = JSON_Data.getValue(level_names, id, false, JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, true, debugOutputPrefixStr);
									JSON_Object<NV, V> name_obj = JSON_Data.getValue(level_names, id, false, JSON_Data.Value.Type.Object, JSON_Data.Value::castToObjectValue, true, debugOutputPrefixStr);
									if (name_str==null && name_obj==null)
										throw new TraverseException("%s isn't a StringValue or an ObjectValue", debugOutputPrefixStr+".level_names");
									
									name = name_str;
									if (name_obj==null) nameMap = null;
									else {
										nameMap = new HashMap<>();
										Vector<String> langNames = name_obj.getNames();
										for (String lang : langNames) {
											String name_lang = JSON_Data.getStringValue(name_obj, lang, debugOutputPrefixStr+".level_names."+id);
											if (!name_lang.isBlank()) nameMap.put(lang, name_lang);
										}
									}
								}
							}
							
							public String getName() {
								return getName(null);
							}
							public String getName(String lang) {
								if (name!=null)
									return name;
								
								if (nameMap!=null) {
									if (lang!=null)
										return nameMap.get(lang);
									
									if (nameMap.containsKey("english"))
										return nameMap.get("english");
									
									Vector<String> langs = new Vector<>( nameMap.keySet() );
									langs.sort(null);
									
									if (!langs.isEmpty())
										return nameMap.get(langs.firstElement());
								}
								
								return null;
							}
						}
					}
				}
				
			}
			static class Friends extends ParsedBlock {
				
				//    "TreeNodes.Player.GameInfos.Friends:Object"
				// Optional Values: [6 blocks]
				//    Block "TreeNodes.Player.GameInfos.Friends" [6]
				//       in_game:Array
				//       in_wishlist:Array
				//       owns:Array
				//       played_ever:Array
				//       played_recently:Array
				//       your_info:Object
				//       your_info == <null>
				//    Block "TreeNodes.Player.GameInfos.Friends.in_wishlist[]" [1]
				//       steamid:String
				//    Block "TreeNodes.Player.GameInfos.Friends.owns[]" [1]
				//       steamid:String
				//    Block "TreeNodes.Player.GameInfos.Friends.played_ever[]" [2]
				//       minutes_played_forever:Integer
				//       steamid:String
				//    Block "TreeNodes.Player.GameInfos.Friends.played_recently[]" [3]
				//       minutes_played:Integer
				//       minutes_played_forever:Integer
				//       steamid:String
				//    Block "TreeNodes.Player.GameInfos.Friends.your_info" [3]
				//       minutes_played:Integer
				//       minutes_played == <null>
				//       minutes_played_forever:Integer
				//       minutes_played_forever == <null>
				//       owned:Bool
				
				private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues(Friends.class)
						.add("in_game"        , JSON_Data.Value.Type.Array)
						.add("in_wishlist"    , JSON_Data.Value.Type.Array)
						.add("owns"           , JSON_Data.Value.Type.Array)
						.add("played_ever"    , JSON_Data.Value.Type.Array)
						.add("played_recently", JSON_Data.Value.Type.Array)
						.add("your_info"      , JSON_Data.Value.Type.Object); // optional
				
				private static final DevHelper.KnownJsonValues KNOWN_VALUES_STEAMID_ONLY = new DevHelper.KnownJsonValues(Friends.class, "[SteamID Only]")
						.add("steamid", JSON_Data.Value.Type.String);

				final SteamId[] in_wishlist;
				final SteamId[] owns;
				final Vector<Entry> played_ever;
				final Vector<Entry> played_recently;
				final Entry your_info;
				
				
				Friends(JSON_Data.Value<NV, V> rawData, long version) {
					super(rawData, version, false);
					in_wishlist     = null;
					owns            = null;
					played_ever     = null;
					played_recently = null;
					your_info       = null;
				}
				Friends(JSON_Data.Value<NV, V> blockDataValue, long version, String debugOutputPrefixStr, File file) throws TraverseException {
					super(null, version, true);
					String baseValueLabel = DevHelper.getValueLabelFor(Friends.class);
					//DevHelper.scanJsonStructure(blockDataValue, baseValueLabel, true);
					
					JSON_Object<NV, V> object = JSON_Data.getObjectValue(blockDataValue, debugOutputPrefixStr);
					
					JSON_Data.Value<NV, V> raw_your_info;
					JSON_Array<NV, V> raw_in_game, raw_in_wishlist, raw_owns, raw_played_ever, raw_played_recently;
					raw_in_game         = JSON_Data.getArrayValue(object, "in_game"        , debugOutputPrefixStr);
					raw_in_wishlist     = JSON_Data.getArrayValue(object, "in_wishlist"    , debugOutputPrefixStr);
					raw_owns            = JSON_Data.getArrayValue(object, "owns"           , debugOutputPrefixStr);
					raw_played_ever     = JSON_Data.getArrayValue(object, "played_ever"    , debugOutputPrefixStr);
					raw_played_recently = JSON_Data.getArrayValue(object, "played_recently", debugOutputPrefixStr);
					raw_your_info       = object.getValue("your_info");
					
					if (!raw_in_game.isEmpty()) {
						DevHelper.unknownValues.add(baseValueLabel+".in_game:Array is not empty");
						DevHelper.scanJsonStructure(raw_in_game, baseValueLabel+".in_game", true);
					}
					
					in_wishlist = new SteamId[raw_in_wishlist.size()];
					traverseArray(raw_in_wishlist, "in_wishlist", baseValueLabel, debugOutputPrefixStr, (i,obj,objectLabel,debugPrefixStr)->{
						in_wishlist[i] = parseSteamId(obj, objectLabel, debugPrefixStr);
						KNOWN_VALUES_STEAMID_ONLY.scanUnexpectedValues(obj, objectLabel);
					});
					
					owns = new SteamId[raw_owns.size()];
					traverseArray(raw_owns, "owns", baseValueLabel, debugOutputPrefixStr, (i,obj,objectLabel,debugPrefixStr)->{
						owns[i] = parseSteamId(obj, objectLabel, debugPrefixStr);
						KNOWN_VALUES_STEAMID_ONLY.scanUnexpectedValues(obj, objectLabel);
					});
					
					played_ever     = parseArray(Entry::new, Entry::new, raw_played_ever    , debugOutputPrefixStr+".played_ever"    , file);
					played_recently = parseArray(Entry::new, Entry::new, raw_played_recently, debugOutputPrefixStr+".played_recently", file);
					
					if (raw_your_info==null) your_info = null;
					else your_info = parse(Entry::new, Entry::new, raw_your_info, debugOutputPrefixStr+".your_info", file);
					
					KNOWN_VALUES.scanUnexpectedValues(object); //, baseValueLabel);
				}
				
				private interface ArrayElementTask {
					void doSomeThing(int index, JSON_Object<NV, V> obj, String objectLabel, String debugOutputPrefixStr) throws TraverseException;
				}

				private static void traverseArray(JSON_Array<NV, V> raw_array, String arrayName, String baseValueLabel, String debugOutputPrefixStr, ArrayElementTask task) throws TraverseException {
					for (int i=0; i<raw_array.size(); i++) {
						String objectDebugPrefixStr = debugOutputPrefixStr+"."+arrayName+"["+i+"]";
						JSON_Object<NV, V> obj = JSON_Data.getObjectValue(raw_array.get(i), objectDebugPrefixStr);
						task.doSomeThing(i,obj,baseValueLabel+"."+arrayName+"[]",objectDebugPrefixStr);
					}
				}
				
				private static SteamId parseSteamId(JSON_Object<NV, V> object, String objectLabel, String debugOutputPrefixStr) throws TraverseException {
					String steamidStr = JSON_Data.getStringValue(object, "steamid", debugOutputPrefixStr);
					Long steamid = parseLongNumber(steamidStr);
					if (steamid==null) DevHelper.unknownValues.add(objectLabel+".steamid:String can't be parsed as a number");
					return new SteamId(steamidStr, steamid);
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || (in_wishlist.length==0 && owns.length==0 && played_ever.isEmpty() && played_recently.isEmpty() && your_info==null));
				}
				
				static class Entry {
					
					//    "TreeNodes.Player.GameInfos.Friends.Entry:Object"
					// Optional Values: [1 blocks]
					//    Block "TreeNodes.Player.GameInfos.Friends.Entry" [4]
					//       minutes_played:Integer
					//       minutes_played == <null>
					//       minutes_played_forever:Integer
					//       minutes_played_forever == <null>
					//       owned:Bool
					//       owned == <null>
					//       steamid:String
					//       steamid == <null>
					
					private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues(Entry.class)
							.add("minutes_played"        , JSON_Data.Value.Type.Integer)  // optional
							.add("minutes_played_forever", JSON_Data.Value.Type.Integer)  // optional
							.add("owned"                 , JSON_Data.Value.Type.Bool   )  // optional
							.add("steamid"               , JSON_Data.Value.Type.String ); // optional

					final JSON_Data.Value<NV, V> rawData;
					final boolean hasParsedData;
					
					final Long minutes_played;
					final Long minutes_played_forever;
					final Boolean owned;
					final SteamId steamid;
					
					Entry(JSON_Data.Value<NV, V> rawData) {
						this.rawData = rawData;
						this.hasParsedData = false;
						minutes_played         = null;
						minutes_played_forever = null;
						owned                  = null;
						steamid                = null;
					}
					Entry(JSON_Data.Value<NV, V> value, String dataValueStr) throws TraverseException {
						this.rawData = null;
						this.hasParsedData = true;
						String baseValueLabel = "TreeNodes.Player.GameInfos.Friends.Entry";
						//DevHelper.scanJsonStructure(value, baseValueLabel, true);
						
						String steamidStr;
						JSON_Object<NV, V> object = JSON_Data.getObjectValue(value, dataValueStr);
						minutes_played         = JSON_Data.getValue(object, "minutes_played"        , true, JSON_Data.Value.Type.Integer, JSON_Data.Value::castToIntegerValue, false, dataValueStr);
						minutes_played_forever = JSON_Data.getValue(object, "minutes_played_forever", true, JSON_Data.Value.Type.Integer, JSON_Data.Value::castToIntegerValue, false, dataValueStr);
						owned                  = JSON_Data.getValue(object, "owned"                 , true, JSON_Data.Value.Type.Bool   , JSON_Data.Value::castToBoolValue   , false, dataValueStr);
						steamidStr             = JSON_Data.getValue(object, "steamid"               , true, JSON_Data.Value.Type.String , JSON_Data.Value::castToStringValue , false, dataValueStr);
						if (steamidStr==null) steamid = null;
						else {
							steamid = SteamId.parse(steamidStr);
							if (steamid.steamid==null)  DevHelper.unknownValues.add(baseValueLabel+".steamid:String can't be parsed as a number");
						}
						
						KNOWN_VALUES.scanUnexpectedValues(object); //, baseValueLabel);
					}
					
				}
			}

			static class Associations extends ParsedBlock {
				
				// "GameStateInfo.Block["associations",V1].dataValue.rgDevelopers:Array"
				// "GameStateInfo.Block["associations",V1].dataValue.rgDevelopers[].strName:String"
				// "GameStateInfo.Block["associations",V1].dataValue.rgDevelopers[].strURL:Null"
				// "GameStateInfo.Block["associations",V1].dataValue.rgDevelopers[].strURL:String"
				// "GameStateInfo.Block["associations",V1].dataValue.rgDevelopers[]:Object"
				// "GameStateInfo.Block["associations",V1].dataValue.rgFranchises:Array"
				// "GameStateInfo.Block["associations",V1].dataValue.rgFranchises[].strName:String"
				// "GameStateInfo.Block["associations",V1].dataValue.rgFranchises[].strURL:Null"
				// "GameStateInfo.Block["associations",V1].dataValue.rgFranchises[].strURL:String"
				// "GameStateInfo.Block["associations",V1].dataValue.rgFranchises[]:Object"
				// "GameStateInfo.Block["associations",V1].dataValue.rgPublishers:Array"
				// "GameStateInfo.Block["associations",V1].dataValue.rgPublishers[].strName:Null"
				// "GameStateInfo.Block["associations",V1].dataValue.rgPublishers[].strName:String"
				// "GameStateInfo.Block["associations",V1].dataValue.rgPublishers[].strURL:Null"
				// "GameStateInfo.Block["associations",V1].dataValue.rgPublishers[].strURL:String"
				// "GameStateInfo.Block["associations",V1].dataValue.rgPublishers[]:Object"
				// "GameStateInfo.Block["associations",V1].dataValue:Object"
				
				private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues(Associations.class)
						.add("rgDevelopers", JSON_Data.Value.Type.Array)
						.add("rgFranchises", JSON_Data.Value.Type.Array)
						.add("rgPublishers", JSON_Data.Value.Type.Array);
				
				final Vector<Association> developers;
				final Vector<Association> franchises;
				final Vector<Association> publishers;
				
				Associations(JSON_Data.Value<NV, V> rawData, long version) {
					super(rawData, version, false);
					developers = null;
					franchises = null;
					publishers = null;
				}
				Associations(JSON_Data.Value<NV, V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					JSON_Object<NV, V> object = JSON_Data.getObjectValue(blockDataValue, dataValueStr);
					developers = parseArray(Association::new, Association::new, object, "rgDevelopers", dataValueStr, file);
					franchises = parseArray(Association::new, Association::new, object, "rgFranchises", dataValueStr, file);
					publishers = parseArray(Association::new, Association::new, object, "rgPublishers", dataValueStr, file);
					KNOWN_VALUES.scanUnexpectedValues(object); //, "TreeNodes.Player.GameInfos.Associations");
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || (developers.isEmpty() && franchises.isEmpty() && publishers.isEmpty())) ;
				}
				
				static class Association {
					
					private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues(Association.class)
							.add("strName", JSON_Data.Value.Type.String)
							.add("strName", JSON_Data.Value.Type.Null  )
							.add("strURL" , JSON_Data.Value.Type.String)
							.add("strURL" , JSON_Data.Value.Type.Null  );
					
					final JSON_Data.Value<NV, V> rawData;
					final boolean hasParsedData;

					final String name;
					final String url;
					
					Association(JSON_Data.Value<NV, V> rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						name = null;
						url  = null;
					}
					Association(JSON_Data.Value<NV, V> value, String dataValueStr) throws TraverseException {
						this.rawData = null;
						hasParsedData = true;
						
						JSON_Object<NV, V> object = JSON_Data.getObjectValue(value, dataValueStr);
						//DevHelper.optionalValues.scan(object, "TreeNodes.Player.GameInfos.Associations.Association");
						
						       JSON_Data.getValue(object, "strName", false, JSON_Data.Value.Type.Null  , JSON_Data.Value::castToNullValue  , true, dataValueStr);
						       JSON_Data.getValue(object, "strURL" , false, JSON_Data.Value.Type.Null  , JSON_Data.Value::castToNullValue  , true, dataValueStr);
						name = JSON_Data.getValue(object, "strName", false, JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, true, dataValueStr);
						url  = JSON_Data.getValue(object, "strURL" , false, JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, true, dataValueStr);
						
						KNOWN_VALUES.scanUnexpectedValues(object); //, "TreeNodes.Player.GameInfos.Associations.Association");
					}
					
				}
			}
			static class SocialMedia extends ParsedBlock {
				
				// "GameStateInfo.Block["socialmedia",V3].dataValue:Array"
				// "GameStateInfo.Block["socialmedia",V3].dataValue[].eType:Integer"
				// "GameStateInfo.Block["socialmedia",V3].dataValue[].strName:String"
				// "GameStateInfo.Block["socialmedia",V3].dataValue[].strURL:String"
				// "GameStateInfo.Block["socialmedia",V3].dataValue[]:Object"
				
				final Vector<SocialMedia.Entry> entries;
				
				SocialMedia(JSON_Data.Value<NV, V> rawData, long version) {
					super(rawData, version, false);
					entries = null;
				}
				SocialMedia(JSON_Data.Value<NV, V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					entries = parseArray(Entry::new, Entry::new, blockDataValue, dataValueStr, file);
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || (entries.isEmpty())) ;
				}
				
				static class Entry {

					private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues(Entry.class)
							.add("eType"  , JSON_Data.Value.Type.Integer)
							.add("strName", JSON_Data.Value.Type.String )
							.add("strURL" , JSON_Data.Value.Type.String );
					
					enum Type {
						Twitter(4), Twitch(5), YouTube(6), Facebook(7);
						private long n;
						Type(long n) { this.n = n; }

						public static Type getType(long type) {
							for (Type t:values())
								if (t.n==type) return t;
							return null;
						}
					}

					final JSON_Data.Value<NV, V> rawData;
					final boolean hasParsedData;
					final Type type;
					final long typeN;
					final String name;
					final String url;
					
					Entry(JSON_Data.Value<NV, V> rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						type  = null;
						typeN = -1;
						name  = null;
						url   = null;
					}
					Entry(JSON_Data.Value<NV, V> value, String dataValueStr) throws TraverseException {
						this.rawData = null;
						hasParsedData = true;
						JSON_Object<NV, V> object = JSON_Data.getObjectValue(value, dataValueStr);
						typeN = JSON_Data.getIntegerValue(object, "eType"  , dataValueStr);
						name  = JSON_Data.getStringValue (object, "strName", dataValueStr);
						url   = JSON_Data.getStringValue (object, "strURL" , dataValueStr);
						type  = Type.getType(typeN);
						if (type==null) DevHelper.unknownValues.add("GameInfos.SocialMedia.SocialMediaEntry.type = "+type+"  <New Emum Value>");
						KNOWN_VALUES.scanUnexpectedValues(object); //, "GameInfos.SocialMedia.SocialMediaEntry");
					}
				}
			}
			
			static class ReleaseData extends ParsedBlock {
				
				// "GameInfo.Block["releasedata",V1].dataValue = <null>"
				
				ReleaseData(JSON_Data.Value<NV, V> rawData, long version) {
					super(rawData, version, false);
				}
				ReleaseData(JSON_Data.Value<NV, V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					//DevHelper.optionalJsonValues.scan(blockDataValue, "GameInfos.ReleaseData(V"+version+")");
					if (blockDataValue!=null) throw new TraverseException("%s != <null>. I have not expected any value.", dataValueStr);
				}
			}
			
			static class AppActivity extends ParsedBlock {
				
				// "GameInfo.Block["appactivity",V3].dataValue:String"
				
				final Base64String value;
				
				AppActivity(JSON_Data.Value<NV, V> rawData, long version) {
					super(rawData, version, false);
					value = null;
				}
				AppActivity(JSON_Data.Value<NV, V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					String str = JSON_Data.getStringValue(blockDataValue, dataValueStr);
					value = Base64String.parse(str, file);
					//DevHelper.unknownValues.add("GameInfos.AppActivity.value = "+toString(bytes)+"");
				}
				
				@SuppressWarnings("unused")
				private static String toString(byte[] bytes) {
					int min = Integer.MAX_VALUE;
					int max = 0;
					for (byte b:bytes) {
						int i = b<0 ? 256+(int)b : (int)b;
						min = Math.min(min, i);
						max = Math.max(max, i);
					}
					return String.format("<%d..%d> %s", min, max, Arrays.toString(bytes));
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || (value.isEmpty())) ;
				}
			}
			
			static class AchievementMap extends ParsedBlock {
				
				// "GameStateInfo.Block["achievementmap",V2].dataValue:String"
				// Unknown Labels: [10]
				//    "GameInfos.AchievementMap(V2):Array"
				//    "GameInfos.AchievementMap(V2)[]:Array"
				//    "GameInfos.AchievementMap(V2)[][0]:String"
				//    "GameInfos.AchievementMap(V2)[][1]:Object"
				//    "GameInfos.AchievementMap(V2)[][1].bAchieved:Bool"
				//    "GameInfos.AchievementMap(V2)[][1].flAchieved:Float"
				//    "GameInfos.AchievementMap(V2)[][1].flAchieved:Integer"
				//    "GameInfos.AchievementMap(V2)[][1].strDescription:String"
				//    "GameInfos.AchievementMap(V2)[][1].strImage:String"
				//    "GameInfos.AchievementMap(V2)[][1].strName:String"
				// Optional Values: [1 blocks]
				//    Block "GameInfos.AchievementMap(V2)[][]" [5]
				//       bAchieved:Bool
				//       flAchieved:Integer
				//       flAchieved:Float
				//       strDescription:String
				//       strImage:String
				//       strName:String
				
				//final Vector<Entry> entries;
				final Vector<GameEntries> gameEntries;
				final JSON_Data.Value<NV, V> parsedJsonValue;
				
				AchievementMap(JSON_Data.Value<NV, V> rawData, long version) {
					super(rawData, version, false);
					gameEntries = null;
					parsedJsonValue = null;
				}
				AchievementMap(JSON_Data.Value<NV, V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					
					String jsonText = JSON_Data.getStringValue(blockDataValue, dataValueStr);
					parsedJsonValue = JSONHelper.parseJsonText(jsonText, dataValueStr);
					
					if (isEmpty(parsedJsonValue, dataValueStr+"<ParsedJsonText>")) {
						gameEntries = new Vector<>();
						
					} else if (isOldVersion(parsedJsonValue, dataValueStr+"<ParsedJsonText>")) {
						gameEntries = new Vector<>();
						gameEntries.add(new GameEntries(-1, parsedJsonValue, dataValueStr+"<ParsedJsonText as Entry List>", file));
						
					} else {
						gameEntries = parseArray(GameEntries::new, GameEntries::new, parsedJsonValue, dataValueStr+"<ParsedJsonText as GameEntries List>", file);
					}
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || gameEntries.isEmpty());
				}
				
				private static boolean isEmpty(JSON_Data.Value<NV, V> parsedJsonValue, String debugOutputPrefixStr) throws TraverseException {
					JSON_Array<NV, V> array = JSON_Data.getArrayValue(parsedJsonValue, debugOutputPrefixStr);
					return array.isEmpty();
				}
				
				private static boolean isOldVersion(JSON_Data.Value<NV, V> parsedJsonValue, String debugOutputPrefixStr) throws TraverseException {
					JSON_Array<NV, V> array0 = JSON_Data.getArrayValue(parsedJsonValue, debugOutputPrefixStr);
					if (array0.isEmpty()) throw new TraverseException("%s:Array is empty", debugOutputPrefixStr);
					
					debugOutputPrefixStr = debugOutputPrefixStr+"[0]";
					JSON_Array<NV, V> array1 = JSON_Data.getArrayValue(array0.get(0), debugOutputPrefixStr);
					if (array1.isEmpty()) throw new TraverseException("%s:Array is empty", debugOutputPrefixStr);
					
					debugOutputPrefixStr = debugOutputPrefixStr+"[0]";
					JSON_Data.Value<NV, V> firstValue = array1.get(0);
					if (null!=JSON_Data.getValue(firstValue, JSON_Data.Value.Type.String , JSON_Data.Value::castToStringValue , true, debugOutputPrefixStr))
						return true;
					if (null!=JSON_Data.getValue(firstValue, JSON_Data.Value.Type.Integer, JSON_Data.Value::castToIntegerValue, true, debugOutputPrefixStr))
						return false;
					
					throw new TraverseException("%s isn't an IntegerValue or a StringValue (found %s)", debugOutputPrefixStr, firstValue.type);
				}

				static class GameEntries {
					
					final JSON_Data.Value<NV, V> rawData;
					final boolean hasParsedData;
					final long gameID;
					final Vector<Entry> entries;
					
					GameEntries(JSON_Data.Value<NV, V> rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						gameID = -1;
						entries = null;
					}
					
					GameEntries(JSON_Data.Value<NV, V> value, String debugOutputPrefixStr, File file) throws TraverseException {
						this.rawData = null;
						hasParsedData = true;
						
						JSON_Array<NV, V> array = JSON_Data.getArrayValue(value, debugOutputPrefixStr);
						if (array.size()!=2) throw new TraverseException("%s:Array has a length(==%d) != 2", debugOutputPrefixStr, array.size());
						
						JSON_Array<NV, V> rawEntries;
						gameID     = JSON_Data.getIntegerValue(array.get(0), debugOutputPrefixStr+"[0]");
						rawEntries = JSON_Data.getArrayValue  (array.get(1), debugOutputPrefixStr+"[1]");
						
						entries = parseArray(Entry::new, Entry::new, rawEntries, debugOutputPrefixStr+"[1]", file);
					}
					
					GameEntries(long gameID, JSON_Data.Value<NV, V> parsedJsonValue, String debugOutputPrefixStr, File file) throws TraverseException {
						this.rawData = null;
						hasParsedData = true;
						
						this.gameID = gameID;
						entries = parseArray(Entry::new, Entry::new, parsedJsonValue, debugOutputPrefixStr, file);
					}
				}
				
				static class Entry {
					
					private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues(Entry.class)
							.add("bAchieved"     , JSON_Data.Value.Type.Bool   )
							.add("bHidden"       , JSON_Data.Value.Type.Bool   )
							.add("flAchieved"    , JSON_Data.Value.Type.Integer)
							.add("flAchieved"    , JSON_Data.Value.Type.Float  )
							.add("strDescription", JSON_Data.Value.Type.String )
							.add("strImage"      , JSON_Data.Value.Type.String )
							.add("strName"       , JSON_Data.Value.Type.String );
					
					final JSON_Data.Value<NV, V> rawData;
					final boolean hasParsedData;
					
					final String id;
					final String name;
					final String image;
					final String description;
					final boolean isAchieved;
					final double achievedRatio;
					final Boolean isHidden;


					Entry(JSON_Data.Value<NV, V> rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						
						id            = null;
						name          = null;
						image         = null;
						description   = null;
						isAchieved    = false;
						achievedRatio = Double.NaN;
						isHidden      = null;
					}
					
					Entry(JSON_Data.Value<NV, V> value, String debugOutputPrefixStr) throws TraverseException {
						this.rawData = null;
						hasParsedData = true;
						
						JSON_Array<NV, V> array = JSON_Data.getArrayValue(value, debugOutputPrefixStr);
						if (array.size()!=2) throw new TraverseException("%s:Array has a length(==%d) != 2", debugOutputPrefixStr, array.size());
						
						JSON_Object<NV, V> object;
						id     = JSON_Data.getStringValue(array.get(0), debugOutputPrefixStr+"[0]");
						object = JSON_Data.getObjectValue(array.get(1), debugOutputPrefixStr+"[1]");
						
						// DevHelper.optional.jsonValues.scan(object, "GameInfos.AchievementMap.Entry");
						
						isHidden      = JSON_Data.getBoolValue   (object,"bHidden"       , true, false, debugOutputPrefixStr+"[1]");
						isAchieved    = JSON_Data.getBoolValue   (object,"bAchieved"     ,debugOutputPrefixStr+"[1]");
						achievedRatio = JSON_Data.getNumber      (object,"flAchieved"    ,debugOutputPrefixStr+"[1]");
						description   = JSON_Data.getStringValue (object,"strDescription",debugOutputPrefixStr+"[1]");
						image         = JSON_Data.getStringValue (object,"strImage"      ,debugOutputPrefixStr+"[1]");
						name          = JSON_Data.getStringValue (object,"strName"       ,debugOutputPrefixStr+"[1]");
						KNOWN_VALUES.scanUnexpectedValues(object); //, "GameInfos.AchievementMap.Entry(array[1])");
					}
				}
			}
			
			static class GameActivity extends ParsedBlock {
				
				// "GameStateInfo.Block["gameactivity",V1].dataValue:String"
				// "GameStateInfo.Block["gameactivity",V2].dataValue:Array"
				// "GameStateInfo.Block["gameactivity",V2].dataValue[]:String"
				// "GameStateInfo.Block["gameactivity",V3].dataValue:Array"
				// "GameStateInfo.Block["gameactivity",V3].dataValue[]:String"
				
				final Vector<Base64String> values;
				
				GameActivity(JSON_Data.Value<NV, V> rawData, long version) {
					super(rawData, version, false);
					values = null;
				}
				GameActivity(JSON_Data.Value<NV, V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					values = new Vector<>();
					
					JSON_Array<NV, V> array = JSON_Data.getValue(blockDataValue, JSON_Data.Value.Type.Array, JSON_Data.Value::castToArrayValue, true, dataValueStr);
					if (array!=null) {
						for (int i=0; i<array.size(); i++)
							values.add(Base64String.parse(JSON_Data.getStringValue(array.get(i), dataValueStr+"["+i+"]"),file));
						return;
					}
					
					String string = JSON_Data.getValue(blockDataValue, JSON_Data.Value.Type.String, JSON_Data.Value::castToStringValue, true, dataValueStr);
					if (string!=null) {
						values.add(Base64String.parse(string,file));
						return;
					}
					
					throw new TraverseException("%s is neither an ArrayValue nor a StringValue", dataValueStr);
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || (values.isEmpty())) ;
				}
				
			}
			
			static class UserNews extends ParsedBlock {
				// "GameStateInfo.Block["usernews",V2].dataValue:Array"
				// "GameStateInfo.Block["usernews",V2].dataValue[]:String"
				
				final Vector<Base64String> values;
				
				UserNews(JSON_Data.Value<NV, V> rawData, long version) {
					super(rawData, version, false);
					values = null;
				}
				UserNews(JSON_Data.Value<NV, V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					JSON_Array<NV, V> array = JSON_Data.getArrayValue(blockDataValue, dataValueStr);
					values = new Vector<>();
					for (int i=0; i<array.size(); i++)
						values.add(Base64String.parse(JSON_Data.getStringValue(array.get(i), dataValueStr+"["+i+"]"),file));
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || (values.isEmpty())) ;
				}
			}
			
			static class Achievements extends ParsedBlock {

				private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues(Achievements.class)
						.add("nAchieved"        , JSON_Data.Value.Type.Integer)
						.add("nTotal"           , JSON_Data.Value.Type.Integer)
						.add("vecAchievedHidden", JSON_Data.Value.Type.Array  )
						.add("vecUnachieved"    , JSON_Data.Value.Type.Array  )
						.add("vecHighlight"     , JSON_Data.Value.Type.Array  );
			
				final long achieved;
				final long total;
				final Vector<Achievement> achievedHidden;
				final Vector<Achievement> unachieved;
				final Vector<Achievement> highlight;
				
				Achievements(JSON_Data.Value<NV, V> rawData, long version) {
					super(rawData, version, false);
					achieved = -1;
					total    = -1;
					achievedHidden = null;
					unachieved = null;
					highlight = null;
				}

				Achievements(JSON_Data.Value<NV, V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					
					//DevHelper.optional.jsonValues.scan(blockDataValue, "GameInfos.Achievements(V"+version+")");
					
					JSON_Object<NV, V> object        = JSON_Data.getObjectValue (blockDataValue, dataValueStr);
					achieved                         = JSON_Data.getIntegerValue(object, "nAchieved"        , dataValueStr);
					total                            = JSON_Data.getIntegerValue(object, "nTotal"           , dataValueStr);
					JSON_Array<NV, V> unachieved     = JSON_Data.getArrayValue  (object, "vecUnachieved"    , dataValueStr);
					JSON_Array<NV, V> highlight      = JSON_Data.getArrayValue  (object, "vecHighlight"     , dataValueStr);
					JSON_Array<NV, V> achievedHidden = JSON_Data.getValue(object, "vecAchievedHidden", true, JSON_Data.Value.Type.Array, JSON_Data.Value::castToArrayValue, false, dataValueStr);
					
					this.achievedHidden = parseArray(Achievement::new, Achievement::new, achievedHidden, dataValueStr+"."+"vecAchievedHidden", file);
					this.unachieved     = parseArray(Achievement::new, Achievement::new, unachieved    , dataValueStr+"."+"vecUnachieved"    , file);
					this.highlight      = parseArray(Achievement::new, Achievement::new, highlight     , dataValueStr+"."+"vecHighlight"     , file);
					
					KNOWN_VALUES.scanUnexpectedValues(object); //, "TreeNodes.Player.GameInfos.Achievements");
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData || ((achievedHidden==null || achievedHidden.isEmpty()) && unachieved.isEmpty() && highlight.isEmpty() && achieved<=0)) ;
				}

				public String getTreeNodeExtraInfo() {
					if (hasParsedData && achieved!=0 && total!=0)
						return String.format("A:%d/%d", achieved, total);
					return "";
				}

				static class Achievement {

					private static final DevHelper.KnownJsonValues KNOWN_VALUES = new DevHelper.KnownJsonValues(Achievement.class)
							.add("bAchieved"        , JSON_Data.Value.Type.Bool   )
							.add("bHidden"          , JSON_Data.Value.Type.Bool   ) // optional
							.add("flAchieved"       , JSON_Data.Value.Type.Float  ) // optional
							.add("flAchieved"       , JSON_Data.Value.Type.Integer) // optional
							.add("flCurrentProgress", JSON_Data.Value.Type.Float  ) // optional
							.add("flCurrentProgress", JSON_Data.Value.Type.Integer) // optional
							.add("flMaxProgress"    , JSON_Data.Value.Type.Float  ) // optional
							.add("flMaxProgress"    , JSON_Data.Value.Type.Integer) // optional
							.add("flMinProgress"    , JSON_Data.Value.Type.Float  ) // optional
							.add("flMinProgress"    , JSON_Data.Value.Type.Integer) // optional
							.add("rtUnlocked"       , JSON_Data.Value.Type.Integer)
							.add("strDescription"   , JSON_Data.Value.Type.String )
							.add("strID"            , JSON_Data.Value.Type.String )
							.add("strImage"         , JSON_Data.Value.Type.String )
							.add("strName"          , JSON_Data.Value.Type.String );
					
					final JSON_Data.Value<NV, V> rawData;
					final boolean hasParsedData;
					final boolean isAchieved;
					final Boolean isHidden;
					final Double achievedRatio;
					final Double currentProgress;
					final Double maxProgress;
					final Double minProgress;
					final long unlocked;
					final String description;
					final String id;
					final String image;
					final String name;


					public Achievement(JSON_Data.Value<NV, V> rawData) {
						this.rawData = rawData;
						hasParsedData   = false;
						isAchieved      = false;
						isHidden        = null;
						achievedRatio   = null;
						currentProgress = null;
						maxProgress     = null;
						minProgress     = null;
						unlocked        = -1;
						description     = null;
						id              = null;
						image           = null;
						name            = null;
					}

					public Achievement(JSON_Data.Value<NV, V> value, String debugOutputPrefixStr) throws TraverseException {
						rawData = null;
						hasParsedData = true;
						//DevHelper.scanJsonStructure(value, Achievement.class); //,"GameStateInfo.Achievements.Achievement");
						//DevHelper.optional.jsonValues.scan(value, DevHelper.getValueLabelFor(Achievement.class));
						
						JSON_Object<NV, V> object = JSON_Data.getObjectValue(value, debugOutputPrefixStr);
						isAchieved      = JSON_Data.getBoolValue   (object, "bAchieved"        , debugOutputPrefixStr);
						isHidden        = JSON_Data.getBoolValue   (object, "bHidden"          , true, false, debugOutputPrefixStr);
						unlocked        = JSON_Data.getIntegerValue(object, "rtUnlocked"       , debugOutputPrefixStr);
						description     = JSON_Data.getStringValue (object, "strDescription"   , debugOutputPrefixStr);
						id              = JSON_Data.getStringValue (object, "strID"            , debugOutputPrefixStr);
						image           = JSON_Data.getStringValue (object, "strImage"         , debugOutputPrefixStr);
						name            = JSON_Data.getStringValue (object, "strName"          , debugOutputPrefixStr);
						achievedRatio   = JSON_Data.getNumber      (object, "flAchieved"       , true, debugOutputPrefixStr);
						currentProgress = JSON_Data.getNumber      (object, "flCurrentProgress", true, debugOutputPrefixStr);
						maxProgress     = JSON_Data.getNumber      (object, "flMaxProgress"    , true, debugOutputPrefixStr);
						minProgress     = JSON_Data.getNumber      (object, "flMinProgress"    , true, debugOutputPrefixStr);
						
						KNOWN_VALUES.scanUnexpectedValues(object); //, "TreeNodes.Player.GameInfos.Achievements.Achievement");
					}
					
				}
			}

			static class Badge extends ParsedBlock {
				
				private static final DevHelper.KnownJsonValues KNOWN_JSON_VALUES = new DevHelper.KnownJsonValues(Badge.class)
						.add("strName"         , JSON_Data.Value.Type.String)
						.add("bHasBadgeData"   , JSON_Data.Value.Type.Bool)
						.add("bMaxed"          , JSON_Data.Value.Type.Null)
						.add("dtNextRetry"     , JSON_Data.Value.Type.Null)
						.add("nMaxLevel"       , JSON_Data.Value.Type.Integer)
						.add("nLevel"          , JSON_Data.Value.Type.Integer)
						.add("nXP"             , JSON_Data.Value.Type.Integer)
						.add("strNextLevelName", JSON_Data.Value.Type.String)
						.add("nNextLevelXP"    , JSON_Data.Value.Type.Integer)
						.add("strIconURL"      , JSON_Data.Value.Type.String)
						.add("rgCards"         , JSON_Data.Value.Type.Array);
			
				final Vector<TradingCard> tradingCards;

				final String  name;
				final Boolean hasBadgeData;
				final long    maxLevel;
				final long    currentLevel;
				final long    currentXP;
				final String  nextLevelName;
				final long    nextLevelXP;
				final String  iconURL;


				Badge(JSON_Data.Value<NV, V> rawData, long version) {
					super(rawData, version, false);
					tradingCards  = null;
					name          = null;
					hasBadgeData  = null;
					maxLevel      = -1;
					currentLevel  = -1;
					currentXP     = -1;
					nextLevelName = null;
					nextLevelXP   = -1;
					iconURL       = null;
				}

				Badge(JSON_Data.Value<NV, V> blockDataValue, long version, String dataValueStr, File file) throws TraverseException {
					super(null, version, true);
					
					// DevHelper.optional.jsonValues.scan(blockDataValue, "GameInfos.Badge(V"+version+")");
					
					JSON_Object<NV, V> object = JSON_Data.getObjectValue (blockDataValue, dataValueStr);
					name          = JSON_Data.getStringValue (object, "strName"         , dataValueStr);
					hasBadgeData  = JSON_Data.getBoolValue   (object, "bHasBadgeData"   , true, false, dataValueStr);
					maxLevel      = JSON_Data.getIntegerValue(object, "nMaxLevel"       , dataValueStr);
					currentLevel  = JSON_Data.getIntegerValue(object, "nLevel"          , dataValueStr);
					currentXP     = JSON_Data.getIntegerValue(object, "nXP"             , dataValueStr);
					nextLevelName = JSON_Data.getStringValue (object, "strNextLevelName", dataValueStr);
					nextLevelXP   = JSON_Data.getIntegerValue(object, "nNextLevelXP"    , dataValueStr);
					iconURL       = JSON_Data.getStringValue (object, "strIconURL"      , dataValueStr);
					JSON_Data.getNullValue(object, "bMaxed", dataValueStr);
					JSON_Data.getNullValue(object, "dtNextRetry", true, false, dataValueStr);
					
					tradingCards = parseArray(TradingCard::new, TradingCard::new, object, "rgCards", dataValueStr, file);
					
					KNOWN_JSON_VALUES.scanUnexpectedValues(object); //, "TreeNodes.Player.GameInfos.Badge");
				}
				
				@Override boolean isEmpty() {
					return super.isEmpty() && (!hasParsedData) ;
				}

				String getTreeNodeExtraInfo() {
					if (!hasParsedData) return "";
					
					String str = "";
					if (currentLevel!=0)
						str += (str.isEmpty()?"":", ") + "B:"+currentLevel;
					
					int tcCount = 0;
					for (TradingCard tc:tradingCards) tcCount += tc.owned;
					if (tcCount>0)
						str += (str.isEmpty()?"":", ") + "TC:"+tcCount;
					
					return str;
				}

				File createTempTradingCardsOverviewHTML() {
					Path htmlPath;
					try { htmlPath = Files.createTempFile(null, ".html"); }
					catch (IOException e) {
						System.err.printf("IOException while createing a temporary file (*.html): %s%n", e.getMessage());
						return null;
					}
					System.out.printf("Create TradingCards Overview HTML: %s%n", htmlPath);
					File htmlFile = htmlPath.toFile();
					try (
						PrintWriter htmlOut = new PrintWriter(new OutputStreamWriter(new FileOutputStream(htmlFile), StandardCharsets.UTF_8));
						BufferedReader templateIn = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/html/templateTradingCardsOverview.html"), StandardCharsets.UTF_8)); 
					) {
						
						String line;
						while ( (line=templateIn.readLine())!=null ) {
							if (line.trim().equals("// write url array here"))
								writeTradingCardsOverviewHTML(htmlOut);
							else
								htmlOut.println(line);
						}
						
					} catch (FileNotFoundException e) {
						System.err.printf("FileNotFoundException while writing in a temporary file (\"%s\"): %s%n", htmlFile, e.getMessage());
					} catch (IOException e) {
						System.err.printf("IOException while writing in a temporary file (\"%s\"): %s%n", htmlFile, e.getMessage());
					}
					return htmlFile;
				}

				private void writeTradingCardsOverviewHTML(PrintWriter htmlOut) {
					if (hasParsedData)
						for (TradingCard tc:tradingCards) {
							htmlOut.printf("		new TradingCard(%s,%s,%s,%s),%n", toString(tc.name), tc.owned, toString(tc.imageURL), toString(tc.artworkURL));
						}
				}

				private String toString(String str) {
					if (str==null) return "null";
					return "\""+str+"\"";
				}

				static class TradingCard {
					
					private static final DevHelper.KnownJsonValues KNOWN_JSON_VALUES = new DevHelper.KnownJsonValues(TradingCard.class)
							.add("strName"      , JSON_Data.Value.Type.String)
							.add("strName"      , JSON_Data.Value.Type.Null)
							.add("strTitle"     , JSON_Data.Value.Type.String)
							.add("strTitle"     , JSON_Data.Value.Type.Null)
							.add("nOwned"       , JSON_Data.Value.Type.Integer)
							.add("strArtworkURL", JSON_Data.Value.Type.String)
							.add("strImgURL"    , JSON_Data.Value.Type.String)
							.add("strMarketHash", JSON_Data.Value.Type.String)
							.add("strMarketHash", JSON_Data.Value.Type.Null);

					final JSON_Data.Value<NV, V> rawData;
					final boolean hasParsedData;
					final String name;
					final String title;
					final long   owned;
					final String artworkURL;
					final String imageURL;
					final String marketHash;

					
					public TradingCard(JSON_Data.Value<NV, V> rawData) {
						this.rawData = rawData;
						hasParsedData = false;
						
						name  = null;
						title = null;
						owned = -1;
						artworkURL = null;
						imageURL   = null;
						marketHash = null;
					}

					public TradingCard(JSON_Data.Value<NV, V> value, String debugOutputPrefixStr) throws TraverseException {
						rawData = null;
						hasParsedData = true;
						
						JSON_Object<NV,V> object = JSON_Data.getObjectValue(value, debugOutputPrefixStr);
						name       = JSON_Data.getStringValue (object, "strName"      , false, true, debugOutputPrefixStr);
						title      = JSON_Data.getStringValue (object, "strTitle"     , false, true, debugOutputPrefixStr);
						owned      = JSON_Data.getIntegerValue(object, "nOwned"       , debugOutputPrefixStr);
						artworkURL = JSON_Data.getStringValue (object, "strArtworkURL", debugOutputPrefixStr);
						imageURL   = JSON_Data.getStringValue (object, "strImgURL"    , debugOutputPrefixStr);
						marketHash = JSON_Data.getStringValue (object, "strMarketHash", false, true, debugOutputPrefixStr);
						JSON_Data.getNullValue (object, "strName"      , false, true, debugOutputPrefixStr);
						JSON_Data.getNullValue (object, "strTitle"     , false, true, debugOutputPrefixStr);
						JSON_Data.getNullValue (object, "strMarketHash", false, true, debugOutputPrefixStr);
						
						KNOWN_JSON_VALUES.scanUnexpectedValues(object); //, "TreeNodes.Player.GameInfos.Badge.TradingCard");
					}
				}
			}
			
			static class Block {

				final JSON_Data.Value<NV, V> rawData;
				final boolean hasParsedData;
				final int blockIndex;
				final String label;
				final long version;
				final JSON_Data.Value<NV, V> dataValue;

				static Block createRawData(int blockIndex, JSON_Data.Value<NV, V> value) {
					try { return new Block(blockIndex, value, true); }
					catch (TraverseException e) { return null; }
				}

				Block(int blockIndex, JSON_Data.Value<NV, V> value) throws TraverseException {
					this(blockIndex, value, false);
				}
				Block(int blockIndex, JSON_Data.Value<NV, V> value, boolean asRawData) throws TraverseException {
					this.blockIndex = blockIndex;
					
					if (asRawData) {
						this.rawData = value;
						this.hasParsedData = false;
						this.label = null;
						this.version = -1;
						this.dataValue = null;
						
					} else {
						this.rawData = null;
						this.hasParsedData = true;
						
						String blockStr     = "GameInfos.Block["+blockIndex+"]";
						String labelStr     = blockStr+".value[0:label]";
						String blockdataStr = blockStr+".value[1:blockdata]";
						
						JSON_Array<NV, V> array = JSON_Data.getArrayValue(value, blockStr);
						if (array.size()!=2) throw new TraverseException("%s.value.length(==%d) != 2", blockStr, array.size());
						
											  label = JSON_Data.getStringValue(array.get(0), labelStr);
						JSON_Object<NV,V> blockdata = JSON_Data.getObjectValue(array.get(1), blockdataStr);
						if (blockdata.size()>2)  throw new TraverseException("%s JSON_Object.length(==%d) > 2: Too much values", blockdataStr, blockdata.size());
						if (blockdata.isEmpty()) throw new TraverseException("%s JSON_Object is empty: Too few values", blockdataStr);
						version   = JSON_Data.getIntegerValue(blockdata,"version", blockdataStr+".version");
						dataValue = blockdata.getValue("data");
						if (dataValue==null && blockdata.size()>1) throw new TraverseException("%s.data not found, but there are other values", blockdataStr);
					}
				}
			}
		}
	}

	static class AppManifest {
		
		private static final String prefix = "appmanifest_";
		private static final String suffix = ".acf";
		
		@SuppressWarnings("unused")
		private final int appID;
		final File file;
		private final VDFTreeNode vdfTree;
		
		AppManifest(int appID, File file) {
			this.appID = appID;
			this.file = file;
			
			if (this.file!=null) {
				
				VDFParser.Result vdfData = null;
				try { vdfData = VDFParser.parse(this.file,StandardCharsets.UTF_8); }
				catch (VDFParseException e) {}
				
				vdfTree = vdfData!=null ? vdfData.createVDFTreeNode() : null;
				
			} else
				vdfTree = null;
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

		String getGameTitle() {
			String appName = null;
			if (vdfTree!=null)
				try { appName = vdfTree.getString("AppState","name"); }
				catch (VDFTraverseException e) {}
			return appName;
		}
	}
	
	static class Game implements Comparable<Game>{
		
		final int appID;
		private final String title;
		final AppManifest appManifest;
		final HashMap<String, File> imageFiles;
		final HashMap<Long, File> steamCloudFolders;
		final HashMap<Long, ScreenShotLists.ScreenShotList> screenShots;
		final HashMap<Long, Player.GameInfos>  gameInfos_LibCache;
		final HashMap<Long, Player.AchievementProgress.AchievementProgressInGame>  achievementProgress;
		final HashMap<Long, Player.LocalConfig.SoftwareValveSteamApps.AppData>  gameInfos_LocCfg;
		
		Game(int appID, AppManifest appManifest, HashMap<String, File> imageFiles, HashMap<Long, Player> players) {
			this.appID = appID;
			this.appManifest = appManifest;
			this.imageFiles = imageFiles;
			title = appManifest==null ? null : appManifest.getGameTitle();
			
			steamCloudFolders = new HashMap<>();
			screenShots = new HashMap<>();
			gameInfos_LibCache = new HashMap<>();
			achievementProgress = new HashMap<>();
			gameInfos_LocCfg = new HashMap<>();
			players.forEach((playerID,player)->{
				if (playerID==null) return;
				
				copyValue(player.steamCloudFolders, steamCloudFolders, appID, playerID);
				copyValue(player.gameInfos        , gameInfos_LibCache, appID, playerID);
				
				if (player.screenShots!=null)
					copyValue(player.screenShots, screenShots, appID, playerID, v->!v.isEmpty());
				
				if (player.achievementProgress!=null && player.achievementProgress.gameStates!=null)
					copyValue(player.achievementProgress.gameStates, achievementProgress, appID, playerID);
				
				if (player.localconfig!=null) {
					if (player.localconfig.softwareValveSteamApps!=null && player.localconfig.softwareValveSteamApps.appsWithID!=null)
						copyValue(player.localconfig.softwareValveSteamApps.appsWithID, gameInfos_LocCfg, appID, playerID);
				}
			});
		}
		
		private static <ValueType> void copyValue(HashMap<Integer,ValueType> source, HashMap<Long,ValueType> target, int sourceKey, long targetKey) {
			copyValue(source, target, sourceKey, targetKey, v->true);
		}
		private static <ValueType> void copyValue(HashMap<Integer,ValueType> source, HashMap<Long,ValueType> target, int sourceKey, long targetKey, Predicate<ValueType> isOK) {
			ValueType value = source.get(sourceKey);
			if (value!=null && isOK.test(value)) target.put(targetKey, value);
		}

		static boolean hasGameATitle(Integer gameID) {
			if (gameID==null) return false;
			Game game = games.get(gameID);
			boolean hasATitle = game==null ? false : game.hasAFixedTitle();
			if (!hasATitle) {
				String storedTitle = knownGameTitles.get(gameID);
				hasATitle = storedTitle!=null && !storedTitle.isEmpty();
			}
			return hasATitle;
		}

		boolean hasAFixedTitle() {
			return title!=null;
		}

		String getTitle() {
			return getTitle(true);
		}

		String getTitle(boolean attachGameID) {
			if (title == null || title.isEmpty())
				return getStoredTitle(appID, attachGameID);
			if (!attachGameID)
				return title;
			return title+" ["+appID+"]";
		}

		static String getTitle(Integer gameID) {
			return getTitle(gameID, true);
		}

		static String getTitle(Integer gameID, boolean attachGameID) {
			if (gameID==null) return "Game ???";
			Game game = games.get(gameID);
			if (game!=null) return game.getTitle( attachGameID );
			return getStoredTitle(gameID, attachGameID);
		}

		static String getStoredTitle(int gameID) {
			return getStoredTitle(gameID, true);
		}

		static String getStoredTitle(int gameID, boolean attachGameID) {
			String storedTitle = knownGameTitles.get(gameID);
			if (storedTitle == null || storedTitle.isEmpty())
				return "Game "+gameID;
			if (!attachGameID)
				return storedTitle;
			return storedTitle+" ["+gameID+"]";
		}

		static Icon getIcon(Integer gameID, TreeIcons defaultIcon) {
			if (gameID != null) {
				Game game = games.get(gameID);
				if (game != null)
					return game.getIcon();
			}
			return defaultIcon==null ? null : defaultIcon.getIcon();
		}

		Icon getIcon() {
			if (imageFiles!=null) {
				File iconImageFile = imageFiles.get("icon");
				if (iconImageFile!=null) {
					try {
						BufferedImage image = ImageIO.read(iconImageFile);
						return IconSource.getScaledIcon(image, 16, 16);
					} catch (IOException e) {}
				}
			}
			return null;
		}

		@Override
		public int compareTo(Game other) {
			if (other==null) return -1;
			if (this.title!=null && other.title==null) return -1;
			if (this.title==null && other.title!=null) return +1;
			if (this.title!=null && other.title!=null) {
				int n = this.title.compareTo(other.title);
				if (n!=0) return n;
			}
			return this.appID-other.appID;
		}
		
		static class GameSortOption implements SortOption {
			
			private final String label;
			private final Comparator<Game> order;

			GameSortOption(String label, Comparator<Game> order) {
				this.label = label;
				this.order = order;
			}

			@Override public String toString() { return label; }
			Comparator<Game> getOrder() { return order; }

			static GameSortOption[] createOptionList() {
				GameSortOption[] gameSortOptions = new GameSortOption[players.size()];
				Vector<Long> keys = new Vector<>(players.keySet());
				keys.sort(null);
				for (int i=0; i<keys.size(); i++) {
					Player player = players.get(keys.get(i));
					gameSortOptions[i] = new GameSortOption("Sort by Last Play Time of "+player.getName(true), createLastPlayedOrder(player));
				}
				return gameSortOptions;
			}

			private static Comparator<Game> createLastPlayedOrder(Player player) {
				return Comparator
						.<Game,Long>comparing(game->getLastPlayed(game,player.playerID),Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(Comparator.nullsLast(Comparator.naturalOrder()));
			}

			private static Long getLastPlayed(Game game, long playerID) {
				if (game==null) return null;
				Player.LocalConfig.SoftwareValveSteamApps.AppData appData = game.gameInfos_LocCfg.get(playerID);
				if (appData==null) return null;
				return appData.lastPlayed_ks;
			}
			
		}
	}

	static class GameImages {
		
		@SuppressWarnings("unused")
		private final File folder;
		final Vector<File> otherFiles;
		final Vector<File> imageFiles;
		private final HashMatrix<Integer, String, File> appImages;
	
		GameImages(File folder) {
			this.folder = folder;
			File[] files = TreeNodes.getFilesAndFolders(folder);
			
			otherFiles = new Vector<>();
			imageFiles = new Vector<>();
			appImages = new HashMatrix<>();
			
			for (File file:files) {
				if (file.isDirectory()) {
					otherFiles.add(file);
					
				} else if (TreeNodes.isImageFile(file)) {
					ImageFileName ifn = ImageFileName.parse(file.getName());
					if (ifn==null || ifn.label==null || ifn.number==null)
						imageFiles.add(file);
					else
						appImages.put(ifn.number, ifn.label, file);
					
				} else
					otherFiles.add(file);
			}
		}
		
		public Collection<? extends Integer> getGameIDs() {
			return appImages.keySet1;
		}

		public Vector<Integer> getSortedGameIDs() {
			Vector<Integer> keySet1 = new Vector<>(getGameIDs());
			keySet1.sort(null);
			return keySet1;
		}
	
		public Vector<String> getSortedImageTypes() {
			Vector<String>  keySet2 = new Vector<>(appImages.keySet2);
			keySet2.sort(null);
			return keySet2;
		}
	
		public File getImageFile(Integer gameID, String ImageType) { return appImages.get(gameID, ImageType); }
		public File getImageFile(String ImageType, Integer gameID) { return appImages.get(gameID, ImageType); }
	
		public HashMap<String, File> getImageFileMap(Integer gameID) {
			return appImages.getMapCopy(gameID);
		}
		
		public File[] getImageFileArrays(Integer gameID) {
			Collection<File> files = appImages.getCollection(gameID);
			return files.toArray(new File[files.size()]);
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
			
			HashMap<KeyType2, ValueType> getMapCopy(KeyType1 key1) {
				HashMap<KeyType2, ValueType> map = matrix.get(key1);
				if (map==null) return null;
				return new HashMap<>(map);
			}
			
			Collection<ValueType> getCollection(KeyType1 key1) {
				HashMap<KeyType2, ValueType> map = matrix.get(key1);
				return map.values();
			}
			
			ValueType get(KeyType1 key1, KeyType2 key2) {
				HashMap<KeyType2, ValueType> map = matrix.get(key1);
				if (map==null) return null;
				return map.get(key2);
			}
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
				Integer number = parseNumber(numberStr);
				if (number==null) return null;
				return new ImageFileName(number,labelStr);
			}
			
		}
	}
	static class ScreenShotLists extends HashMap<Integer,ScreenShotLists.ScreenShotList>{
		private static final long serialVersionUID = -428703055699412094L;
		
		final File folder;
	
		ScreenShotLists(File folder) { 
			File subFolder = new File(folder,"remote");
			if (subFolder.isDirectory()) {
				this.folder = subFolder;
				File[] folders = subFolder.listFiles(file->file.isDirectory() && parseNumber(file.getName())!=null);
				for (File gameFolder:folders) {
					Integer gameID = parseNumber(gameFolder.getName());
					File imagesFolder = new File(gameFolder,"screenshots");
					if (imagesFolder.isDirectory()) {
						File thumbnailsFolder = new File(imagesFolder,"thumbnails");
						if (!thumbnailsFolder.isDirectory()) thumbnailsFolder = null;
						put(gameID, new ScreenShotList(imagesFolder,thumbnailsFolder));
					}
				}
			} else
				this.folder = null;
		}
		
		static class ScreenShotList extends Vector<ScreenShot> {
			private static final long serialVersionUID = 8285684141839919150L;
			
			final File imagesFolder;
			final File thumbnailsFolder;
			
			public ScreenShotList(File imagesFolder, File thumbnailsFolder) {
				this.imagesFolder = imagesFolder;
				this.thumbnailsFolder = thumbnailsFolder;
				File[] imageFiles = imagesFolder.listFiles(TreeNodes::isImageFile);
				for (File image:imageFiles) {
					File thumbnail = null;
					if (thumbnailsFolder!=null)
						thumbnail = new File(thumbnailsFolder,image.getName());
					add(new ScreenShot(image,thumbnail));
				}
			}
		}
	}
	static class ScreenShot implements Comparable<ScreenShot> {
		final File image;
		final File thumbnail;
		ScreenShot(File image, File thumbnail) {
			this.image = image;
			this.thumbnail = thumbnail;
			if (image==null || !image.isFile())
				throw new IllegalArgumentException();
		}
		@Override public int compareTo(ScreenShot other) {
			if (other==null) return -1;
			return this.image.getAbsolutePath().compareTo(other.image.getAbsolutePath());
		}
	}
}