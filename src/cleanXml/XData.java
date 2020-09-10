package cleanXml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.lang.reflect.*;
import java.util.*;

//类似XStream功能
public class XData {
    private final String scanPkg;
    private final Map<Class<?>, XClass> xClassMap = new IdentityHashMap<>();
    private Set<Class<?>> scan;
    private Map<String, XClass> tagToXClassMapCache;
    private boolean changed;

    public XData(String scanPkg) {
        this.scanPkg = scanPkg;
    }

    enum XType {
        INT,
        FLOAT,
        BOOL,
        STR,

        CLASS,
        LIST,
    }

    static class XParam {
        String name; //code里的名字
        Field field;

        XType xType;
        XClass xClass;

        String alias;
        XConverter converter;
        boolean explicit;
        boolean hasDefaultValue;
        Object defaultValue;

        String attr() {
            if (alias != null) {
                return alias;
            }
            return name;
        }

        boolean nullable() {
            return hasDefaultValue && defaultValue == null;
        }
    }

    enum XClassType {
        INTERFACE,
        CLASS,
        ENUM,
    }

    static class XClass {
        Class<?> rawClass;
        Constructor<?> rawConstructor;
        String name;

        XClassType xClassType;
        List<XClass> xImplClassList;
        List<XParam> xParamList;
        List<Object> xEnumObjList;

        String alias;
        XConverter converter;
        boolean paramNoOrder;

        String tag() {
            if (alias != null) {
                return alias;
            }
            return name;
        }

        boolean isTagOk(String tag) {
            switch (xClassType) {
                case INTERFACE -> {
                    for (XClass xClass : xImplClassList) {
                        if (xClass.tag().equals(tag)) {
                            return true;
                        }
                    }
                }
                case CLASS -> {
                    return tag().equals(tag);
                }
            }
            return false;
        }

        XParam getXParamAssure(String paramName) {
            if (xParamList == null) {
                throw new IllegalArgumentException(String.format("类型%s是接口或枚举，没有参数", name));
            }

            for (XParam xParam : xParamList) {
                if (xParam.name.equals(paramName)) {
                    return xParam;
                }
            }
            throw new IllegalArgumentException(String.format("%s 没找到参数%s", name, paramName));
        }

        Object getEnumObjAssure(String str) {
            for (Object o : xEnumObjList) {
                if (o.toString().equalsIgnoreCase(str))
                    return o;
            }
            throw new IllegalArgumentException(String.format("%s 枚举没找到%s", name, str));
        }
    }


    public XClass register(Class<?> cls) {
        XClass xClass = xClassMap.get(cls);
        if (xClass != null) {
            return xClass;
        }

        changed = true;
        if (cls.isInterface()) {
            return registerInterface(cls);
        } else if (cls.isEnum()) {
            return registerEnum(cls);
        } else if (cls.isPrimitive() || cls == String.class || cls.isArray() || Collection.class.isAssignableFrom(cls)) {
            throw new IllegalArgumentException("不支持注册原子或聚合类型");
        } else {
            return registerCls(cls);
        }
    }

    public XClass register(Constructor<?> constructor) {
        Class<?> cls = constructor.getDeclaringClass();
        XClass xClass = xClassMap.get(cls);
        if (xClass != null) {
            return xClass;
        }

        changed = true;
        return registerCls(cls, constructor);
    }

    private XClass registerEnum(Class<?> cls) {
        XClass xClass = new XClass();
        xClass.xClassType = XClassType.ENUM;
        xClass.rawClass = cls;
        xClass.name = cls.getSimpleName();
        xClass.xEnumObjList = new ArrayList<>();
        xClassMap.put(cls, xClass);

        xClass.xEnumObjList.addAll(Arrays.asList(cls.getEnumConstants()));
        return xClass;
    }

    private XClass registerInterface(Class<?> cls) {
        XClass xClass = new XClass();
        xClass.xClassType = XClassType.INTERFACE;
        xClass.rawClass = cls;
        xClass.name = cls.getSimpleName();
        xClass.xImplClassList = new ArrayList<>();
        xClassMap.put(cls, xClass);

        if (scan == null) {
            scan = PackageScanUtils.scan(scanPkg, true);
        }

        for (Class<?> aClass : scan) {
            int modifiers = aClass.getModifiers();
            if (!Modifier.isAbstract(modifiers) && Modifier.isPublic(modifiers) &&
                    ClassUtils.getAllInterfaces(aClass).contains(cls)) {

                XClass xSubClass = register(aClass);
                if (xSubClass != null) {
                    xClass.xImplClassList.add(xSubClass);
                }

            }
        }
        return xClass;
    }

