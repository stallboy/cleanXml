package cleanXml;

import org.w3c.dom.*;

import java.util.ArrayList;
import java.util.List;

//主要用于收集没用到的属性或子，打印出来
public class XElement {

    public static class XAttr {
        private final Attr attr;
        private boolean used;

        public XAttr(Attr a) {
            attr = a;
        }
    }

    public static class XEle {
        private final Element ele;
        private boolean used;

        public XEle(Element e) {
            ele = e;
        }

        public Element getEle() {
            return ele;
        }

        public boolean isUnused() {
            return !used;
        }

        public void setUsed() {
            used = true;
        }
    }

    private final Element ele;
    private final List<XAttr> xAttrs = new ArrayList<>();
    private final List<XEle> xElements = new ArrayList<>();


    public XElement(Element ele) {
        this.ele = ele;
        NamedNodeMap attributes = ele.getAttributes();
        for (int i = 0; i < attributes.getLength(); ++i) {
            Attr a = (Attr) attributes.item(i);
            xAttrs.add(new XAttr(a));
        }

        NodeList childNodes = ele.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            org.w3c.dom.Node node = childNodes.item(i);
            if (org.w3c.dom.Node.ELEMENT_NODE != node.getNodeType())
                continue;
            Element e = (Element) node;
            xElements.add(new XEle(e));
        }
    }

    public boolean hasAttr(String attr) {
        for (XAttr xAttr : xAttrs) {
            if (xAttr.attr.getName().equals(attr)) {
                return true;
            }
        }
        return false;
    }

    public String useAttrAssure(String attr) {
        for (XAttr xAttr : xAttrs) {
            if (xAttr.attr.getName().equals(attr)) {
                xAttr.used = true;
                return xAttr.attr.getValue();
            }
        }
        throw new IllegalArgumentException(String.format("%s[%s] 不存在", ele.getTagName(), attr));
    }

    public Element getChildElementByTagAssure1Or0(String tag) {
        Element t = null;
        for (XEle xElement : xElements) {
            if (xElement.ele.getTagName().equals(tag)) {
                if (t != null) {
                    throw new IllegalArgumentException(String.format("%s<%s> 有多个，应该只有1个或0个", ele.getTagName(), tag));
                }
                t = xElement.ele;
                xElement.used = true;
            }
        }
        return t;
    }

    public List<XEle> getChildXElementsStartWithUpperLetter() {
        List<XEle> res = new ArrayList<>();
        for (XEle xe : xElements) {
            if (Character.isUpperCase(xe.ele.getTagName().charAt(0))) {
                res.add(xe);
            }
        }
        return res;
    }

    @SuppressWarnings("unused")
    public List<XEle> getChildXElements() {
        return xElements;
    }

    public void printUnused() {
        for (XAttr xAttr : xAttrs) {
            if (!xAttr.used) {
                System.out.printf("%s[%s] = %s 未使用%n", ele.getTagName(), xAttr.attr.getName(), xAttr.attr.getValue());
            }
        }

        for (XEle xElement : xElements) {
            if (!xElement.used) {
                System.out.printf("%s<%s> 未使用%n", ele.getTagName(), xElement.ele.getTagName());
            }
        }
    }

}

