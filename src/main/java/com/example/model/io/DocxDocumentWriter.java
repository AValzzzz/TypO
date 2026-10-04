package com.example.model.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.w3c.dom.Element;

public final class DocxDocumentWriter {

    private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private static final String ROOT_RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
            <Relationship Id="rId1" Type="%s/officeDocument" Target="word/document.xml"/>\
            </Relationships>""".formatted(REL_NS);

    private static final String ITEM_RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
            <Relationship Id="rId1" Type="%s/customXmlProps" Target="itemProps1.xml"/>\
            </Relationships>""".formatted(REL_NS);

    private static final String ITEM_PROPS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <ds:datastoreItem ds:itemID="{6B1F5C3E-2D4A-4E8B-9C7A-1F0E3A5B7D91}" \
            xmlns:ds="http://schemas.openxmlformats.org/officeDocument/2006/customXml">\
            <ds:schemaRefs><ds:schemaRef ds:uri="urn:typo:docx-sidecar:1"/></ds:schemaRefs>\
            </ds:datastoreItem>""";

    private static final String STYLES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">\
            <w:docDefaults><w:rPrDefault><w:rPr>\
            <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Calibri" w:cs="Calibri"/>\
            <w:sz w:val="24"/><w:szCs w:val="24"/><w:lang w:val="fr-FR"/>\
            </w:rPr></w:rPrDefault>\
            <w:pPrDefault><w:pPr><w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr></w:pPrDefault>\
            </w:docDefaults>\
            <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>\
            <w:style w:type="character" w:default="1" w:styleId="DefaultParagraphFont">\
            <w:name w:val="Default Paragraph Font"/><w:uiPriority w:val="1"/><w:semiHidden/></w:style>\
            <w:style w:type="character" w:styleId="Hyperlink"><w:name w:val="Hyperlink"/>\
            <w:basedOn w:val="DefaultParagraphFont"/><w:uiPriority w:val="99"/>\
            <w:rPr><w:color w:val="0563C1"/><w:u w:val="single"/></w:rPr></w:style>\
            <w:style w:type="paragraph" w:styleId="Footer"><w:name w:val="footer"/><w:basedOn w:val="Normal"/>\
            <w:pPr><w:jc w:val="center"/></w:pPr><w:rPr><w:sz w:val="20"/><w:szCs w:val="20"/></w:rPr></w:style>\
            <w:style w:type="paragraph" w:customStyle="1" w:styleId="TypOCode"><w:name w:val="TypO Code"/>\
            <w:basedOn w:val="Normal"/><w:pPr><w:shd w:val="clear" w:color="auto" w:fill="F2F2F2"/></w:pPr>\
            <w:rPr><w:rFonts w:ascii="Consolas" w:hAnsi="Consolas" w:cs="Consolas"/></w:rPr></w:style>\
            </w:styles>""";

    private static final String FOOTER = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" \
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">\
            <w:p><w:pPr><w:pStyle w:val="Footer"/><w:jc w:val="center"/></w:pPr>\
            <w:fldSimple w:instr=" PAGE "><w:r><w:rPr><w:sz w:val="20"/></w:rPr><w:t>1</w:t></w:r></w:fldSimple>\
            </w:p></w:ftr>""";

    public void write(List<PageContent> pages, Path target) throws IOException {
        WordXmlWriter w = new WordXmlWriter();
        String document = w.documentXml(pages); 

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", contentTypes(w));
            put(zip, "_rels/.rels", ROOT_RELS);
            put(zip, "word/document.xml", document);
            put(zip, "word/_rels/document.xml.rels", documentRels(w));
            put(zip, "word/styles.xml", STYLES);
            put(zip, "word/footer1.xml", FOOTER);
            for (Map.Entry<String, byte[]> m : w.media().entrySet())
                put(zip, m.getKey(), m.getValue());
            put(zip, "customXml/item1.xml", w.sidecar().toXml());
            put(zip, "customXml/_rels/item1.xml.rels", ITEM_RELS);
            put(zip, "customXml/itemProps1.xml", ITEM_PROPS);
        }
        Files.write(target, bytes.toByteArray());
    }

    private static String contentTypes(WordXmlWriter w) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        for (String ext : w.mediaExtensions()) {
            String type = switch (ext) {
                case "jpg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "bmp" -> "image/bmp";
                default -> "image/png";
            };
            sb.append("<Default Extension=\"").append(ext).append("\" ContentType=\"").append(type).append("\"/>");
        }
        sb.append("<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>");
        sb.append("<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>");
        sb.append("<Override PartName=\"/word/footer1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml\"/>");
        sb.append("<Override PartName=\"/customXml/itemProps1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.customXmlProperties+xml\"/>");
        return sb.append("</Types>").toString();
    }

    private static String documentRels(WordXmlWriter w) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"" + REL_NS + "/styles\" Target=\"styles.xml\"/>"
                + "<Relationship Id=\"" + WordXmlWriter.FOOTER_REL + "\" Type=\"" + REL_NS
                + "/footer\" Target=\"footer1.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"" + REL_NS
                + "/customXml\" Target=\"../customXml/item1.xml\"/>"
                + w.relationshipsXml() + "</Relationships>";
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        put(zip, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    public List<PageContent> read(Path source) throws IOException {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipFile zip = new ZipFile(source.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory())
                    continue;
                try (InputStream in = zip.getInputStream(e)) {
                    parts.put(e.getName(), in.readAllBytes());
                }
            }
        }
        try {
            return new WordXmlReader(parts, mainPart(parts)).read();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Document Word invalide : " + e.getMessage(), e);
        }
    }

    private static String mainPart(Map<String, byte[]> parts) {
        byte[] rels = parts.get("_rels/.rels");
        if (rels != null) {
            try {
                var nl = Ooxml.parse(rels).getDocumentElement().getElementsByTagNameNS("*", "Relationship");
                for (int i = 0; i < nl.getLength(); i++) {
                    Element r = (Element) nl.item(i);
                    if (r.getAttribute("Type").endsWith("/officeDocument")) {
                        String t = r.getAttribute("Target");
                        return t.startsWith("/") ? t.substring(1) : t;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return "word/document.xml";
    }
}