    private XClass registerCls(Class<?> cls) {
        Constructor<?>[] constructors = cls.getConstructors();
        if (constructors.length != 1) {
            throw new IllegalArgumentException(cls + " 有多个构造器");
        }
        Constructor<?> constructor = constructors[0];
        return registerCls(cls, constructor);
    }

    private XClass registerCls(Class<?> cls, Constructor<?> constructor) {
        XClass xClass = new XClass();
        xClass.xClassType = XClassType.CLASS;
        xClass.rawClass = cls;
        constructor.setAccessible(true);
        xClass.rawConstructor = constructor;
        xClass.name = cls.getSimpleName();
        xClass.xParamList = new ArrayList<>();
        xClassMap.put(cls, xClass);

        for (Parameter parameter : constructor.getParameters()) {
            Class<?> type = parameter.getType();
            XParam xp = new XParam();
            xp.name = parameter.getName();
            if (!Character.isLowerCase(xp.name.charAt(0))) {
                throw new RuntimeException(
                        String.format("类:%s 构造器的形参名字:%s 应该以小写开头", xClass.name, xp.name));
            }

            xp.field = ClassUtils.getField(cls, xp.name); //注意这里的假设，假设每个形参都有一个相同名字的实例变量，存储相同的值
            if (xp.field == null) {
                throw new RuntimeException(
                        String.format("类:%s 中没有成员变量:%s，构造器里的形参名应该都有对应的类成员变量名", xClass.name, xp.name));
            }

            if (type.isPrimitive()) {
                if (type == int.class) {
                    xp.xType = XType.INT;
                } else if (type == float.class) {
                    xp.xType = XType.FLOAT;
                } else if (type == boolean.class) {
                    xp.xType = XType.BOOL;
                } else {
                    throw new IllegalArgumentException(String.format("不支持%s这个原子类型", type));
                }

            } else if (type == String.class) {
                xp.xType = XType.STR;

            } else if (type.isArray()) {
                throw new IllegalArgumentException("不支持Array类型");

            } else if (Collection.class.isAssignableFrom(type)) {
                xp.xType = XType.LIST;
                ParameterizedType t = (ParameterizedType) parameter.getParameterizedType();
                Class<?> ele = (Class<?>) t.getActualTypeArguments()[0];
                xp.xClass = register(ele);

            } else {
                xp.xType = XType.CLASS;
                xp.xClass = register(type);
            }

            xClass.xParamList.add(xp);
        }
        return xClass;
    }


    private XClass getXClassAssure(Class<?> cls) {
        XClass xClass = xClassMap.get(cls);
        if (xClass == null) {
            throw new IllegalArgumentException("请先注册类型" + cls);
        }
        return xClass;
    }

    private XParam getXParamAssure(Class<?> cls, String paramName) {
        return getXClassAssure(cls).getXParamAssure(paramName);
    }

    public void alias(String alias, Class<?> cls) {
        getXClassAssure(cls).alias = alias;
        changed = true;
    }

    public void alias(String paramAlias, Class<?> cls, String paramName) {
        getXParamAssure(cls, paramName).alias = paramAlias;
        changed = true;
    }


    public void noOrder(Class<?> cls) {
        XClass xClass = getXClassAssure(cls);
        if (xClass.xClassType != XClassType.CLASS) {
            throw new IllegalArgumentException(String.format("类型%s是接口或枚举，无需配置noOrder", cls));
        }
        xClass.paramNoOrder = true;
        changed = true;
    }


    public void explicit(Class<?> cls, String paramName) {
        getXParamAssure(cls, paramName).explicit = true;
        changed = true;
    }

    public void defaultValue(Object defaultValue, Class<?> cls, String paramName) {
        XParam xParam = getXParamAssure(cls, paramName);
        xParam.hasDefaultValue = true;
        xParam.defaultValue = defaultValue;
        changed = true;
    }

    public void converter(XConverter converter, Class<?> cls) {
        XClass xClass = getXClassAssure(cls);
        if (xClass.xClassType == XClassType.ENUM) {
            throw new IllegalArgumentException(String.format("类型%s是枚举，无需设置converter", cls));
        }

        xClass.converter = converter;
        changed = true;
    }

    public void converter(XConverter converter, Class<?> cls, String paramName) {
        getXParamAssure(cls, paramName).converter = converter;
        changed = true;
    }


