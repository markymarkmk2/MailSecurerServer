/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.DemuxOutputStream;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Echo;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.Commandline;
import org.apache.tools.ant.types.Path;
import org.fuin.utils4j.Utils4J;

/**
 *
 * @author Administrator
 */
public class Loader {

   private static String classpath = "MailArchiv.jar;lib/LibShare.jar;lib/LibEWS.jar;lib/jilter-1.2.jar;"
            + "lib/activation.jar;lib/FastInfoset.jar;lib/gmbal-api-only.jar;lib/http.jar;"
            + "lib/jaxb-api.jar;lib/jaxb-impl.jar;lib/jaxb-xjc.jar;lib/jaxws-api.jar;lib/jaxws-rt.jar;lib/jaxws-tools.jar;"
            + "lib/jsr173_api.jar;lib/jsr181-api.jar;lib/jsr250-api.jar;lib/management-api.jar;lib/mimepull.jar;"
            + "lib/policy.jar;lib/resolver.jar;lib/saaj-api.jar;lib/saaj-impl.jar;lib/servlet.jar;lib/stax-ex.jar;"
            + "lib/streambuffer.jar;lib/woodstox.jar;"
            + "lib/log4j-1.2.15.jar;lib/commons-codec-1.3.jar;lib/mail.jar;lib/derby.jar;lib/tar.jar;lib/jdom.jar;"
            + "lib/poi-3.2-FINAL-20081019.jar;lib/poi-contrib-3.2-FINAL-20081019.jar;lib/poi-scratchpad-3.2-FINAL-20081019.jar;"
            + "lib/appsrvbridge.jar;lib/jsp-parser-ext.jar;lib/jstl.jar;"
            + "lib/servlet2.5-jsp2.1-api.jar;lib/standard.jar;lib/antlr-2.7.6.jar;lib/asm.jar;lib/asm-attrs.jar;"
            + "lib/cglib-2.1.3.jar;lib/commons-collections-2.1.1.jar;lib/commons-logging-1.1.jar;lib/dom4j-1.6.1.jar;"
            + "lib/ehcache-1.2.3.jar;lib/jdbc2_0-stdext.jar;lib/jta.jar;lib/hibernate3.jar;lib/hibernate-tools.jar;"
            + "lib/hibernate-annotations.jar;lib/hibernate-commons-annotations.jar;lib/hibernate-entitymanager.jar;"
            + "lib/javassist.jar;lib/ejb3-persistence.jar;lib/junit-3.8.2.jar;lib/junit-4.5.jar;lib/commons-codec-1.3.jar;"
            + "lib/commons-httpclient-3.1.jar;lib/bcprov-jdk16-144.jar;lib/xpp3_min-1.1.4c.jar;lib/xstream-1.3.1.jar;"
            + "lib/commons-lang-2.4.jar;lib/pdfbox-0.8.0-incubating.jar;lib/jempbox-0.8.0-incubating.jar;lib/fontbox-0.8.0-incubating.jar;"
            + "lib/lucene-core-3.0.2.jar;lib/lucene-analyzers-3.0.2.jar;lib/lucene-queries-3.0.2.jar;"
            + "lib/slf4j-api-1.5.6.jar;lib/slf4j-simple-1.5.6.jar;lib/subethasmtp.jar";



    private static ArrayList<String> get_jar_list_from_manifest()
    {
        try
        {
            Class clazz = Main.class;
            String className = clazz.getSimpleName() + ".class";
            String classPath = clazz.getResource(className).toString();
            if (!classPath.startsWith("jar"))
            {
                // Class not from JAR
                return null;
            }
            String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF";
            Manifest manifest = new Manifest(new URL(manifestPath).openStream());
            Attributes attr = manifest.getMainAttributes();

            if (attr != null)
            {
                System.out.println("Path: " + attr.getValue("Class-Path") );
                String[] jars = attr.getValue("Class-Path").split(" " );
                ArrayList<String> list = new ArrayList<String>();
                for (int i = 0; i < jars.length; i++)
                {
                    String string = jars[i];
                    if (string.length() > 0 )
                        list.add( string );
                }

                return list;
            }
        }
        catch (Exception exception)
        {
            System.err.println("Cannot read jar list: ");
            exception.printStackTrace(System.err);
            System.exit(1);
        }
        return null;


    }

