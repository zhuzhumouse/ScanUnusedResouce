package com.chu;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Created by chulili on 2017/8/3.
 */

public class ScanAndClearUnusedResource {
    private static final String TAG = ScanAndClearUnusedResource.class.getSimpleName();

    private static boolean REAL_DEL = false; // 是否真的删除文件

    private final static boolean DEL_JAVA = true; // 是否删除java文件

    private final static boolean NEED_READ_JAR = true; // 是否需要扫描jar

    private final static boolean FULL_LOG = false;// 是否显示具体Log

    private final static boolean DEL_ALL = true;

    private static ArrayList<String> allUnusedExp = null;

    // 存在拼接查找资源的逻辑
    private final static String[] HOLD = new String[]{
            "cate_", "music_top_fans_", "gift_num_", "ico_hot_"
    };

    public final static String ROOT_DIR = "E:\\pps_svn\\Android O\\WebpSample\\";

    public final static String[] EXCLUDE_DIR = {
            ".git",
            ".gitignore",
            ".gradle",
            ".idea",
            "build",
            "gradle"
    };


    public final static String[] INCLUDE_DIR = {
            "app"
    };

    public final static String[] TO_CLEAR_PROJECTS = {
            "app"
    };

    public static List<String> TO_CLEAR = new ArrayList<>();

    private final static String[] XML_DIRS = {"/res/layout", "/res/menu", "/res/xml", "/res/anim", "/res/drawable"};
    private final static String[] IMG_DIRS = {"/res/drawable", "/res/drawable-hdpi", "/res/drawable-xhdpi", "/res/drawable-xxhdpi", "/res/drawable-xxxhdpi"};

    // 保存所有代码，以文件名做key
    private final static HashMap<String, String> codeMap = new HashMap<String, String>();

    private static int delFiles = 0;
    private static long totalBytes = 0;
    private static long delXMLBytes = 0;
    private static long delIMGBytes = 0;

    public static void main(String[] args) {

        getAllUnusedResExcept();

        // 加载所有代码到内存中
        loadCode();

        System.out.print("***************************************\n");

        int lastDelFiles = -1;
        while (true) {
            lastDelFiles = delFiles;
            if (!REAL_DEL) {
                delFiles = 0;
                totalBytes = 0;
                delXMLBytes = 0;
                delIMGBytes = 0;
            }

            if (DEL_ALL) {
                // 代码
                if (DEL_JAVA) {
                    for (String p : INCLUDE_DIR) {
                        File java_root = new File(ROOT_DIR + p + "/src/main/java");
                        if (!java_root.exists()) {
                            java_root = new File(ROOT_DIR + p + "/src");
                        }
                        delUnusedJava(java_root);
                    }
                }

                // 扫描所有XML
                for (String p : INCLUDE_DIR) {
                    File xml_root = new File(ROOT_DIR + p + "/src/main");
                    if (!xml_root.exists()) {
                        xml_root= new File(ROOT_DIR + p);
                    }
                    for (String dir : XML_DIRS) {
                        File f = new File(xml_root.getAbsoluteFile() + dir);
                        delUnusedXml(f);
                    }
                }

                // Image
                for (String p : INCLUDE_DIR) {
                    File image_root = new File(ROOT_DIR + p + "/src/main");
                    if (!image_root.exists()) {
                        image_root = new File(ROOT_DIR + p);
                    }
                    for (String dir : IMG_DIRS) {
                        File f = new File(image_root.getAbsoluteFile() + dir);
                        delUnusedImage(f);
                    }
                }

            } else {
                // 代码
                if (DEL_JAVA) {
                    for (String p : TO_CLEAR_PROJECTS) {
                        File f = new File(ROOT_DIR + p + "/src");
                        delUnusedJava(f);
                    }
                }

                // 扫描所有XML
                for (String p : TO_CLEAR_PROJECTS) {
                    for (String dir : XML_DIRS) {
                        File f = new File(ROOT_DIR + p + dir);
                        delUnusedXml(f);
                    }
                }

                // Image
                for (String p : TO_CLEAR_PROJECTS) {
                    for (String dir : IMG_DIRS) {
                        File f = new File(ROOT_DIR + p + dir);
                        delUnusedImage(f);
                    }
                }

            }

            System.out.println("可删除文件数：" + delFiles);
            if (delFiles == lastDelFiles) {
                break;
            }
        }

        System.out.println("最终删除文件数：" + delFiles);
        System.out.println("删除的XML字节数：" + delXMLBytes);
        System.out.println("删除的IMG字节数：" + delIMGBytes);
    }

