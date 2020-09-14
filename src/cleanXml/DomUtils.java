package cleanXml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DomUtils {

    public static List<Element> getChildElements(Element ele) {
        NodeList nodeList = ele.getChildNodes();
        List<Element> children = new ArrayList<>();
        for (int i = 0, end = nodeList.getLength(); i < end; ++i) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                children.add((Element) node);
            }
        }
        return children;
    }

    public static List<Element> getChildElementsAssureSize(Element ele, int size) {
        List<Element> childElements = getChildElements(ele);
        if (childElements.size() != size) {
            throw new IllegalArgumentException(
                    String.format("%s需要大小为%d,实际大小为%d", ele.getTagName(), size, childElements.size()));
        }
        return childElements;
    }

    public static Element getChildElementAssure1(Element ele) {
        List<Element> childElements = getChildElementsAssureSize(ele, 1);
        return childElements.get(0);
    }

    public static Document newDocument() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Element newChild(Element parent, String tag) {
        Element e = parent.getOwnerDocument().createElement(tag);
        parent.appendChild(e);
        return e;
    }

    public static Element stringToElement(String xml) {
        try {
            return DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                    .getDocumentElement();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String elementToString(Element ele) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            DOMSource source = new DOMSource(ele);
            StreamResult result = new StreamResult(new StringWriter());
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(source, result);

            return result.getWriter().toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
