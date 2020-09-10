package cleanXml;

import org.w3c.dom.Element;

public interface XConverter {
    Object fromXmlElement(XElement xEle); //如果返回为null，使用系统方式

    boolean toXmlElement(Object obj, Element parentEle); //返回是否成功，如果false，使用系统方式
}