    // 加载所有代码（包括xml和java）
    private static void loadCode() {
        codeMap.clear();
        for (String dirName : INCLUDE_DIR) {
            File project = new File(ROOT_DIR + dirName);
            if (project.isFile()) {
                continue;
            }

            File dir = null;

//            System.out.println(project.getAbsolutePath());

            String filename = project.getAbsolutePath() + "\\src\\main";
            File src_main_file = new File(filename);

            if(src_main_file.exists()) {
                dir = new File(filename, "/java");
                loadDirCode(dir);

                dir = new File(filename, "/res");
                loadDirCode(dir);

                dir = new File(filename, "/AndroidManifest.xml");
                if (dir.exists()) {
                    loadFileCode(dir);
                }

            } else {
                dir = new File(project, "/src");
                loadDirCode(dir);

                dir = new File(project, "/res");
                loadDirCode(dir);
            }

            if (NEED_READ_JAR) {
                dir = new File(project, "/libs");
                loadContantsInJar(dir);
            }

            dir = new File(project, "/AndroidManifest.xml");
            if (dir.exists()) {
                loadFileCode(dir);
            }

        }
        System.out.println("初始文件总数：" + codeMap.size());
        System.out.println("代码字节数：" + totalBytes);
    }

    // 筛选出没有引用的java文件并且删除
    private static void delUnusedJava(File dir) {
        if (codeMap == null || codeMap.size() == 0) {
            System.err.println("textMap没有初始化");
        }
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }

        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                delUnusedJava(f);
            } else {
                String fileName = f.getAbsolutePath();
                if (fileName.endsWith(".java") && !isExclude(f)) {
                    if (!containsStringByJava(f)) {
                        logD(f);
                        codeMap.remove(f.getAbsolutePath());
                        delXMLBytes += f.length();
                        delFiles++;
                        if (REAL_DEL) {
                            f.delete();
                        }
                    }
                }
            }
        }
    }

    private static void logD(File f) {
        System.out.println(f.getAbsolutePath());
    }

    // 筛选出没有引用的xml文件并且删除
    private static void delUnusedXml(File dir) {
        if (codeMap == null || codeMap.size() == 0) {
            System.err.println("textMap没有初始化");
        }
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }

        for (File f : dir.listFiles()) {
            String fileName = f.getAbsolutePath();
            if (fileName.endsWith(".xml") && !isExclude(f)) {
                if (!containsString(f)) {
                    logD(f);
                    codeMap.remove(f.getAbsolutePath());
                    delXMLBytes += f.length();
                    delFiles++;
                    if (REAL_DEL) {
                        f.delete();
                    }
                }
            }
        }
    }

    // 筛选出没有引用的图片文件并且删除
    private static void delUnusedImage(File dir) {
        if (codeMap.size() == 0) {
            System.err.println("textMap没有初始化");
        }
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        for (File f : dir.listFiles()) {
            String fileName = f.getName();
            if ((fileName.endsWith(".9.png") || fileName.endsWith(".png") || fileName.endsWith(".jpg"))  && !isExclude(f)) {
                if (!containsString(f)) {
                    logD(f);
                    codeMap.remove(f.getAbsolutePath());
                    delIMGBytes += f.length();
                    delFiles++;
                    if (REAL_DEL) {
                        f.delete();
                    }
                }
            }
        }
    }

    // 查找指定字符串
    private static boolean containsString(File currentFile) {
        String currentName = currentFile.getName();
        String currentFilePath = currentFile.getAbsolutePath();
        String fileName = currentName.substring(0, currentName.indexOf("."));

        // TODO; 存在拼接查找资源的逻辑
        for (String s : HOLD) {
            if (fileName.startsWith(s)) {
                return true;
            }
        }

        int c = 0;
        for (String filePath : codeMap.keySet()) {
            if (filePath.equals(currentFilePath)) {
                continue;
            }
            String text = codeMap.get(filePath);
            if (text.contains(fileName)) {
                c++;
                if (filePath.contains(".jar")) {
                    if (FULL_LOG) {
                        System.err.println("###" + filePath + "包含" + currentFilePath);
                    }
                }
                break;
            }
        }

        return c > 0;
    }

    // 查找指定字符串
    private static boolean containsStringByJava(File currentFile) {
        String currentName = currentFile.getName();
        String currentFilePath = currentFile.getAbsolutePath();
        String fileName = currentName.substring(0, currentName.indexOf("."));

        int c = 0;
        for (String filePath : codeMap.keySet()) {
//            if (!filePath.endsWith(".java") && !filePath.endsWith("AndroidManifest.xml")) {
//                continue;
//            }
            if (filePath.equals(currentFilePath)) {
                continue;
            }
            String text = codeMap.get(filePath);
            if (text.contains(fileName)) {
                c++;
                break;
            }
        }

        if (c == 0) {
            return false;
        } else {
            return true;
        }
    }

    // 只加载jar包中String类型的常量
    private static void loadContantsInJar(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            for (File file : f.listFiles()) {
                loadContantsInJar(file);
            }
        } else {
            if (f.getName().endsWith(".jar")) {
                readJar(f);
            }
        }
    }

    private static void readJar(File f) {
        JarFile jarfile = null;
        StringBuilder sb = new StringBuilder();
        try {
            jarfile = new JarFile(f);
            Enumeration<JarEntry> entryList = jarfile.entries();
            while (entryList.hasMoreElements()) {
                JarEntry jarentry = (JarEntry) entryList.nextElement();
                if (!jarentry.getName().endsWith(".class")) {
                    continue;
                }
                try {
                    InputStream is = jarfile.getInputStream(jarentry);
                    readContantsInClass(is, sb);
                    is.close();
                } catch (IOException e) {
                }
            }
            jarfile.close();
        } catch (IOException e) {
        }
        codeMap.put(f.getAbsolutePath(), sb.toString());
    }

    private static void readContantsInClass(InputStream is, StringBuilder sb) throws IOException {
        DataInputStream dis = new DataInputStream(is);
        int magic = 0xCAFEBABE;
        if (!(magic == dis.readInt())) {
            dis.close();
            return;
        }

        dis.readShort(); //minor_version
        dis.readShort();//major_version
        int constant_pool_count = dis.readShort();

	/*		常量池中数据项类型		类型标志 	类型描述
            CONSTANT_Utf8				1		UTF-8编码的Unicode字符串
			CONSTANT_Integer			3		int类型字面值
			CONSTANT_Float				4		float类型字面值
			CONSTANT_Long				5		long类型字面值
			CONSTANT_Double				6		double类型字面值
			CONSTANT_Class				7		对一个类或接口的符号引用
			CONSTANT_String	            8		String类型字面值
			CONSTANT_Fieldref			9		对一个字段的符号引用
			CONSTANT_Methodref			10		对一个类中声明的方法的符号引用
			CONSTANT_InterfaceMethodref	11		对一个接口中声明的方法的符号引用
			CONSTANT_NameAndType		12		对一个字段或方法的部分符号引用
	 */
        for (int i = 1; i < constant_pool_count; i++) { // 常量池
            int tag = dis.readByte();
            switch (tag) {
                case 1: //CONSTANT_Utf8
                    short len = dis.readShort();
                    if (len < 0) {
                        System.out.println("len " + len);
                        continue;
                    }
                    byte[] bs = new byte[len];
                    dis.read(bs);
                    pln(new String(bs), sb);
                    continue;
                case 3: //CONSTANT_Integer
                    int v_int = dis.readInt();
                    pln(v_int, sb);
                    continue;
                case 4: //CONSTANT_Float
                    float v_float = dis.readFloat();
                    pln(v_float, sb);
                    continue;
                case 5: //CONSTANT_Long
                    long v_long = dis.readLong();
                    pln(v_long, sb);
                    continue;
                case 6: //CONSTANT_Double
                    double v_double = dis.readDouble();
                    pln(v_double, sb);
                    continue;
                case 7: //CONSTANT_String
                    dis.readShort();
                    continue;
                case 8: //CONSTANT_String
                    dis.readShort();
                    continue;
                case 9: //CONSTANT_Fieldref_info
                    dis.readShort(); //指向一个CONSTANT_Class_info数据项
                    dis.readShort(); //指向一个CONSTANT_NameAndType_info
                    continue;
                case 10: //CONSTANT_Methodref_info
                    dis.readShort(); //指向一个CONSTANT_Class_info数据项
                    dis.readShort(); //指向一个CONSTANT_NameAndType_info
                    continue;
                case 11: //CONSTANT_InterfaceMethodref_info
                    dis.readShort(); //指向一个CONSTANT_Class_info数据项
                    dis.readShort(); //指向一个CONSTANT_NameAndType_info
                    continue;
                case 12:
                    dis.readShort();
                    dis.readShort();
                    continue;
                default:
                    return;
            }
        }
    }

    private static void pln(Object string, StringBuilder sb) {
        sb.append(string.toString()).append("\n");
    }

    // 加载文件夹中的代码
    private static void loadDirCode(File dir) {
//		System.out.println("加载代码：" + dir.getAbsolutePath());
        if (dir == null || !dir.exists() || dir.isFile() || dir.listFiles() == null) {
            return;
        }

//        System.out.println(dir.getAbsoluteFile());

        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                loadDirCode(f);
            } else {
                if (f.getName().endsWith(".xml") || f.getName().endsWith(".java")) {
                    loadFileCode(f);
                }
            }
        }
    }

    // 加载代码
    private static void loadFileCode(File f) {
        if (f == null || f.isDirectory() || !f.exists()) {
            return;
        }

        if (isExclude(f)) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        try {
            FileReader fr = new FileReader(f);
            BufferedReader sr = new BufferedReader(fr);
            String s = null;
            do {
                s = sr.readLine();
                if (s != null) {
                    sb.append(s).append("\n");
                } else {
                    break;
                }
            } while (true);
//			System.out.println(f.getAbsolutePath());
            codeMap.put(f.getAbsolutePath(), sb.toString());
            totalBytes += sb.toString().length();
            sr.close();
        } catch (Exception e) {
        }
    }

    private static void getAllUnusedResExcept() {
        allUnusedExp = new ArrayList<String>();
        try {
            FileReader fileReader = new FileReader(new File("E:\\pps_svn\\Android N\\APK_Lose_Weight\\ScanUnusedResource\\app\\src\\unused_res_except.txt"));
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String  line = "";
            while(true){
                line = bufferedReader.readLine();
                if (line != null && !line.isEmpty()) {
                    allUnusedExp.add(line);
                } else {
                    break;
                }
            }
        } catch (FileNotFoundException e){
            e.printStackTrace();
            Log.d(TAG, e.getMessage());
        } catch (IOException E){

        } finally{

        }

    }

    public static boolean isExclude(File file) {

        String  filepath = file.getAbsolutePath();

        for (int i = 0; i < allUnusedExp.size(); i ++){
            String filename = allUnusedExp.get(i);

            if (filepath.equals(filename) || file.getName().equals(filename)) {
//                System.out.print("-----------------------" + filepath + "\n");
                return true;
            }
        }

        return false;
    }
}
