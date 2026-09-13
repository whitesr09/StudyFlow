package app.studyflow;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** Bounded, offline OOXML visual preview. Not an Office pagination or animation engine. */
final class OfficePreview implements AutoCloseable {
    private final ZipFile zip;
    private int budget=24*1024*1024;
    private final Map<String,String> theme=new HashMap<>();
    private final Map<String,Element> wordStyles=new HashMap<>();
    private final Set<String> warnings=new LinkedHashSet<>();
    static final class Preview {
        final List<String> pages=new ArrayList<>();
        String notice;
    }
    private OfficePreview(File file)throws IOException{zip=new ZipFile(file);}
    public void close()throws IOException{zip.close();}
    static boolean supports(String name){String e=DocumentText.extension(name);return e.equals("docx")||e.equals("pptx");}
    static Preview read(File file,String name)throws Exception {
        try(OfficePreview r=new OfficePreview(file)){return r.render(DocumentText.extension(name));}
    }
    private byte[] bytes(String path)throws IOException {
        ZipEntry entry=zip.getEntry(path);if(entry==null)throw new IOException("Missing part: "+path);
        if(entry.getSize()>budget)throw new IOException("Preview exceeds the 24 MB expanded-content limit.");
        try(InputStream in=zip.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){budget-=n;if(budget<0)throw new IOException("Preview exceeds the 24 MB expanded-content limit.");out.write(b,0,n);}return out.toByteArray();
        }
    }
    private Element xml(String path)throws Exception {
        byte[] data=bytes(path);
        // Reject DTDs before parsing, including UTF-16/32 encoded XML.
        String probe=new String(data,java.nio.charset.StandardCharsets.ISO_8859_1).replace("\u0000","");
        if(probe.contains("<!DOCTYPE")||probe.contains("<!ENTITY"))throw new IOException("External entities are not supported.");
        javax.xml.parsers.SAXParserFactory sf=javax.xml.parsers.SAXParserFactory.newInstance();sf.setNamespaceAware(true);
        org.xml.sax.XMLReader reader=sf.newSAXParser().getXMLReader();reader.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("External entities blocked");});
        reader.setContentHandler(new org.xml.sax.helpers.DefaultHandler(){int depth,count;public void startElement(String u,String l,String q,org.xml.sax.Attributes a)throws org.xml.sax.SAXException{if(++depth>128||++count>100000)throw new org.xml.sax.SAXException("Document structure exceeds preview limits");}public void endElement(String u,String l,String q){depth--;}});
        reader.parse(new InputSource(new ByteArrayInputStream(data)));
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setExpandEntityReferences(false);
        DocumentBuilder b=f.newDocumentBuilder();b.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("External entities blocked");});
        return b.parse(new ByteArrayInputStream(data)).getDocumentElement();
    }
    private static String local(Node n){return n.getLocalName()==null?n.getNodeName():n.getLocalName();}
    private static List<Element> children(Element e){List<Element> out=new ArrayList<>();if(e!=null)for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element)out.add((Element)n);return out;}
    private static Element child(Element e,String name){for(Element n:children(e))if(local(n).equals(name))return n;return null;}
    private static Element first(Element e,String name){if(e==null)return null;if(local(e).equals(name))return e;NodeList ns=e.getElementsByTagNameNS("*",name);return ns.getLength()==0?null:(Element)ns.item(0);}
    private static String attr(Element e,String name){if(e==null)return "";NamedNodeMap a=e.getAttributes();for(int i=0;i<a.getLength();i++)if(local(a.item(i)).equals(name))return a.item(i).getNodeValue();return "";}
    private static double num(String value,double fallback){try{double n=Double.parseDouble(value);return Double.isFinite(n)?Math.max(-1e8,Math.min(1e8,n)):fallback;}catch(Exception e){return fallback;}}
    private static String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private static String path(String source,String target)throws IOException {
        if(target.contains(":")||target.contains("\\")||target.contains("?")||target.contains("#"))throw new IOException("Unsupported document link");
        String raw=target.startsWith("/")?target.substring(1):source.substring(0,source.lastIndexOf('/')+1)+target;
        Deque<String> parts=new ArrayDeque<>();for(String p:raw.split("/")){if(p.equals("..")){if(parts.isEmpty())throw new IOException("Invalid document path");parts.removeLast();}else if(!p.isEmpty()&&!p.equals("."))parts.add(p);}return String.join("/",parts);
    }
    private Map<String,String> rels(String source)throws Exception {
        String rp=source.substring(0,source.lastIndexOf('/')+1)+"_rels/"+source.substring(source.lastIndexOf('/')+1)+".rels";
        Map<String,String> out=new HashMap<>();if(zip.getEntry(rp)==null)return out;
        for(Element r:children(xml(rp)))if(!attr(r,"TargetMode").equals("External"))out.put(attr(r,"Id"),path(source,attr(r,"Target")));return out;
    }
    private String related(String source,String kind)throws Exception {
        String rp=source.substring(0,source.lastIndexOf('/')+1)+"_rels/"+source.substring(source.lastIndexOf('/')+1)+".rels";if(zip.getEntry(rp)==null)return null;
        for(Element r:children(xml(rp)))if(attr(r,"Type").endsWith("/"+kind)&&!attr(r,"TargetMode").equals("External"))return path(source,attr(r,"Target"));return null;
    }
    private String color(Element e,String fallback){if(e==null)return fallback;Element rgb=first(e,"srgbClr");String v=attr(rgb,"val");if(v.matches("[0-9a-fA-F]{6}"))return "#"+v;String key=attr(first(e,"schemeClr"),"val");return theme.getOrDefault(key,fallback);}
    private static String wordColor(Element e,String fallback){String v=attr(e,"val");return v.matches("[0-9a-fA-F]{6}")?"#"+v:fallback;}
    private String image(String source,String id)throws Exception {
        String target=rels(source).get(id);if(target==null){warnings.add("Some linked images are unavailable offline.");return "";}
        String ext=DocumentText.extension(target);String mime=ext.equals("png")?"image/png":ext.equals("jpg")||ext.equals("jpeg")?"image/jpeg":ext.equals("gif")?"image/gif":ext.equals("webp")?"image/webp":"";
        if(mime.isEmpty()){warnings.add("Some vector or embedded objects need an Office viewer.");return "";}
        return "data:"+mime+";base64,"+Base64.getEncoder().encodeToString(bytes(target));
    }
    private String wrap(String html,int width){return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width="+width+"'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; img-src data:; style-src 'unsafe-inline'\"><style>body{margin:0;background:#e9e9ed;color:#111;font-family:Arial,sans-serif}*{box-sizing:border-box}p{margin:0 0 8px;white-space:pre-wrap}table{border-collapse:collapse;table-layout:fixed;width:100%}td{border:1px solid #999;padding:6px;vertical-align:top;overflow-wrap:anywhere}img{max-width:100%;object-fit:contain}.page{background:white;margin:12px auto;overflow:hidden}.slide{position:relative;overflow:hidden} .object{position:absolute;overflow:hidden;white-space:pre-wrap} .notice{padding:14px;color:#444;font-size:14px}</style></head><body>"+html+"</body></html>";}
    private Preview render(String ext)throws Exception {
        Preview out=new Preview();String tp=ext.equals("pptx")?"ppt/theme/theme1.xml":"word/theme/theme1.xml";
        if(zip.getEntry(tp)!=null){Element scheme=first(xml(tp),"clrScheme");for(Element c:children(scheme)){String v=attr(first(c,"srgbClr"),"val");if(v.isEmpty())v=attr(first(c,"sysClr"),"lastClr");if(v.matches("[0-9a-fA-F]{6}"))theme.put(local(c),"#"+v);}}
        theme.put("tx1",theme.getOrDefault("dk1","#111111"));theme.put("bg1",theme.getOrDefault("lt1","#ffffff"));theme.put("tx2",theme.getOrDefault("dk2","#222222"));theme.put("bg2",theme.getOrDefault("lt2","#eeeeee"));
        if(ext.equals("docx")) {
            if(zip.getEntry("word/styles.xml")!=null)for(Element st:children(xml("word/styles.xml")))if(local(st).equals("style"))wordStyles.put(attr(st,"styleId"),st);
            Element document=xml("word/document.xml"),body=first(document,"body"),section=first(document,"sectPr");
            double width=num(attr(first(section,"pgSz"),"w"),12240)/15;
            width=Math.max(320,Math.min(2000,width));
            Element margins=first(section,"pgMar");double left=Math.max(0,Math.min(width/3,num(attr(margins,"left"),720)/15)),right=Math.max(0,Math.min(width/3,num(attr(margins,"right"),720)/15)),top=Math.max(0,Math.min(200,num(attr(margins,"top"),720)/15));
            StringBuilder html=new StringBuilder("<main class='page' style='width:"+width+"px;min-height:1000px;padding:"+top+"px "+right+"px 48px "+left+"px'>");
            Map<String,String> references=rels("word/document.xml");
            Element headerRef=first(section,"headerReference");String headerPath=references.get(attr(headerRef,"id"));
            if(headerPath!=null){html.append("<header>");for(Element e:children(xml(headerPath)))html.append(wordBlock(e,headerPath));html.append("</header>");}
            for(Element e:children(body))html.append(wordBlock(e,"word/document.xml"));
            Element footerRef=first(section,"footerReference");String footerPath=references.get(attr(footerRef,"id"));if(footerPath!=null){html.append("<footer>");for(Element e:children(xml(footerPath)))html.append(wordBlock(e,footerPath));html.append("</footer>");}html.append("</main>");out.pages.add(wrap(html.toString(),(int)width+24));
            warnings.add("Document preview: page breaks, fonts, floating objects and pagination may differ from Office.");
        }else {
            Element presentation=xml("ppt/presentation.xml");Element size=first(presentation,"sldSz");double w=num(attr(size,"cx"),9144000)/9525,h=num(attr(size,"cy"),6858000)/9525;
            w=Math.max(320,Math.min(3000,w));h=Math.max(200,Math.min(3000,h));Map<String,String> slides=rels("ppt/presentation.xml");
            for(Element id:children(first(presentation,"sldIdLst"))){String relationship=id.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id");if(relationship.isEmpty())relationship=id.getAttribute("r:id");String source=slides.get(relationship);if(source==null)continue;if(out.pages.size()>=300)throw new IOException("Preview is limited to 300 slides.");
                Element slide=xml(source);String layoutPath=related(source,"slideLayout"),masterPath=layoutPath==null?null:related(layoutPath,"slideMaster");Element layout=layoutPath==null?null:xml(layoutPath),master=masterPath==null?null:xml(masterPath);
                String background=color(first(slide,"bg"),color(first(layout,"bg"),color(first(master,"bg"),"#ffffff")));
                StringBuilder html=new StringBuilder("<main class='page slide' style='width:"+w+"px;height:"+h+"px;background:"+background+"'>");
                if(master!=null&&!attr(slide,"showMasterSp").equals("0"))html.append(shapes(first(master,"spTree"),masterPath,null,null,true));
                if(layout!=null)html.append(shapes(first(layout,"spTree"),layoutPath,null,master,true));
                html.append(shapes(first(slide,"spTree"),source,layout,master,false)).append("</main>");out.pages.add(wrap(html.toString(),(int)w+24));
            }
            warnings.add("Slide preview: animations, SmartArt, charts, grouped shapes and some inherited styling need an Office viewer.");
        }
        if(out.pages.isEmpty())throw new IOException("No document pages found.");out.notice=String.join(" ",warnings);return out;
    }
    private String wordBlock(Element e,String source)throws Exception {
        switch(local(e)){
            case "p":return wordParagraph(e,source);
            case "tbl":StringBuilder table=new StringBuilder("<table>");for(Element row:children(e))if(local(row).equals("tr")){table.append("<tr>");for(Element cell:children(row))if(local(cell).equals("tc")){int span=(int)num(attr(first(child(cell,"tcPr"),"gridSpan"),"val"),1);table.append("<td colspan='").append(Math.max(1,Math.min(100,span))).append("'>");for(Element part:children(cell))table.append(wordBlock(part,source));table.append("</td>");}table.append("</tr>");}return table.append("</table>").toString();
            case "sdt":StringBuilder nested=new StringBuilder();for(Element part:children(child(e,"sdtContent")))nested.append(wordBlock(part,source));return nested.toString();
            default:return "";
        }
    }
    private String wordParagraph(Element e,String source)throws Exception {
        Element pp=child(e,"pPr"),style=wordStyles.get(attr(child(pp,"pStyle"),"val"));String align=attr(child(pp,"jc"),"val");if(align.isEmpty())align=attr(first(style,"jc"),"val");if(align.equals("both"))align="justify";if(!Arrays.asList("left","right","center","justify").contains(align))align="left";
        StringBuilder out=new StringBuilder("<p style='text-align:"+align+";"+runStyle(child(style,"rPr"),true)+"'>");if(child(pp,"numPr")!=null)out.append("• ");
        for(Element part:children(e))out.append(wordInline(part,source));return out.append("</p>").toString();
    }
    private String wordInline(Element e,String source)throws Exception {
        StringBuilder out=new StringBuilder();String name=local(e);
        if(name.equals("r")){out.append("<span style='").append(runStyle(child(e,"rPr"),true)).append("'>");for(Element part:children(e))out.append(wordInline(part,source));return out.append("</span>").toString();}
        if(name.equals("t"))return esc(e.getTextContent());if(name.equals("br"))return "<br>";if(name.equals("tab"))return "&#8195;";
        if(name.equals("drawing")){Element blip=first(e,"blip"),extent=first(e,"extent");String src=image(source,attr(blip,"embed"));if(src.isEmpty())return "<span>[Image requires Office viewer]</span>";double width=num(attr(extent,"cx"),2857500)/9525,height=num(attr(extent,"cy"),1905000)/9525;return "<img alt='Document image' src='"+src+"' style='width:"+Math.max(1,width)+"px;height:"+Math.max(1,height)+"px'>";}
        if(name.equals("hyperlink")||name.equals("smartTag")||name.equals("ins"))for(Element part:children(e))out.append(wordInline(part,source));return out.toString();
    }
    private String runStyle(Element r,boolean word){if(r==null)return "";StringBuilder s=new StringBuilder();String size=attr(word?child(r,"sz"):r,word?"val":"sz");if(!size.isEmpty())s.append("font-size:").append(Math.max(5,Math.min(180,num(size,word?22:1800)/(word?2:100)))).append("pt;");
        if(word?child(r,"b")!=null&&!attr(child(r,"b"),"val").equals("0"):attr(r,"b").equals("1"))s.append("font-weight:bold;");
        if(word?child(r,"i")!=null&&!attr(child(r,"i"),"val").equals("0"):attr(r,"i").equals("1"))s.append("font-style:italic;");
        if(word?child(r,"u")!=null&&!attr(child(r,"u"),"val").equals("none"):!attr(r,"u").isEmpty()&&!attr(r,"u").equals("none"))s.append("text-decoration:underline;");
        if(word&&child(r,"color")!=null)s.append("color:").append(wordColor(child(r,"color"),"#111111")).append(';');else if(!word&&child(r,"solidFill")!=null)s.append("color:").append(color(child(r,"solidFill"),"#111111")).append(';');return s.toString();
    }
    private Element placeholder(Element shape,Element tree){Element ph=first(shape,"ph");if(ph==null||tree==null)return null;String index=attr(ph,"idx"),type=attr(ph,"type");for(Element candidate:children(first(tree,"spTree"))){Element other=first(candidate,"ph");if(other!=null&&(!index.isEmpty()?index.equals(attr(other,"idx")):type.equals(attr(other,"type"))))return candidate;}return null;}
    private String shapes(Element tree,String source,Element layout,Element master,boolean skipPlaceholders)throws Exception {
        StringBuilder out=new StringBuilder();for(Element shape:children(tree)){
            String kind=local(shape);if(!Arrays.asList("sp","pic","graphicFrame","cxnSp","grpSp").contains(kind))continue;
            if(skipPlaceholders&&first(shape,"ph")!=null)continue;
            if(kind.equals("grpSp")){warnings.add("Grouped artwork may be omitted.");continue;}
            Element fallback=placeholder(shape,layout);if(fallback==null)fallback=placeholder(shape,master);
            Element x=first(shape,"xfrm");if(x==null)x=first(fallback,"xfrm");if(x==null){warnings.add("Some object positions are unavailable.");continue;}
            Element off=child(x,"off"),ext=child(x,"ext");double left=num(attr(off,"x"),0)/9525,top=num(attr(off,"y"),0)/9525,width=num(attr(ext,"cx"),3000000)/9525,height=num(attr(ext,"cy"),500000)/9525,rotation=num(attr(x,"rot"),0)/60000;
            Element props=child(shape,"spPr"),fill=child(props,"solidFill"),ln=child(props,"ln");String style="left:"+left+"px;top:"+top+"px;width:"+Math.max(1,width)+"px;height:"+Math.max(1,height)+"px;transform:rotate("+rotation+"deg);";
            if(fill!=null)style+="background:"+color(fill,"transparent")+";";if(ln!=null&&child(ln,"noFill")==null)style+="border:1px solid "+color(ln,"#888888")+";";String preset=attr(first(props,"prstGeom"),"prst");if(preset.equals("ellipse"))style+="border-radius:50%;";if(preset.equals("roundRect"))style+="border-radius:14px;";
            out.append("<div class='object' style='").append(style).append("'>");
            if(kind.equals("pic")){String src=image(source,attr(first(shape,"blip"),"embed"));out.append(src.isEmpty()?"[Image requires Office viewer]":"<img alt='Slide image' src='"+src+"' style='width:100%;height:100%'>");}
            else if(first(shape,"tbl")!=null)out.append(slideTable(first(shape,"tbl")));
            else if(first(shape,"chart")!=null||first(shape,"relIds")!=null)out.append("[Chart or diagram: open original in Office viewer]");
            else {Element tx=child(shape,"txBody"),bodyPr=child(tx,"bodyPr");String anchor=attr(bodyPr,"anchor"),justify=anchor.equals("ctr")?"center":anchor.equals("b")?"flex-end":"flex-start";out.append("<div style='height:100%;display:flex;flex-direction:column;justify-content:").append(justify).append(";padding:6px;font-size:18pt'>");for(Element para:children(tx))if(local(para).equals("p"))out.append(slideParagraph(para));out.append("</div>");}
            out.append("</div>");
        }return out.toString();
    }
    private String slideTable(Element table){StringBuilder s=new StringBuilder("<table>");for(Element row:children(table))if(local(row).equals("tr")){s.append("<tr>");for(Element cell:children(row))if(local(cell).equals("tc")){s.append("<td>");for(Element p:children(child(cell,"txBody")))if(local(p).equals("p"))s.append(slideParagraph(p));s.append("</td>");}s.append("</tr>");}return s.append("</table>").toString();}
    private String slideParagraph(Element para){Element pr=child(para,"pPr");String a=attr(pr,"algn"),align=a.equals("ctr")?"center":a.equals("r")?"right":a.equals("just")?"justify":"left";StringBuilder s=new StringBuilder("<p style='text-align:"+align+";"+runStyle(child(pr,"defRPr"),false)+"'>");if(child(pr,"buChar")!=null)s.append(esc(attr(child(pr,"buChar"),"char"))).append(' ');for(Element run:children(para)){if(local(run).equals("br"))s.append("<br>");else if(local(run).equals("r")||local(run).equals("fld")){s.append("<span style='").append(runStyle(child(run,"rPr"),false)).append("'>");Element t=child(run,"t");if(t!=null)s.append(esc(t.getTextContent()));s.append("</span>");}}return s.append("</p>").toString();}
}
