package ua.museclass.musicxml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Маленьке дерево XML, як parseXML у прототипі: ім'я, атрибути, діти і
 * власний текст. MusicXML невеликий, а розбір по дереву читається простіше,
 * ніж потоковий.
 */
final class Element {
    private static final String FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed";
    private static final Pattern LEADING_NUMBER =
            Pattern.compile("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    final String name;
    final Map<String, String> attrs = new HashMap<>();
    final List<Element> kids = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();

    private Element(String name) {
        this.name = name;
    }

    /**
     * Корінь документа. DTD не завантажується (XmlPullParser зовнішніх сутностей
     * не тягне), невідомі сутності в relaxed-режимі лишаються текстом.
     */
    static Element parse(String src) throws MusicXmlException {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser p = factory.newPullParser();
            try {
                p.setFeature(FEATURE_RELAXED, true);
            } catch (XmlPullParserException ignored) {
                // не кожна реалізація вміє relaxed — тоді суворий розбір
            }
            p.setInput(new StringReader(src));
            Deque<Element> stack = new ArrayDeque<>();
            Element root = null;
            for (int ev = p.next(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev == XmlPullParser.START_TAG) {
                    Element e = new Element(p.getName());
                    for (int i = 0; i < p.getAttributeCount(); i++) {
                        e.attrs.put(p.getAttributeName(i), p.getAttributeValue(i));
                    }
                    if (stack.isEmpty()) {
                        if (root == null) root = e;
                    } else {
                        stack.peek().kids.add(e);
                    }
                    stack.push(e);
                } else if (ev == XmlPullParser.END_TAG) {
                    if (!stack.isEmpty()) stack.pop();
                } else if (ev == XmlPullParser.TEXT && !stack.isEmpty()) {
                    stack.peek().text.append(p.getText());
                }
            }
            return root;
        } catch (XmlPullParserException | IOException e) {
            throw new MusicXmlException("Це не схоже на XML.", e);
        }
    }

    String attr(String key) {
        return attrs.get(key);
    }

    /** Власний текст без пробілів по краях. */
    String text() {
        return text.toString().trim();
    }

    static Element kid(Element n, String name) {
        if (n == null) return null;
        for (Element k : n.kids) if (k.name.equals(name)) return k;
        return null;
    }

    static List<Element> kids(Element n, String name) {
        if (n == null) return Collections.emptyList();
        List<Element> out = new ArrayList<>();
        for (Element k : n.kids) if (k.name.equals(name)) out.add(k);
        return out;
    }

    /** Текст дитини name; порожній рядок, якщо її немає. */
    static String txt(Element n, String name) {
        Element k = kid(n, name);
        return k == null ? "" : k.text();
    }

    /** Число з тексту дитини, як parseFloat: «2abc» → 2, нечисло → def. */
    static double num(Element n, String name, double def) {
        Matcher m = LEADING_NUMBER.matcher(txt(n, name));
        return m.find() ? Double.parseDouble(m.group()) : def;
    }
}