    private static void write_start_script(String [] args)
    {
        ArrayList<String> list = get_jar_list_from_manifest();

        if (Main.is_win())
        {
            write_win_start_script(list, args);
        }
        else if(Main.is_osx())
        {
            write_osx_start_script(list, args);
        }
        else
        {
            write_linux_start_script(list, args);
        }
    }
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) 
    {
        if (args.length >= 1 && args[0].compareTo("-startscript") == 0)
        {
            write_start_script(args);
            return;
        }

  /*      final ClassLoader classLoader = Utils4J.class.getClassLoader();
        final URLClassLoader urlClassLoader = (URLClassLoader) classLoader;
        URL[] ulrs = urlClassLoader.getURLs();
*/
        Main.print_system_property( "java.class.path");

/*        System.out.println("URLS:" + ulrs.length);

        for (int i = 0; i < ulrs.length; i++)
        {
            URL url = ulrs[i];
            System.out.println( url.toString());
        }


        String[] jars = classpath.split(";");
        for (int i = 0; i < jars.length; i++)
        {
            String string = jars[i];
            File f = new File("dist/" + string.trim());
            if (!f.exists())
            {
                f = new File(string.trim());
            }
            if (!f.exists())
            {
                System.out.println("Mist. fehlt: " + f.getName());
                System.exit(1);
            }
            try
            {
                URL u;
                u = f.toURI().toURL();
                Utils4J.addToClasspath(u);
            }
            catch (MalformedURLException malformedURLException)
            {
                System.out.println("Mist. geht nicht: " + f.getName());
                System.exit(1);
            }
            
        }*/
        StringBuilder sb = new StringBuilder();

        
        ArrayList<String> list = get_jar_list_from_manifest();
        String deli = ":";
        if (Main.is_win())
            deli = ";";

        String add_dir = "";
        if (new File("dist").exists())
            add_dir = "dist/";

        // US FIRST
        sb.append(add_dir).append("MailArchiv.jar");

        for (int i = 0; i < list.size(); i++)
        {
            
            sb.append(deli);
            sb.append(add_dir).append( list.get(i));
        }

        System.setProperty("java.class.path", sb.toString());


        Project project = new Project();
        project.setBaseDir(new File(System.getProperty("user.dir")));
        project.init();
        DefaultLogger logger = new DefaultLogger();
        project.addBuildListener(logger);
        logger.setOutputPrintStream(System.out);
        logger.setErrorPrintStream(System.err);
        logger.setMessageOutputLevel(Project.MSG_INFO);
        System.setOut(new PrintStream(new DemuxOutputStream(project, false)));
        System.setErr(new PrintStream(new DemuxOutputStream(project, true)));
        project.fireBuildStarted();

        System.out.println("running");
        Throwable caught = null;
        try {
                Echo echo = new Echo();
                echo.setTaskName("Echo");
                echo.setProject(project);
                echo.init();
                echo.setMessage("Launching Some Class");
                echo.execute();

                Java javaTask = new Java();
                javaTask.setTaskName("MailSecurer");
                javaTask.setProject(project);
                javaTask.setFork(true);
                javaTask.setFailonerror(true);
                javaTask.setClassname(Main.class.getName());
                javaTask.setCloneVm( false );
                Path path = new Path(project,  sb.toString() );

                for (int i = 0; i < args.length; i++)
                {
                    String string = args[i];
                    Commandline.Argument arg = javaTask.createArg();
                    arg.setValue(string);
                }
                javaTask.setClasspath(path);
                javaTask.init();

                int ret = javaTask.executeJava();
                System.out.println("java task return code: " + ret);

        } 
        catch (BuildException e)
        {
            caught = e;
        }
        project.log("finished");
        project.fireBuildFinished(caught);


//        Main.main(args);
        
        // TODO code application logic here
    }

    private static void write_win_start_script(ArrayList<String> jars, String[] args)
    {

        StringBuilder cmdline = new StringBuilder( "cmdline = -Xmx512m -cp " );

        for (int i = 0; i < jars.size(); i++)
        {
            String string = jars.get(i);
            if (i > 0)
                cmdline.append(";");
            cmdline.append( string);
        }

        cmdline.append(";MailArchiv.jar dimm.home.mailarchiv.Main");

        // i == 0 WAS OUR ARGUMENT -startscript
        for (int i = 1; i < args.length; i++)
        {
            String string = args[i];
            if (i > 0)
                cmdline.append(" ");

            cmdline.append( string );
        }
        File startup_file = new File("J:\\Develop\\Java\\JMailArchiv\\Installer\\ServerStart\\mss.ini");

        // KEEP ENOUGH SPACE IF ALL WERE UNICODES...
        byte[] buff = new byte[ (int)startup_file.length() + cmdline.length() * 2 ];

        try
        {
            FileInputStream fis = new FileInputStream(startup_file);

            fis.read(buff);

            fis.close();

            String startup_str = new String(buff);
            int idx = startup_str.indexOf("cmdline =");
            if (idx < 0)
            {
                startup_str = startup_str + "\n" + cmdline.toString();
            }
            else
            {
                startup_str = startup_str.substring(0, idx) + cmdline.toString();
            }

            FileOutputStream fos = new FileOutputStream(startup_file);

            fos.write(startup_str.getBytes());

            fos.close();
        }
        catch (Exception exception)
        {
            System.err.println("Cannot create Startup file: ");
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void write_osx_start_script( ArrayList<String> list, String[] args )
    {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private static void write_linux_start_script( ArrayList<String> list, String[] args )
    {
        StringBuilder sb = new StringBuilder();
        sb.append("MailArchiv.jar");
        for (int i = 0; i < list.size(); i++)
        {
            String string = list.get(i);
            sb.append(":");
            sb.append(string);            
        }
        try
        {
            FileOutputStream fos = new FileOutputStream("jarlist.txt");

            fos.write(sb.toString().getBytes());

            fos.close();
        }
        catch (Exception exception)
        {
            System.err.println("Cannot create Startup file: ");
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

}
