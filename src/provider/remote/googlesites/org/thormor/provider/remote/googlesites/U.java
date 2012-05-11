package org.thormor.provider.remote.googlesites;

// grab bag of utilities

import org.thormor.provider.IProgressMonitor;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import org.json2012.JSONObject;
import org.json2012.JSONArray;
import org.json2012.JSONException;
import org.json2012.JSONTokener;

class U
{
    static boolean isEmpty(String s)
    { return ((s == null) || (s.length()==0)); }

    static byte[] getBytes(String in)
    {
        try { return in.getBytes("utf-8"); }
        catch (UnsupportedEncodingException uee) {
            throw new RuntimeException(uee);
        }
    }

    static String htmlEncode(String s)
    {
        StringBuilder out = new StringBuilder();
        char[] chars = s.toCharArray();
        for(int i=0; i<chars.length; i++) {
            char c = chars[i];
            if (c > 127 || c=='"' || c=='<' || c=='>'){
                out.append("&#"+(int)c+";");
            }
            else {
                out.append(c);
            }
        }
        return out.toString();
    }

    static JSONObject getJSON(InputStream inp)
        throws IOException
    {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(inp));
            return new JSONObject(new JSONTokener(br));
        }
        catch (JSONException jse) {
            throw new IOException(jse);
        }
        finally {
            if (br != null) {
                try { br.close(); }
                catch (IOException ign){}
            }
        }
    }

    static Element getRoot(InputStream inp)
        throws IOException
    {
        try {
            DocumentBuilderFactory dbFactory =
                DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder =
                dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inp);
            return doc.getDocumentElement();
        }
        catch (ParserConfigurationException pce) {
            throw new IOException(pce);
        }
        catch (SAXException se) {
            throw new IOException(se);
        }
        finally {
            try { inp.close(); }
            catch (IOException ign){}
        }
    }

    static String textUnder(Element el)
    {
        StringBuilder sb = new StringBuilder();
        NodeList nl = el.getChildNodes();
        if (nl == null) { return ""; }
        int len = nl.getLength();
        for (int i=0; i<len; i++) {
            Node n = nl.item(i);
            if (!(n instanceof Text)) {
                continue;
            }
            Text cur = (Text) n;
            sb.append(cur.getWholeText());
        }
        return sb.toString();
    }

    static Element getChild(Element el, String name)
    {
        NodeList nl = el.getChildNodes();
        if (nl == null) { return null; }
        int len = nl.getLength();
        for (int i=0; i<len; i++) {
            Node n = nl.item(i);
            if (!(n instanceof Element)) {
                continue;
            }
            Element cur = (Element) n;
            if (cur.getTagName().equals(name)) {
                return cur;
            }
        }
        return null;
    }

    static List<Element> getChildren(Element el, String name)
    {
        NodeList nl = el.getChildNodes();
        List<Element> ret = new ArrayList<Element>();
        if (nl == null) { return ret; }
        int len = nl.getLength();
        for (int i=0; i<len; i++) {
            Node n = nl.item(i);
            if (!(n instanceof Element)) {
                continue;
            }
            Element cur = (Element) n;
            if (cur.getTagName().equals(name)) {
                ret.add(cur);
            }
        }
        return ret;
    }

    static InputStream authGet(URL url, String atok, IProgressMonitor mon)
        throws IOException
    {
        if (mon != null) {
            mon.status("Connecting to url");
        }
        URLConnection con = url.openConnection();
        auth(con, atok);
        return con.getInputStream();
    }

    static InputStream authPostString
        (URL url, String atok, String content, IProgressMonitor mon)
        throws IOException
    {
        if (mon != null) {
            mon.status("Connecting to "+url);
        }

        URLConnection con = url.openConnection();
        con.setDoOutput(true);
        auth(con, atok);
        con.setRequestProperty("Content-type", "application/octet-stream");
        OutputStreamWriter wr = new OutputStreamWriter(con.getOutputStream());
        wr.write(content);
        wr.flush();
        return con.getInputStream();
    }

    static InputStream authPostMulti
        (URL url,String atok,String content,File payload,IProgressMonitor mon)
        throws IOException
    {
        if (mon != null) {
            mon.status("Connecting to "+url);
        }

        URLConnection con = url.openConnection();
        con.setDoOutput(true);
        auth(con, atok);
        con.setRequestProperty("Content-type", "multipart/related; boundary="+
                               BOUNDARY);

        OutputStream os = con.getOutputStream();

        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os));
        bw.write("--"+BOUNDARY+"\r\n");
        bw.write("Content-type: application/atom+xml\r\n\r\n");
        bw.write(content);
        bw.flush();
        bw.write("\r\n--"+BOUNDARY+"\r\n");
        bw.write("Content-Type: application/octet-stream\r\n\r\n");
        bw.flush();
        copy(payload, os);
        bw.write("\r\n--"+BOUNDARY+"--\r\n");
        bw.flush();
        return con.getInputStream();
    }

    static InputStream authPutFile
        (URL url,String atok,File payload,IProgressMonitor mon)
        throws IOException
    {
        if (mon != null) {
            mon.status("Connecting to "+url);
        }

        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        con.setDoOutput(true);
        con.setRequestMethod("PUT");
        auth(con, atok);
        con.setRequestProperty("Content-type", "application/octet-stream");
        con.setRequestProperty("If-Match", "*");

        OutputStream os = con.getOutputStream();
        copy(payload, os);
        os.flush();
        return con.getInputStream();
    }

    static InputStream authDelete
        (URL url, String atok, IProgressMonitor mon)
        throws IOException
    {
        if (mon != null) {
            mon.status("Connecting to "+url);
        }

        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        con.setRequestMethod("DELETE");
        auth(con, atok);
        con.setRequestProperty("If-Match", "*");
        return con.getInputStream();
    }

    static void copy(File src, OutputStream out)
        throws IOException
    {
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(src);
            byte[] buf = new byte[8192];
            int nread;

            while ((nread = fin.read(buf)) > 0) {
                out.write(buf, 0, nread);
            }
        }
        finally {
            if (fin != null) {
                try { fin.close(); } catch (IOException ign) {}
            }
        }
    }

    static InputStream post
        (URL url, Map<String,String> params, IProgressMonitor mon)
        throws IOException
    {
        if (mon != null) {
            mon.status("Connecting to "+url);
        }
        URLConnection con = url.openConnection();
        con.setDoOutput(true);

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String k: params.keySet()) {
            if (first) { first = false; }
            else { sb.append("&"); }
            sb.append(URLEncoder.encode(k, "utf-8"));
            sb.append("=");
            sb.append(URLEncoder.encode(params.get(k), "utf-8"));
        }
        OutputStreamWriter wr = new OutputStreamWriter(con.getOutputStream());
        wr.write(sb.toString());
        wr.flush();
        return con.getInputStream();
    }

    // purely to avoid checked exceptions
    static JSONObject put(JSONObject j, String k, String v)
    {
        try { return j.put(k, v); }
        catch (JSONException jse) {
            throw new RuntimeException(jse);
        }
    }
    static JSONObject put(JSONObject j, String k, int v)
    {
        try { return j.put(k, v); }
        catch (JSONException jse) {
            throw new RuntimeException(jse);
        }
    }
    static JSONObject put(JSONObject j, String k, long v)
    {
        try { return j.put(k, v); }
        catch (JSONException jse) {
            throw new RuntimeException(jse);
        }
    }
    static JSONObject put(JSONObject j, String k, JSONArray v)
    {
        try { return j.put(k, v); }
        catch (JSONException jse) {
            throw new RuntimeException(jse);
        }
    }
    static JSONObject put(JSONObject j, String k, JSONObject v)
    {
        try { return j.put(k, v); }
        catch (JSONException jse) {
            throw new RuntimeException(jse);
        }
    }

    // private helpers
    private static void auth(URLConnection con, String atok)
    {
        con.setRequestProperty("Authorization", "Bearer "+atok);
        con.setRequestProperty("GData-Version", "1.4");
    }

    private final static String BOUNDARY = "THIS_IS_A_BOUNDARY";
}