    public void check() {
        changed = false;
        tagToXClassMapCache = new HashMap<>();
        for (XClass xClass : xClassMap.values()) {
            if (xClass.xClassType == XClassType.CLASS) {
                //检查是否能生成
                checkOne(xClass);
                //cache下来
                String tag = xClass.tag();
                if (tagToXClassMapCache.put(tag, xClass) != null) {
                    throw new IllegalStateException(tag + " tag重复了");
                }
            }
        }
    }

    private void checkOne(XClass xClass) {
        if (xClass.converter != null) {
            return;
        }

        Set<Class<?>> listClassSet = new HashSet<>();
        List<XParam> testXParams = new ArrayList<>();
        boolean explicit = false;
        for (XParam xParam : xClass.xParamList) {
            if ((!xParam.explicit) &&
                    (xParam.xType == XType.LIST || xParam.xType == XType.CLASS) &&
                    xParam.xClass.xClassType != XClassType.ENUM) {
                //默认全展开作为子element,但万一有2个类型相同，xml一定要包一层参数名为tag的element
                testXParams.add(xParam);
                if (!listClassSet.add(xParam.xClass.rawClass)) {
                    explicit = true;
                    break;
                }
            }
        }

        if (explicit) {
            for (XParam xParam : testXParams) {
                xParam.explicit = true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T fromXmlString(String xml) {
        return (T) fromXmlElementToObj(DomUtils.stringToElement(xml));
    }

    @SuppressWarnings({"unchecked", "unused"})
    public <T> T fromXmlElement(Element ele) {
        return (T) fromXmlElementToObj(ele);
    }

    @SuppressWarnings("unchecked")
    public <T> T fromXmlElementAssure(Element ele) {
        Object t = fromXmlElementToObj(ele);
        if (t == null) {
            throw new RuntimeException("反序列化失败:\n" + DomUtils.elementToString(ele));
        }
        return (T) t;
    }

    public Object fromXmlElementToObj(Element ele) {
        if (changed) {
            check();
        }

        if (ele == null) {
            return null;
        }

        XClass xClass = tagToXClassMapCache.get(ele.getTagName());
        if (xClass == null) {
            return null;
        }


        XElement xEle = new XElement(ele);
        if (xClass.converter != null) {
            Object res = xClass.converter.fromXmlElement(xEle);
            if (res != null) {
                xEle.printUnused();
                return res;
            }
        }

        if (xClass.xParamList == null) {
            return null;
        }

        Object[] params = new Object[xClass.xParamList.size()];
        List<XElement.XEle> childElements = xEle.getChildXElementsStartWithUpperLetter(); //注意这里的假设，假设构造器的形参都小写字母开头
        int childIdx = 0;
        int paramIdx = 0;
        for (XParam xParam : xClass.xParamList) {
            String attr = xParam.attr();
            Object v = null;
            if (xParam.converter != null) {
                v = xParam.converter.fromXmlElement(xEle);
            }
            if (v == null) {  //如果返回为null，使用系统方式
                switch (xParam.xType) {
                    case INT -> {
                        if (xEle.hasAttr(attr)) {
                            v = Integer.parseInt(xEle.useAttrAssure(attr));
                        } else {
                            require(xParam.hasDefaultValue, String.format("缺少属性%s[%s]", ele.getTagName(), attr));
                            v = xParam.defaultValue;
                        }
                    }
                    case FLOAT -> {
                        if (xEle.hasAttr(attr)) {
                            v = Float.parseFloat(xEle.useAttrAssure(attr));
                        } else {
                            require(xParam.hasDefaultValue, String.format("缺少属性%s[%s]", ele.getTagName(), attr));
                            v = xParam.defaultValue;
                        }
                    }
                    case BOOL -> {
                        if (xEle.hasAttr(attr)) {
                            v = Boolean.parseBoolean(xEle.useAttrAssure(attr));
                        } else {
                            require(xParam.hasDefaultValue, String.format("缺少属性%s[%s]", ele.getTagName(), attr));
                            v = xParam.defaultValue;
                        }
                    }
                    case STR -> {
                        if (xEle.hasAttr(attr)) {
                            v = xEle.useAttrAssure(attr);
                        } else {
                            require(xParam.hasDefaultValue, String.format("缺少属性%s[%s]", ele.getTagName(), attr));
                            v = xParam.defaultValue;
                        }
                    }
                    case CLASS -> {
                        if (xParam.xClass.xClassType == XClassType.ENUM) {
                            String vStr = xEle.useAttrAssure(attr);
                            v = xParam.xClass.getEnumObjAssure(vStr);

                        } else if (xParam.explicit) {
                            Element attrEle = xEle.getChildElementByTagAssure1Or0(attr);
                            if (attrEle == null) {
                                require(xParam.hasDefaultValue, String.format("缺少子元素%s<%s>", ele.getTagName(), attr));
                                v = xParam.defaultValue;
                            } else {
                                Element attrChild = DomUtils.getChildElementAssure1(attrEle);
                                v = fromXmlElementAssure(attrChild);
                                require(xParam.xClass.rawClass.isInstance(v), String.format("子元素%s<%s>里非%s类型",
                                        ele.getTagName(), attr, xParam.xClass.tag()));

                            }

                        } else if (xClass.paramNoOrder) {
                            XElement.XEle choose = null;
                            for (XElement.XEle child : childElements) {
                                if (child.isUnused() && xParam.xClass.isTagOk(child.getEle().getTagName())) {
                                    choose = child;
                                    break;
                                }
                            }

                            if (choose != null) {
                                choose.setUsed();
                                v = fromXmlElementAssure(choose.getEle());
                            } else {
                                require(xParam.hasDefaultValue, String.format("%s.%s 没找到类型为%s的子元素",
                                        ele.getTagName(), attr, xParam.xClass.tag()));
                                v = xParam.defaultValue;
                            }

                        } else { //按顺序一个一个来
                            if (childIdx < childElements.size()) {
                                XElement.XEle child = childElements.get(childIdx);
                                if (xParam.xClass.isTagOk(child.getEle().getTagName())) {
                                    child.setUsed();
                                    childIdx++;
                                    v = fromXmlElementAssure(child.getEle());

                                } else {
                                    require(xParam.hasDefaultValue, String.format("%s.%s 下一个元素是%s，不符合类型%s要求",
                                            ele.getTagName(), attr, child.getEle().getTagName(), xParam.xClass.tag()));
                                    v = xParam.defaultValue;
                                }
                            } else {
                                require(xParam.hasDefaultValue, String.format("%s.%s 无符合类型%s要求的子元素了",
                                        ele.getTagName(), attr, xParam.xClass.tag()));
                                v = xParam.defaultValue;
                            }
                        }
                    }
                    case LIST -> {
                        List<Object> list = new ArrayList<>();
                        v = list;
                        if (xParam.explicit) {
                            Element attrEle = xEle.getChildElementByTagAssure1Or0(attr);
                            if (attrEle != null) { //这里假设所有的list都empty able
                                for (Element childElement : DomUtils.getChildElements(attrEle)) {
                                    Object c = fromXmlElementAssure(childElement);
                                    require(xParam.xClass.rawClass.isInstance(c), String.format("%s.%s 下子元素%s不符合类型%s要求",
                                            ele.getTagName(), attr, childElement.getTagName(), xParam.xClass.tag()));
                                    list.add(c);
                                }
                            }

                        } else if (xClass.paramNoOrder) {
                            for (XElement.XEle child : childElements) {
                                if (child.isUnused() && xParam.xClass.isTagOk(child.getEle().getTagName())) {
                                    child.setUsed();
                                    Object c = fromXmlElementAssure(child.getEle());
                                    list.add(c);
                                }
                            }

                        } else {
                            while (childIdx < childElements.size()) { //按顺序一个一个来
                                XElement.XEle child = childElements.get(childIdx);
                                if (xParam.xClass.isTagOk(child.getEle().getTagName())) {
                                    child.setUsed();
                                    childIdx++;
                                    Object c = fromXmlElementAssure(child.getEle());
                                    list.add(c);
                                } else {
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            params[paramIdx++] = v;
        }

        xEle.printUnused();

        try {
            return xClass.rawConstructor.newInstance(params);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void require(boolean ok, String err) {
        if (!ok) {
            throw new IllegalArgumentException(err);
        }
    }

    public String toXmlString(Object obj) {
        Document doc = DomUtils.newDocument();
        Element tmp = doc.createElement("tmp");
        if (!toXmlElement(obj, tmp)) {
            return null;
        }
        Element thisE = DomUtils.getChildElementAssure1(tmp);
        return DomUtils.elementToString(thisE);
    }

    public void toXmlElementAssure(Object obj, Element parentEle) {
        if (!toXmlElement(obj, parentEle)) {
            throw new RuntimeException(String.format("%s 类型为%s，序列化到xml失败", obj, obj.getClass()));
        }
    }

    public boolean toXmlElement(Object obj, Element parentEle) {
        if (changed) {
            check();
        }

        XClass xClass = xClassMap.get(obj.getClass());
        if (xClass == null) {
            return false;
        }

        if (xClass.converter != null) {
            boolean res = xClass.converter.toXmlElement(obj, parentEle);
            if (res) {
                return true;
            }
        }

        if (xClass.xParamList == null) {
            return false;
        }

        Element ele = DomUtils.newChild(parentEle, xClass.tag());

        for (XParam xParam : xClass.xParamList) {
            try {
                if (xParam.converter != null) {
                    Object v = xParam.field.get(obj);
                    if (xParam.converter.toXmlElement(v, ele)) {
                        continue;
                    }
                }

                String attr = xParam.attr();
                switch (xParam.xType) {
                    case INT -> {
                        int v = xParam.field.getInt(obj);
                        ele.setAttribute(attr, String.valueOf(v));
                    }
                    case FLOAT -> {
                        float v = xParam.field.getFloat(obj);
                        ele.setAttribute(attr, String.valueOf(v));
                    }
                    case BOOL -> {
                        boolean v = xParam.field.getBoolean(obj);
                        ele.setAttribute(attr, String.valueOf(v));
                    }
                    case STR -> {
                        Object v = xParam.field.get(obj);
                        if (v != null) {
                            ele.setAttribute(attr, v.toString());
                        } else {
                            require(xParam.nullable(), String.format("%s.%s需要不为null", xClass.name, attr));
                        }
                    }
                    case CLASS -> {
                        Object v = xParam.field.get(obj);
                        if (v == null) {
                            require(xParam.nullable(), String.format("%s.%s需要不为null", xClass.name, attr));

                        } else if (xParam.xClass.xClassType == XClassType.ENUM) {
                            ele.setAttribute(attr, v.toString());

                        } else if (xParam.explicit) {
                            Element attrEle = DomUtils.newChild(ele, attr);
                            boolean ok = toXmlElement(v, attrEle);
                            require(ok, String.format("%s.%s 类型为%s, 生成xml失败", xClass.name, attr, v.getClass()));

                        } else {
                            boolean ok = toXmlElement(v, ele);
                            require(ok, String.format("%s.%s 类型为%s, 生成xml失败", xClass.name, attr, v.getClass()));
                        }
                    }
                    case LIST -> {
                        Object v = xParam.field.get(obj);
                        @SuppressWarnings("unchecked")
                        List<Object> list = (List<Object>) v;
                        Element attrEle = ele;
                        if (xParam.explicit) {
                            attrEle = DomUtils.newChild(ele, attr);
                        }
                        for (Object o : list) {
                            boolean ok = toXmlElement(o, attrEle);
                            require(ok, String.format("%s.%s 中元素类型类型为%s, 生成xml失败", xClass.name, attr, o.getClass()));
                        }
                    }
                }
            } catch (IllegalAccessException exception) {
                throw new RuntimeException(exception);
            }
        }

        return true;
    }

    @SuppressWarnings("unused")
    public void print() {
        print(XClassType.INTERFACE);
        print(XClassType.CLASS);
        print(XClassType.ENUM);
    }

    private void print(XClassType dst) {
        for (XClass value : xClassMap.values()) {
            if (value.xClassType == dst) {
                print(value);
            }
        }
    }

    private void print(XClass xClass) {
        StringBuilder sb = new StringBuilder();
        String name = xClass.tag();

        switch (xClass.xClassType) {
            case INTERFACE:
                sb.append("----").append(name);
                for (XClass aClass : xClass.xImplClassList) {
                    sb.append("\n      ").append(aClass.name);
                }
                break;

            case CLASS:
                sb.append(name).append("(");
                for (XParam xParam : xClass.xParamList) {
                    sb.append("\n      ").append(xParam.attr()).append(":");
                    sb.append(xParam.xType);
                    if (xParam.xClass != null) {
                        sb.append("<").append(xParam.xClass.name).append(">");
                    }
                }
                if (xClass.xParamList.size() > 0) {
                    sb.append("\n      ");
                }
                sb.append(")");
                break;

            case ENUM:
                sb.append("####").append(name);
                for (Object enumObj : xClass.xEnumObjList) {
                    sb.append("\n      ").append(enumObj);
                }
                break;
        }

        System.out.println(sb);
    }
}
